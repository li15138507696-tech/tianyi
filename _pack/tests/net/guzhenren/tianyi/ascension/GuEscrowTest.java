package net.guzhenren.tianyi.ascension;

import java.util.UUID;

import net.guzhenren.tianyi.ascension.GuEscrow.Action;
import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.GuEscrow.State;
import net.guzhenren.tianyi.ascension.GuEscrow.Transition;

public final class GuEscrowTest {
    private GuEscrowTest() {
    }

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        Record prepared = record(owner, State.PREPARED, 0L);
        Transition removal = GuEscrow.advance(prepared, Action.BEGIN_REMOVAL, 0L,
                owner, Evidence.EXACT_SOURCE, false, true);
        check(removal.accepted(), "exact server-checked source begins removal");
        check(removal.record().state() == State.SOURCE_REMOVAL_PENDING, "removal is checkpointed");
        check(removal.record().revision() == 1L, "revision advances once");

        Transition held = GuEscrow.advance(removal.record(), Action.CONFIRM_HELD, 1L,
                owner, Evidence.EMPTY_SOURCE, false, true);
        check(held.accepted() && held.record().state() == State.HELD,
                "empty source confirms the escrow now holds the stack");
        check(held.record().revision() == 2L, "held revision advances once");

        rejectedUnchanged(prepared, GuEscrow.advance(prepared, Action.CONFIRM_HELD, 0L,
                owner, Evidence.EMPTY_SOURCE, false, true), "cannot skip source removal");
        rejectedUnchanged(prepared, GuEscrow.advance(prepared, Action.BEGIN_REMOVAL, 9L,
                owner, Evidence.EXACT_SOURCE, false, true), "stale revision is rejected");
        rejectedUnchanged(prepared, GuEscrow.advance(prepared, Action.BEGIN_REMOVAL, 0L,
                UUID.randomUUID(), Evidence.EXACT_SOURCE, false, true), "other owner is rejected");
        rejectedUnchanged(prepared, GuEscrow.advance(prepared, Action.BEGIN_REMOVAL, 0L,
                owner, Evidence.EXACT_SOURCE, false, false), "client validation is rejected");
        rejectedUnchanged(prepared, GuEscrow.advance(prepared, Action.BEGIN_REMOVAL, 0L,
                owner, Evidence.SOURCE_UNAVAILABLE, false, true), "unavailable source is unchanged");

        Transition cancelled = GuEscrow.advance(prepared, Action.CANCEL, 0L,
                owner, Evidence.EXACT_SOURCE, false, true);
        check(cancelled.accepted() && cancelled.record().state() == State.CANCELLED,
                "preparation can cancel while the exact source remains");
        rejectedUnchanged(prepared, GuEscrow.advance(prepared, Action.CANCEL, 0L,
                owner, Evidence.EXACT_SOURCE, true, true), "broken session cannot cancel escrow");

        Transition changed = GuEscrow.advance(prepared, Action.QUARANTINE, 0L,
                owner, Evidence.CHANGED_SOURCE, false, true);
        check(changed.accepted() && changed.record().state() == State.QUARANTINED,
                "changed source is quarantined");
        Transition duplicate = GuEscrow.advance(prepared, Action.QUARANTINE, 0L,
                owner, Evidence.MULTIPLE_COPIES, false, true);
        check(duplicate.accepted() && duplicate.record().state() == State.QUARANTINED,
                "multiple copies are quarantined");
        rejectedUnchanged(cancelled.record(), GuEscrow.advance(cancelled.record(), Action.QUARANTINE, 1L,
                owner, Evidence.MULTIPLE_COPIES, false, true), "terminal state cannot migrate");
        System.out.println("PASS escrow state transitions preserve ownership and evidence");
    }

    private static Record record(UUID owner, State state, long revision) {
        return new Record(UUID.randomUUID(), owner, UUID.randomUUID(), "source", "fingerprint",
                state, revision);
    }

    private static void rejectedUnchanged(Record original, Transition result, String message) {
        check(!result.accepted(), message + " (accepted)");
        check(result.record().equals(original), message + " (record changed)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
