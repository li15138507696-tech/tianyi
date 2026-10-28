package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 灾劫：地灾、天劫、浩劫、万劫。
 *
 * <h2>不能用一道闪电冒充灾劫</h2>
 * 每一场灾劫都必须是<b>有过程的</b>：
 * <ol>
 *   <li>{@link Phase#GESTATING} 酝酿：世界里出现可被观察的征兆（灵气异动、天气、兽群回避、
 *       器物发热）。玩家此时还能做准备，甚至**反过来利用**。</li>
 *   <li>{@link Phase#MANIFEST} 显形：具体实体或环境效果登场（火雨、雷云、地裂、兽潮并不是
 *       灾劫本体，只是其中一档的表现）。</li>
 *   <li>{@link Phase#CLIMAX} 判定：到这里才分胜负。</li>
 *   <li>{@link Phase#AFTERMATH} 余波：给道痕收益，或按上限扣除。</li>
 * </ol>
 *
 * <h2>针对性有硬上限</h2>
 * 天意可以把一部分火力对准目标的弱点，但 {@link WillConfig#CALAMITY_TARGETING_CAP}
 * 之外必须是无差别部分 —— 这就是「不能无条件发动必杀攻击」的可执行形式。
 * 玩家也可以利用灾劫成长（{@link Run#profitable()}）。
 *
 * <h2>混沌不在天意的支配范围</h2>
 * {@link #chaosInfluenced()} 恒为 {@code false}：本模组的灾劫由天意/天道推动，
 * 混沌需要单独考据与实现，不在这里假定天意能支配它。
 */
public final class Calamity {
    private Calamity() {
    }

    /** 四等灾劫。升阶与转数/仙窍条件绑定（具体门槛见 {@link #minRank}）。 */
    public enum Tier {
        /** 地灾：最轻，环境级。 */
        DI_ZAI("地灾", 0, 0.25),
        /** 天劫：天象级，带实体。 */
        TIAN_JIE("天劫", 3, 0.45),
        /** 浩劫：大规模，区域级。 */
        HAO_JIE("浩劫", 5, 0.70),
        /** 万劫：最重，改变地貌。 */
        WAN_JIE("万劫", 6, 1.00);

        public final String cn;
        /** 触发所需的最低转数（[平衡参数]，原著未给数值门槛）。 */
        public final int minRank;
        /** 相对规模（0..1）。 */
        public final double scale;

        Tier(String cn, int minRank, double scale) {
            this.cn = cn;
            this.minRank = minRank;
            this.scale = scale;
        }

        public long gestation() {
            return switch (this) {
                case DI_ZAI -> WillConfig.CALAMITY_GESTATION_DI;
                case TIAN_JIE -> WillConfig.CALAMITY_GESTATION_TIAN;
                case HAO_JIE -> WillConfig.CALAMITY_GESTATION_HAO;
                case WAN_JIE -> WillConfig.CALAMITY_GESTATION_WAN;
            };
        }
    }

    /** 灾劫阶段。 */
    public enum Phase {
        GESTATING, MANIFEST, CLIMAX, AFTERMATH, DONE
    }

    /** 一场灾劫的规格（由条件推出，不是随机事件）。 */
    public static final class Spec {
        public final Tier tier;
        public final double scale;
        /** 针对性比例（0..{@link WillConfig#CALAMITY_TARGETING_CAP}）。 */
        public final double targeting;
        /** 通过后可得的道痕（可以按比例被玩家拿走）。 */
        public final double daoMarkReward;
        /** 失败惩罚系数（0..{@link WillConfig#CALAMITY_MAX_FAIL_PENALTY}）。 */
        public final double failPenalty;
        /** 世界效果键：由 MC 层按这些键施加真实效果。 */
        public final List<String> effectKeys;

        public Spec(Tier tier, double scale, double targeting, double daoMarkReward,
                    double failPenalty, List<String> effectKeys) {
            this.tier = tier;
            this.scale = Knowledge.clamp(scale, 0.0, 1.0);
            this.targeting = Knowledge.clamp(targeting, 0.0,
                    WillConfig.CALAMITY_TARGETING_CAP);
            this.daoMarkReward = Math.max(0.0, daoMarkReward);
            this.failPenalty = Knowledge.clamp(failPenalty, 0.0,
                    WillConfig.CALAMITY_MAX_FAIL_PENALTY);
            this.effectKeys = List.copyOf(effectKeys);
        }
    }

    /** 一场正在发生的灾劫。 */
    public static final class Run {
        public final String id;
        public final String target;
        public final String dim;
        public final long pos;
        public final Spec spec;
        public final long createdAt;
        public Phase phase = Phase.GESTATING;
        private long phaseSince;
        /** 已经落下的伤害（归一化 0..1）。 */
        public double damageDealt;
        /** 目标是否做了准备（渡劫准备、结阵、求助）。 */
        public boolean prepared;
        /** 是否被玩家反过来利用。 */
        public boolean exploited;
        public boolean resolved;
        public boolean success;
        public double daoMarks;

        public Run(String id, String target, String dim, long pos, Spec spec, long now) {
            this.id = id;
            this.target = target == null ? "" : target;
            this.dim = dim == null ? "" : dim;
            this.pos = pos;
            this.spec = spec;
            this.createdAt = now;
            this.phaseSince = now;
        }

        public long phaseSince() {
            return phaseSince;
        }

        /**
         * 推进灾劫。
         *
         * @param now      当前游戏刻
         * @param defense  目标的抵抗（0..1，含准备程度）
         * @return 当前阶段
         */
        public Phase tick(long now, double defense) {
            if (resolved) {
                return phase;
            }
            long inPhase = now - phaseSince;
            long gest = spec.tier.gestation();
            switch (phase) {
                case GESTATING -> {
                    if (inPhase >= gest) {
                        advance(Phase.MANIFEST, now);
                    }
                }
                case MANIFEST -> {
                    long manifest = Math.max(600L, gest / 3L);
                    if (inPhase >= manifest) {
                        advance(Phase.CLIMAX, now);
                    }
                }
                case CLIMAX -> {
                    long climax = Math.max(400L, gest / 6L);
                    if (inPhase >= climax) {
                        resolve(now, defense);
                        advance(Phase.AFTERMATH, now);
                    }
                }
                case AFTERMATH -> {
                    if (inPhase >= 200L) {
                        phase = Phase.DONE;
                    }
                }
                default -> {
                }
            }
            return phase;
        }

        private void advance(Phase p, long now) {
            phase = p;
            phaseSince = now;
        }

        /**
         * 判定成败。
         *
         * <p>关键：{@code targeting} 只是一部分火力，剩下的无差别部分意味着
         * <b>准备充分的玩家总能活下来</b>。这是「不是必杀」的算法形式。
         */
        private void resolve(long now, double defense) {
            double d = Knowledge.clamp(defense + (prepared ? 0.25 : 0.0), 0.0, 1.0);
            double aimed = spec.targeting * spec.scale;
            double unaimed = (1.0 - spec.targeting) * spec.scale * 0.55;
            double pressure = (aimed + unaimed) * (1.0 - d);
            damageDealt = Knowledge.clamp(pressure, 0.0, 1.0);
            // 活下来即算渡过；被削多少看 damageDealt
            success = damageDealt < 0.62 || d >= 0.8;
            resolved = true;
            if (success) {
                daoMarks = spec.daoMarkReward * (1.0 - damageDealt)
                        * (exploited ? 1.35 : 1.0);
            } else {
                daoMarks = -spec.daoMarkReward * spec.failPenalty;
            }
            exploding = false;
        }

        /** 目标被彻底击溃后，灾劫不再继续制造伤害（防越界抹杀）。 */
        public boolean exploding;

        /** 玩家是否从这场灾劫里真正得到了东西。 */
        public boolean profitable() {
            return resolved && success && daoMarks > 0.0;
        }

        public boolean finished() {
            return phase == Phase.DONE;
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "Calamity{%s %s %s target=%s dmg=%.2f marks=%.2f %s}",
                    id, spec.tier.cn, phase, target, damageDealt, daoMarks,
                    success ? "PASS" : "FAIL");
        }
    }

    /** 条件判定：灾劫不是想发就发，必须满足真实条件。 */
    public static boolean conditionsMet(Tier tier, int rank, double apertureStability,
                                        double heavenControl, double willBudget,
                                        boolean destinyDestroyed) {
        if (rank < tier.minRank) {
            return false;
        }
        // 宿命毁灭后，高阶灾劫的发动条件放宽（封锁解除），但仍需天道权限
        double need = destinyDestroyed ? 0.45 : 0.65;
        if (heavenControl < need) {
            return false;
        }
        if (willBudget < 0.30 * tier.scale + 0.10) {
            return false;
        }
        // 仙窍不稳时，天劫以上会失控 → 不允许发动（这是对天意的限制）
        return !(tier.ordinal() >= Tier.TIAN_JIE.ordinal() && apertureStability < 0.35);
    }

    /** 铸造一场灾劫的规格。 */
    public static Spec spec(Tier tier, double targetingWant, double daoMarkBase,
                            boolean destinyDestroyed) {
        return spec(tier, targetingWant, daoMarkBase, destinyDestroyed, 1.0);
    }

    /**
     * 3.14：天意强度带来的「规模」倍率。
     *
     * <p>规模只乘在 {@link Tier#scale} 上（最终仍夹在 0..1），<b>不增加灾劫种类、
     * 不增加针对性上限、不额外送道痕</b>：挨过来的收益仍是原来的公式，
     * 所以在高挡位下「硬扛灾劫」是更难而不是更值。
     *
     * @param scaleFactor 规模倍率（档位 1 为 1.0，与 3.13 完全一致）
     */
    public static Spec spec(Tier tier, double targetingWant, double daoMarkBase,
                            boolean destinyDestroyed, double scaleFactor) {
        double t = Knowledge.clamp(targetingWant, 0.0, WillConfig.CALAMITY_TARGETING_CAP);
        double reward = daoMarkBase * tier.scale * (destinyDestroyed ? 1.2 : 1.0);
        double scale = Knowledge.clamp(tier.scale * Math.max(0.2, scaleFactor), 0.0, 1.0);
        List<String> fx = switch (tier) {
            case DI_ZAI -> List.of("env:ground_crack", "env:qi_surge");
            case TIAN_JIE -> List.of("env:storm_cloud", "entity:thunder_manifest", "env:qi_surge");
            case HAO_JIE -> List.of("env:storm_cloud", "entity:calamity_beast_wave",
                    "env:terrain_scar", "env:qi_surge");
            case WAN_JIE -> List.of("env:storm_cloud", "entity:calamity_beast_wave",
                    "env:terrain_rewrite", "env:qi_collapse", "entity:tribulation_lord");
        };
        return new Spec(tier, scale, t, reward,
                tier.scale * WillConfig.CALAMITY_MAX_FAIL_PENALTY, fx);
    }

    /** 混沌是否被天意支配。恒 {@code false}。 */
    public static boolean chaosInfluenced() {
        return false;
    }

    /** 灾劫簿。 */
    public static final class Book {
        private final Map<String, Run> runs = new LinkedHashMap<>();

        public Run open(String id, String target, String dim, long pos, Spec spec, long now) {
            Run r = new Run(id, target, dim, pos, spec, now);
            runs.put(id, r);
            return r;
        }

        public Run get(String id) {
            return runs.get(id);
        }

        public void tick(long now) {
            runs.entrySet().removeIf(e -> e.getValue().finished()
                    && now - e.getValue().createdAt > 24_000L * 10L);
        }

        public List<Run> active() {
            List<Run> out = new ArrayList<>();
            for (Run r : runs.values()) {
                if (!r.finished()) {
                    out.add(r);
                }
            }
            return out;
        }

        public int countFor(String target) {
            int n = 0;
            for (Run r : runs.values()) {
                if (r.target.equals(target) && !r.finished()) {
                    n++;
                }
            }
            return n;
        }

        public List<Run> all() {
            return new ArrayList<>(runs.values());
        }
    }
}
