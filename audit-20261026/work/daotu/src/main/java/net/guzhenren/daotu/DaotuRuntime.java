package net.guzhenren.daotu;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.server.level.ServerPlayer;

/**
 * 每 tick 的运行时维护（按秒节流）。
 *
 * <p>三件事：
 * <ol>
 *   <li>按 {@code GongXianZhi} 刷新势力档位（只升不降）；</li>
 *   <li>把道途的气运上限加成写进 {@code qiyun_shangxian}（<b>只升不降</b>）；</li>
 *   <li>属性修正按需重建。</li>
 * </ol>
 *
 * <p><b>气运为什么只升不降</b>：本体有 {@code if (qiyun_shangxian < qiyun) qiyun = qiyun_shangxian}
 * 的逻辑，且从不覆盖 {@code qiyun_shangxian} 本身。若我们下调它，玩家已攒的气运会被削掉。
 * 因此这里只做 {@code max(当前, 基础值 × (1 + 加成))}。
 *
 * <p><b>为什么不写 {@code zuida_zhenyuan}</b>：本体会从它反推 {@code kongqiao}，
 * 改动会破坏开窍进度。
 */
public final class DaotuRuntime {

    /** 每秒一次（20 tick）。 */
    private static final int PERIOD = 20;

    /** 上次由本插件写入的 {@code qiyun_shangxian} 值（用于识别本体是否改写过）。 */
    private static final String KEY_QIYUN_SET = "qiyunSet";

    /** 上次写入时所依据的本体基准值（用于在 pct 变化后重算，而不把加成再乘一遍）。 */
    private static final String KEY_QIYUN_BASE = "qiyunBase";

    private DaotuRuntime() {
    }

    public static void onTick(ServerPlayer player) {
        if (player.tickCount % PERIOD != 0) {
            return;
        }
        try {
            boolean changed = false;

            // ---- 历练：游历 + 修炼（每秒一次，内部再按 tick 节流） ----
            if (DaotuOpening.hasOpenedQiao(player)) {
                DaotuLiLian.onSecond(player);
            }

            // ---- 成长 buff 维护 ----
            // 本体在四气 buff 到期时会 removeAllEffects() 清空全部效果，
            // 一次性发放靠不住，必须每秒兜底续上。
            if (DaotuOpening.hasOpenedQiao(player)) {
                DaotuBuffs.maintain(player);
            }

            // ---- 词条：每秒回复（生命 / 真元 / 念头） ----
            if (DaotuOpening.hasOpenedQiao(player)) {
                DaotuCombatEffects.tickRegen(player);
            }

            // ---- 气运上限（只升不降） ----
            changed |= syncQiyunCap(player);

            // ---- 属性修正 ----
            if (changed || player.tickCount % (PERIOD * 5) == 0) {
                DaotuAttributes.sync(player);
            }
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onTick 失败（已忽略）", t);
        }
    }

    /**
     * 气运上限加成。
     *
     * <p><b>v1.4.0</b>：原本由「术道节点」提供，节点系统删除后没有来源了，
     * 恒返回 0。保留整个方法体是为了让「只升不降」的逻辑在将来重新接入时不必重写。
     *
     * @return 是否改动
     */
    private static boolean syncQiyunCap(ServerPlayer player) {
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double pct = 0.0;
        if (pct <= 0.0) {
            return false; // 无加成，绝不去碰本体字段
        }
        double lastSet = DaotuData.getDouble(player, KEY_QIYUN_SET, -1.0);
        double lastBase = DaotuData.getDouble(player, KEY_QIYUN_BASE, -1.0);

        double base;
        if (lastSet >= 0.0 && Math.abs(v.qiyun_shangxian - lastSet) <= 1.0E-6) {
            // 当前值仍是我们上次写入的值 -> 本体没动过，基准沿用上次的
            base = Math.max(0.0, lastBase);
        } else {
            // 本体改写（或首次进入）：以当前值为新的基准
            base = Math.max(0.0, v.qiyun_shangxian);
        }

        double desired = base * pct;
        double target = base + desired;

        if (Math.abs(target - v.qiyun_shangxian) <= 1.0E-6) {
            return false; // 已经是对的，幂等
        }
        // 只升不降：绝不把上限往下调（那会连带削掉本体已攒的气运）
        if (target < v.qiyun_shangxian) {
            if (Math.abs(base - lastBase) > 1.0E-6 || lastSet < 0.0) {
                DaotuData.putDouble(player, KEY_QIYUN_BASE, base);
                DaotuData.putDouble(player, KEY_QIYUN_SET, v.qiyun_shangxian);
            }
            return false;
        }

        v.qiyun_shangxian = target;
        v.markSyncDirty();
        DaotuData.putDouble(player, KEY_QIYUN_BASE, base);
        DaotuData.putDouble(player, KEY_QIYUN_SET, target);
        return true;
    }
}
