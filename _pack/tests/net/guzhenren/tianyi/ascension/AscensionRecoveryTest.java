package net.guzhenren.tianyi.ascension;

import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.GuEscrow.State;

public final class AscensionRecoveryTest {
    private static final UUID OWNER = UUID.fromString("14fda6e2-2d5f-4ce7-b2b4-c6707cc52c55");
    private static final UUID SESSION_ID = UUID.fromString("20c4ed93-83c5-41ee-a8df-28e75086cd60");
    private static final UUID ESCROW_ID = UUID.fromString("2c158483-1320-4553-84e1-ea5333bc8e83");

    private AscensionRecoveryTest() {
    }

    public static void main(String[] args) {
        cancelsPreparedEscrowWhenSourceIsStillExact();
        rejectsEveryActionOnCancelledSession();
        cancelsPendingRemovalWhenSourceIsStillExact();
        resumesRemovalWhenSourceIsEmptyAndSnapshotIsFresh();
        quarantinesChangedOrDuplicatedEvidence();
        waitsWithoutMutatingWhenSourceIsUnavailable();
        quarantinesMismatchedOwners();
        preservesBrokenCommitOnEveryRecoveryPath();
        System.out.println("PASS ascension recovery preserves evidence and broken state");
    }

    private static void cancelsPendingRemovalWhenSourceIsStillExact() {
        var result = AscensionRecovery.reconcile(preparing(false),
                escrow(State.SOURCE_REMOVAL_PENDING, OWNER, SESSION_ID, 1L),
                Evidence.EXACT_SOURCE, snapshot(OWNER, 20L));
        check(result.status() == AscensionRecovery.Status.CANCEL_PREPARATION,
                "pending removal with exact source cancels");
        check(result.session().outcome() == Outcome.CANCELLED && result.escrow().state() == State.CANCELLED,
                "both records cancelled");
    }

    private static void rejectsEveryActionOnCancelledSession() {
        Session cancelled = new Session(SESSION_ID, OWNER, "aperture", "ordinary", Phase.CANCELLED,
                Outcome.CANCELLED, 10L, false, QiState.empty(), ESCROW_ID.toString(), "", 1L);
        for (var action : AscensionModel.Action.values()) {
            var command = new AscensionModel.Command(action, 1L, 20L, null,
                    new AscensionModel.Validation(true, true, true, true), ESCROW_ID.toString(), "result");
            var result = AscensionMachine.advance(cancelled, command);
            check(!result.accepted() && result.session().equals(cancelled), "terminal action rejects: " + action);
        }
    }

    private static void cancelsPreparedEscrowWhenSourceIsStillExact() {
        Session session = preparing(false);
        Record escrow = escrow(State.PREPARED, OWNER, SESSION_ID, 0L);
        AscensionRecovery.RecoveryDecision result =
                AscensionRecovery.reconcile(session, escrow, Evidence.EXACT_SOURCE, snapshot(OWNER, 20L));
        check(result.status() == AscensionRecovery.Status.CANCEL_PREPARATION,
                "prepared escrow with exact source is cancelled");
        check(result.session().phase() == Phase.CANCELLED && result.session().terminal(),
                "cancelled session is terminal");
        check(result.escrow().state() == State.CANCELLED, "prepared escrow is cancelled");
    }

    private static void resumesRemovalWhenSourceIsEmptyAndSnapshotIsFresh() {
        Session session = preparing(false);
        Record escrow = escrow(State.SOURCE_REMOVAL_PENDING, OWNER, SESSION_ID, 1L);
        AscensionRecovery.RecoveryDecision result = AscensionRecovery.reconcile(
                session, escrow, Evidence.EMPTY_SOURCE, snapshot(OWNER, 20L));
        check(result.status() == AscensionRecovery.Status.RESUME_HELD,
                "empty source with fresh owner snapshot resumes held escrow");
        check(result.session().equals(session), "recovery preserves the session snapshot");
        check(result.escrow().state() == State.HELD && result.escrow().revision() == 2L,
                "escrow advances one revision to held");
    }

    private static void quarantinesChangedOrDuplicatedEvidence() {
        Session session = preparing(false);
        Record escrow = escrow(State.SOURCE_REMOVAL_PENDING, OWNER, SESSION_ID, 1L);
        for (Evidence evidence : new Evidence[]{Evidence.CHANGED_SOURCE, Evidence.MULTIPLE_COPIES}) {
            AscensionRecovery.RecoveryDecision result =
                    AscensionRecovery.reconcile(session, escrow, evidence, snapshot(OWNER, 20L));
            check(result.status() == AscensionRecovery.Status.QUARANTINE, "ambiguous evidence quarantines");
            check(result.session().outcome() == Outcome.QUARANTINED,
                    "ambiguous session is marked quarantined");
            check(result.escrow().state() == State.QUARANTINED,
                    "ambiguous escrow is marked quarantined");
        }
    }

    private static void waitsWithoutMutatingWhenSourceIsUnavailable() {
        Session session = preparing(false);
        Record escrow = escrow(State.SOURCE_REMOVAL_PENDING, OWNER, SESSION_ID, 1L);
        AscensionRecovery.RecoveryDecision result = AscensionRecovery.reconcile(
                session, escrow, Evidence.SOURCE_UNAVAILABLE, null);
        check(result.status() == AscensionRecovery.Status.WAIT_FOR_SOURCE,
                "unavailable source waits");
        check(result.session().equals(session) && result.escrow().equals(escrow),
                "unavailable source does not mutate either record");
    }

    private static void quarantinesMismatchedOwners() {
        Session session = preparing(false);
        Record escrow = escrow(State.PREPARED, UUID.fromString("7f817a9c-16bd-42fe-bba7-8a3e50436eab"),
                SESSION_ID, 0L);
        AscensionRecovery.RecoveryDecision result = AscensionRecovery.reconcile(
                session, escrow, Evidence.EXACT_SOURCE, snapshot(OWNER, 20L));
        check(result.status() == AscensionRecovery.Status.QUARANTINE,
                "mismatched owner evidence quarantines");
    }

    private static void preservesBrokenCommitOnEveryRecoveryPath() {
        Session broken = new Session(SESSION_ID, OWNER, "aperture", "ordinary", Phase.BROKEN,
                Outcome.RUNNING, 10L, true, QiState.empty(), ESCROW_ID.toString(), "", 2L);
        Record escrow = escrow(State.SOURCE_REMOVAL_PENDING, OWNER, SESSION_ID, 1L);
        AscensionRecovery.RecoveryDecision held = AscensionRecovery.reconcile(
                broken, escrow, Evidence.EMPTY_SOURCE, snapshot(OWNER, 20L));
        check(held.session().brokenCommitted(), "held recovery preserves brokenCommitted");
        AscensionRecovery.RecoveryDecision quarantined = AscensionRecovery.reconcile(
                broken, escrow, Evidence.MULTIPLE_COPIES, snapshot(OWNER, 20L));
        check(quarantined.session().brokenCommitted(), "quarantine recovery preserves brokenCommitted");
    }

    private static Session preparing(boolean brokenCommitted) {
        return new Session(SESSION_ID, OWNER, "aperture", "ordinary", Phase.PREPARING,
                Outcome.RUNNING, 10L, brokenCommitted, QiState.empty(), ESCROW_ID.toString(), "", 0L);
    }

    private static Record escrow(State state, UUID owner, UUID session, long revision) {
        return new Record(ESCROW_ID, owner, session, "source", "fingerprint", state, revision);
    }

    private static ProgressionSnapshot snapshot(UUID owner, long tick) {
        return new ProgressionSnapshot(owner, 5.0, 4.0, 0.0, 100.0, 80.0, 120.0, tick);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
