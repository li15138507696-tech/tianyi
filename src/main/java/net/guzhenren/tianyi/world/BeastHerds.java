package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianyi.TianyiBestiary;
import net.guzhenren.tianyi.will.BeastHerd;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * 野生兽群的世界侧：把 {@link BeastHerd} 的种群账接到真实实体上。
 *
 * <h2>一条硬规则：生成一只就少一只</h2>
 * 天意要「借兽伤人」，只能从<b>已经存在的种群</b>里赶兽出来。
 * 所以本类所有生成路径都必须先过 {@link BeastHerd#drive}，
 * 种群不够就一只都生不出来 —— 从结构上杜绝「在玩家身边无限刷怪」。
 *
 * <h2>兽群从哪来</h2>
 * 兽群不是我们凭空点名生成的：{@link #ensureLocal} 在一个区域里
 * 按栖息地质量开一处兽群账，之后靠自然增长（每日基础增长率）把种群养起来。
 * 玩家杀得越多，种群掉得越快；杀到见底就崩溃，要很久才回得来。
 *
 * <h2>与本体妖兽档位的关系</h2>
 * 兽实体 id 一律取 {@link TianyiBestiary} 里核对过的那两族
 * （魂兽 / 木魅树精），一阶为基础种群，兽王级只在种群很大时偶尔出现。
 */
public final class BeastHerds {
    private BeastHerds() {
    }

    /**
     * 兽群基础兽：名录里 1–2 阶（特殊种 + 百兽）。
     *
     * <p>3.10.0 起从 {@link net.guzhenren.tianyi.will.BeastCatalog} 派生，
     * 不再只认魂兽／木魅树精两族 —— 雷道的狼、金道的虎、水道的羚等都会成群。
     */
    private static final String[] BASE_BEASTS = idsOf(1, 2);

    /** 兽王级（种群够大时才出现）：3–5 阶。 */
    private static final String[] ELITE_BEASTS = idsOf(3, 4, 5);

    /** 从名录取若干阶的全部 id。 */
    private static String[] idsOf(int... tiers) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int t : tiers) {
            for (var e : net.guzhenren.tianyi.will.BeastCatalog.byTier(t)) {
                out.add(e.id());
            }
        }
        return out.toArray(new String[0]);
    }

    /** 一处兽群的承载力基数。[平衡参数] */
    private static final double BASE_CAPACITY = 18.0;
    /** 一次「借兽」最多赶出去几只（防一次拉满）。 */
    private static final int MAX_DRIVE_PER_CALL = 8;
    /** 认定同一处兽群的距离（格）。 */
    private static final double SAME_HERD_RADIUS = 96.0;

    // ---------------------------------------------------------------- 建群

    /**
     * 确保玩家所在区域有一处兽群账（没有就按当地栖息地开一处）。
     *
     * @return 该区域的兽群（可能是既有的）
     */
    public static BeastHerd ensureLocal(WillState state, ServerLevel level, BlockPos center,
                                        long now) {
        String dim = level.dimension().location().toString();
        BeastHerd existing = state.herds().at(dim, center.asLong(), SAME_HERD_RADIUS);
        if (existing != null) {
            return existing;
        }
        // 栖息地质量：用「是否在已加载的地表、附近有没有水/植被」做粗略折算
        double habitat = estimateHabitat(level, center);
        String beastId = BASE_BEASTS[(int) Math.floorMod(now + center.asLong(), BASE_BEASTS.length)];
        String id = "herd-" + now + "-" + dim.replace(':', '_') + "-" + (center.asLong() & 0xFFFF);
        BeastHerd herd = state.herds().open(id, beastId, dim, center.asLong(),
                BASE_CAPACITY, habitat, now);
        if (herd != null) {
            state.core().note("herd.open " + id + " beast=" + beastId
                    + " habitat=" + String.format("%.2f", habitat));
            state.setDirty();
        }
        return herd;
    }

    /** 粗略的栖息地质量（0.25..1.0）：只看「地表是否可站立 + 是否临水」。 */
    private static double estimateHabitat(ServerLevel level, BlockPos center) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, center);
        if (!level.isLoaded(surface)) {
            return 0.25;
        }
        double q = 0.6;
        // 附近 8 格内有水 → 更适合野生兽群
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                BlockPos p = surface.offset(dx, -1, dz);
                if (!level.isLoaded(p)) {
                    continue;
                }
                if (!level.getFluidState(p).isEmpty()) {
                    q += 0.15;
                }
            }
        }
        return Math.min(1.0, q);
    }

    // ---------------------------------------------------------------- 生态推进

    /**
     * 推进所有兽群的种群与迁徙（每 200 刻调一次）。
     *
     * @return 本轮发生迁徙的兽群数
     */
    public static int census(MinecraftServer server, WillState state, long now, Logger log) {
        double days = 200.0 / 24_000.0;
        state.herds().tick(days, now);

        int migrated = 0;
        for (BeastHerd h : state.herds().all()) {
            if (!h.shouldMigrate()) {
                continue;
            }
            ServerLevel level = levelByKey(server, h.dim);
            if (level == null) {
                continue;
            }
            BlockPos from = unpack(h.anchorPos());
            if (!level.isLoaded(from)) {
                continue; // 没人看的地方不动，省性能也符合「不在未加载区搞事」
            }
            BlockPos to = shift(level, from, now + h.anchorPos());
            if (to == null) {
                continue;
            }
            String why = h.habitatQuality() < 0.35 ? "habitat-poor" : "over-crowded";
            if (h.migrate(to.asLong(), now, why)) {
                migrated++;
                state.core().note("herd.migrate " + h.id + " why=" + why);
            }
        }
        if (migrated > 0) {
            state.setDirty();
            if (log != null && log.isDebugEnabled()) {
                log.debug("[天意] 兽群迁徙 {} 次", migrated);
            }
        }
        return migrated;
    }

    // ---------------------------------------------------------------- 借兽

    /**
     * 天意借兽施压：从最近的、还有兽的兽群里赶一批出来，朝目标方向出现。
     *
     * <p>被赶出来的兽**从种群账上扣**，所以种群不够时一只也生不出来。
     *
     * @return 实际生成的兽数
     */
    public static int driveToward(WillState state, ServerLevel level, ServerPlayer target,
                                  long now, int want) {
        String dim = level.dimension().location().toString();
        BlockPos dest = target.blockPosition();
        // 先在附近找；找不到就在更大范围找一次（兽群可以远远地赶过来）
        BeastHerd herd = state.herds().nearestDrivable(dim, dest.asLong(), 512.0, 1);
        if (herd == null) {
            herd = ensureLocal(state, level, dest, now);
        }
        if (herd == null) {
            return 0;
        }
        int n = Math.min(Math.max(1, want), MAX_DRIVE_PER_CALL);
        int got = herd.drive(n, now);
        if (got <= 0) {
            return 0;
        }
        int spawned = 0;
        BlockPos from = unpack(herd.anchorPos());
        for (int i = 0; i < got; i++) {
            // 从兽群锚点朝目标方向推进，落点仍要求已加载
            double t = got <= 1 ? 1.0 : (double) i / (got - 1);
            int x = (int) Math.round(from.getX() + (dest.getX() - from.getX()) * t);
            int z = (int) Math.round(from.getZ() + (dest.getZ() - from.getZ()) * t);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.isLoaded(p)) {
                continue;
            }
            boolean elite = herd.population() > herd.capacity() * 0.8
                    && Math.abs(Math.sin(now * 0.003 + i)) > 0.9;
            String id = elite
                    ? ELITE_BEASTS[(int) Math.floorMod(now + i, ELITE_BEASTS.length)]
                    : herd.beastId;
            Entity e = WorldExec.spawnEntity(level, id, p);
            if (e != null) {
                e.getPersistentData().putString("tianyi_herd", herd.id);
                spawned++;
            }
        }
        // B07：预留了但没生成出来的，必须把种群还回去
        int leftover = got - spawned;
        if (leftover > 0) {
            int back = herd.returnPopulation(leftover, now);
            state.core().note("herd.drive-return " + herd.id + " 预留=" + got
                    + " 生成=" + spawned + " 归还=" + back);
        }
        if (spawned > 0) {
            state.core().note("herd.drive " + herd.id + " n=" + spawned
                    + " 余=" + String.format("%.1f", herd.population()));
            state.setDirty();
        }
        return spawned;
    }

    // ---------------------------------------------------------------- 捕猎

    /**
     * 玩家杀了一只<b>从兽群账里赶出来</b>的兽。
     *
     * <p>B07 修复的两点：
     * <ol>
     *   <li><b>不再重复扣种群</b>：这些个体在 {@code drive} 时已经从账上扣走了，
     *       再扣一次就是重复记账（复现里 10 只赶出再杀 1 只会变成 8）。</li>
     *   <li><b>按物种核验</b>：只认带 {@code tianyi_herd} 标记、且物种与该兽群一致的实体。
     *       附近无关动物、NPC 被玩家杀掉都不会污染任何兽群的账。</li>
     * </ol>
     *
     * @return 记到哪一处兽群上（找不到/不匹配返回 null）
     */
    public static BeastHerd recordKill(WillState state, LivingEntity victim, long now) {
        String herdId = victim.getPersistentData().getString("tianyi_herd");
        if (herdId.isEmpty()) {
            return null; // 没有兽群标记 → 不属于任何账，不记
        }
        BeastHerd herd = state.herds().get(herdId);
        if (herd == null) {
            return null;
        }
        // 物种核验：兽群账上是 A 种，杀的是 B 种，不记
        String species = net.guzhenren.tianyi.TianyiBestiary.idOf(victim);
        // 物种核验：与这处兽群同种，或至少是本名录里的真实蛊兽
        boolean sameSpecies = herd.beastId.equals(species)
                || net.guzhenren.tianyi.will.BeastCatalog.get(species) != null;
        if (!sameSpecies) {
            return null;
        }
        // 只记战果，不扣种群
        herd.noteSlain(1, now);
        state.core().note("herd.slain " + herd.id + " 已实体化个体+" + 1
                + "（种群不再重复扣）");
        state.setDirty();
        return herd;
    }

    // ---------------------------------------------------------------- 查询

    public static List<String> lines(WillState state, long now) {
        List<String> out = new ArrayList<>();
        out.add("野生兽群：共 " + state.herds().size() + " 处"
                + "（繁盛 " + state.herds().countState(BeastHerd.State.THRIVING)
                + " / 受压 " + state.herds().countState(BeastHerd.State.STRAINED)
                + " / 崩溃 " + state.herds().countState(BeastHerd.State.COLLAPSED) + "）"
                + " 总种群 " + String.format("%.1f", state.herds().totalPopulation()));
        for (BeastHerd h : state.herds().all()) {
            out.add("  " + h.describe());
        }
        if (state.herds().size() == 0) {
            out.add("  （还没有兽群：玩家所在区域被观测到时会按栖息地开一处）");
        }
        out.add("  规则：生成一只就少一只；猎杀掉种群、见底崩溃、栖息地差会迁徙");
        return out;
    }

    // ---------------------------------------------------------------- 工具

    private static BlockPos shift(ServerLevel level, BlockPos from, long seed) {
        double angle = (seed % 360L) * Math.PI / 180.0;
        int dist = 64 + (int) (Math.abs(Math.sin(seed * 0.00021)) * 96);
        int x = from.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = from.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos p = new BlockPos(x, y, z);
        return level.isLoaded(p) ? p : null;
    }

    private static ServerLevel levelByKey(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(dim)) {
                return l;
            }
        }
        return null;
    }

    private static BlockPos unpack(long packed) {
        int x = (int) (packed >> 38);
        int z = (int) ((packed << 26) >> 38);
        int y = (int) ((packed << 52) >> 52);
        return new BlockPos(x, y, z);
    }
}
