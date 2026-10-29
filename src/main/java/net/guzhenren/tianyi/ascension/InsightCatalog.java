package net.guzhenren.tianyi.ascension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.guzhenren.tianyi.ascension.AscensionModel.InsightState;
import net.guzhenren.tianyi.ascension.AscensionModel.KnowledgeGrant;
import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;

/** Finite, evidence-backed questions for the concurrent 师法自然 process. */
public final class InsightCatalog {
    private InsightCatalog() {
    }

    /** Costs and partial-completeness bounds are explicit game-mapping parameters. */
    public record Question(String id, String knowledgeId, String evidenceRef,
                           double minimumCost, double completeCost,
                           double minimumCompleteness, double maximumPartialCompleteness) {
        public Question {
            id = AscensionModel.requireText(id, "question id");
            knowledgeId = AscensionModel.requireText(knowledgeId, "knowledge id");
            evidenceRef = AscensionModel.requireText(evidenceRef, "question evidence");
            if (id.length() > 128 || knowledgeId.length() > 256 || evidenceRef.length() > 512) {
                throw new IllegalArgumentException("insight question metadata is too long");
            }
            if (!Double.isFinite(minimumCost) || !Double.isFinite(completeCost)
                    || minimumCost <= 0.0 || completeCost <= minimumCost
                    || !Double.isFinite(minimumCompleteness) || !Double.isFinite(maximumPartialCompleteness)
                    || minimumCompleteness <= 0.0 || maximumPartialCompleteness < minimumCompleteness
                    || maximumPartialCompleteness >= 1.0) {
                throw new IllegalArgumentException("invalid question cost or partial-completeness mapping");
            }
        }
    }

    /** Sequence and revision are client request coordinates; tick/window evidence stays server-owned. */
    public record Request(long expectedRevision, long sequence, long serverTick, String questionId) {
        public Request {
            if (expectedRevision < 0 || sequence < 0 || serverTick < 0) {
                throw new IllegalArgumentException("negative insight request counter");
            }
            questionId = AscensionModel.requireText(questionId, "question id");
            if (questionId.length() > 128) {
                throw new IllegalArgumentException("question id is too long");
            }
        }
    }

    public enum Rejection {
        NONE,
        STALE_REVISION,
        INVALID_SEQUENCE,
        INVALID_SERVER_TICK,
        SERVER_VALIDATION_REQUIRED,
        PROFILE_DISABLED,
        PROFILE_MISMATCH,
        WINDOW_CLOSED,
        INVALID_PHASE,
        UNKNOWN_QUESTION,
        QUESTION_ALREADY_GRANTED,
        INSUFFICIENT_BUDGET,
        LEDGER_CAPACITY
    }

    public record Decision(boolean accepted, Session session, Optional<KnowledgeGrant> grant,
                           Rejection rejection) {
        public Decision {
            Objects.requireNonNull(session, "session");
            grant = Objects.requireNonNull(grant, "grant");
            Objects.requireNonNull(rejection, "rejection");
            if (accepted != (rejection == Rejection.NONE) || accepted != grant.isPresent()) {
                throw new IllegalArgumentException("accepted insight decisions require exactly one grant");
            }
        }
    }

    /**
     * Resolve only server-allowlisted topics. The window is derived from the saved server-side qi
     * snapshot and the selected profile; neither the request nor its question id can assert a window.
     */
    public static Decision resolve(Session current, Request request, List<Question> questions,
                                    RuleProfile profile, boolean serverValidated) {
        Objects.requireNonNull(current, "current session");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(profile, "rule profile");
        Map<String, Question> byId = new HashMap<>();
        for (Question question : Objects.requireNonNull(questions, "question catalog")) {
            if (byId.put(question.id(), question) != null) {
                throw new IllegalArgumentException("duplicate question id: " + question.id());
            }
        }

        if (request.expectedRevision() != current.revision()) {
            return reject(current, Rejection.STALE_REVISION);
        }
        if (!serverValidated) {
            return reject(current, Rejection.SERVER_VALIDATION_REQUIRED);
        }
        if (!profile.enabled()) {
            return reject(current, Rejection.PROFILE_DISABLED);
        }
        if (!profile.id().equals(current.profileId())) {
            return reject(current, Rejection.PROFILE_MISMATCH);
        }
        if (current.phase() != Phase.ABSORBING || current.outcome() != Outcome.RUNNING) {
            return reject(current, Rejection.INVALID_PHASE);
        }
        if (!resonanceWindowOpen(current.qi(), profile.resonanceTolerance())) {
            return reject(current, Rejection.WINDOW_CLOSED);
        }
        InsightState state = current.insight();
        if (request.sequence() != state.nextSequence()) {
            return reject(current, Rejection.INVALID_SEQUENCE);
        }
        if (request.serverTick() < current.phaseStartedAt()
                || request.serverTick() < state.lastRequestTick()) {
            return reject(current, Rejection.INVALID_SERVER_TICK);
        }
        Question question = byId.get(request.questionId());
        if (question == null) {
            return reject(current, Rejection.UNKNOWN_QUESTION);
        }
        if (state.grants().size() >= 128) {
            return reject(current, Rejection.LEDGER_CAPACITY);
        }
        if (state.grants().stream().anyMatch(grant -> grant.questionId().equals(question.id()))) {
            return reject(current, Rejection.QUESTION_ALREADY_GRANTED);
        }

        if (state.nextSequence() == Long.MAX_VALUE || current.revision() == Long.MAX_VALUE) {
            return reject(current, Rejection.LEDGER_CAPACITY);
        }
        double available = state.availableBudget();
        if (available < question.minimumCost()) {
            return reject(current, Rejection.INSUFFICIENT_BUDGET);
        }
        boolean complete = available >= question.completeCost();
        double spent = complete ? question.completeCost() : available;
        double completeness;
        if (complete) {
            completeness = 1.0;
        } else {
            double progress = (spent - question.minimumCost())
                    / (question.completeCost() - question.minimumCost());
            completeness = question.minimumCompleteness()
                    + progress * (question.maximumPartialCompleteness() - question.minimumCompleteness());
        }
        KnowledgeGrant grant = new KnowledgeGrant(request.sequence(), question.id(), question.knowledgeId(),
                question.evidenceRef(), spent, completeness, request.serverTick());
        List<KnowledgeGrant> grants = new ArrayList<>(state.grants());
        grants.add(grant);
        InsightState updatedState = new InsightState(state.totalBudget(), state.spentBudget() + spent,
                state.nextSequence() + 1L, request.serverTick(), grants);
        Session updated = new Session(current.sessionId(), current.ownerId(), current.apertureId(),
                current.profileId(), current.phase(), current.outcome(), current.phaseStartedAt(),
                current.brokenCommitted(), current.qi(), current.guEscrowId(), current.apertureResultId(),
                current.revision() + 1L, updatedState);
        return new Decision(true, updated, Optional.of(grant), Rejection.NONE);
    }

    /** The only starter question is a topic already attested by the cited chapter, not a recipe. */
    public static List<Question> evidenceBackedTopics() {
        return List.of(new Question("study_strength_path", "knowledge.strength_path.topic",
                "第三卷第二百二十三章《枭雄黑楼兰》", 0.25, 1.0, 0.25, 0.75));
    }

    private static boolean resonanceWindowOpen(AscensionModel.QiState qi, double tolerance) {
        if (tolerance <= 0.0 || qi.tian() <= 0.0 || qi.di() <= 0.0 || qi.ren() <= 0.0) {
            return false;
        }
        double maximum = Math.max(qi.tian(), Math.max(qi.di(), qi.ren()));
        double minimum = Math.min(qi.tian(), Math.min(qi.di(), qi.ren()));
        return (maximum - minimum) / maximum <= tolerance;
    }

    private static Decision reject(Session session, Rejection rejection) {
        return new Decision(false, session, Optional.empty(), rejection);
    }
}
