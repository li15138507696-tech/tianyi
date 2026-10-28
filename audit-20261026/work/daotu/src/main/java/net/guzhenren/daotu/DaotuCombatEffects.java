package net.guzhenren.daotu;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 战斗与回复乘区：读取 {@code run/attrs} 里以 {@code c_} 开头的键。
 *
 * <p>这些效果来自并入的 tianfu 词条（暴击、闪避、吸血、减伤、反伤等）。
 * 原版没有对应的 Attribute，只能挂在伤害事件上。
 *
 * <p><b>与 {@link DaotuRunAttributes} 的分工</b>：
 * <ul>
 *   <li>{@code DaotuRunAttributes} 管能挂成 AttributeModifier 的（力量/生命/速度…）；</li>
 *   <li>本类管挂不了的（暴击率、闪避率、吸血比例…）。</li>
 * </ul>
 *
 * <p><b>为什么键名带 {@code c_} 前缀</b>：{@code DaotuRunAttributes.apply} 会遍历
 * {@code run/attrs} 的全部键去挂 modifier，遇到 {@code c_crit_chance} 这种
 * 非属性键会刷「未知属性键」日志。加前缀后，那边直接跳过。
 *
 * <p><b>数值口径</b>：全部是「加性比例」，如 {@code c_reduction = 0.35} 表示减伤 35%。
 * 多个来源直接相加，最终各自钳到合理区间。
 */
public final class DaotuCombatEffects {

    /** 暴击率上限（100%）。 */
    private static final double CRIT_CAP = 1.0;
    /** 闪避率上限（75%，再高会变成无敌）。 */
    private static final double DODGE_CAP = 0.75;
    /** 减伤上限（80%，留 20% 让玩家仍会被打疼）。 */
    private static final double REDUCTION_CAP = 0.80;
    /** 吸血上限（50%）。 */
    private static final double LEECH_CAP = 0.50;

    private DaotuCombatEffects() {
    }

    private static double v(Player player, String key) {
        return DaotuRun.attr(player, key);
    }

    /**
     * 受伤侧：减伤 + 闪避。
     *
     * <p>闪避直接 {@code setAmount(0)}——原版没有「未命中」概念，
     * 伤害归零是最接近的表现，且不会误触发受击效果。
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        try {
            // 闪避：概率判定
            double dodge = Math.min(DODGE_CAP, Math.max(0.0, v(player, "c_dodge")));
            if (dodge > 0.0 && player.getRandom().nextDouble() < dodge) {
                event.setAmount(0.0f);
                return;
            }
            // 减伤
            double red = Math.min(REDUCTION_CAP, Math.max(0.0, v(player, "c_reduction")));
            if (red > 0.0) {
                event.setAmount((float) (event.getAmount() * (1.0 - red)));
            }
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onIncomingDamage(词条) 失败（已忽略）", t);
        }
    }

    /**
     * 输出侧：暴击 + 残血/满血增伤 + 吸血。
     *
     * <p>暴击在这里实现为「概率放大本次伤害」，因为原版没有暴击系统。
     * 暴击伤害倍率固定 2.0 再加 {@code c_crit_damage}。
     */
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        try {
            float amount = event.getNewDamage();

            // 暴击
            double critChance = Math.min(CRIT_CAP, Math.max(0.0, v(player, "c_crit_chance")));
            if (critChance > 0.0 && player.getRandom().nextDouble() < critChance) {
                double critMult = 2.0 + Math.max(0.0, v(player, "c_crit_damage"));
                amount *= (float) critMult;
            }

            // 满血增伤（生命 ≥ 90%）
            double outFull = Math.max(0.0, v(player, "c_out_full"));
            if (outFull > 0.0 && player.getHealth() >= player.getMaxHealth() * 0.9f) {
                amount *= (float) (1.0 + outFull);
            }

            // 残血增伤（生命 ≤ 40%）
            double outLow = Math.max(0.0, v(player, "c_out_low"));
            if (outLow > 0.0 && player.getHealth() <= player.getMaxHealth() * 0.4f) {
                amount *= (float) (1.0 + outLow);
            }

            event.setNewDamage(amount);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onLivingDamage(词条) 失败（已忽略）", t);
        }
    }

    /**
     * 吸血：由 {@link DaotuEvents} 在 {@code LivingDamageEvent.Post} 调用。
     *
     * <p>必须在 Post 阶段做，因为 Pre 阶段还不知道最终扣了多少血。
     */
    public static void healAfterDamage(Player player, float dealt) {
        try {
            double leech = Math.min(LEECH_CAP, Math.max(0.0, v(player, "c_leech")));
            if (leech <= 0.0 || dealt <= 0.0f) {
                return;
            }
            float heal = (float) (dealt * leech);
            if (heal > 0.0f && player.getHealth() < player.getMaxHealth()) {
                player.heal(heal);
            }
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu 吸血结算失败（已忽略）", t);
        }
    }

    /**
     * 每秒回复：生命 / 真元 / 念头。
     *
     * <p>由 {@link DaotuRuntime} 每秒调用一次。真元与念头都<b>不写上限字段</b>，
     * 只做「读→加→夹到 zuida_*」，与 tianfu 的口径一致。
     */
    public static void tickRegen(Player player) {
        try {
            // 生命：c_regen_hp 是「每 5 秒回复的比例」，这里按秒折算
            double hpPct = Math.max(0.0, v(player, "c_regen_hp"));
            if (hpPct > 0.0 && player.getHealth() < player.getMaxHealth()) {
                float heal = (float) (player.getMaxHealth() * hpPct / 5.0);
                if (heal > 0.0f) {
                    player.heal(heal);
                }
            }

            // 真元 / 念头
            double zyPct = Math.max(0.0, v(player, "c_regen_zy"));
            double ntPct = Math.max(0.0, v(player, "c_regen_nt"));
            if (zyPct <= 0.0 && ntPct <= 0.0) {
                return;
            }
            var vars = player.getData(
                    net.guzhenren.network.GuzhenrenModVariables.PLAYER_VARIABLES);
            boolean dirty = false;
            if (zyPct > 0.0 && vars.zuida_zhenyuan > 0.0) {
                double add = vars.zuida_zhenyuan * zyPct / 5.0;
                double next = Math.min(vars.zuida_zhenyuan, vars.zhenyuan + add);
                if (next > vars.zhenyuan) {
                    vars.zhenyuan = next;
                    dirty = true;
                }
            }
            if (ntPct > 0.0 && vars.niantou_rongliang > 0.0) {
                // 本体没有 zuida_niantou，念头上限就是 niantou_rongliang
                double add = vars.niantou_rongliang * ntPct / 5.0;
                double next = Math.min(vars.niantou_rongliang, vars.niantou + add);
                if (next > vars.niantou) {
                    vars.niantou = next;
                    dirty = true;
                }
            }
            if (dirty) {
                vars.markSyncDirty();
            }
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu 每秒回复失败（已忽略）", t);
        }
    }
}
