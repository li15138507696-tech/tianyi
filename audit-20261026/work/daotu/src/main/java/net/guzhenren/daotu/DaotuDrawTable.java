package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * 抽选档位表。
 *
 * <p><b>刻意用查表而不是公式</b>：公式（如 {@code base * decay^n}）在前几次抽选时
 * 掉得太快，玩家开局二十次就感觉「没成长」。查表可以手工把前 5 次调得很甜，
 * 之后平缓衰减，手感更接近肉鸽游戏。
 *
 * <p><b>力量 / 生命 / 速度 / 攻速必须是百分比</b>：原版攻击力 base = 1.0，
 * 若用平加，抽 100 次后 +84 点等于 84 倍攻击，直接崩。生命同理（base = 20）。
 * 防御、抗性、其它是平加，因为它们本身量级小。
 */
public final class DaotuDrawTable {

    /** 一次档位：所有值都是「一转、未缩放」的原始值。 */
    public record Tier(int index, double forcePct, double defense, double healthPct,
                       double speedPct, double resistPct, double other) {
    }

    /**
     * 档位表。抽次从 1 开始。
     *
     * <pre>
     * 抽次   档   力量%   防御   生命%   速度%   抗性%   其它
     * 1-5    1   +3.0%  +0.5   +3.0%  +1.0%  +1.0%  +0.5
     * 6-15   2   +2.5%  +0.4   +2.5%  +0.8%  +0.8%  +0.4
     * 16-30  3   +2.0%  +0.3   +2.0%  +0.6%  +0.6%  +0.3
     * 31-60  4   +1.5%  +0.2   +1.5%  +0.4%  +0.4%  +0.2
     * 61+    5   +1.0%  +0.1   +1.0%  +0.3%  +0.3%  +0.1
     * </pre>
     */
    private static final Tier[] TIERS = {
        new Tier(1, 0.030, 0.5, 0.030, 0.010, 0.010, 0.5),
        new Tier(2, 0.025, 0.4, 0.025, 0.008, 0.008, 0.4),
        new Tier(3, 0.020, 0.3, 0.020, 0.006, 0.006, 0.3),
        new Tier(4, 0.015, 0.2, 0.015, 0.004, 0.004, 0.2),
        new Tier(5, 0.010, 0.1, 0.010, 0.003, 0.003, 0.1),
    };

    /** 每次给几个候选。 */
    public static final int OPTIONS = 5;

    // ---------------------------------------------------------------- 软上限

    /** 击退抗性总上限（原版语义就是 0..1）。 */
    public static final double KNOCKBACK_CAP = 1.0;
    /** 移速总上限（+100%）。 */
    public static final double SPEED_CAP = 1.0;
    /** 攻速总上限（+150%）。 */
    public static final double ATTACK_SPEED_CAP = 1.5;
    /** 生命总上限（+200%）。 */
    public static final double HEALTH_CAP = 2.0;
    /** 体型上限（+100%）：再高会卡墙。 */
    public static final double SCALE_CAP = 1.0;
    /** 跨越高度上限（+2.0 格）：再高等于爬墙。 */
    public static final double STEP_CAP = 2.0;

    private DaotuDrawTable() {
    }

    /** 抽次（从 1 开始）对应的档位。 */
    public static Tier tierFor(int drawIndex) {
        if (drawIndex <= 5) {
            return TIERS[0];
        }
        if (drawIndex <= 15) {
            return TIERS[1];
        }
        if (drawIndex <= 30) {
            return TIERS[2];
        }
        if (drawIndex <= 60) {
            return TIERS[3];
        }
        return TIERS[4];
    }

    /**
     * 转数缩放：{@code 1.4^(转数-1)} → 1 / 1.40 / 1.96 / 2.74 / 3.84。
     *
     * <p>转数越高，同样一次抽选给得越多，这样高转玩家不会觉得成长停滞。
     * {@code zhuanshu} 只读，绝不写。
     */
    public static double zhuanScale(double zhuanshu) {
        double z = Math.max(1.0, Math.min(5.0, zhuanshu));
        return Math.pow(1.4, z - 1.0);
    }

    /** 候选。{@code key} 是 {@code run/attrs} 的键；{@code percent} 决定挂哪种 modifier。 */
    public record Candidate(String key, String label, double amount, boolean percent) {
        /** 是否是整条词条（key 以 {@code talent:} 开头）。 */
        public boolean isTalent() {
            return key.startsWith(TALENT_PREFIX);
        }

        /** 词条 id（非词条返回 null）。 */
        public String talentId() {
            return isTalent() ? key.substring(TALENT_PREFIX.length()) : null;
        }
    }

    /** 词条候选的 key 前缀。 */
    public static final String TALENT_PREFIX = "talent:";

    /**
     * 生成本次的 5 个候选。
     *
     * <p>软上限已满的项<b>不再出现</b>，避免给出无效选项。
     *
     * <p><b>v1.4.0</b>：并入 tianfu 词条后，候选池里混有两类东西：
     * <ul>
     *   <li><b>属性</b>——key 是属性键，{@code DaotuRunAttributes} 直接挂 modifier；</li>
     *   <li><b>词条</b>——key 形如 {@code talent:xxx}，选中时整条应用
     *       （可能同时给属性 + 道痕 + buff）。</li>
     * </ul>
     * 词条给的是「一次性大礼」，属性给的是「小步累积」，两者混排让每次抽选都有期待。
     */
    public static List<Candidate> candidates(Player player, int drawIndex, RandomSource rnd) {
        Tier t = tierFor(drawIndex);
        double s = zhuanScale(DaotuRun.zhuanshu(player));
        List<Candidate> pool = new ArrayList<>();

        // ---- 力量（百分比，无上限） ----
        pool.add(new Candidate("attack_damage", "力量 +" + pct(t.forcePct() * s),
                t.forcePct() * s, true));

        // ---- 生命（百分比，软上限 +200%） ----
        if (DaotuRun.attr(player, "max_health") < HEALTH_CAP) {
            pool.add(new Candidate("max_health", "生命 +" + pct(t.healthPct() * s),
                    t.healthPct() * s, true));
        }

        // ---- 速度（百分比，软上限 +100%） ----
        if (DaotuRun.attr(player, "movement_speed") < SPEED_CAP) {
            pool.add(new Candidate("movement_speed", "速度 +" + pct(t.speedPct() * s),
                    t.speedPct() * s, true));
        }

        // ---- 攻速（百分比，软上限 +150%） ----
        if (DaotuRun.attr(player, "attack_speed") < ATTACK_SPEED_CAP) {
            pool.add(new Candidate("attack_speed", "攻速 +" + pct(t.speedPct() * s * 1.5),
                    t.speedPct() * s * 1.5, true));
        }

        // ---- 防御（平加，无上限）：护甲韧性 + 伤害吸收 ----
        pool.add(new Candidate("armor_toughness", "护甲韧性 +" + flat(t.defense() * s),
                t.defense() * s, false));
        pool.add(new Candidate("max_absorption", "伤害吸收 +" + flat(t.defense() * s * 2.0),
                t.defense() * s * 2.0, false));

        // ---- 抗性（平加，击退抗性软上限 1.0） ----
        if (DaotuRun.attr(player, "knockback_resistance") < KNOCKBACK_CAP) {
            pool.add(new Candidate("knockback_resistance", "击退抗性 +" + pct(t.resistPct() * s),
                    t.resistPct() * s, false));
        }
        pool.add(new Candidate("fall_damage_multiplier", "摔伤减免 +" + pct(t.resistPct() * s),
                -t.resistPct() * s, false));

        // ---- 其它（平加，无上限） ----
        pool.add(new Candidate("luck", "幸运 +" + flat(t.other() * s), t.other() * s, false));
        pool.add(new Candidate("safe_fall_distance", "安全摔落 +" + flat(t.other() * s),
                t.other() * s, false));
        pool.add(new Candidate("attack_knockback", "攻击击退 +" + flat(t.other() * s * 0.5),
                t.other() * s * 0.5, false));
        pool.add(new Candidate("oxygen_bonus", "水下呼吸 +" + flat(t.other() * s),
                t.other() * s, false));
        if (DaotuRun.attr(player, "step_height") < STEP_CAP) {
            pool.add(new Candidate("step_height", "跨越高度 +" + flat(t.other() * s * 0.5),
                    t.other() * s * 0.5, false));
        }
        if (DaotuRun.attr(player, "scale") < SCALE_CAP) {
            pool.add(new Candidate("scale", "体型 +" + pct(t.other() * s),
                    t.other() * s, true));
        }

        // ---- 词条（整条效果，一次性大礼） ----
        // 只取未获得的，避免重复；词条数远多于 5，所以每次都能凑齐
        List<DaotuTalent.Entry> talents = DaotuTalentApply.pickCandidates(
                player, 3, DaotuRun.talents(player));
        for (DaotuTalent.Entry te : talents) {
            String sum = te.summary();
            String label = te.name() + (sum.isEmpty() ? "" : " · " + sum);
            pool.add(new Candidate(TALENT_PREFIX + te.id(), label, 0.0, false));
        }

        // 洗牌后取前 5 个
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Candidate tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        int n = Math.min(OPTIONS, pool.size());
        return new ArrayList<>(pool.subList(0, n));
    }

    /** 百分比格式化：{@code 0.03 -> "3.0%"}。 */
    public static String pct(double v) {
        return String.format("%.1f%%", v * 100.0);
    }

    /** 平加格式化：{@code 0.5 -> "0.50"}。 */
    public static String flat(double v) {
        return String.format("%.2f", v);
    }
}
