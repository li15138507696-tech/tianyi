package net.guzhenren.tianyi.world;

import java.util.EnumSet;

import net.guzhenren.tianyi.ascension.AscensionModel.Blocker;
import net.guzhenren.tianyi.ascension.AscensionModel.Capability;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.ProgressionSnapshot;

public final class GuEscrowServiceTest {
    private GuEscrowServiceTest() {
    }

    public static void main(String[] args) {
        RuleProfile profile = new RuleProfile("ordinary", "service test", true,
                EnumSet.allOf(Capability.class));
        ProgressionSnapshot snapshot = new ProgressionSnapshot(java.util.UUID.randomUUID(),
                5.0, 4.0, 0.0, 100.0, 80.0, 120.0, 10L);
        GuEscrowService.BeginResult result = GuEscrowService.disabled().begin(null,
                new AscensionService.StartContext("aperture", true, false, "menu-slot:0"),
                snapshot, profile, 10L);

        check(result.decision().blockers().contains(Blocker.MISSING_GU_ESCROW),
                "missing stable source disables escrow capability");
        check(result.decision().blockers().contains(Blocker.PHYSICAL_GU_NOT_VERIFIED),
                "menu cache is not treated as physical Gu evidence");
        check(result.session().isEmpty() && result.escrow().isEmpty(),
                "disabled escrow service creates no records");
        System.out.println("PASS absent stable Gu source leaves admission closed without mutation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
