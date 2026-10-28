package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.network.GuzhenrenModVariables;
import net.minecraft.server.level.ServerPlayer;

/**
 * 把 {@link DaotuTalent} 的词条效果落到玩家身上。
 *
 * <p><b>为什么不用「撤销」而是「重放」</b>：v1.4.0 起抽选选定即锁定，永不撤销，
 * 所以这里只需要「正向应用」。转世时整个 {@code run} 子标签被删，
 * 属性 modifier 与道痕账本一起清空，不需要逐条回滚。
 *
 * <p><b>四类效果各自的落地通道</b>：
 * <ul>
 *   <li>{@code ATTR} —— 累加进 {@code run/attrs}，由
 *       {@link DaotuRunAttributes#apply} 挂成 AttributeModifier；</li>
 *   <li>{@code COMBAT} —— 同样累加进 {@code run/attrs}，键名以 {@code c_} 开头，
 *       由 {@link DaotuCombatEffects} 读取；</li>
 *   <li>{@code DAOHEN} / {@code DAOHEN_ALL} —— 直接写本体道痕字段，
 *       同时记进 {@code run/ledger}，转世按账本扣回；</li>
 *   <li>{@code BUFF} —— 记进 {@code run/buffs}，由 {@link DaotuBuffs#maintain}
 *       每秒兜底续期。</li>
 * </ul>
 */
public final class DaotuTalentApply {

    private DaotuTalentApply() {
    }

    /**
     * 应用一条词条的全部效果。
     *
     * @param entry 词条
     * @param scale 转数缩放（道痕类要乘，属性类由抽选表已经缩放过了，这里不再乘）
     */
    public static void apply(ServerPlayer player, DaotuTalent.Entry entry, double scale) {
        if (entry == null) {
            return;
        }
        for (DaotuTalent.Effect fx : entry.fx()) {
            try {
                applyOne(player, fx, scale);
            } catch (Throwable t) {
                DaotuMod.LOGGER.warn("daotu 应用词条效果失败: {} / {}",
                        entry.id(), fx.kind(), t);
            }
        }
    }

    private static void applyOne(ServerPlayer player, DaotuTalent.Effect fx, double scale) {
        switch (fx.kind()) {
            case "ATTR" -> {
                // 属性：直接累加，百分比键由 DaotuRunAttributes 决定挂哪种 modifier
                DaotuRunAttributes.grant(player, fx.arg(), fx.a(), true);
            }
            case "COMBAT" -> {
                // 战斗乘区：累加进 run/attrs 的 c_* 键
                double cur = DaotuRun.attr(player, fx.arg());
                DaotuRun.setAttr(player, fx.arg(), cur + fx.a());
            }
            case "DAOHEN" -> {
                double amount = fx.a() * scale;
                var v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
                if (DaotuGrowthApply.addDaohen(v, fx.arg(), amount)) {
                    v.markSyncDirty();
                    DaotuRun.addLedger(player, fx.arg(), amount);
                } else {
                    DaotuMod.LOGGER.warn("词条道痕字段不存在: {}", fx.arg());
                }
            }
            case "DAOHEN_ALL" -> {
                double amount = fx.a() * scale;
                var v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
                int n = 0;
                for (String key : DaotuSpecial.DAOHEN_KEYS) {
                    if (DaotuGrowthApply.addDaohen(v, key, amount)) {
                        DaotuRun.addLedger(player, key, amount);
                        n++;
                    }
                }
                if (n > 0) {
                    v.markSyncDirty();
                }
            }
            case "BUFF" -> {
                String id = fx.arg();
                if (id == null || id.isEmpty()) {
                    return;
                }
                DaotuRun.addBuff(player, id);
                DaotuBuffs.grantNow(player, id);
            }
            default -> DaotuMod.LOGGER.warn("未知词条效果类型: {}", fx.kind());
        }
    }

    /**
     * 从词条池里挑 {@code n} 条候选。
     *
     * <p>只挑「还没拿过」的——同一条词条拿两次没有意义（属性会叠，
     * 但玩家的获得感是重复的）。已经拿满时退回允许重复。
     *
     * @param exclude 已获得的词条 id
     */
    public static List<DaotuTalent.Entry> pickCandidates(net.minecraft.world.entity.player.Player player,
                                                         int n, List<String> exclude) {
        List<DaotuTalent.Entry> pool = new ArrayList<>();
        for (DaotuTalent.Entry e : DaotuTalent.all()) {
            if (!exclude.contains(e.id())) {
                pool.add(e);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(DaotuTalent.all());
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        // 洗牌取前 n
        var rnd = player.getRandom();
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            DaotuTalent.Entry tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return new ArrayList<>(pool.subList(0, Math.min(n, pool.size())));
    }

    /** 已获得的词条 id 列表（存在 {@code run/talents}）。 */
    public static List<String> owned(ServerPlayer player) {
        return DaotuRun.talents(player);
    }

    /** 记一条已获得的词条。 */
    public static void markOwned(ServerPlayer player, String id) {
        DaotuRun.addTalent(player, id);
    }
}
