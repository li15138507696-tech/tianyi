package net.guzhenren.tianyi.ascension;

import java.util.Objects;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Action;
import net.guzhenren.tianyi.ascension.AscensionModel.Command;
import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.AscensionModel.Transition;
import net.guzhenren.tianyi.ascension.AscensionModel.Validation;
import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.GuEscrow.State;

/** Pure restart reconciliation. It never creates an item or claims an unverified source. */
public final class AscensionRecovery {
    private AscensionRecovery() {
    }

    public enum Status {
        WAIT_FOR_SOURCE,
        CANCEL_PREPARATION,
        RESUME_HELD,
        QUARANTINE,
        NO_CHANGE
    }

    public record RecoveryDecision(Status status, Session session, Record escrow) {
        public RecoveryDecision {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(escrow, "escrow");
        }
    }

    public static RecoveryDecision reconcile(Session session, Record escrow,
                                             Evidence evidence, ProgressionSnapshot snapshot) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(escrow, "escrow");
        Objects.requireNonNull(evidence, "evidence");

        if (!identityMatches(session, escrow)) {
            // Do not try to revise records that may belong to different transactions.
            return new RecoveryDecision(Status.QUARANTINE, session, escrow);
        }
        if (evidence == Evidence.SOURCE_UNAVAILABLE) {
            return new RecoveryDecision(Status.WAIT_FOR_SOURCE, session, escrow);
        }
        if (session.outcome() == Outcome.QUARANTINED || escrow.state() == State.QUARANTINED) {
            return new RecoveryDecision(Status.NO_CHANGE, session, escrow);
        }
        if (session.terminal() || isTerminal(escrow.state())) {
            return new RecoveryDecision(Status.NO_CHANGE, session, escrow);
        }
        if (evidence == Evidence.CHANGED_SOURCE || evidence == Evidence.MULTIPLE_COPIES) {
            return quarantine(session, escrow, evidence);
        }

        if (escrow.state() == State.PREPARED
                || (escrow.state() == State.SOURCE_REMOVAL_PENDING && evidence == Evidence.EXACT_SOURCE)) {
            if (session.phase() == Phase.PREPARING && !session.brokenCommitted()
                    && evidence == Evidence.EXACT_SOURCE) {
                Transition sessionChange = AscensionMachine.advance(session,
                        new Command(Action.CANCEL_PREPARATION, session.revision(),
                                session.phaseStartedAt(), null,
                                new Validation(true, false, false, false),
                                session.guEscrowId(), ""));
                var escrowChange = GuEscrow.advance(escrow,
                        net.guzhenren.tianyi.ascension.GuEscrow.Action.CANCEL,
                        escrow.revision(), escrow.ownerId(), Evidence.EXACT_SOURCE, false, true);
                if (sessionChange.accepted() && escrowChange.accepted()) {
                    return new RecoveryDecision(Status.CANCEL_PREPARATION,
                            sessionChange.session(), escrowChange.record());
                }
            }
            return quarantine(session, escrow, Evidence.EXACT_SOURCE);
        }

        if (escrow.state() == State.SOURCE_REMOVAL_PENDING) {
            if (evidence == Evidence.EMPTY_SOURCE) {
                if (!hasFreshOwnerSnapshot(session, snapshot)) {
                    return new RecoveryDecision(Status.WAIT_FOR_SOURCE, session, escrow);
                }
                var escrowChange = GuEscrow.advance(escrow,
                        net.guzhenren.tianyi.ascension.GuEscrow.Action.CONFIRM_HELD,
                        escrow.revision(), escrow.ownerId(), Evidence.EMPTY_SOURCE,
                        session.brokenCommitted(), true);
                if (escrowChange.accepted()) {
                    return new RecoveryDecision(Status.RESUME_HELD, session, escrowChange.record());
                }
            }
            return quarantine(session, escrow, evidence);
        }

        if (escrow.state() == State.HELD) {
            if (evidence == Evidence.EMPTY_SOURCE && hasFreshOwnerSnapshot(session, snapshot)) {
                return new RecoveryDecision(Status.RESUME_HELD, session, escrow);
            }
            if (evidence == Evidence.EMPTY_SOURCE) {
                return new RecoveryDecision(Status.WAIT_FOR_SOURCE, session, escrow);
            }
            return quarantine(session, escrow, evidence);
        }

        // A crash during disposition is ambiguous: the stack may already have been restored.
        return quarantine(session, escrow, evidence);
    }

    private static boolean identityMatches(Session session, Record escrow) {
        if (!session.sessionId().equals(escrow.sessionId())
                || !session.ownerId().equals(escrow.ownerId())) {
            return false;
        }
        try {
            return UUID.fromString(session.guEscrowId()).equals(escrow.escrowId());
        } catch (IllegalArgumentException invalidId) {
            return false;
        }
    }

    private static boolean hasFreshOwnerSnapshot(Session session, ProgressionSnapshot snapshot) {
        return snapshot != null && snapshot.ownerId().equals(session.ownerId())
                && snapshot.sampledAt() >= session.phaseStartedAt();
    }

    private static RecoveryDecision quarantine(Session session, Record escrow, Evidence evidence) {
        Session quarantinedSession = session;
        if (!session.terminal() && session.outcome() != Outcome.QUARANTINED) {
            Transition transition = AscensionMachine.advance(session,
                    new Command(Action.QUARANTINE, session.revision(), session.phaseStartedAt(),
                            null, new Validation(true, false, false, false),
                            session.guEscrowId(), ""));
            if (transition.accepted()) {
                quarantinedSession = transition.session();
            }
        }

        Record quarantinedEscrow = escrow;
        if (!isTerminal(escrow.state())) {
            var transition = GuEscrow.advance(escrow,
                    net.guzhenren.tianyi.ascension.GuEscrow.Action.QUARANTINE,
                    escrow.revision(), escrow.ownerId(), evidence,
                    session.brokenCommitted(), true);
            if (transition.accepted()) {
                quarantinedEscrow = transition.record();
            }
        }
        return new RecoveryDecision(Status.QUARANTINE, quarantinedSession, quarantinedEscrow);
    }

    private static boolean isTerminal(State state) {
        return state == State.RESTORED || state == State.COMMITTED || state == State.CANCELLED;
    }
}
