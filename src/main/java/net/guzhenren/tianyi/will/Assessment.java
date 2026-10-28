package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 世界格局评估：把「天意实际看到的」状态折算成失衡、威胁与棋子价值。
 *
 * <h2>三个必须分清的量</h2>
 * <table border="1">
 *   <tr><th>量</th><th>吃什么</th><th>不吃什么</th></tr>
 *   <tr><td>威胁 threat</td><td>实力、增长趋势、<b>天意已知的</b>逆天行为、情报确定性</td>
 *       <td><b>道德、气运</b>（道德高不是威胁，气运高更不是）</td></tr>
 *   <tr><td>社会代价 socialCost</td><td>道德（社会信任/组织反应）、势力规模</td>
 *       <td>气运</td></tr>
 *   <tr><td>棋子价值 utility</td><td>实力能否被撬动、社会位置、可替换性</td>
 *       <td>善恶评价（魔头与高德者都可能是好棋子）</td></tr>
 * </table>
 *
 * <p>气运<b>不进入</b>威胁与压制判定 —— 原著里气运管的是机缘与遭际的走向，
 * 不是天意的喜恶。这一点由 {@code WilCoreTest#luckDoesNotDriveSuppression} 反证。
 */
public final class Assessment {
    private Assessment() {
    }

    public enum ActorKind {
        PLAYER, NPC, FACTION
    }

    /** 天意可用的干预渠道。渠道是「世界给的能力」，不是凭空的权限。 */
    public enum Channel {
        /** 资源成熟/出现（改变资源演化）。 */
        RESOURCE,
        /** 传承线索（让线索在世界上「变得可被发现」）。 */
        HERITAGE,
        /** NPC 信息与协助（借人之口）。 */
        RUMOR,
        /** 势力牵制（让两个势力因实际利益冲突互相拖住）。 */
        FACTION,
        /** 兽群与生态（迁徙、伏击，需要实际来源）。 */
        BEAST,
        /** 灾劫（地灾/天劫/浩劫/万劫，需满足条件）。 */
        CALAMITY,
        /** 载体（仙元石、宿命蛊等回报信息/施加影响）。 */
        CARRIER
    }

    /** 一个参与者的世界画像。数值都由 {@code knowledge} 层提供或推算而来。 */
    public static final class Actor {
        public final String id;
        public final ActorKind kind;
        public final String faction;

        /** 综合实力（0 起，无上限；由转数/战力/道痕合成）。 */
        public double power;
        /** 资源控制（0..1 归一化后的份额）。 */
        public double resources;
        /** 增长趋势：每游戏日的实力增量（已归一）。 */
        public double growth;
        /** 道德真实值（本体原生字段，可为负；不是 0..100 的评分类）。 */
        public double morality;
        /** 气运真实值（本体原生字段，可为负、可大于 100）。 */
        public double luck;
        /** 气运上限（本体 {@code qiyun_shangxian}；为 0 表示「上限为零」，不是惩罚）。 */
        public double luckCap;

        /** 天意对该主体的整体情报置信度（0..1）。 */
        public double confidence = 1.0;
        /** 天意<b>实际知道</b>的逆天行为强度（0 = 完全不知道）。 */
        public double knownDefiance = 0.0;
        /** 该主体拥有的反制手段。 */
        public final EnumSet<Counter.Kind> counters = EnumSet.noneOf(Counter.Kind.class);
        /** 该主体是否处于天意盲区。 */
        public boolean hidden = false;
        /** 该主体当前所在维度（完整维度键，空串表示未知）。 */
        public String dim = "";
        /**
         * 体内 / 仙窍里是否持有<b>天道道痕</b>。
         *
         * <p>这是「天意能不能在他身上产生」的物质前提（原著：方源仙窍里之所以有天意，
         * 是因为他自己炼宿命蛊得了三千多道天道道痕）。同时也是「炼化天道道痕」这条
         * 治根反制的前提。
         */
        public boolean hasHeavenDaoMark = false;

        public Actor(String id, ActorKind kind, String faction) {
            this.id = Knowledge.requireIdentity(id);
            this.kind = kind;
            this.faction = faction == null ? "" : faction;
        }

        public Actor power(double v) {
            this.power = v;
            return this;
        }

        public Actor resources(double v) {
            this.resources = v;
            return this;
        }

        public Actor growth(double v) {
            this.growth = v;
            return this;
        }

        public Actor morality(double v) {
            this.morality = v;
            return this;
        }

        public Actor luck(double v, double cap) {
            this.luck = v;
            this.luckCap = cap;
            return this;
        }

        public Actor confidence(double v) {
            this.confidence = Knowledge.clamp(v, 0.0, 1.0);
            return this;
        }

        public Actor knownDefiance(double v) {
            this.knownDefiance = Math.max(0.0, v);
            return this;
        }

        public Actor hidden(boolean v) {
            this.hidden = v;
            return this;
        }

        public Actor counter(Counter.Kind c) {
            this.counters.add(c);
            return this;
        }

        public Actor dim(String d) {
            this.dim = d == null ? "" : d;
            return this;
        }

        public Actor heavenDaoMark(boolean v) {
            this.hasHeavenDaoMark = v;
            return this;
        }

        /**
         * 气运是否处于「超常范围」。
         *
         * <p>本体气运不保证落在 0..100：上限字段可能为 0（气运被锁死），
         * 也可能被堆到远超 100。这里不把它压回 0..100，只标记异常。
         * <b>负气运不算超常</b>——它只是「凶」，仍在正常取值区间内。
         */
        public boolean luckAnomalous() {
            return luckCap <= 0.0 || luck > 100.0 || luck < -100.0;
        }

        /** 气运的<b>有效</b>档位：上限为零时气运不参与任何走向。 */
        public double effectiveLuck() {
            if (luckCap <= 0.0) {
                return 0.0;
            }
            return luck;
        }

        /** 社会信任：道德越高，社会越信任他，动他的社会代价越大。 */
        public double socialTrust() {
            // 道德是原生的原始值，量级可能很大；用软饱和避免一个超高人德压死全场
            return morality / (Math.abs(morality) + 100.0);
        }
    }

    /** 世界快照（一次决策的输入）。 */
    public static final class Situation {
        public final long now;
        public final List<Actor> actors = new ArrayList<>();
        public final EnumSet<Channel> channels = EnumSet.noneOf(Channel.class);
        /** 天意本轮的「意志余量」（消耗与恢复：木桶模型的容量）。 */
        public double willBudget = 1.0;
        /** 世界阶段修正（宿命毁灭后天道封锁减弱等），由 {@link Destiny} 提供。 */
        public double heavenControl = 1.0;
        /**
         * 3.14：本轮的天意强度（档位）。
         *
         * <p>默认 {@link WillForce#gentle()}，它的所有倍率都是 1.0、阈值都是
         * {@link WillConfig} 原值 —— 所以「不设强度」与 3.13 的行为逐位一致。
         * 强度只改这一轮读到的数与阈值，不改变任何判定结构。
         */
        public WillForce force = WillForce.gentle();

        public Situation(long now) {
            this.now = now;
        }

        /** 设置本轮档位（null 视为温和）。 */
        public Situation withForce(WillForce f) {
            this.force = f == null ? WillForce.gentle() : f;
            return this;
        }

        public Situation with(Channel c) {
            channels.add(c);
            return this;
        }

        public Actor add(Actor a) {
            actors.add(a);
            return a;
        }

        public Actor find(String id) {
            for (Actor a : actors) {
                if (a.id.equals(id)) {
                    return a;
                }
            }
            return null;
        }
    }

    /** 评估结果。 */
    public static final class Report {
        public final long now;
        public final double totalPower;
        public final Map<String, Double> share = new LinkedHashMap<>();
        public final Map<String, Double> threat = new LinkedHashMap<>();
        public final Map<String, Double> utility = new LinkedHashMap<>();
        public final Map<String, Double> socialCost = new LinkedHashMap<>();
        public final List<String> supportCandidates = new ArrayList<>();
        public final List<String> suppressCandidates = new ArrayList<>();
        public String dominant;
        public String topThreat;
        public boolean imbalanced;
        public double imbalance;
        public String note = "";

        Report(long now, double totalPower) {
            this.now = now;
            this.totalPower = totalPower;
        }

        public double shareOf(String id) {
            return share.getOrDefault(id, 0.0);
        }

        public double threatOf(String id) {
            return threat.getOrDefault(id, 0.0);
        }

        public double utilityOf(String id) {
            return utility.getOrDefault(id, 0.0);
        }

        public double socialCostOf(String id) {
            return socialCost.getOrDefault(id, 0.0);
        }

        @Override
        public String toString() {
            return "Report{" + "now=" + now + " total=" + Knowledge.fmt(totalPower)
                    + " imbalance=" + Knowledge.fmt(imbalance)
                    + " dominant=" + dominant + " topThreat=" + topThreat
                    + " support=" + supportCandidates + " suppress=" + suppressCandidates + '}';
        }
    }

    // ---------------------------------------------------------------- 评估本体

    /**
     * 评估当前格局。
     *
     * <p>只使用参与者画像与 {@link Knowledge.Store} 的<b>已观测</b>信息；
     * 处于盲区的主体按「lost」处理（不参与压制候选，但仍占实力份额——天意
     * 记得它的体量，只是不知道它现在在干什么）。
     */
    public static Report assess(Situation s) {
        // 3.14：阈值全部走档位（档位 1 时等于 WillConfig 原值）
        WillForce f = s.force == null ? WillForce.gentle() : s.force;
        double total = 0.0;
        for (Actor a : s.actors) {
            total += Math.max(0.0, a.power);
        }
        Report r = new Report(s.now, total);

        // 1) 份额
        for (Actor a : s.actors) {
            double sh = total <= 0.0 ? 0.0 : Math.max(0.0, a.power) / total;
            r.share.put(a.id, sh);
        }

        // 2) 失衡：最大份额超过阈值，且超出一个死区
        double maxShare = 0.0;
        String biggest = null;
        for (Map.Entry<String, Double> e : r.share.entrySet()) {
            if (e.getValue() > maxShare) {
                maxShare = e.getValue();
                biggest = e.getKey();
            }
        }
        r.dominant = biggest;
        r.imbalance = maxShare - f.balanceShare();
        r.imbalanced = s.actors.size() > 1
                && maxShare >= f.balanceShare()
                && r.imbalance > f.balanceDeadband();

        // 3) 威胁：实力 + 增长 + 已知逆天，乘确定性
        for (Actor a : s.actors) {
            double raw = WillConfig.THREAT_POWER_WEIGHT * r.shareOf(a.id)
                    + WillConfig.THREAT_GROWTH_WEIGHT * Math.max(0.0, a.growth) * 10.0
                    + WillConfig.THREAT_DEFIANCE_WEIGHT * a.knownDefiance;
            double certainty = Knowledge.clamp(a.confidence, 0.0, 1.0);
            // 不确定性让威胁打折：天意不会为"听说的"下死手
            double t = raw * (certainty >= 1.0 ? 1.0
                    : WillConfig.UNCERTAINTY_PENALTY + (1.0 - WillConfig.UNCERTAINTY_PENALTY) * certainty);
            r.threat.put(a.id, t);
        }
        double bestThreat = -1.0;
        for (Map.Entry<String, Double> e : r.threat.entrySet()) {
            if (e.getValue() > bestThreat) {
                bestThreat = e.getValue();
                r.topThreat = e.getKey();
            }
        }

        // 4) 社会代价：道德 + 势力
        for (Actor a : s.actors) {
            double cost = WillConfig.MORAL_SOCIAL_COST * Math.max(0.0, a.socialTrust());
            if (a.kind == ActorKind.FACTION) {
                cost += 0.2;
            }
            r.socialCost.put(a.id, Knowledge.clamp(cost, 0.0, 1.5));
        }

        // 5) 棋子价值：可被撬动 × 位置 × 不可替代性
        for (Actor a : s.actors) {
            double leverage = WillConfig.UNCERTAINTY_PENALTY
                    + (1.0 - WillConfig.UNCERTAINTY_PENALTY) * Knowledge.clamp(a.confidence, 0.0, 1.0);
            // 弱而有用最好用：实力太大反而不听话
            double controllability = 1.0 / (1.0 + Math.max(0.0, a.power) * 0.6);
            // 社会位置：高德者可当"正道旗帜"，低德者可当"魔头刀"
            double positioning = 0.35 + 0.65 * Math.abs(a.socialTrust());
            // 资源控制让棋子更有用
            double res = 0.5 + 0.5 * Knowledge.clamp(a.resources, 0.0, 1.0);
            double u = leverage * controllability * positioning * res;
            r.utility.put(a.id, Knowledge.clamp(u, 0.0, 1.0));
        }

        // 6) 候选
        for (Actor a : s.actors) {
            double u = r.utilityOf(a.id);
            double t = r.threatOf(a.id);
            double cost = r.socialCostOf(a.id);

            // 扶持：有价值、不是头号威胁、不被遮蔽到无法行动
            if (u >= f.supportThreshold() && !a.hidden) {
                r.supportCandidates.add(a.id);
            }
            // 压制：威胁够高、天意还有控制权、且不是完全看不见
            if (t >= f.suppressThreshold() && !a.hidden) {
                // 社会代价高 → 仍可压制，但排后面（靠"借人施劫"降低直接动作）
                r.suppressCandidates.add(a.id);
            }
        }
        r.supportCandidates.sort((x, y) -> Double.compare(r.utilityOf(y), r.utilityOf(x)));
        r.suppressCandidates.sort((x, y) -> {
            double sx = r.threatOf(x) - r.socialCostOf(x) * 0.5;
            double sy = r.threatOf(y) - r.socialCostOf(y) * 0.5;
            return Double.compare(sy, sx);
        });

        if (r.imbalanced && r.dominant != null) {
            r.note = String.format(Locale.ROOT,
                    "失衡：%s 占 %.0f%% 实力", r.dominant, r.shareOf(r.dominant) * 100.0);
        } else if (r.topThreat != null) {
            r.note = String.format(Locale.ROOT,
                    "未失衡；头号威胁 %s（%.2f）", r.topThreat, r.threatOf(r.topThreat));
        } else {
            r.note = "空世界";
        }
        return r;
    }

    /**
     * 高德强者是否「应当被压制」。
     *
     * <p>单列一个方法，是为了让这条判断可以被单独测试：道德不提供豁免，
     * 只改变动作方式（直接降灾 → 借人施劫）。
     */
    public static boolean shouldSuppressDespiteMorality(Report r, Actor a) {
        return shouldSuppressDespiteMorality(r, a, WillForce.gentle());
    }

    /** 3.14：压制门槛可随档位下调（档位 1 与旧签名完全等价）。 */
    public static boolean shouldSuppressDespiteMorality(Report r, Actor a, WillForce force) {
        if (a == null) {
            return false;
        }
        WillForce f = force == null ? WillForce.gentle() : force;
        double t = r.threatOf(a.id);
        if (t < f.suppressThreshold()) {
            return false;
        }
        // 道德高只是让"社会代价"变高，不取消压制资格
        return t > r.socialCostOf(a.id) * WillConfig.MORAL_SOCIAL_COST + 0.10;
    }
}
