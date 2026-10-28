package net.guzhenren.tianyi.will;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 仙道机制：三气升仙、师法自然、仙窍、仙元、内部时间、寿蛊供给。
 *
 * <h2>这一层存在的理由</h2>
 * 天意要能「扶持/压制/制约长生」，它作用的对象必须是真实的仙道结构：
 * 升仙的门槛（三气调和与升仙人气）、仙窍的资源与内部时间、灾劫的收益与损失、
 * 以及天道对长生的封锁。没有这些，天意的干预就只能退回「刷怪/给物品」。
 *
 * <h2>天道封锁不由宿命当总闸（3.13.0）</h2>
 * 旧实现用 {@code Ascension.heavenLock(destiny, rank)} 把宿命阶段折算成「升仙封死」，
 * 等于把宿命当成普通玩法的总开关。现已<b>删除</b>：普通升仙在任何宿命阶段都允许，
 * 天道对长生的压力由灾劫与寿蛊各自承担（见 {@link DestinyRules#normalAscension}）。
 * 具体手段能不能做，去问 {@link DestinyRules}，不要再回到这里找布尔开关。
 *
 * <h2>寿蛊：只动未来供给，不动玩家背包</h2>
 * {@link LifespanSupply} <b>没有任何</b>扣除库存的方法。天意制约长生的方式是
 * 改变自然的产出周期与数量，玩家已有的寿蛊照旧能用。这是原著的因果方向
 * （供给变化），不是后台删物品。
 */
public final class Immortal {
    private Immortal() {
    }

    /** 升仙三气。数值由本体状态折算，语义见考据文档。 */
    public record SanQi(double tian, double di, double ren) {
        public double total() {
            return tian + di + ren;
        }

        /** 调和度：三者越接近 1:1:1 越调和；差得越多越容易失败。 */
        public double harmony() {
            double sum = total();
            if (sum <= 0.0) {
                return 0.0;
            }
            double a = tian / sum;
            double b = di / sum;
            double c = ren / sum;
            // 理想 1/3 的最大偏差 2/3 映射到 0..1
            double dev = (Math.abs(a - 1.0 / 3) + Math.abs(b - 1.0 / 3) + Math.abs(c - 1.0 / 3)) / (4.0 / 3.0);
            return Knowledge.clamp(1.0 - dev, 0.0, 1.0);
        }
    }

    /**
     * @deprecated 这是未接线的旧概率演示模型，不是升仙机制实现；新逻辑使用
     * {@code net.guzhenren.tianyi.ascension} 的服务端状态机。此模型不迁移为已完成的升仙记录。
     */
    @Deprecated(since = "3.13.3", forRemoval = true)
    public static final class Ascension {
        /** 师法自然累积的领悟（0..1）。 */
        public double shifaZiran = 0.0;
        /** 已尝试次数（失败可再来，但代价递增）。 */
        public int attempts = 0;
        public double rank = 0.0;

        /**
         * 师法自然：观察自然、效法自然的积累。
         *
         * @param observationTicks 实际处在自然环境中并专注观察的时长
         */
        public void observeNature(long observationTicks) {
            double gain = Math.min(0.02, observationTicks / 1_000_000.0);
            shifaZiran = Knowledge.clamp(shifaZiran + gain, 0.0,
                    WillConfig.SHIFA_ZIRAN_BONUS_CAP);
        }

        /** 该次升仙的成功率（0..1）。<b>[平衡参数]</b> */
        public double successChance(SanQi qi, double renqi) {
            double h = qi.harmony();
            double renqiTerm = Knowledge.clamp(renqi / 100.0, -0.5, 1.0) * 0.25;
            double ziran = shifaZiran * 0.30;
            double base = 0.18 + 0.55 * h + ziran + renqiTerm;
            base -= attempts * 0.06;
            return Knowledge.clamp(base, 0.02, 0.95);
        }

        /** 尝试结果。 */
        public record Result(boolean success, double chance, double daoMarks,
                             double lifespanCost, double apertureStability) {
        }

        /**
         * 执行一次升仙。
         *
         * <p>3.13.0 起没有 {@code heavenLocked} 参数：宿命不整体封死普通升仙，
         * 能不能升由三气、人气、师法自然与灾劫决定。
         *
         * @param roll 外部随机（0..1）。把随机源放在外面，测试才能确定性复现。
         */
        public Result attempt(SanQi qi, double renqi,
                              double stability, double roll) {
            double p = successChance(qi, renqi);
            attempts++;
            boolean ok = roll < p;
            if (ok) {
                rank += 1.0;
                // 升仙成功：仙窍立起来，稳定性从三气调和里拿
                return new Result(true, p, 10.0 * qi.harmony(),
                        -qi.total() * 0.05, Knowledge.clamp(0.4 + 0.5 * qi.harmony(), 0, 1));
            }
            // 失败：寿元与稳定性受损，但不会直接死（不搞必死判定）
            return new Result(false, p, 0.0,
                    qi.total() * 0.10 + 1.0, Knowledge.clamp(stability - 0.15, 0.0, 1.0));
        }

        public String describe() {
            return String.format(Locale.ROOT, "Ascension{rank=%.1f 师法自然=%.3f attempts=%d}",
                    rank, shifaZiran, attempts);
        }
    }

    /** 仙窍：仙元、内部时间、资源、稳定性。 */
    public static final class Aperture {
        public final String owner;
        /** 仙窍等级（对应转数层，1 起）。 */
        public int grade = 1;
        /** 稳定性 0..1：不稳时无法发动高阶灾劫，也会漏仙元。 */
        public double stability = 1.0;
        /** 仙元储量与上限。 */
        public double essence = 0.0;
        public double capacity = 100.0;
        /** 内部时间倍率（原著有内外时间差，具体比例未量化 → [平衡参数]）。 */
        public double innerTimeRatio = WillConfig.APERTURE_TIME_RATIO_MIN;
        /** 内部资源账（道痕/蛊材/元石等，键为注册 ID）。 */
        public final Map<String, Double> resources = new LinkedHashMap<>();
        /** 内部已累积的刻数（用于资源成熟）。 */
        public long innerTicks = 0L;
        /** 是否独立仙窍（洞天/福地）。 */
        public boolean independent;
        /** 仙窍内是否持有天道道痕（天意能在此产生的物质前提）。 */
        public double heavenDaoMark = 0.0;

        public Aperture(String owner) {
            this.owner = Knowledge.requireIdentity(owner);
        }

        public void advanceTime(long realTicks) {
            innerTicks += (long) (realTicks * innerTimeRatio);
            // 内部时间推移带来仙元恢复（速率 [平衡参数]）
            double regen = realTicks * 0.0005 * innerTimeRatio * stability;
            essence = Knowledge.clamp(essence + regen, 0.0, capacity);
        }

        public boolean consumeEssence(double amount) {
            if (essence + 1e-9 < amount) {
                return false;
            }
            essence -= amount;
            return true;
        }

        public void addResource(String registryId, double amount) {
            if (registryId == null || !registryId.contains(":")) {
                throw new IllegalArgumentException("仙窍资源必须用完整注册 ID：" + registryId);
            }
            resources.merge(registryId.toLowerCase(Locale.ROOT), amount, Double::sum);
        }

        public double resource(String registryId) {
            return resources.getOrDefault(registryId.toLowerCase(Locale.ROOT), 0.0);
        }

        public void damage(double amount) {
            stability = Knowledge.clamp(stability - Math.max(0.0, amount), 0.0, 1.0);
        }

        public void repair(double amount) {
            stability = Knowledge.clamp(stability + Math.max(0.0, amount), 0.0, 1.0);
        }

        /** 升级（升仙/仙窍成长；<b>不再</b>受宿命封锁开关限制）。 */
        public void upgrade(double stabilityBonus) {
            grade++;
            capacity *= 1.35;
            innerTimeRatio = Math.min(WillConfig.APERTURE_TIME_RATIO_MAX,
                    innerTimeRatio + 0.5);
            stability = Knowledge.clamp(stability + stabilityBonus, 0.0, 1.0);
        }

        /**
         * 天意能否渗入这个仙窍。
         *
         * <p>「在别的维度」不等于安全，也不等于天意进得去：取决于载体与接触渠道
         * （{@code carrierReach} 由 {@link Carrier} 层给出）。
         */
        public boolean willIntrusionPossible(boolean independentAperture, boolean hasHeavenDaoMark) {
            if (independentAperture) {
                return hasHeavenDaoMark;
            }
            return true;
        }

        /** 便捷重载：按自身字段判断（避风港成立，绝对免疫不成立）。 */
        public boolean willIntrusionPossible() {
            return willIntrusionPossible(independent, heavenDaoMark > 0.0);
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "Aperture{%s grade=%d stab=%.2f essence=%.0f/%.0f time×%.1f indep=%s}",
                    owner, grade, stability, essence, capacity, innerTimeRatio, independent);
        }
    }

    /**
     * 寿蛊供给。
     *
     * <p><b>没有任何扣除玩家库存的方法。</b>天意只能改变未来自然产出的周期与数量 ——
     * 这是「通过未来供给制约长生」的唯一合法实现方式。
     */
    public static final class LifespanSupply {
        /** 下一次自然产出时刻。 */
        public long nextSupplyAt;
        /** 自然产出速率倍率（1.0 = 原样）。 */
        public double rate = 1.0;
        /** 累积影响（正=丰产，负=歉收）。 */
        public double drift = 0.0;
        /** 已产出次数。 */
        public int produced = 0;
        /**
         * 经审计的寿蛊注册 ID 白名单。
         *
         * <p>由 {@code world/ShouGuSupply} 从本体 {@code NpcStock.longevityIds()} 灌入 ——
         * 也就是说「产出哪些寿蛊」不是我们编的，是本体自己认定属于寿蛊的那些。
         * 白名单为空时<b>不产出任何东西</b>，宁可不发也不发错的。
         */
        private final java.util.Set<String> allowedIds = new java.util.LinkedHashSet<>();

        public LifespanSupply(long startAt) {
            this.nextSupplyAt = startAt + WillConfig.SHOU_GU_SUPPLY_PERIOD;
        }

        /** 灌入审计过的寿蛊 ID 集合。只保留完整注册 ID（含命名空间）。 */
        public int audit(java.util.Collection<String> ids) {
            allowedIds.clear();
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && id.indexOf(':') > 0) {
                        allowedIds.add(id.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return allowedIds.size();
        }

        public int auditedCount() {
            return allowedIds.size();
        }

        public java.util.Set<String> auditedIds() {
            return new java.util.LinkedHashSet<>(allowedIds);
        }

        /** 该 ID 是否属于经审计的寿蛊来源。 */
        public boolean allowed(String id) {
            return id != null && allowedIds.contains(id.toLowerCase(Locale.ROOT));
        }

        /**
         * 从白名单里挑一个 ID 作为本轮自然产出的产物。
         *
         * @return 空串表示白名单为空 —— 调用方必须什么都不生成
         */
        public String pick(long seed) {
            if (allowedIds.isEmpty()) {
                return "";
            }
            java.util.List<String> list = new java.util.ArrayList<>(allowedIds);
            java.util.Collections.sort(list);
            return list.get((int) Math.floorMod(seed, list.size()));
        }

        /** 天意影响未来的供给。{@code delta} 为正=丰产，负=歉收；有界。 */
        public void influence(double delta) {
            drift = Knowledge.clamp(drift + delta, -0.8, 0.8);
            rate = Knowledge.clamp(1.0 + drift, 0.25, 2.0);
        }

        /** 是否到产出时刻。 */
        public boolean due(long now) {
            return now >= nextSupplyAt;
        }

        /**
         * 结算一次自然产出。
         *
         * @return 本次自然产出的寿蛊数量（0 表示这次没有成蛊）
         */
        public int settle(long now) {
            if (!due(now)) {
                return 0;
            }
            long period = (long) (WillConfig.SHOU_GU_SUPPLY_PERIOD / rate);
            nextSupplyAt = now + Math.max(2_400L, period);
            produced++;
            // 歉收时可能一颗都不成 —— 但不是"取消掉落"，而是这一轮自然没成蛊
            if (rate < 0.4 && produced % 3 != 0) {
                return 0;
            }
            return Math.max(1, (int) Math.round(rate));
        }

        /** 恒 {@code true}：本实现永不触碰玩家背包。用于静态审计。 */
        public boolean neverTouchesInventory() {
            return true;
        }
    }
}
