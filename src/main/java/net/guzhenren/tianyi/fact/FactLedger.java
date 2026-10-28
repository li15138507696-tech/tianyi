package net.guzhenren.tianyi.fact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事实总账：世界里「已经发生过、并且被记下来」的事都在这里，按 id 去重、按时间过期。
 *
 * <h2>为什么要有总账</h2>
 * 天意、NPC、势力三方读的必须是<b>同一份</b>事实，否则「谁先看到的」就说不清，
 * 传播链也会重复计数。总账只做四件事：去重写入、按 id 查、列表（活的 / 全量）、
 * 过期清理。它<b>不做广播</b>（那是 {@link IntelNet} 的事），也<b>不做判定</b>
 * （那是天意/势力的决策层的事）。
 *
 * <h2>四条纪律</h2>
 * <ul>
 *   <li><b>幂等写入。</b>{@link #observe} 对同 id 返回 {@code false} 且<b>不覆盖</b>：
 *       先到的那条（可信度更高、时间更早）说了算，后来的重复观测不能把它改小。</li>
 *   <li><b>过期 ≠ 删除。</b>过期只是「不再被当作依据」（{@link #live} 过滤），
 *       真要回收空间得显式 {@link #purge}——这样追溯与审计还能看得到旧事。</li>
 *   <li><b>有界。</b>上限 {@link #MAX_FACTS}，超了先淘汰最旧的<b>已过期</b>条目，
 *       还不下去才淘汰最旧条目。宁可丢旧事，也不能让一个刷子把服务端内存吃穿。</li>
 *   <li><b>持久化在本类之外。</b>{@link #snapshot()} / {@link #restore} 交出纯对象列表，
 *       MC 侧（SavedData）自己决定写进哪个 CompoundTag，本类保持零依赖。</li>
 * </ul>
 *
 * <p>非线程安全：只在服务端主线程（或离线测试线程）使用。
 */
public final class FactLedger {

    /** 容量上限。 */
    public static final int MAX_FACTS = 4096;

    private final Map<String, WorldFact> byId = new LinkedHashMap<>();

    // ---------------------------------------------------------------- 写入

    /**
     * 记一条事实。
     *
     * @return {@code true} 首次入库；{@code false} 已存在同 id（不改写、不重复计数）
     */
    public boolean observe(WorldFact f) {
        if (f == null || byId.containsKey(f.id)) {
            return false;
        }
        byId.put(f.id, f);
        enforceCap(f.atTick);
        return true;
    }

    /** 批量写入，返回实际新增条数。 */
    public int observeAll(Collection<WorldFact> facts) {
        if (facts == null) {
            return 0;
        }
        int n = 0;
        for (WorldFact f : facts) {
            if (observe(f)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 读取

    /** 按 id 取；不存在返回 {@code null}。 */
    public WorldFact get(String id) {
        return id == null ? null : byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** 未过期的事实，按 {@code atTick} 升序（同一时刻按 id 稳定排序）。 */
    public List<WorldFact> live(long now) {
        List<WorldFact> out = new ArrayList<>(byId.size());
        for (WorldFact f : byId.values()) {
            if (!f.expired(now)) {
                out.add(f);
            }
        }
        out.sort(Comparator.comparingLong((WorldFact f) -> f.atTick).thenComparing(f -> f.id));
        return out;
    }

    /** 未过期的某一类事实，按 {@code atTick} 升序。 */
    public List<WorldFact> live(WorldFact.Kind kind, long now) {
        List<WorldFact> out = new ArrayList<>();
        for (WorldFact f : live(now)) {
            if (f.kind == kind) {
                out.add(f);
            }
        }
        return out;
    }

    /** 全部事实（含已过期，插入顺序）。 */
    public List<WorldFact> all() {
        return new ArrayList<>(byId.values());
    }

    // ---------------------------------------------------------------- 回收

    /**
     * 删除已过期的事实。
     *
     * @return 删除条数
     */
    public int purge(long now) {
        int before = byId.size();
        byId.values().removeIf(f -> f.expired(now));
        return before - byId.size();
    }

    public void clear() {
        byId.clear();
    }

    // ---------------------------------------------------------------- 持久化（供 MC 侧）

    /** 复制一份全量事实（含过期），供 SavedData 写出。 */
    public List<WorldFact> snapshot() {
        return new ArrayList<>(byId.values());
    }

    /** 从存档恢复：先清空，再逐条写入（重复 id 只留第一条），最后按上限收敛。 */
    public void restore(List<WorldFact> facts) {
        byId.clear();
        if (facts == null) {
            return;
        }
        long newest = 0L;
        boolean any = false;
        for (WorldFact f : facts) {
            if (f == null || byId.containsKey(f.id)) {
                continue;
            }
            byId.put(f.id, f);
            if (!any || f.atTick > newest) {
                newest = f.atTick;
                any = true;
            }
        }
        if (any) {
            enforceCap(newest);
        }
    }

    @Override
    public String toString() {
        return "FactLedger{size=" + byId.size() + "}";
    }

    // ---------------------------------------------------------------- 内部

    /** 超限收敛：先淘汰最旧的过期项，仍超限则淘汰最旧项。 */
    private void enforceCap(long now) {
        while (byId.size() > MAX_FACTS) {
            String victim = oldest(true, now);
            if (victim == null) {
                victim = oldest(false, now);
            }
            if (victim == null) {
                return;
            }
            byId.remove(victim);
        }
    }

    /** 找 {@code atTick} 最小的条目；{@code expiredOnly} 时只考虑已过期的。 */
    private String oldest(boolean expiredOnly, long now) {
        String victim = null;
        long best = Long.MAX_VALUE;
        for (WorldFact f : byId.values()) {
            if (expiredOnly && !f.expired(now)) {
                continue;
            }
            if (f.atTick < best) {
                best = f.atTick;
                victim = f.id;
            }
        }
        return victim;
    }
}
