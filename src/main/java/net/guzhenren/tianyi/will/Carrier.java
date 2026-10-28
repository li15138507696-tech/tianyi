package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 天意载体：天意寄存、传播、侵入、消耗、清除、恢复。
 *
 * <h2>载体决定「天意能摸到哪里」</h2>
 * 天意不是一个飘在世界上的全局变量，它要落在具体载体上：
 * 天道道痕、仙元石、宿命蛊、以及地脉式的区域载体。载体有位置、
 * 有完整度、有传播半径 —— {@link Network#reachAt} 就是天意在该点的实际触达。
 *
 * <h2>局部清除 ≠ 世界天意死亡</h2>
 * {@link Network#purge} 只清掉一处载体：该区域立刻进入盲区
 * （{@link Knowledge.Store#cover}），天意在那片地方既看不到也算不出；
 * 但世界其余部分的载体、布局与灾劫照旧运行。载体还会在
 * {@link WillConfig#CARRIER_REGROW} 之后慢慢渗回来。
 *
 * <h2>「在别的维度」不等于安全</h2>
 * 独立仙窍是否被天意影响，取决于它自身的载体与接触渠道，
 * 而不是它挂在哪个维度键下面。{@link #reachAt} 按维度分别记账。
 */
public final class Carrier {
    private Carrier() {
    }

    /** 载体种类。 */
    public enum Kind {
        /** 天道道痕：最基础的天意寄存层。 */
        DAO_MARK(0.95),
        /** 仙元石：可以携带天意（原著「含有天意的仙元石」）。 */
        PRIMEVAL_STONE(0.70),
        /** 宿命蛊：最强的载体，但只有一条正品。 */
        DESTINY_GU(1.00),
        /** 区域地脉：一整片地的天意。 */
        REGION(0.55);

        /** 传播强度系数。 */
        public final double strength;

        Kind(double strength) {
            this.strength = strength;
        }
    }

    /**
     * 载体的统一状态。
     *
     * <p>把「寄存 → 传播 → 消耗 → 清除 → 恢复」这五件事收敛到<b>一个</b>
     * 状态机上，避免同一种「载体还行不行」的判断散落在四处、彼此矛盾。
     */
    public enum State {
        /** 沉睡：还有实体载体，但完整度不够，基本传不动。 */
        DORMANT,
        /** 活跃：能正常寄存与传播。 */
        ACTIVE,
        /** 枯竭：被反复消耗，几乎传不动（没被清，但也没力气了）。 */
        DRAINED,
        /** 已清除：被天消意散/炼道清掉，处在盲区里。 */
        PURGED,
        /** 恢复中：已经过了盲区，正在慢慢渗回来。 */
        REGROWING
    }

    /** 一处载体。 */
    public static final class Node {
        public final String id;
        public final Kind kind;
        public final String dim;
        public final long pos;
        public final double reach;
        /** 完整度 0..1。被消耗/被清除都会掉。 */
        public double integrity = 1.0;
        /** 被清除后的盲区到期时刻。 */
        public long blindUntil = 0L;
        /** 重新生长累计（刻）。 */
        public long regrowTicks = 0L;

        public Node(String id, Kind kind, String dim, long pos, double reach) {
            this.id = id;
            this.kind = kind;
            this.dim = dim == null ? "" : dim;
            this.pos = pos;
            this.reach = reach <= 0 ? WillConfig.CARRIER_REACH : reach;
        }

        public boolean blind(long now) {
            return blindUntil > now;
        }

        /** 有效触达（0..1）：完整度 × 强度，盲区时为 0。 */
        public double effectiveReach(long now) {
            if (blind(now)) {
                return 0.0;
            }
            return Knowledge.clamp(integrity * kind.strength, 0.0, 1.0);
        }

        /**
         * 统一状态判定（唯一入口，别在别处自己算）。
         *
         * <p>顺序有意义：先看「是否在盲区」（清除优先），再看完整度。
         */
        public State state(long now) {
            if (blind(now)) {
                return State.PURGED;
            }
            // 已经被清过一次、正在渗回来 → 恢复中（优先于「枯竭」判断，
            // 否则刚被清的载体会被误报成"枯竭"，看不出它其实在恢复）
            if (regrowTicks > 0 && integrity < 0.60) {
                return State.REGROWING;
            }
            if (integrity >= 0.60) {
                return State.ACTIVE;
            }
            if (integrity < 0.20) {
                return State.DRAINED;
            }
            return State.DORMANT;
        }

        /** 该状态下还能不能寄存新的天意。 */
        public boolean canHost(long now) {
            State s = state(now);
            return s == State.ACTIVE || s == State.REGROWING;
        }

        /** 状态机的显示名。 */
        public String stateName(long now) {
            return state(now).name();
        }
    }

    /** 载体网络。 */
    public static final class Network {
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        /** 局部天意被清除的区域（维度 → 盲区到期时刻列表）。 */
        private final Map<String, List<Long>> regionalBlind = new LinkedHashMap<>();

        public Node add(String id, Kind kind, String dim, long pos, double reach) {
            Node n = new Node(id, kind, dim, pos, reach);
            nodes.put(id, n);
            return n;
        }

        public Node get(String id) {
            return nodes.get(id);
        }

        /** 某点上的天意触达强度（0..1）。按位置与维度筛选最近的载体。 */
        public double reachAt(String dim, long pos, long now, double maxDistance) {
            double best = 0.0;
            for (Node n : nodes.values()) {
                if (!n.dim.equals(dim) || n.blind(now)) {
                    continue;
                }
                double d = distance(n.pos, pos);
                if (d > Math.min(n.reach, maxDistance)) {
                    continue;
                }
                double falloff = 1.0 - (d / Math.min(n.reach, maxDistance));
                best = Math.max(best, n.effectiveReach(now) * falloff);
            }
            return Knowledge.clamp(best, 0.0, 1.0);
        }

        /** 只按维度看触达（用于独立仙窍：它不在任何具体坐标上）。 */
        public double reachInDim(String dim, long now) {
            double best = 0.0;
            for (Node n : nodes.values()) {
                if (n.dim.equals(dim)) {
                    best = Math.max(best, n.effectiveReach(now));
                }
            }
            return best;
        }

        /** 侵入：把一个载体的完整度往下压（天意被排斥/被夺取）。 */
        public boolean intrude(String nodeId, double amount) {
            Node n = nodes.get(nodeId);
            if (n == null) {
                return false;
            }
            n.integrity = Knowledge.clamp(n.integrity - Math.max(0.0, amount), 0.0, 1.0);
            return true;
        }

        /** 消耗：天意行使一次干预要烧掉载体完整度。 */
        public boolean consume(String nodeId, double amount, long now) {
            Node n = nodes.get(nodeId);
            if (n == null || n.effectiveReach(now) < 0.2) {
                return false;
            }
            n.integrity = Knowledge.clamp(n.integrity - Math.max(0.0, amount), 0.0, 1.0);
            return true;
        }

        /**
         * 恢复：慢慢渗回来。
         *
         * <p>渗满（完整度回到 0.6 以上）就把恢复计时清零，
         * 否则载体会永远停在 {@link State#REGROWING} 上，状态机就没意义了。
         */
        public void regrow(long now, long deltaTicks) {
            for (Node n : nodes.values()) {
                if (n.blind(now)) {
                    continue;
                }
                n.regrowTicks += deltaTicks;
                double per = (double) deltaTicks / WillConfig.CARRIER_REGROW;
                n.integrity = Knowledge.clamp(n.integrity + per * 0.25, 0.0, 1.0);
                if (n.integrity >= 0.60) {
                    n.regrowTicks = 0L; // 已经渗回来了，退出恢复态
                }
            }
        }

        /**
         * 局部清除天意（天消意散 / 炼道清除的落点）。
         *
         * @return 被清除的载体数量
         */
        public int purge(String dim, long pos, double radius, Knowledge.Store knowledge,
                         long now, String cause) {
            int hit = 0;
            for (Node n : nodes.values()) {
                if (!n.dim.equals(dim)) {
                    continue;
                }
                if (distance(n.pos, pos) > radius) {
                    continue;
                }
                n.integrity = 0.0;
                n.blindUntil = now + WillConfig.CARRIER_PURGE_BLIND;
                hit++;
            }
            regionalBlind.computeIfAbsent(dim, k -> new ArrayList<>())
                    .add(now + WillConfig.CARRIER_PURGE_BLIND);
            nodes.values().stream()
                    .filter(n -> n.dim.equals(dim) && n.blind(now))
                    .forEach(n -> knowledge.cover("carrier:" + n.id, now,
                            WillConfig.CARRIER_PURGE_BLIND, cause));
            return hit;
        }

        /** 该维度当前是否处于区域盲区（局部天意被清）。 */
        public boolean regionalBlind(String dim, long now) {
            List<Long> list = regionalBlind.get(dim);
            if (list == null) {
                return false;
            }
            list.removeIf(t -> t <= now);
            return !list.isEmpty();
        }

        public int count() {
            return nodes.size();
        }

        public double totalIntegrity() {
            if (nodes.isEmpty()) {
                return 0.0;
            }
            double s = 0.0;
            for (Node n : nodes.values()) {
                s += n.integrity;
            }
            return s / nodes.size();
        }

        /** 世界的天意是否还活着（局部全灭不等于世界天意死亡，见类注释）。 */
        public boolean willAlive(long now) {
            for (Node n : nodes.values()) {
                if (n.effectiveReach(now) > 0.05) {
                    return true;
                }
            }
            return false;
        }

        /** 某个载体的统一状态（找不到返回 {@code null}）。 */
        public State stateOf(String nodeId, long now) {
            Node n = nodes.get(nodeId);
            return n == null ? null : n.state(now);
        }

        /** 各状态的载体数量（诊断用）。 */
        public Map<State, Integer> stateCounts(long now) {
            Map<State, Integer> m = new java.util.EnumMap<>(State.class);
            for (State s : State.values()) {
                m.put(s, 0);
            }
            for (Node n : nodes.values()) {
                m.merge(n.state(now), 1, Integer::sum);
            }
            return m;
        }

        /**
         * 世界级的统一状态：取「最强那处载体」的状态。
         *
         * <p>这样「世界天意还好不好」只有一个答案，不会出现
         * 「按载体看是活的、按区域盲区看是死的」这种自相矛盾。
         */
        public State worldState(long now) {
            State best = State.PURGED;
            for (Node n : nodes.values()) {
                State s = n.state(now);
                if (rank(s) > rank(best)) {
                    best = s;
                }
            }
            return best;
        }

        private static int rank(State s) {
            return switch (s) {
                case PURGED -> 0;
                case DRAINED -> 1;
                case DORMANT -> 2;
                case REGROWING -> 3;
                case ACTIVE -> 4;
            };
        }

        public List<Node> all() {
            return new ArrayList<>(nodes.values());
        }

        /** 从存档恢复一处载体。 */
        public Node restore(String id, Kind kind, String dim, long pos, double reach,
                            double integrity, long blindUntil, long regrowTicks) {
            Node n = new Node(id, kind, dim, pos, reach);
            n.integrity = Knowledge.clamp(integrity, 0.0, 1.0);
            n.blindUntil = blindUntil;
            n.regrowTicks = regrowTicks;
            nodes.put(id, n);
            return n;
        }

        /** 该维度当前是否登记了区域盲区（用于恢复）。 */
        public void restoreRegionBlind(String dim, List<Long> untils) {
            if (untils != null && !untils.isEmpty()) {
                regionalBlind.put(dim, new ArrayList<>(untils));
            }
        }

        public Map<String, List<Long>> snapshotRegionBlind() {
            Map<String, List<Long>> m = new LinkedHashMap<>();
            regionalBlind.forEach((k, v) -> m.put(k, new ArrayList<>(v)));
            return m;
        }

        private static double distance(long a, long b) {
            // 与 BlockPos.asLong() 的位布局一致：x 在 38..63、z 在 12..37、y 在 0..11
            int ax = (int) (a >> 38);
            int az = (int) ((a << 26) >> 38);
            int ay = (int) ((a << 52) >> 52);
            int bx = (int) (b >> 38);
            int bz = (int) ((b << 26) >> 38);
            int by = (int) ((b << 52) >> 52);
            double dx = ax - bx;
            double dy = ay - by;
            double dz = az - bz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public String describe(long now) {
            return String.format(Locale.ROOT, "Carrier{节点=%d 平均完整=%.2f 天意存活=%s}",
                    nodes.size(), totalIntegrity(), willAlive(now));
        }
    }
}
