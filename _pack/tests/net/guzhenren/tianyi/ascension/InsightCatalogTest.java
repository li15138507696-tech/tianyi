package net.guzhenren.tianyi.ascension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.InsightState;
import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;

public final class InsightCatalogTest {
    private static final UUID SESSION = UUID.fromString("f4f3b31d-f936-48c0-a364-78c720f573d5");
    private static final UUID OWNER = UUID.fromString("900355c1-312d-4a3d-a239-65b113c47a82");
    private static final RuleProfile PROFILE = new RuleProfile("test", "test evidence", true,
            Set.of(), 2.0, 0.75);
    private static final RuleProfile NARROW_WINDOW = new RuleProfile("test", "test evidence", true,
            Set.of(), 2.0, 0.1);
    private static final List<InsightCatalog.Question> QUESTIONS = List.of(
            new InsightCatalog.Question("rare_formula", "knowledge.rare_formula", "verified source A",
                    1.0, 4.0, 0.2, 0.8),
            new InsightCatalog.Question("common_topic", "knowledge.common_topic", "verified source B",
                    0.5, 1.0, 0.2, 0.7));

    private InsightCatalogTest() {
    }

    public static void main(String[] args) {
        rejectsOutsideWindowAndUntrustedRequests();
        grantsFinitePartialKnowledgeWithoutPausingQi();
        rejectsReplayAndDuplicateQuestion();
        rejectsUnknownQuestionAndInsufficientBudget();
        mainPhaseTransitionsPreserveInsightLedger();
        System.out.println("PASS ascension insight window, budget, and idempotency");
    }

    private static void rejectsOutsideWindowAndUntrustedRequests() {
        Session session = absorbing(2.0);
        InsightCatalog.Request request = new InsightCatalog.Request(0L, 0L, 20L, "rare_formula");
        check(InsightCatalog.resolve(session, request, QUESTIONS, PROFILE, false).rejection()
                == InsightCatalog.Rejection.SERVER_VALIDATION_REQUIRED, "untrusted request is rejected");
        check(InsightCatalog.resolve(session, request, QUESTIONS, NARROW_WINDOW, true).rejection()
                == InsightCatalog.Rejection.WINDOW_CLOSED, "unbalanced qi closes the profile window");
        RuleProfile disabled = new RuleProfile("test", "test evidence", false, Set.of(), 2.0, 0.75);
        check(InsightCatalog.resolve(session, request, QUESTIONS, disabled, true).rejection()
                == InsightCatalog.Rejection.PROFILE_DISABLED, "disabled profile cannot grant knowledge");
        RuleProfile wrongProfile = new RuleProfile("other", "test evidence", true, Set.of(), 2.0, 0.75);
        check(InsightCatalog.resolve(session, request, QUESTIONS, wrongProfile, true).rejection()
                == InsightCatalog.Rejection.PROFILE_MISMATCH, "session cannot fall back to another profile");
        Session preparing = copyPhase(session, Phase.PREPARING, false);
        check(InsightCatalog.resolve(preparing, request, QUESTIONS, PROFILE, true).rejection()
                == InsightCatalog.Rejection.INVALID_PHASE, "insight cannot be requested before absorption");
    }

    private static void grantsFinitePartialKnowledgeWithoutPausingQi() {
        Session session = absorbing(2.0);
        InsightCatalog.Decision result = InsightCatalog.resolve(session,
                new InsightCatalog.Request(0L, 0L, 25L, "rare_formula"), QUESTIONS, PROFILE, true);
        check(result.accepted(), "valid resonance-window query is accepted");
        check(result.grant().isPresent(), "accepted query returns a durable grant record");
        var grant = result.grant().orElseThrow();
        check(grant.knowledgeId().equals("knowledge.rare_formula"), "only catalog knowledge is granted");
        check(close(grant.budgetSpent(), 2.0) && close(grant.completeness(), 0.4),
                "limited budget yields a partial rather than complete rare result");
        Session updated = result.session();
        check(updated.revision() == 1L && updated.insight().nextSequence() == 1L,
                "grant and sequence are committed in the next session revision");
        check(updated.phase() == session.phase() && updated.phaseStartedAt() == session.phaseStartedAt(),
                "parallel insight does not advance or pause the main phase");
        check(updated.qi().equals(session.qi()), "parallel insight does not replace the current qi snapshot");
        check(close(updated.insight().availableBudget(), 0.0), "budget is finite and consumed exactly once");
    }

    private static void rejectsReplayAndDuplicateQuestion() {
        Session session = absorbing(2.0);
        Session updated = InsightCatalog.resolve(session,
                new InsightCatalog.Request(0L, 0L, 21L, "common_topic"), QUESTIONS, PROFILE, true).session();
        InsightCatalog.Decision replay = InsightCatalog.resolve(updated,
                new InsightCatalog.Request(1L, 0L, 22L, "common_topic"), QUESTIONS, PROFILE, true);
        check(replay.rejection() == InsightCatalog.Rejection.INVALID_SEQUENCE,
                "replayed request sequence cannot create another grant");
        InsightCatalog.Decision duplicate = InsightCatalog.resolve(updated,
                new InsightCatalog.Request(1L, 1L, 23L, "common_topic"), QUESTIONS, PROFILE, true);
        check(duplicate.rejection() == InsightCatalog.Rejection.QUESTION_ALREADY_GRANTED,
                "the same question cannot be farmed with a new sequence");
        check(updated.equals(replay.session()) && updated.equals(duplicate.session()),
                "rejections leave the session unchanged");
    }

    private static void rejectsUnknownQuestionAndInsufficientBudget() {
        Session session = absorbing(0.4);
        InsightCatalog.Decision unknown = InsightCatalog.resolve(session,
                new InsightCatalog.Request(0L, 0L, 21L, "free_text"), QUESTIONS, PROFILE, true);
        check(unknown.rejection() == InsightCatalog.Rejection.UNKNOWN_QUESTION,
                "free text is never interpreted as a knowledge answer");
        InsightCatalog.Decision exhausted = InsightCatalog.resolve(session,
                new InsightCatalog.Request(0L, 0L, 21L, "rare_formula"), QUESTIONS, PROFILE, true);
        check(exhausted.rejection() == InsightCatalog.Rejection.INSUFFICIENT_BUDGET,
                "a question below its minimum cost cannot grant knowledge");
    }

    private static void mainPhaseTransitionsPreserveInsightLedger() {
        Session session = absorbing(2.0);
        Session learned = InsightCatalog.resolve(session,
                new InsightCatalog.Request(0L, 0L, 21L, "common_topic"), QUESTIONS, PROFILE, true).session();
        var transition = AscensionMachine.advance(learned,
                new AscensionModel.Command(AscensionModel.Action.UPDATE_QI, learned.revision(), 22L,
                        new QiState(4.0, 2.0, 1.0),
                        new AscensionModel.Validation(true, true, false, false), "", ""));
        check(transition.accepted(), "qi updates continue after parallel insight");
        check(transition.session().insight().equals(learned.insight()),
                "main phase updates preserve persisted grants and budget");
    }

    private static Session absorbing(double budget) {
        return new Session(SESSION, OWNER, "aperture", PROFILE.id(), Phase.ABSORBING, Outcome.RUNNING,
                10L, true, new QiState(3.0, 2.0, 1.0), "escrow", "", 0L,
                InsightState.initial(budget, 0L));
    }

    private static Session copyPhase(Session old, Phase phase, boolean broken) {
        return new Session(old.sessionId(), old.ownerId(), old.apertureId(), old.profileId(), phase,
                Outcome.RUNNING, old.phaseStartedAt(), broken, old.qi(), old.guEscrowId(),
                old.apertureResultId(), old.revision(), old.insight());
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.0000001;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
