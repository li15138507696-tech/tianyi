import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 3.14.1 热路径微基准（纯 Java，无 Minecraft 依赖）。
 *
 * <p>只测「纯 Java 骨架」：循环次数、临时对象分配、集合遍历。真实方块/方块实体/区块查询
 * 需要 Minecraft 服务端，本类<b>不</b>给出那些数字——那部分在文档里标成静态估算。
 *
 * <p>输出是本机实测，不是设计目标；只有同机前后对比才有意义。
 */
public final class HotPathBench {

    /** 与 SiteSettings.AVOIDANCE_RADIUS 一致。 */
    private static final int RADIUS = 32;

    private static volatile long sink;

    public static void main(String[] args) {
        System.out.println("== 3.14.1 热路径微基准（纯 Java）");
        System.out.println("半径 " + RADIUS + "，立方边长 " + (2 * RADIUS + 1));
        System.out.println();

        benchSphereSkeleton();
        benchSlotLookup();
        benchPendingCopy();
        benchWrittenSum();

        System.out.println();
        System.out.println("sink=" + sink + "（防止 JIT 把结果优化掉）");
        System.out.println("未测：getBlockState / getBlockEntity / hasChunkAt / 网络编解码 / 实体表查询");
    }

    // ---------------------------------------------------------------- 1. 球壳骨架

    /**
     * {@code SiteProtection.check} 的循环骨架：65^3 个位置，每个算一次 distSqr 并按半径过滤。
     * 真实实现还要对「通过过滤的位置」读方块状态，那部分不在本基准里。
     */
    private static void benchSphereSkeleton() {
        int side = 2 * RADIUS + 1;
        long visited = 0;
        long total = 0;
        int rounds = 5;
        for (int round = 0; round < rounds; round++) {
            long t0 = System.nanoTime();
            visited = 0;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                        if ((double) (dx * dx + dy * dy + dz * dz) > (double) RADIUS * RADIUS) continue;
                        visited++;
                    }
                }
            }
            if (round > 0) total += System.nanoTime() - t0;
        }
        sink += visited;
        report("球壳骨架：位置遍历 + 距离过滤",
                (long) side * side * side, total / (rounds - 1),
                "球内（通过过滤）位置数 = " + visited);
    }

    // ---------------------------------------------------------------- 2. 槽位查找

    /** 模拟 1.21 的 BlockPos（只保留本方法用到的能力）。 */
    static final class Pos {
        final int x, y, z;
        Pos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) {
            return o instanceof Pos p && p.x == x && p.y == y && p.z == z;
        }
        @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
    }

    /** 模拟 SitePlacement.Slot：materialized + 坐标；{@code pos()} 与真实实现一样新建对象。 */
    static final class Slot {
        final boolean materialized;
        final int x, y, z;
        Slot(boolean m, int x, int y, int z) { materialized = m; this.x = x; this.y = y; this.z = z; }
        Pos pos() { return new Pos(x, y, z); }
    }

    /**
     * 改法前后对比：32 格球壳里每个位置都要判断「是不是本站的已物化槽位」。
     *
     * <p>旧写法：{@code slots().stream().filter(s -> pos(s).equals(p))} —— 每个位置 × 每个槽位
     * 都 new 一个 Pos，再加一次 Stream 管线。
     * <p>新写法：纯 int 比较，零分配。
     */
    private static void benchSlotLookup() {
        int slots = 8;
        List<Slot> site = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            // 真实槽位就是 origin.offset(i%3, 0, i/3)
            site.add(new Slot(i < slots - 1, i % 3, 0, i / 3));
        }
        Pos[] positions = spherePositions();
        int rounds = 5;
        long oldNanos = 0, newNanos = 0;
        long hits = 0;

        for (int round = 0; round < rounds; round++) {
            long t0 = System.nanoTime();
            long h1 = 0;
            for (Pos p : positions) {
                Slot hit = site.stream()
                        .filter(s -> s.materialized && s.pos().equals(p))
                        .findFirst().orElse(null);
                if (hit != null) h1++;
            }
            long a = System.nanoTime() - t0;

            long t1 = System.nanoTime();
            long h2 = 0;
            for (Pos p : positions) {
                Slot hit = null;
                for (Slot s : site) {
                    if (s.materialized && s.x == p.x && s.y == p.y && s.z == p.z) { hit = s; break; }
                }
                if (hit != null) h2++;
            }
            long b = System.nanoTime() - t1;

            if (round > 0) { oldNanos += a; newNanos += b; hits += h1 + h2; }
        }
        sink += hits;
        int n = positions.length;
        double oldMs = oldNanos / (double) (rounds - 1) / 1_000_000.0;
        double newMs = newNanos / (double) (rounds - 1) / 1_000_000.0;
        System.out.println("-- 槽位查找（每个球内位置一次，" + n + " 位置 × " + slots + " 槽位）");
        System.out.printf("   旧（new Pos + Stream）%10.3f ms/次扫描%n", oldMs);
        System.out.printf("   新（纯 int 比较）        %10.3f ms/次扫描%n", newMs);
        System.out.printf("   净省 %.3f ms/次；旧新耗时比 %.1f×%n",
                oldMs - newMs, newMs <= 0 ? 0.0 : oldMs / newMs);
    }

    /** 预先算好球内位置，避免基准本身退化成平方复杂度。 */
    private static Pos[] spherePositions() {
        List<Pos> out = new ArrayList<>(140_000);
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
                for (int dy = -RADIUS; dy <= RADIUS; dy++)
                    if ((double) (dx * dx + dy * dy + dz * dz) <= (double) RADIUS * RADIUS)
                        out.add(new Pos(dx, dy, dz));
        return out.toArray(new Pos[0]);
    }

    // ---------------------------------------------------------------- 3. 每 tick 集合拷贝

    /** {@code JournalNet.tick} 每 tick 都跑；PENDING 绝大多数时刻为空。 */
    private static void benchPendingCopy() {
        Set<UUID> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
        int rounds = 20_000;
        long nanos = 0;
        for (int i = 0; i < rounds; i++) {
            long t0 = System.nanoTime();
            Set<UUID> snapshot = Set.copyOf(pending);
            nanos += System.nanoTime() - t0;
            sink += snapshot.size();
        }
        System.out.println("-- JournalNet.tick：Set.copyOf(空集)");
        System.out.printf("   %d 次共 %.3f ms，单次 %.0f ns（服务端 20 次/秒）%n",
                rounds, nanos / 1_000_000.0, nanos / (double) rounds);
    }

    // ---------------------------------------------------------------- 4. 见闻写入的求和

    /** {@code SiteEvidence.remember} 原来的写法：每次写入都现算全体 key 总数。 */
    private static void benchWrittenSum() {
        Map<String, Set<String>> written = new HashMap<>();
        for (int p = 0; p < 20; p++) {
            Set<String> keys = new HashSet<>();
            for (int k = 0; k < 50; k++) keys.add("site:key:" + k);
            written.put("player-" + p, keys);
        }
        int rounds = 20_000;
        long nanos = 0;
        for (int i = 0; i < rounds; i++) {
            long t0 = System.nanoTime();
            int total = written.values().stream().mapToInt(Set::size).sum();
            nanos += System.nanoTime() - t0;
            sink += total;
        }
        System.out.println("-- SiteEvidence.remember：现算 20 人 × 50 key 的总数");
        System.out.printf("   %d 次共 %.3f ms，单次 %.0f ns%n",
                rounds, nanos / 1_000_000.0, nanos / (double) rounds);
    }

    private static void report(String name, long ops, long nanos, String note) {
        System.out.println("-- " + name);
        System.out.printf("   每轮 %d 次访问，平均 %.3f ms/轮，%.1f ns/次%n",
                ops, nanos / 1_000_000.0, nanos / (double) ops);
        System.out.println("   " + note);
    }
}
