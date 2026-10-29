package net.guzhenren.tianyi.ascension;

import java.util.Objects;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;

/** Pure server-side decision layer for owner-controlled gas regulation. */
public final class QiControlService {
    private QiControlService() {
    }

    public record Request(long expectedRevision, long serverTick, QiRegulator.Control control) {
        public Request {
            if (expectedRevision < 0L || serverTick < 0L) {
                throw new IllegalArgumentException("negative qi request revision or tick");
            }
            Objects.requireNonNull(control, "qi control");
        }
    }

    public enum Rejection {
        NONE,
        PROFILE_DISABLED,
        PROFILE_MISMATCH,
        WRONG_OWNER,
        STALE_REVISION,
        INVALID_PHASE,
        STALE_SERVER_TICK,
        INVALID_RESULT
    }

    public record Decision(boolean accepted, Session session, Rejection rejection) {
        public Decision {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(rejection, "rejection");
            if (accepted != (rejection == Rejection.NONE)) {
                throw new IllegalArgumentException("accepted qi controls require no rejection");
            }
        }
    }

    /** Environment and tuning must come from server policy, never client payload fields. */
    public static Decision advance(Session current, UUID actorId, Request request,
                                   QiRegulator.Environment environment, QiRegulator.Tuning tuning,
                                   RuleProfile profile) {
        Objects.requireNonNull(current, "current session");
        Objects.requireNonNull(request, "qi request");
        Objects.requireNonNull(environment, "server environment");
        Objects.requireNonNull(tuning, "server tuning");
        Objects.requireNonNull(profile, "server profile");
        if (!profile.enabled()) {
            return reject(current, Rejection.PROFILE_DISABLED);
        }
        if (!profile.id().equals(current.profileId())) {
            return reject(current, Rejection.PROFILE_MISMATCH);
        }
        if (actorId == null || !current.ownerId().equals(actorId)) {
            return reject(current, Rejection.WRONG_OWNER);
        }
        if (request.expectedRevision() != current.revision() || current.revision() == Long.MAX_VALUE) {
            return reject(current, Rejection.STALE_REVISION);
        }
        if (current.phase() != Phase.ABSORBING || current.outcome() != Outcome.RUNNING) {
            return reject(current, Rejection.INVALID_PHASE);
        }
        QiRegulator.Result step = QiRegulator.advance(current.qi(), request.serverTick(), request.control(),
                environment, tuning);
        if (!step.accepted()) {
            Rejection rejection = step.rejection() == QiRegulator.Rejection.STALE_SERVER_TICK
                    ? Rejection.STALE_SERVER_TICK : Rejection.INVALID_RESULT;
            return reject(current, rejection);
        }
        Session updated = new Session(current.sessionId(), current.ownerId(), current.apertureId(),
                current.profileId(), current.phase(), current.outcome(), current.phaseStartedAt(),
                current.brokenCommitted(), step.state(), current.guEscrowId(), current.apertureResultId(),
                current.revision() + 1L, current.insight());
        return new Decision(true, updated, Rejection.NONE);
    }

    private static Decision reject(Session current, Rejection rejection) {
        return new Decision(false, current, rejection);
    }
}
