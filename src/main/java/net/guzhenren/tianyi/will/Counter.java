package net.guzhenren.tianyi.will;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

/**
 * 反制手段的逐项效果矩阵。
 *
 * <h2>纪律：没有「免疫天意」这一个效果</h2>
 * 每一种反制改变的是<b>不同的一环</b>。这个映射不是设计取舍，而是
 * {@code docs/考据-天意能力清单.md} 里逐条核到的原文落点：
 *
 * <table border="1">
 *   <tr><th>手段</th><th>改变的环节</th><th>原文依据（节选）</th></tr>
 *   <tr><td>暗渡</td><td>感知</td>
 *       <td>与神不知齐名；本章只说「神秘非凡、众说纷纭…从没有公认的正确答案」
 *           → <b>机制未核实</b>，故只给最保守的一层（痕迹不被看到）</td></tr>
 *   <tr><td>神不知</td><td><b>只改推算</b></td>
 *       <td>「任何的念头、意志、情感，都推测不出你的状态和来历」
 *           「除非是九转智道蛊仙来推算你，才有成功的可能」</td></tr>
 *   <tr><td>天消意散</td><td><b>只改载体</b>（并损伤载体）</td>
 *       <td>「专门针对天意，能够将天意驱除干净」；代价见「春秋蝉裂纹」</td></tr>
 *   <tr><td>炼道清除</td><td><b>只改载体</b></td>
 *       <td>升炼再逆炼，可行但极难</td></tr>
 *   <tr><td>炼化天道道痕</td><td><b>只改载体</b>（治根）</td>
 *       <td>「治根之法便是直接炼化了这些天道道痕」</td></tr>
 *   <tr><td>天机手段</td><td>推算</td>
 *       <td>天机仙蛊是牵引天意、也用于预测灾劫的载体 → 落在推算一侧</td></tr>
 *   <tr><td>天外之魔（完整）</td><td><b>只改宿命约束</b></td>
 *       <td>「方源成为完整的天外之魔，已经超脱限制」；但第七章整章显示
 *           天意照样摧毁其资源点、照样在其仙窍内生天意</td></tr>
 *   <tr><td>天外之魔（半成品）</td><td>宿命约束（部分）</td>
 *       <td>「他却又不是完整的天外之魔。所以，他还受到天意的影响和摆布」</td></tr>
 * </table>
 *
 * <h2>三条硬边界</h2>
 * <ol>
 *   <li><b>天外之魔不等于免疫攻击。</b>{@link #immuneToCalamity} 恒为 {@code false}。</li>
 *   <li><b>神不知只防推算，不产生感知盲区。</b>天意照样看得见你，只是算不出你的来历。</li>
 *   <li><b>任何手段都不改变「天道控制」这一环。</b>那一环只随宿命阶段变化，
 *       不是某个杀招能撬动的。</li>
 * </ol>
 */
public final class Counter {
    private Counter() {
    }

    /** 反制手段。名字与原著一致。 */
    public enum Kind {
        /** 暗渡：与神不知齐名，机制原文未明说。 */
        ANDU,
        /** 神不知：屏蔽念头/意志/情感三要素的推算。 */
        SHENBUZHI,
        /** 天消意散：智道杀招，把天意从载体上剥离。 */
        TIANXIAOYISAN,
        /** 炼道清除：升炼再逆炼。 */
        LIANDAO_PURGE,
        /** 炼化天道道痕：治根，从源头断掉天意产生的条件。 */
        REFINE_HEAVEN_DAOMARK,
        /** 天机手段：以天机仙蛊牵引/预测。 */
        TIANJI,
        /** 完整的天外之魔：超脱限制。 */
        TIANWAI_FULL,
        /** 半成品天外之魔：仍受天意摆布。 */
        TIANWAI_PARTIAL
    }

    /** 被改变的环节。 */
    public enum Aspect {
        /** 天意能不能看到。 */
        PERCEPTION,
        /** 天意能不能推算（神不知的唯一落点）。 */
        DIVINATION,
        /** 天意赖以存在的载体（天消意散 / 炼道 / 炼化道痕的唯一落点）。 */
        CARRIER,
        /** 宿命对个体的约束（天外之魔的唯一落点）。 */
        DESTINY_BIND,
        /** 天意对天道的控制份额。只随宿命阶段变化，任何杀招都撬不动。 */
        HEAVEN_CONTROL
    }

    /**
     * 一次反制的结算结果。
     *
     * @param carrierDamage 对载体的损伤（天消意散会把载体打出裂纹）
     * @param unreliable    该手段的机制在原著里未被明确（暗渡）
     */
    public record Result(Kind kind, EnumSet<Aspect> changed, long coverTicks,
                         boolean localBlind, double carrierDamage, boolean unreliable,
                         String note) {
    }

    /** 该手段改变哪些环节。 */
    public static EnumSet<Aspect> aspectsOf(Kind kind) {
        return switch (kind) {
            // 机制未核实 → 只取最保守的一层
            case ANDU -> EnumSet.of(Aspect.PERCEPTION);
            // 原文：只有「念头、意志、情感」推测不出来 → 纯推算
            case SHENBUZHI, TIANJI -> EnumSet.of(Aspect.DIVINATION);
            // 原文：把天意从载体上驱除 / 逆炼 / 炼化道痕 → 纯载体
            case TIANXIAOYISAN, LIANDAO_PURGE, REFINE_HEAVEN_DAOMARK ->
                    EnumSet.of(Aspect.CARRIER);
            // 原文：超脱限制 → 纯宿命约束，不碰感知也不碰天道控制
            case TIANWAI_FULL -> EnumSet.of(Aspect.DESTINY_BIND);
            case TIANWAI_PARTIAL -> EnumSet.of(Aspect.DESTINY_BIND);
        };
    }

    /** 该手段的强度系数（半成品天外之魔只有一部分）。 */
    public static double strengthFactor(Kind kind) {
        return switch (kind) {
            case TIANWAI_PARTIAL -> 0.35;
            case ANDU -> 0.6;          // 机制未核实，保守
            case REFINE_HEAVEN_DAOMARK -> 1.0; // 治根，但需要炼道能力（由前提判定）
            default -> 1.0;
        };
    }

    /** 该手段的机制在原著里是否未被明确。 */
    public static boolean unverifiedInCanon(Kind kind) {
        return kind == Kind.ANDU;
    }

    /**
     * 神不知可被九转智道蛊仙破掉（原文明确）。
     *
     * @param nineTurnWisdom 推算者是否达到九转智道
     */
    public static boolean breakableBy(Kind kind, boolean nineTurnWisdom) {
        return nineTurnWisdom && (kind == Kind.SHENBUZHI || kind == Kind.TIANJI);
    }

    /**
     * 该手段必须满足的世界前提；不满足就不能生效（不是自动免疫）。
     *
     * <p>注意：<b>没有任何一个手段的前提是「我是天外之魔所以全都免疫」</b>。
     */
    public static boolean prerequisitesMet(Kind kind, Assessment.Actor actor,
                                          Destiny.DestinyGu destiny, boolean hasHeavenDaoMark) {
        if (actor == null) {
            return false;
        }
        return switch (kind) {
            case ANDU, SHENBUZHI, TIANJI, TIANWAI_FULL, TIANWAI_PARTIAL -> true;
            // 清载体类手段必须真的存在可被清除的天意载体
            case TIANXIAOYISAN, LIANDAO_PURGE -> destiny != null;
            // 炼化天道道痕：前提是体内/仙窍里确实有天道道痕，否则无从炼起
            case REFINE_HEAVEN_DAOMARK -> hasHeavenDaoMark;
        };
    }

    /**
     * 施加一次反制，并<b>真的</b>写进知识库 / 载体网络。
     *
     * @param strength 施术强度 0..1（会再乘 {@link #strengthFactor}）
     * @return 结算结果；{@code null} 表示前提不满足，什么都没发生
     */
    public static Result apply(Kind kind, Assessment.Actor actor, Knowledge.Store knowledge,
                               Destiny.DestinyGu destiny, long now, double strength) {
        return apply(kind, actor, knowledge, destiny, null, now, strength, false);
    }

    public static Result apply(Kind kind, Assessment.Actor actor, Knowledge.Store knowledge,
                               Destiny.DestinyGu destiny, Carrier.Network carriers,
                               long now, double strength, boolean nineTurnWisdom) {
        boolean hasHeavenDaoMark = actor != null && actor.hasHeavenDaoMark;
        if (!prerequisitesMet(kind, actor, destiny, hasHeavenDaoMark)) {
            return null;
        }
        if (breakableBy(kind, nineTurnWisdom)) {
            return new Result(kind, EnumSet.noneOf(Aspect.class), 0L, false, 0.0,
                    false, "被九转智道蛊仙破掉：推算照常成立");
        }

        double s = Knowledge.clamp(strength, 0.0, 1.0) * strengthFactor(kind);
        EnumSet<Aspect> changed = aspectsOf(kind);
        long cover = 0L;
        boolean localBlind = false;
        double carrierDamage = 0.0;
        String note;

        if (changed.contains(Aspect.PERCEPTION) && actor != null) {
            // 只有「感知」这一环会真的产生盲区
            cover = (long) (WillConfig.BLIND_MIN_TICKS * 2L * s);
            knowledge.cover(actor.id, now, cover,
                    kind.name().toLowerCase(Locale.ROOT));
        }
        if (changed.contains(Aspect.CARRIER) && actor != null) {
            localBlind = true;
            // 天消意散 / 炼道清除会损伤载体（春秋蝉被打出裂纹）
            carrierDamage = kind == Kind.TIANXIAOYISAN ? 0.20 * s
                    : kind == Kind.LIANDAO_PURGE ? 0.12 * s : 0.05 * s;
            if (carriers != null) {
                var region = carriers.get("region:" + actor.id);
                if (region == null) {
                    // 没有具名载体时，按世界区域载体逐个扣（由 MC 层给出具体 node 亦可）
                    for (var n : carriers.all()) {
                        if (n.dim.equals(actor.dim)) {
                            carriers.intrude(n.id, carrierDamage);
                        }
                    }
                } else {
                    carriers.intrude(region.id, carrierDamage);
                }
            }
        }
        note = switch (kind) {
            case ANDU -> "痕迹被抹（机制未核实，仅按最保守一层实现）";
            case SHENBUZHI -> "念头/意志/情感三要素推算不出；天意仍看得见你";
            case TIANXIAOYISAN -> "把天意从载体上驱除，但载体受损（裂纹）";
            case LIANDAO_PURGE -> "升炼再逆炼剥掉载体上的天意";
            case REFINE_HEAVEN_DAOMARK -> "炼化天道道痕，从源头断掉天意产生的条件";
            case TIANJI -> "以天机仙蛊牵引/遮蔽推算";
            case TIANWAI_FULL -> "超脱宿命限制；天意仍能换棋子、造人劫、毁资源";
            case TIANWAI_PARTIAL -> "只超脱一部分，仍受天意摆布";
        };
        return new Result(kind, changed, cover, localBlind, carrierDamage,
                unverifiedInCanon(kind), note);
    }

    /**
     * 是否免疫灾劫。<b>恒为 {@code false}</b>。
     *
     * <p>原文明确：完整天外之魔「超脱限制」，但天意照样摧毁其资源点；
     * 灾劫同时是道痕的主要来源，逃避它只会让修为停滞。把任何手段写成
     * 「免疫攻击」都同时违背原著与平衡。
     */
    public static boolean immuneToCalamity(Kind kind) {
        return false;
    }

    /** 该手段给「推算」带来的噪声（0..1）。感知类不给推算噪声。 */
    public static double divinationNoise(Kind kind) {
        return switch (kind) {
            // 神不知/天机：正面挡推算
            case SHENBUZHI, TIANJI -> 0.75 * strengthFactor(kind);
            // 暗渡：只挡痕迹，顺带一点推算噪声，且机制未核实 → 保守
            case ANDU -> 0.15 * strengthFactor(kind);
            // 载体类与宿命类都不影响「推算」这一环
            case TIANXIAOYISAN, LIANDAO_PURGE, REFINE_HEAVEN_DAOMARK -> 0.0;
            case TIANWAI_FULL, TIANWAI_PARTIAL -> 0.0;
        };
    }

    /** 汇总一个主体身上所有反制带来的推算噪声（取最大，不叠乘到 1）。 */
    public static double totalNoise(EnumSet<Kind> kinds) {
        double best = 0.0;
        for (Kind k : kinds) {
            best = Math.max(best, divinationNoise(k));
        }
        return best;
    }

    public static String describe(Map<Kind, Double> active) {
        StringBuilder sb = new StringBuilder();
        active.forEach((k, v) -> sb.append(k).append('=').append(Knowledge.fmt(v)).append(' '));
        return sb.toString().trim();
    }
}
