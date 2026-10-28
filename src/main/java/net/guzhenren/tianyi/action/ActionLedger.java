package net.guzhenren.tianyi.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 布局步骤总账：去重、排队、结算、清理。
 *
 * <h2>它防的是什么</h2>
 * 需求明写「同一事件不会重复触发追杀或结算」。世界每 tick 都在扫，一次抢矿会被
 * 采集事件、拾取事件、目击扫描各看到一遍 —— 如果不去重，玩家一次行为会招来三支追兵。
 * 去重键由 {@link ActionPlan#dedupeKey} 生成（动作 + 目标 + 时间桶），
 * {@link #admit} 是唯一的入口。
 *
 * <p>纯逻辑，不 import Minecraft。持久化由 Minecraft 侧把 {@link #snapshot()} 写进 NBT。
 */
public final class ActionLedger {
    /** 存档里最多保留多少条（含已结算的，用于复发判断）。 */
    public static final int MAX_PLANS = 256;
    /** 已结算步骤保留多久（刻）。6 小时。 */
    public static final long KEEP_SETTLED = 24_000L * 6L;

    private final Map<String, ActionPlan> byId = new LinkedHashMap<>();
    /** dedupeKey → planId */
    private final Map<String, String> byKey = new LinkedHashMap<>();

    /**
     * 收下一条步骤。同去重键已有<b>未终结</b>步骤时拒收。
     *
     * @return 真的收下了返回 true
     */
    public boolean admit(ActionPlan plan, String dedupeKey, long now) {
        if (plan == null) {
            return false;
        }
        String key = dedupeKey == null ? "" : dedupeKey;
        if (!key.isEmpty()) {
            String existingId = byKey.get(key);
            if (existingId != null) {
                ActionPlan existing = byId.get(existingId);
                if (existing != null && !terminal(existing.state())) {
                    // 同一件事已经在排队/执行 —— 不再生成第二条
                    return false;
                }
            }
        }
        if (byId.size() >= MAX_PLANS) {
            purge(now, 0L);
            if (byId.size() >= MAX_PLANS) {
                dropOldestSettled();
            }
        }
        byId.put(plan.id(), plan);
        // 去重键必须随步骤一起落盘，否则重启后 byKey 无法重建（见 restore）
        plan.bindDedupe(key);
        if (!key.isEmpty()) {
            byKey.put(key, plan.id());
        }
        return true;
    }

    public static boolean terminal(ActionPlan.State s) {
        return s == ActionPlan.State.DONE || s == ActionPlan.State.ABANDONED;
    }

    /** 到点可跑的步骤，按创建时间升序。 */
    public List<ActionPlan> due(long now) {
        List<ActionPlan> out = new ArrayList<>();
        for (ActionPlan p : byId.values()) {
            if (p.open(now)) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Long.compare(a.createdAt(), b.createdAt()));
        return out;
    }

    /** 结算一条。结算后按需重新排队（被阻断但还有余量）。 */
    public boolean settle(String planId, ActResult r, long now) {
        ActionPlan p = byId.get(planId);
        if (p == null) {
            return false;
        }
        p.settle(r, now);
        if (p.state() == ActionPlan.State.BLOCKED || p.state() == ActionPlan.State.FAILED) {
            p.requeue(now);
        }
        return true;
    }

    public ActionPlan get(String planId) {
        return byId.get(planId);
    }

    public ActionPlan byDedupe(String key) {
        String id = byKey.get(key);
        return id == null ? null : byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public int count(ActionPlan.State state) {
        int n = 0;
        for (ActionPlan p : byId.values()) {
            if (p.state() == state) {
                n++;
            }
        }
        return n;
    }

    public List<ActionPlan> all() {
        return new ArrayList<>(byId.values());
    }

    /**
     * 清理。
     *
     * @param now      当前时间
     * @param extraTtl 额外保留（0 = 用 {@link #KEEP_SETTLED}）
     * @return 删了几条
     */
    public int purge(long now, long extraTtl) {
        long keep = extraTtl > 0 ? extraTtl : KEEP_SETTLED;
        List<String> drop = new ArrayList<>();
        for (Map.Entry<String, ActionPlan> e : byId.entrySet()) {
            ActionPlan p = e.getValue();
            // 参照点取「结算时刻」与「创建时刻」的较大者：abandon() 不掉 settledAt，
            // 只用 settledAt 作判定会让被放弃的步骤永远删不掉。
            long ref = Math.max(p.settledAt(), p.createdAt());
            if (terminal(p.state()) && now - ref > keep) {
                drop.add(e.getKey());
            }
        }
        for (String id : drop) {
            byId.remove(id);
        }
        if (!drop.isEmpty()) {
            byKey.entrySet().removeIf(e -> !byId.containsKey(e.getValue()));
        }
        return drop.size();
    }

    private void dropOldestSettled() {
        String oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (ActionPlan p : byId.values()) {
            if (terminal(p.state()) && p.settledAt() < oldestAt) {
                oldestAt = p.settledAt();
                oldest = p.id();
            }
        }
        if (oldest != null) {
            byId.remove(oldest);
            byKey.entrySet().removeIf(e -> !byId.containsKey(e.getValue()));
        }
    }

    public void clear() {
        byId.clear();
        byKey.clear();
    }

    // ------------------------------------------------------------ 持久化

    /** 扁平化：每条步骤一行 {@code Map}，由 Minecraft 侧写进 ListTag。 */
    public List<Map<String, String>> snapshot() {
        List<Map<String, String>> out = new ArrayList<>(byId.size());
        for (ActionPlan p : byId.values()) {
            out.add(p.toMap());
        }
        return out;
    }

    /**
     * 反序列化。<b>不</b>清空再填，而是逐条按 id 幂等并入 —— 重放不会产生重复。
     *
     * <p>同时按存档里的 {@link ActionPlan#dedupeKey()} 重建去重索引：
     * 不重建的话，服务端重启后同一件事会被重新收进来，正好踩中
     * 「重启后不能重复生成追杀」。
     */
    public int restore(List<Map<String, String>> rows) {
        int n = 0;
        if (rows == null) {
            return 0;
        }
        for (Map<String, String> row : rows) {
            ActionPlan p = ActionPlan.fromMap(row);
            if (p == null) {
                continue;
            }
            byId.put(p.id(), p);
            String key = p.dedupeKey();
            if (key != null && !key.isEmpty()) {
                byKey.put(key, p.id());
            }
            n++;
        }
        return n;
    }
}
