package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.guzhenren.tianyi.TianyiMod;
import net.guzhenren.tianyi.action.ActionLedger;
import net.guzhenren.tianyi.action.ActionPlan;
import net.guzhenren.tianyi.action.ActResult;
import net.guzhenren.tianyi.action.InterventionAction;
import net.guzhenren.tianyi.action.WorldView;
import net.guzhenren.tianyi.action.impl.InvestigateAction;
import net.guzhenren.tianyi.action.impl.PursueAction;
import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.fact.FactLedger;
import net.guzhenren.tianyi.fact.IntelNet;
import net.guzhenren.tianyi.fact.WorldFact;
import net.guzhenren.tianyi.will.WillConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界侧的情报存档：<b>事实总账 + 传播网络 + 布局队列</b>，三份共用一份未存盘标记。
 *
 * <h2>为什么三样东西必须存在同一个 SavedData 里</h2>
 * 它们互相引用：{@code net} 里的每条情报都指向 {@code facts} 里的一个 id，
 * {@code plans} 的每一步又建立在某条事实上。分开存会出现「情报恢复了、事实没恢复」，
 * 于是重启后 {@link IntelNet#known} 全部落空——玩家看到的现象是「NPC 忽然集体失忆」。
 * 同一个根节点、同一个 {@code schemaVersion}、同一次 {@link #setDirty()}，这几件事才可能一致。
 *
 * <h2>旧档只读不删</h2>
 * 反序列化先看 {@code schemaVersion}：缺失或偏旧时<b>不</b>清空任何玩家数据，
 * 而是逐条尽力读，读不懂的单条跳过（见 {@link #factFromTag}），最后把版本号升到当前值。
 * 丢掉一整张情报网比丢掉几条脏数据严重得多。
 *
 * <h2>传播边为什么缓存</h2>
 * 「同维度、距离 ≤ {@link WillConfig#CARRIER_REACH}」是 O(n²) 的两两比较。
 * 每轮都全量重算是拿服务端主线程的钱买方便。这里把边放进 {@link IntelNet.SimpleGraph}
 * 缓存，每轮只重算一批 {@link #EDGE_BATCH} 人；居民超过这个数时就是分批扫，
 * 走完一圈才清空旧边——中途清空会让正在传播的情报突然断路。
 * 边自带 {@link #EDGE_TTL}，过期即不可走，所以「清空」只是回收内存，不是正确性前提。
 */
public final class IntelState extends SavedData {
    /** 存档里的数据名。 */
    public static final String NAME = "tianyi_intel";
    /** 当前 schema。改动结构时 +1 并写迁移。 */
    public static final int SCHEMA_VERSION = 1;

    // ------------------------------------------------------------ 传播参数

    /** 每轮最多重算多少人的传输出边（居民多于此数时按批轮扫）。 */
    private static final int EDGE_BATCH = 64;
    /** 一条边的行期（刻）：走完全程要多久。 */
    private static final long EDGE_TRAVEL_TICKS = 200L;
    /** 沿一条边传一次，可信度打几折。 */
    private static final double EDGE_ATTENUATION = 0.8;
    /** 边的有效期（刻）。过期边不再参与传播，下一圈会重建。 */
    private static final long EDGE_TTL = 2_400L;
    /** 边数上限：宁可情报传得慢，也不能让两两连边把内存吃穿。 */
    private static final int MAX_EDGES = 8_192;

    // ------------------------------------------------------------ 三份数据

    private final FactLedger facts = new FactLedger();
    private final IntelNet net = new IntelNet(facts);
    private final ActionLedger plans = new ActionLedger();

    /** 缓存的传播图（每轮只增量刷新一批）。 */
    private final IntelNet.SimpleGraph graph = new IntelNet.SimpleGraph();
    /** 分段扫描游标：走到头说明一圈扫完，可以清旧边。 */
    private int edgeCursor;

    /** 读档时看到的版本；写档时取它与 {@link #SCHEMA_VERSION} 的较大者。 */
    private int schemaVersion = SCHEMA_VERSION;

    /** 可执行的干预原语。id → 实现的映射由这里统一持有，布局只记 id。 */
    private static final List<InterventionAction> ACTIONS = List.of(
            new InvestigateAction(),
            new PursueAction());

    public IntelState() {
        // net 的构造已经绑了 facts；这里再显式绑一次，读档后走的是同一条路径
        net.bindLedger(facts);
    }

    // ------------------------------------------------------------ 取用

    /** 取世界唯一实例（挂主世界的数据存储，全维度共用一份）。 */
    public static IntelState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(IntelState::new, IntelState::load, null), NAME);
    }

    public FactLedger facts() {
        return facts;
    }

    public IntelNet net() {
        return net;
    }

    public ActionLedger plans() {
        return plans;
    }

    /** 存档版本（诊断用）。 */
    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public void setDirty() {
        super.setDirty();
    }

    // ------------------------------------------------------------ 每 tick

    /**
     * 每次调度调一次：只推计划队列，不做传播。
     *
     * <p>计划队列是「已经决定要做的事」，代价是若干次桥接调用；传播是「情报流动」，
     * 代价是 O(居民数) 的图扫描。前者随 tick 走，后者必须按周期走
     * （见 {@link #propagate}），否则每 tick 都在替全服 NPC 算两两距离。
     */
    public void onTick(MinecraftServer server, long now) {
        runDuePlans(server, now);
    }

    /** 逐条执行到点的布局；每 tick 最多 {@link WillConfig#ACTIVE_JOBS_PER_TICK} 条。 */
    private void runDuePlans(MinecraftServer server, long now) {
        List<ActionPlan> due = plans.due(now);
        if (due.isEmpty()) {
            return;
        }
        int limit = Math.max(1, WillConfig.ACTIVE_JOBS_PER_TICK);
        int ran = 0;
        for (ActionPlan plan : due) {
            if (ran >= limit) {
                break;
            }
            InterventionAction action = actionOf(plan.actionId());
            ServerLevel level = levelOf(server, plan.dim());
            if (action == null) {
                // 认不出的干预不会因为等下去而变认识：直接关门，否则它永远排队重试
                plans.settle(plan.id(), ActResult.denied(plan.actionId(), "未知干预", now,
                        plan.executorId()), now);
                setDirty();
                continue;
            }
            if (level == null) {
                // 维度没加载不算「条件不成立」：记一次失败进冷却，维度回来再试
                plans.settle(plan.id(), ActResult.failed(plan.actionId(), 0.0, "维度未加载", now,
                        plan.executorId()), now);
                setDirty();
                continue;
            }
            WorldView view = new LiveWorld(level, now, reachAt(server, plan, now), budgetOf(server))
                    .executor(plan.executorId())
                    .target(plan.targetId())
                    .pos(plan.pos());
            ActResult result;
            try {
                plan.start(now);
                result = action.applicable(view)
                        ? action.execute(view)
                        : ActResult.denied(action.id(), "条件不成立", now, plan.executorId());
            } catch (RuntimeException e) {
                // 实现方本该自己兜住；这里再兜一层，异常绝不允许穿进 tick
                result = ActResult.failed(action.id(), action.cost(), e.toString(), now, plan.executorId());
            }
            plans.settle(plan.id(), result, now);
            ran++;
            setDirty();
            TianyiMod.LOGGER.info("[天意·情报] {}", result.line());
        }
    }

    private static InterventionAction actionOf(String actionId) {
        for (InterventionAction a : ACTIONS) {
            if (a.id().equals(actionId)) {
                return a;
            }
        }
        return null;
    }

    /** 布局目标所在维度；没加载就返回 null（不跨维度凭空动手）。 */
    private static ServerLevel levelOf(MinecraftServer server, String dim) {
        if (dim == null || dim.isEmpty()) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dim)) {
                return level;
            }
        }
        return null;
    }

    /** 该布局位置上的天意触达（0..1）；无位置时退回「本维度触达」。 */
    private static double reachAt(MinecraftServer server, ActionPlan plan, long now) {
        WillState state = WillState.get(server);
        if (plan.pos() == WorldFact.NO_POS) {
            return state.carriers().reachInDim(plan.dim(), now);
        }
        return state.carriers().reachAt(plan.dim(), plan.pos(), now, 1024.0);
    }

    private static double budgetOf(MinecraftServer server) {
        return WillState.get(server).core().budget();
    }

    // ------------------------------------------------------------ 传播

    /**
     * 一轮情报传播：增量刷新传播边 → 推一跳 → 清掉无处可依的情报与过期事实。
     *
     * @return 本轮新增或提升的持有数量
     */
    public int propagate(MinecraftServer server, long now) {
        try {
            refreshEdges(server, now);
        } catch (RuntimeException e) {
            TianyiMod.LOGGER.warn("[天意·情报] 传播边刷新失败（本轮沿用旧边）：{}", e.toString());
        }
        int moved = 0;
        try {
            moved = net.spread(graph, facts, now);
        } catch (RuntimeException e) {
            TianyiMod.LOGGER.warn("[天意·情报] 传播异常（本轮跳过）：{}", e.toString());
        }
        // 事实已经不存在/已过期的转述记录留着只会污染「谁知道什么」
        net.prune(facts, now);
        facts.purge(now);
        if (moved > 0) {
            setDirty();
            if (TianyiMod.LOGGER.isDebugEnabled()) {
                TianyiMod.LOGGER.debug("[天意·情报] 本轮 {} 条情报易手，持有者 {} 人 / 事实 {} 条",
                        moved, net.holderCount(), facts.size());
            }
        }
        return moved;
    }

    /**
     * 重建一批传播边。
     *
     * <p>只有「活着且位置已知」的居民才有渠道：已故的人传不了话，
     * 位置未知的人连自己的坐标都不敢确认，更不该被当成中转站。
     */
    private void refreshEdges(MinecraftServer server, long now) {
        // 用桥接返回的集合给编译期定类型（本类不 import 本体类型）。
        // 先清空再逐维度收齐：getAllLevels() 已经包含主世界，留着会重复计入
        var roster = new ArrayList<>(GuzhenrenBridge.allResidents(server.overworld()));
        roster.clear();
        for (ServerLevel level : server.getAllLevels()) {
            roster.addAll(GuzhenrenBridge.allResidents(level));
        }
        roster.removeIf(r -> r.deceased() || !r.positionKnown());
        if (roster.isEmpty()) {
            graph.clear();
            edgeCursor = 0;
            return;
        }
        int size = roster.size();
        if (edgeCursor >= size) {
            // 一圈扫完：此时所有边都是上一圈的遗物，统一回收
            graph.clear();
            edgeCursor = 0;
        }
        int end = Math.min(size, edgeCursor + Math.min(EDGE_BATCH, size));
        double reach = WillConfig.CARRIER_REACH;
        double reach2 = reach * reach;
        for (int i = edgeCursor; i < end; i++) {
            if (graph.edgeCount() > MAX_EDGES) {
                break;
            }
            var a = roster.get(i);
            String from = a.npcId().toString();
            String dim = a.dimension();
            for (int j = 0; j < size; j++) {
                if (j == i) {
                    continue;
                }
                var b = roster.get(j);
                // 跨维度不算同一条路：情报告知是「有人走过来讲的」，不是世界广播
                if (!dim.equals(b.dimension())) {
                    continue;
                }
                double dx = a.x() - b.x();
                double dz = a.z() - b.z();
                if (dx * dx + dz * dz > reach2) {
                    continue;
                }
                graph.link(from, b.npcId().toString(), EDGE_TRAVEL_TICKS, EDGE_ATTENUATION, now + EDGE_TTL);
            }
        }
        edgeCursor = end;
    }

    /** 当前缓存的边数（诊断用）。 */
    public int edgeCount() {
        return graph.edgeCount();
    }

    // ------------------------------------------------------------ 情报 → 行动

    /**
     * 把「谁知道了什么」变成「谁打算做什么」。
     *
     * <p>这是整条链上最容易漏掉的一环：没有这一步，{@link #runDuePlans} 永远面对一个空队列，
     * 调查与追捕两个动作虽然写好了却从不执行 —— 那就是「写完了但没接线」。
     *
     * <h2>凭什么选他当执行者</h2>
     * 只有<b>真的知道这条情报</b>的居民（{@code net.knows}）才会被选为执行者。
     * 不知道这件事的人不会莫名其妙地跨半个地图来追你 —— 这正是「NPC 只使用自己实际获得的信息」。
     *
     * <h2>为什么会挑这条情报</h2>
     * 只处理有实体后果的行为（采集 / 抢占 / 杀人 / 抢夺），且可信度要过
     * {@link WillConfig#CONFIDENCE_ACT_FLOOR}。一条道听途说、快过期、把握不足的传闻
     * 不会变成追兵。
     *
     * @return 本轮新收下的布局数
     */
    public int consider(MinecraftServer server, long now) {
        if (facts.isEmpty()) {
            return 0;
        }
        int admitted = 0;
        int budget = Math.max(1, WillConfig.MAX_SAME_ACTION);
        for (WorldFact f : facts.live(now)) {
            if (admitted >= budget) {
                break;
            }
            String actionId = actionFor(f.kind);
            if (actionId == null) {
                continue;
            }
            if (f.effectiveConfidence(now) < WillConfig.CONFIDENCE_ACT_FLOOR) {
                continue;
            }
            ServerLevel level = levelOf(server, f.dim);
            if (level == null) {
                continue;
            }
            // 去重：同一动作 + 同一目标 + 同一分钟只排一次
            String key = ActionPlan.dedupeKey(actionId, f.actor, now, 1200L);
            if (plans.byDedupe(key) != null) {
                continue;
            }
            String executor = pickExecutor(level, f, now);
            if (executor == null) {
                // 没人知道 → 不排；这条分支本身就是「无情报来源不会反应」的实现
                continue;
            }
            ActionPlan plan = new ActionPlan(actionId, executor, f.actor, f.dim, f.pos,
                    now, now, f.effectiveConfidence(now));
            if (plans.admit(plan, key, now)) {
                admitted++;
                if (TianyiMod.LOGGER.isDebugEnabled()) {
                    TianyiMod.LOGGER.debug("[天意·情报] 排入布局 {}：执行者 {} ← 事实 {}", actionId,
                            executor, f.id);
                }
            }
        }
        if (admitted > 0) {
            setDirty();
        }
        return admitted;
    }

    /** 只有这几类行为会招来 NPC 的调查或追捕；其余事实只是「世界知道了」，不生成行动。 */
    private static String actionFor(WorldFact.Kind kind) {
        return switch (kind) {
            case HARVEST, SEIZE, SIGHTING -> "npc.investigate";
            case ROBBERY, KILL_NPC, KILL_PLAYER -> "npc.pursue";
            // 商贸、炼蛊、对抗、兽群不直接变成对人的行动
            case TRADE, ALCHEMY, DEFIANCE, HERD -> null;
        };
    }

    /**
     * 在知道这条事实的居民里挑一个执行者。
     *
     * <p>优先挑<b>最近</b>的那个：离得近的人先到，也少走冤枉路。
     * 取不到（没人知道 / 都在加载区外 / 情报无人持有）就返回 {@code null}。
     */
    private String pickExecutor(ServerLevel level, WorldFact fact, long now) {
        String best = null;
        double bestDist = Double.MAX_VALUE;
        boolean factHasPos = fact.positionKnown();
        double fx = factHasPos ? unpackX(fact.pos) : 0.0;
        double fz = factHasPos ? unpackZ(fact.pos) : 0.0;
        for (var resident : GuzhenrenBridge.allResidents(level)) {
            if (resident == null || resident.deceased() || !resident.positionKnown()) {
                continue;
            }
            String id = resident.npcId().toString();
            // 关键：只有真的知道这条情报的人才会被选中
            if (!net.knows(id, fact.id)) {
                continue;
            }
            if (!resident.dimension().equals(fact.dim)) {
                continue;
            }
            double dx = resident.x() - fx;
            double dz = resident.z() - fz;
            double d = factHasPos ? dx * dx + dz * dz : 0.0;
            if (d < bestDist) {
                bestDist = d;
                best = id;
            }
        }
        return best;
    }

    /** 解包 X（与 {@code BlockPos.getX(long)} 同构）。 */
    private static double unpackX(long packed) {
        return (int) (packed >> 38);
    }

    /** 解包 Z。 */
    private static double unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    // ------------------------------------------------------------ 序列化

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", Math.max(SCHEMA_VERSION, schemaVersion));

        ListTag factList = new ListTag();
        for (WorldFact f : facts.all()) {
            factList.add(factToTag(f));
        }
        tag.put("facts", factList);

        ListTag intelList = new ListTag();
        for (String row : net.exportRows()) {
            intelList.add(StringTag.valueOf(row));
        }
        tag.put("intel", intelList);

        ListTag planList = new ListTag();
        for (Map<String, String> row : plans.snapshot()) {
            CompoundTag c = new CompoundTag();
            // 布局行本来就是扁平的「键 → 字符串」，原样搬进 NBT 即可往返
            row.forEach(c::putString);
            planList.add(c);
        }
        tag.put("plans", planList);
        return tag;
    }

    public static IntelState load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        IntelState s = new IntelState();
        int version = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 0;

        // 事实：逐条尽力读，读不懂的跳过（不因为一条脏数据丢掉整本账）
        for (Tag t : tag.getList("facts", Tag.TAG_COMPOUND)) {
            WorldFact f = factFromTag((CompoundTag) t);
            if (f != null) {
                s.facts.observe(f);
            }
        }

        List<String> rows = new ArrayList<>();
        for (Tag t : tag.getList("intel", Tag.TAG_STRING)) {
            rows.add(((StringTag) t).getAsString());
        }
        s.net.importRows(rows);
        s.net.bindLedger(s.facts);

        List<Map<String, String>> planRows = new ArrayList<>();
        for (Tag t : tag.getList("plans", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Map<String, String> row = new LinkedHashMap<>();
            for (String key : c.getAllKeys()) {
                row.put(key, c.getString(key));
            }
            planRows.add(row);
        }
        s.plans.restore(planRows);

        // 只读不删：版本旧也照读，最后把版本升上来，下次存盘即完成迁移
        s.schemaVersion = Math.max(SCHEMA_VERSION, version);
        if (version < SCHEMA_VERSION) {
            s.setDirty();
        }
        return s;
    }

    /**
     * 事实 → NBT。
     *
     * <p>故意不写 {@code WorldFact} 的 id：它由「种类+行为人+目标+维度+位置+时间桶」派生，
     * 读回来重算一定一致。存一份反而会出现「存的 id 和字段对不上」的假一致性。
     */
    private static CompoundTag factToTag(WorldFact f) {
        CompoundTag c = new CompoundTag();
        c.putString("kind", f.kind.name());
        c.putString("actor", f.actor);
        c.putString("target", f.target);
        c.putString("dim", f.dim);
        c.putLong("pos", f.pos);
        c.putLong("at", f.atTick);
        c.putLong("ttl", f.ttl);
        c.putDouble("conf", f.baseConfidence);
        c.putString("detail", f.detail);
        return c;
    }

    /** NBT → 事实；种类缺失/不认识、或没有发生时刻时返回 null（跳过该条）。 */
    private static WorldFact factFromTag(CompoundTag c) {
        if (c == null) {
            return null;
        }
        WorldFact.Kind kind;
        try {
            kind = WorldFact.Kind.valueOf(c.getString("kind"));
        } catch (RuntimeException e) {
            return null;
        }
        if (!c.contains("at")) {
            // 没有发生时刻就算不出过期时刻，留着会被当成「永远有效」的旧情报
            return null;
        }
        long pos = c.contains("pos") ? c.getLong("pos") : WorldFact.NO_POS;
        long ttl = c.contains("ttl") ? c.getLong("ttl") : WorldFact.DEFAULT_TTL;
        double conf = c.contains("conf") ? c.getDouble("conf") : 0.5;
        try {
            return new WorldFact(kind, c.getString("actor"), c.getString("target"),
                    c.getString("dim"), pos, c.getLong("at"), ttl, conf, c.getString("detail"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
