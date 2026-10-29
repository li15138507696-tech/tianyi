package net.guzhenren.tianyi.ascension;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianyi.ascension.AscensionModel.Blocker;
import net.guzhenren.tianyi.ascension.AscensionModel.Capability;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.StartDecision;
import net.guzhenren.tianyi.ascension.AscensionModel.StartFacts;

/** 升仙开始前的硬性门槛；未知路线和缺失实现能力都在碎窍前拒绝。 */
public final class AscensionRules {
    private AscensionRules() {
    }

    /**
     * 校验普通五转巅峰起点。rank=5、stage=4 来自 10.20 培养按钮路径的字节码观察，
     * 不是对本体所有角色等级逻辑的全局结论。
     */
    public static StartDecision validateStart(StartFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts must not be null");
        }

        List<Blocker> blockers = new ArrayList<>();
        RuleProfile profile = facts.profile();
        if (!profile.enabled()) {
            blockers.add(Blocker.UNSUPPORTED_PROFILE);
        }

        if (!profile.has(Capability.UPSTREAM_RANK_ADAPTER)) {
            blockers.add(Blocker.MISSING_UPSTREAM_RANK_ADAPTER);
        }
        if (!profile.has(Capability.IMMORTAL_APERTURE_SPACE)) {
            blockers.add(Blocker.MISSING_APERTURE_SPACE);
        }
        if (!profile.has(Capability.UNIQUE_GU_ESCROW)) {
            blockers.add(Blocker.MISSING_GU_ESCROW);
        }

        if (facts.apertureId().isBlank() || !facts.apertureVerified()) {
            blockers.add(Blocker.UNKNOWN_APERTURE);
        }
        if (facts.apertureBroken()) {
            blockers.add(Blocker.APERTURE_ALREADY_BROKEN);
        }
        if (!Double.isFinite(facts.rank()) || !Double.isFinite(facts.stage())
                || Double.compare(facts.rank(), 5.0) != 0
                || Double.compare(facts.stage(), 4.0) != 0) {
            blockers.add(Blocker.NOT_TURN_FIVE_PEAK);
        }
        if (!facts.hasVerifiedPhysicalGu()) {
            blockers.add(Blocker.PHYSICAL_GU_NOT_VERIFIED);
        }

        return new StartDecision(blockers.isEmpty(), blockers);
    }
}
