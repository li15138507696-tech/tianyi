package net.guzhenren.tianyi.ascension;

import java.util.Objects;
import java.util.UUID;

/** Pure custody state machine. It records evidence transitions but never mutates an item. */
public final class GuEscrow {
    private GuEscrow() {
    }

    public enum State {
        PREPARED,
        SOURCE_REMOVAL_PENDING,
        HELD,
        DISPOSITION_PENDING,
        RESTORED,
        COMMITTED,
        CANCELLED,
        QUARANTINED
    }

    public enum Action {
        BEGIN_REMOVAL,
        CONFIRM_HELD,
        BEGIN_DISPOSITION,
        RESTORE,
        COMMIT,
        CANCEL,
        QUARANTINE
    }

    public enum Evidence {
        EXACT_SOURCE,
        EMPTY_SOURCE,
        CHANGED_SOURCE,
        MULTIPLE_COPIES,
        SOURCE_UNAVAILABLE
    }

    public enum Rejection {
        NONE,
        STALE_REVISION,
        WRONG_OWNER,
        SERVER_VALIDATION_REQUIRED,
        INVALID_STATE,
        SOURCE_MISMATCH,
        SOURCE_UNAVAILABLE,
        BROKEN_SESSION
    }

    public record Record(UUID escrowId, UUID ownerId, UUID sessionId, String sourceSlot,
                         String itemFingerprint, State state, long revision) {
        public Record {
            Objects.requireNonNull(escrowId, "escrowId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(sessionId, "sessionId");
            sourceSlot = AscensionModel.requireText(sourceSlot, "sourceSlot");
            itemFingerprint = AscensionModel.requireText(itemFingerprint, "itemFingerprint");
            Objects.requireNonNull(state, "state");
            if (revision < 0L) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
        }
    }

    public record Transition(boolean accepted, Record record, Rejection rejection) {
        public Transition {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(rejection, "rejection");
            if (accepted != (rejection == Rejection.NONE)) {
                throw new IllegalArgumentException("accepted transition must have no rejection");
            }
        }
    }

    public static Transition advance(Record current, Action action, long expectedRevision,
                                     UUID actorId, Evidence evidence, boolean brokenCommitted,
                                     boolean serverValidated) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(evidence, "evidence");
        if (expectedRevision != current.revision()) {
            return reject(current, Rejection.STALE_REVISION);
        }
        if (actorId == null || !current.ownerId().equals(actorId)) {
            return reject(current, Rejection.WRONG_OWNER);
        }
        if (!serverValidated) {
            return reject(current, Rejection.SERVER_VALIDATION_REQUIRED);
        }
        if (action == Action.CANCEL && brokenCommitted) {
            return reject(current, Rejection.BROKEN_SESSION);
        }
        if (isTerminal(current.state())) {
            return reject(current, Rejection.INVALID_STATE);
        }
        if (evidence == Evidence.SOURCE_UNAVAILABLE) {
            return reject(current, Rejection.SOURCE_UNAVAILABLE);
        }
        if (evidence == Evidence.CHANGED_SOURCE || evidence == Evidence.MULTIPLE_COPIES) {
            return accept(copy(current, State.QUARANTINED));
        }

        return switch (action) {
            case BEGIN_REMOVAL -> current.state() == State.PREPARED && evidence == Evidence.EXACT_SOURCE
                    ? accept(copy(current, State.SOURCE_REMOVAL_PENDING))
                    : reject(current, current.state() == State.PREPARED
                    ? Rejection.SOURCE_MISMATCH : Rejection.INVALID_STATE);
            case CONFIRM_HELD -> current.state() == State.SOURCE_REMOVAL_PENDING
                    && evidence == Evidence.EMPTY_SOURCE
                    ? accept(copy(current, State.HELD))
                    : reject(current, current.state() == State.SOURCE_REMOVAL_PENDING
                    ? Rejection.SOURCE_MISMATCH : Rejection.INVALID_STATE);
            case BEGIN_DISPOSITION -> current.state() == State.HELD && evidence == Evidence.EMPTY_SOURCE
                    ? accept(copy(current, State.DISPOSITION_PENDING))
                    : reject(current, current.state() == State.HELD
                    ? Rejection.SOURCE_MISMATCH : Rejection.INVALID_STATE);
            case RESTORE -> current.state() == State.DISPOSITION_PENDING
                    && evidence == Evidence.EXACT_SOURCE
                    ? accept(copy(current, State.RESTORED))
                    : reject(current, current.state() == State.DISPOSITION_PENDING
                    ? Rejection.SOURCE_MISMATCH : Rejection.INVALID_STATE);
            case COMMIT -> current.state() == State.DISPOSITION_PENDING
                    && evidence == Evidence.EMPTY_SOURCE
                    ? accept(copy(current, State.COMMITTED))
                    : reject(current, current.state() == State.DISPOSITION_PENDING
                    ? Rejection.SOURCE_MISMATCH : Rejection.INVALID_STATE);
            case CANCEL -> (current.state() == State.PREPARED
                    || current.state() == State.SOURCE_REMOVAL_PENDING)
                    && evidence == Evidence.EXACT_SOURCE
                    ? accept(copy(current, State.CANCELLED))
                    : reject(current, (current.state() == State.PREPARED
                    || current.state() == State.SOURCE_REMOVAL_PENDING)
                    ? Rejection.SOURCE_MISMATCH : Rejection.INVALID_STATE);
            case QUARANTINE -> accept(copy(current, State.QUARANTINED));
        };
    }

    /** Store-level guard for a replacement already produced by {@link #advance}. */
    public static boolean isLegalStateTransition(State from, State to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PREPARED -> to == State.SOURCE_REMOVAL_PENDING || to == State.CANCELLED
                    || to == State.QUARANTINED;
            case SOURCE_REMOVAL_PENDING -> to == State.HELD || to == State.CANCELLED
                    || to == State.QUARANTINED;
            case HELD -> to == State.DISPOSITION_PENDING || to == State.QUARANTINED;
            case DISPOSITION_PENDING -> to == State.RESTORED || to == State.COMMITTED
                    || to == State.QUARANTINED;
            case RESTORED, COMMITTED, CANCELLED, QUARANTINED -> false;
        };
    }

    private static boolean isTerminal(State state) {
        return state == State.RESTORED || state == State.COMMITTED || state == State.CANCELLED
                || state == State.QUARANTINED;
    }

    private static Transition accept(Record record) {
        return new Transition(true, record, Rejection.NONE);
    }

    private static Transition reject(Record current, Rejection rejection) {
        return new Transition(false, current, rejection);
    }

    private static Record copy(Record current, State state) {
        return new Record(current.escrowId(), current.ownerId(), current.sessionId(),
                current.sourceSlot(), current.itemFingerprint(), state, current.revision() + 1L);
    }
}
