package net.guzhenren.tianyi.ascension;

import java.util.EnumSet;

import net.guzhenren.tianyi.ascension.AscensionModel.Blocker;
import net.guzhenren.tianyi.ascension.AscensionModel.Capability;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.StartDecision;
import net.guzhenren.tianyi.ascension.AscensionModel.StartFacts;

public final class AscensionRulesTest {
    private AscensionRulesTest() {
    }

    public static void main(String[] args) {
        check(blockedWithout(Capability.UPSTREAM_RANK_ADAPTER)
                        .blockers().contains(Blocker.MISSING_UPSTREAM_RANK_ADAPTER),
                "missing upstream adapter has a specific blocker");
        check(blockedWithout(Capability.IMMORTAL_APERTURE_SPACE)
                        .blockers().contains(Blocker.MISSING_APERTURE_SPACE),
                "missing aperture space has a specific blocker");
        check(blockedWithout(Capability.UNIQUE_GU_ESCROW)
                        .blockers().contains(Blocker.MISSING_GU_ESCROW),
                "missing Gu escrow has a specific blocker");
        check(decide(allCapabilities(), 4.0, 4.0, true, false, "gu")
                        .blockers().contains(Blocker.NOT_TURN_FIVE_PEAK),
                "non-rank-five snapshot is blocked");
        check(decide(allCapabilities(), 5.0, 4.0, false, false, "gu")
                        .blockers().contains(Blocker.UNKNOWN_APERTURE),
                "unverified aperture is blocked");
        check(decide(allCapabilities(), 5.0, 4.0, true, false, "")
                        .blockers().contains(Blocker.PHYSICAL_GU_NOT_VERIFIED),
                "missing physical Gu is blocked");
        System.out.println("PASS ascension admission reports exact capability blockers");
    }

    private static StartDecision blockedWithout(Capability missing) {
        EnumSet<Capability> capabilities = allCapabilities();
        capabilities.remove(missing);
        return decide(capabilities, 5.0, 4.0, true, false, "gu");
    }

    private static EnumSet<Capability> allCapabilities() {
        return EnumSet.allOf(Capability.class);
    }

    private static StartDecision decide(EnumSet<Capability> capabilities, double rank, double stage,
                                        boolean apertureVerified, boolean apertureBroken, String gu) {
        RuleProfile profile = new RuleProfile("ordinary", "ascension rules test", true, capabilities);
        StartFacts facts = new StartFacts("aperture", rank, stage, apertureVerified,
                apertureBroken, gu, profile);
        return AscensionRules.validateStart(facts);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
