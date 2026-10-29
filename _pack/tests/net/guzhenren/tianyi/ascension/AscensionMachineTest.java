package net.guzhenren.tianyi.ascension;

import java.util.Set;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Action;
import net.guzhenren.tianyi.ascension.AscensionModel.Capability;
import net.guzhenren.tianyi.ascension.AscensionModel.Command;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.AscensionModel.StartFacts;
import net.guzhenren.tianyi.ascension.AscensionModel.Transition;
import net.guzhenren.tianyi.ascension.AscensionModel.Validation;

public final class AscensionMachineTest {
    private AscensionMachineTest() {
    }

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        Session preparing = AscensionMachine.start(UUID.randomUUID(), owner, facts(), 10L);
        Transition cancelled = AscensionMachine.advance(preparing,
                command(Action.CANCEL_PREPARATION, preparing.revision(), 11L));
        check(cancelled.accepted(), "preparing session can cancel");
        check(cancelled.session().phase() == Phase.CANCELLED, "cancel uses terminal cancelled phase");
        check(cancelled.session().outcome() == AscensionModel.Outcome.CANCELLED,
                "cancel uses terminal cancelled outcome");
        check(cancelled.session().terminal(), "cancelled session is terminal");

        Session breaking = AscensionMachine.start(UUID.randomUUID(), owner, facts(), 20L);
        breaking = accepted(breaking, command(Action.START_BREAKING, breaking.revision(), 21L));
        breaking = accepted(breaking, command(Action.COMMIT_BROKEN, breaking.revision(), 22L));
        Transition rejected = AscensionMachine.advance(breaking,
                command(Action.CANCEL_PREPARATION, breaking.revision(), 23L));
        check(!rejected.accepted(), "committed broken aperture cannot cancel preparation");
        check(rejected.session().equals(breaking), "rejected cancel preserves session");
        System.out.println("PASS ascension cancellation is limited to unbroken preparation");
    }

    private static Session accepted(Session current, Command command) {
        Transition transition = AscensionMachine.advance(current, command);
        check(transition.accepted(), "expected transition " + command.action());
        return transition.session();
    }

    private static Command command(Action action, long revision, long tick) {
        return new Command(action, revision, tick, null,
                new Validation(true, false, false, false), "", "");
    }

    private static StartFacts facts() {
        RuleProfile profile = new RuleProfile("test", "test evidence", true,
                Set.of(Capability.UPSTREAM_RANK_ADAPTER,
                        Capability.IMMORTAL_APERTURE_SPACE, Capability.UNIQUE_GU_ESCROW));
        return new StartFacts("aperture", 5.0, 4.0, true, false, "gu", profile);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
