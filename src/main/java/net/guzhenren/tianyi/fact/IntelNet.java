package net.guzhenren.tianyi.fact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情报在持有者之间的<b>有限</b>传播网络。
 *
 * <h2>它要证伪什么</h2>
 * 一个常见的偷懒写法是「NPC 全都知道」：任何事件发生后，所有 NPC 立即拿到完整信息。
 * 那等于没有情报系统。这里把传播做成可验证的物理量——
 * <ul>
 *   <li><b>谁不知道。</b>没目击、也没从任何上游收到过的持有者，{@link #knows} 一律
 *       {@code false}。本类只认存入过的条目，不查世界状态。</li>
 *   <li><b>只能沿路走。</b>传播必须借由一条 {@link Edge}（商队、使者、家族往来），
 *       没有边的两个持有者之间信息不会自己流动。</li>
 *   <li><b>跳数有限（{@link #MAX_HOPS}）。</b>转述三次以后就散了：
 *       {@code hops == MAX_HOPS} 的持有者不再往外传。</li>
 *   <li><b>时间有限。</b>边带 {@code travelTicks}，{@code 学到的时刻 + 行期 > now}
 *       就还没到，传播不生效；边本身也有 {@code expiresAt}，过期的路不能走。</li>
 *   <li><b>越传越不可信。</b>新可信度 = 上游可信度 × 边衰减 × 行期衰减，
 *       永不超过上游（去掉「转述反而更确定」这种反常识）。</li>
 *   <li><b>不回灌。</b>不会把情报传回给它自己的上游（{@code Intel.from}），
 *       否则两个人互相转述就能把可信度来回抬高、跳数账目也乱了。</li>
 * </ul>
 *
 * <h2>持有的是事实 id，不是人名</h2>
 * 键是 {@code holderId -> factId -> Intel}。两个不同行为人做的同类事是两条不同事实
 * （id 不同），各自传播、各自过期。禁止按「人物名字」合并，否则换身份就等于洗白证据。
 *
 * <h2>身份切换</h2>
 * 身份是玩家侧的概念；本类只认 {@code holderId}。切换身份不会删任何条目——
 * 只是用另一个 holderId 去查，原证据仍在原 holder 名下。这正是「换身份不能洗白」的落点。
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class IntelNet {

    /** 最大转述跳数：直接目击为 0，最多再传三次。 */
    public static final int MAX_HOPS = 3;

    /** 低于该可信度的情报不再继续传播（避免「据说据说据说」的噪声自我放大）。 */
    public static final double MIN_CONFIDENCE = 0.05;

    /** 行期衰减基准：走过 {@code TRAVEL_REF_TICKS} 的可信度减半。 */
    public static final long TRAVEL_REF_TICKS = 2400L;

    /** 直接目击/直授的来源标记。 */
    public static final String WITNESS = "witness";

    private static final double EPS = 1.0E-9;

    private final Map<String, Map<String, Intel>> byHolder = new LinkedHashMap<>();

    /** 可选绑定：用于「事实过期后不能再算掌握」的过滤（{@link #known}）。 */
    private FactLedger ledger;

    public IntelNet() {
    }

    public IntelNet(FactLedger ledger) {
        this.ledger = ledger;
    }

    // ---------------------------------------------------------------- 结构

    /**
     * 一条传播边：从 {@code from} 指向 {@code to}，走 {@code travelTicks}，
     * 沿路可信度乘 {@code attenuation}，{@code expiresAt} 之后此路作废。
     */
    public record Edge(String from, String to, long travelTicks, double attenuation, long expiresAt) {

        /** 边是否还能走（{@code now} 不晚于 {@code expiresAt}）。 */
        public boolean usable(long now) {
            return now <= expiresAt;
        }

        public boolean expired(long now) {
            return now > expiresAt;
        }

        /** 边的总衰减系数 = 边衰减 × 行期衰减。 */
        public double factor() {
            return attenuation() * IntelNet.distanceDecay(travelTicks());
        }
    }

    /** 邻接查询。MC 侧接真实数据，测试侧用 {@link SimpleGraph}。 */
    public interface EdgeGraph {
        /** 从 {@code holderId} 出发、此刻可走的边（返回 null 视同没有边）。 */
        List<Edge> out(String holderId, long now);
    }

    /** 自带的简单实现：内存里存一份 {@code from -> edges}，够用且可离线构造。 */
    public static final class SimpleGraph implements EdgeGraph {
        private final Map<String, List<Edge>> byFrom = new LinkedHashMap<>();

        /** 加一条边；自环与空 id 直接忽略。 */
        public SimpleGraph link(String from, String to, long travelTicks, double attenuation, long expiresAt) {
            String f = WorldFact.identity(from);
            String t = WorldFact.identity(to);
            if (f.isEmpty() || t.isEmpty() || f.equals(t)) {
                return this;
            }
            byFrom.computeIfAbsent(f, k -> new ArrayList<>())
                    .add(new Edge(f, t, Math.max(0L, travelTicks),
                            WorldFact.clamp01(attenuation), expiresAt));
            return this;
        }

        /** 永不过期的边。 */
        public SimpleGraph link(String from, String to, long travelTicks, double attenuation) {
            return link(from, to, travelTicks, attenuation, Long.MAX_VALUE);
        }

        @Override
        public List<Edge> out(String holderId, long now) {
            List<Edge> all = byFrom.get(WorldFact.identity(holderId));
            if (all == null || all.isEmpty()) {
                return List.of();
            }
            List<Edge> out = new ArrayList<>(all.size());
            for (Edge e : all) {
                if (e.usable(now)) {
                    out.add(e);
                }
            }
            return out;
        }

        public int edgeCount() {
            int n = 0;
            for (List<Edge> l : byFrom.values()) {
                n += l.size();
            }
            return n;
        }

        public void clear() {
            byFrom.clear();
        }
    }

    /** 一条持有记录。不可变。 */
    public static final class Intel {
        public final String factId;
        public final double confidence;
        /** 0 = 亲眼所见/直授；每转述一次 +1。 */
        public final int hops;
        public final long learnedAt;
        /** 上游持有者 id；直接目击为 {@link IntelNet#WITNESS}。 */
        public final String from;

        public Intel(String factId, double confidence, int hops, long learnedAt, String from) {
            this.factId = factId == null ? "" : factId;
            this.confidence = WorldFact.clamp01(confidence);
            this.hops = Math.max(0, hops);
            this.learnedAt = learnedAt;
            this.from = from == null ? "" : from;
        }

        /** 是否来自第三方（非亲见）。 */
        public boolean hearsay() {
            return hops > 0;
        }

        public String toRow() {
            return factId + WorldFact.SEP + confidence + WorldFact.SEP + hops
                    + WorldFact.SEP + learnedAt + WorldFact.SEP + from;
        }

        /** 行反序列化；坏行返回 {@code null}。 */
        public static Intel fromRow(String row) {
            String[] p = WorldFact.fields(row);
            if (p.length < 5) {
                return null;
            }
            try {
                return new Intel(p[0], Double.parseDouble(p[1]), Integer.parseInt(p[2]),
                        Long.parseLong(p[3]), p[4]);
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Intel)) {
                return false;
            }
            Intel i = (Intel) o;
            return factId.equals(i.factId) && hops == i.hops && learnedAt == i.learnedAt
                    && from.equals(i.from) && Math.abs(confidence - i.confidence) < EPS;
        }

        @Override
        public int hashCode() {
            return factId.hashCode() * 31 + hops;
        }

        @Override
        public String toString() {
            return "Intel{" + shortId(factId) + " conf=" + WorldFact.fmt(confidence)
                    + " hops=" + hops + " from=" + shortId(from) + "}";
        }
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 直接目击 / 直授：{@code hops = 0}，来源记为 {@link #WITNESS}。
     *
     * <p>幂等：同一个 holder 已持有同一个事实且可信度不低时返回 {@code false}，
     * <b>不刷新</b> {@code learnedAt}（刷新会让「传播延迟」被反复归零，等于无限续命）。
     *
     * @return 是否发生了变化（新增，或可信度提高/由传闻升格为亲见）
     */
    public boolean teach(String holderId, WorldFact fact, double confidence, long now) {
        if (fact == null) {
            return false;
        }
        String holder = WorldFact.identity(holderId);
        if (holder.isEmpty()) {
            return false;
        }
        double conf = WorldFact.clamp01(Math.min(WorldFact.clamp01(confidence), fact.baseConfidence));
        Map<String, Intel> held = byHolder.computeIfAbsent(holder, k -> new LinkedHashMap<>());
        Intel cur = held.get(fact.id);
        if (cur == null) {
            held.put(fact.id, new Intel(fact.id, conf, 0, now, WITNESS));
            return true;
        }
        boolean witnessed = cur.hops == 0 && WITNESS.equals(cur.from);
        double best = Math.max(conf, cur.confidence);
        if (witnessed && best <= cur.confidence + EPS) {
            return false;
        }
        // 亲见优先：原先若只是传闻，现在可升格为 hops=0；否则保留较早的学得时刻
        long learnedAt = witnessed ? Math.min(cur.learnedAt, now) : now;
        held.put(fact.id, new Intel(fact.id, best, 0, learnedAt, WITNESS));
        return true;
    }

    /**
     * 一轮传播：每个持有者沿自己的边走一步。
     *
     * <p>一轮只推一跳（本轮新学会的持有者在下一轮才继续传），因此
     * {@code MAX_HOPS} 轮之后必然停住——这正是「转述有限」的可测形式。
     *
     * @param graph 邻接来源；{@code null} 视同没有边
     * @param facts 事实账本；{@code null} 时用构造时绑定的账本（都没绑定则不做过期过滤）
     * @return 新增或提升的持有数量
     */
    public int spread(EdgeGraph graph, FactLedger facts, long now) {
        if (graph == null || byHolder.isEmpty()) {
            return 0;
        }
        FactLedger led = facts != null ? facts : ledger;
        Map<String, Map<String, Intel>> incoming = new LinkedHashMap<>();
        List<String> holders = new ArrayList<>(byHolder.keySet());

        for (String holder : holders) {
            Map<String, Intel> held = byHolder.get(holder);
            if (held == null || held.isEmpty()) {
                continue;
            }
            List<Edge> edges = graph.out(holder, now);
            if (edges == null || edges.isEmpty()) {
                continue;
            }
            for (Edge e : edges) {
                if (e == null) {
                    continue;
                }
                String to = WorldFact.identity(e.to());
                if (to.isEmpty() || to.equals(holder)) {
                    continue;
                }
                String from = WorldFact.identity(e.from());
                if (!from.isEmpty() && !from.equals(holder)) {
                    continue; // 这条边不是从本人出发的，不走
                }
                if (!e.usable(now)) {
                    continue; // 路已经断了
                }
                long travel = Math.max(0L, e.travelTicks());
                double edgeFactor = e.factor();
                for (Intel in : held.values()) {
                    if (in.hops >= MAX_HOPS) {
                        continue; // 转述次数用尽
                    }
                    if (to.equals(WorldFact.identity(in.from))) {
                        continue; // 不回灌给上游
                    }
                    if (in.learnedAt + travel > now) {
                        continue; // 行期未到
                    }
                    if (led != null) {
                        WorldFact f = led.get(in.factId);
                        if (f == null || f.expired(now)) {
                            continue; // 事实已经过期，不能再当依据传播
                        }
                    }
                    double conf = WorldFact.clamp01(in.confidence * edgeFactor);
                    if (conf < MIN_CONFIDENCE) {
                        continue;
                    }
                    merge(incoming, to, new Intel(in.factId, conf, in.hops + 1, now, holder));
                }
            }
        }

        int changed = 0;
        for (Map.Entry<String, Map<String, Intel>> en : incoming.entrySet()) {
            Map<String, Intel> held = byHolder.computeIfAbsent(en.getKey(), k -> new LinkedHashMap<>());
            for (Intel cand : en.getValue().values()) {
                Intel cur = held.get(cand.factId);
                if (cur == null || better(cand, cur)) {
                    held.put(cand.factId, cand);
                    changed++;
                }
            }
        }
        return changed;
    }

    /** 让一个持有者忘掉某条事实。 */
    public boolean forget(String holderId, String factId) {
        Map<String, Intel> held = byHolder.get(WorldFact.identity(holderId));
        return held != null && held.remove(factId) != null;
    }

    /** 丢掉所有事实已不存在/已过期的持有记录，返回删除条数。 */
    public int prune(FactLedger facts, long now) {
        FactLedger led = facts != null ? facts : ledger;
        if (led == null) {
            return 0;
        }
        int before = count();
        byHolder.values().forEach(held -> held.values().removeIf(i -> {
            WorldFact f = led.get(i.factId);
            return f == null || f.expired(now);
        }));
        byHolder.values().removeIf(Map::isEmpty);
        return before - count();
    }

    public void clear() {
        byHolder.clear();
    }

    // ---------------------------------------------------------------- 读取

    /** 该持有者的某条情报；没有则 {@code null}。 */
    public Intel know(String holderId, String factId) {
        Map<String, Intel> held = byHolder.get(WorldFact.identity(holderId));
        return held == null ? null : held.get(factId);
    }

    /** 该持有者是否掌握这条事实（只看是否存过，时效见 {@link #known}）。 */
    public boolean knows(String holderId, String factId) {
        return know(holderId, factId) != null;
    }

    /**
     * 该持有者<b>实际</b>掌握的情报：绑定了账本时，事实已过期/已不存在的条目会被滤掉，
     * 所以「过期事实不可再被当作依据」。按可信度降序，可信度相同按 factId 稳定排序。
     */
    public List<Intel> known(String holderId, long now) {
        Map<String, Intel> held = byHolder.get(WorldFact.identity(holderId));
        if (held == null || held.isEmpty()) {
            return List.of();
        }
        List<Intel> out = new ArrayList<>(held.size());
        for (Intel i : held.values()) {
            if (alive(i, now)) {
                out.add(i);
            }
        }
        out.sort(Comparator.comparingDouble((Intel i) -> i.confidence).reversed()
                .thenComparing(i -> i.factId));
        return out;
    }

    /** 该持有者全部记录的副本（不含时效过滤），供持久化/调试。 */
    public Map<String, Intel> snapshotOf(String holderId) {
        Map<String, Intel> held = byHolder.get(WorldFact.identity(holderId));
        return held == null ? new LinkedHashMap<>() : new LinkedHashMap<>(held);
    }

    /** 持有任一情报的人数。 */
    public int holderCount() {
        return byHolder.size();
    }

    /** 全部 Holder id（副本）。 */
    public List<String> holders() {
        return new ArrayList<>(byHolder.keySet());
    }

    /** 全部持有记录总数（所有 holder 的条目之和）。 */
    public int count() {
        int n = 0;
        for (Map<String, Intel> held : byHolder.values()) {
            n += held.size();
        }
        return n;
    }

    public FactLedger ledger() {
        return ledger;
    }

    public void bindLedger(FactLedger facts) {
        this.ledger = facts;
    }

    // ---------------------------------------------------------------- 持久化（供 MC 侧）

    /** 每行「holder + 一条 Intel」，分隔符 U+0001，可 {@link #importRows} 往返。 */
    public List<String> exportRows() {
        List<String> rows = new ArrayList<>(count());
        byHolder.forEach((holder, held) -> {
            for (Intel i : held.values()) {
                rows.add(holder + WorldFact.SEP + i.toRow());
            }
        });
        return rows;
    }

    /** 从存档恢复：坏行忽略，同键取可信度更高者，最后丢掉空 holder。 */
    public void importRows(List<String> rows) {
        byHolder.clear();
        if (rows == null) {
            return;
        }
        for (String row : rows) {
            String[] p = WorldFact.fields(row);
            if (p.length < 6) {
                continue;
            }
            String holder = WorldFact.identity(p[0]);
            if (holder.isEmpty()) {
                continue;
            }
            Intel i;
            try {
                i = new Intel(p[1], Double.parseDouble(p[2]), Integer.parseInt(p[3]),
                        Long.parseLong(p[4]), p[5]);
            } catch (RuntimeException ex) {
                continue; // 坏行忽略，不能因为一行脏数据丢掉整张网
            }
            if (i.factId.isEmpty()) {
                continue;
            }
            Map<String, Intel> held = byHolder.computeIfAbsent(holder, k -> new LinkedHashMap<>());
            Intel cur = held.get(i.factId);
            if (cur == null || better(i, cur)) {
                held.put(i.factId, i);
            }
        }
        byHolder.values().removeIf(Map::isEmpty);
    }

    @Override
    public String toString() {
        return "IntelNet{holders=" + byHolder.size() + " entries=" + count() + "}";
    }

    // ---------------------------------------------------------------- 工具

    /** 行期衰减：走过 {@link #TRAVEL_REF_TICKS} 减半，下限 {@link #MIN_CONFIDENCE}。 */
    public static double distanceDecay(long travelTicks) {
        long t = Math.max(0L, travelTicks);
        double d = 1.0 / (1.0 + ((double) t / (double) TRAVEL_REF_TICKS));
        return d < MIN_CONFIDENCE ? MIN_CONFIDENCE : Math.min(1.0, d);
    }

    /** 候选是否比现有的更好：可信度更高；同可信度则跳数更少。 */
    private static boolean better(Intel cand, Intel cur) {
        if (cand.confidence > cur.confidence + EPS) {
            return true;
        }
        return Math.abs(cand.confidence - cur.confidence) <= EPS && cand.hops < cur.hops;
    }

    private static void merge(Map<String, Map<String, Intel>> acc, String to, Intel cand) {
        Map<String, Intel> slot = acc.computeIfAbsent(to, k -> new LinkedHashMap<>());
        Intel cur = slot.get(cand.factId);
        if (cur == null || better(cand, cur)) {
            slot.put(cand.factId, cand);
        }
    }

    private boolean alive(Intel i, long now) {
        if (ledger == null) {
            return true;
        }
        WorldFact f = ledger.get(i.factId);
        return f != null && !f.expired(now);
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "-";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
