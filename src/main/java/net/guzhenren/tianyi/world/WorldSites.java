package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianyi.will.ResourceSite;
import net.guzhenren.tianyi.will.WillConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * 资源产地的世界侧：把 {@link ResourceSite} 接到真实方块世界里。
 *
 * <h2>产出模型</h2>
 * 产地不是「一次掉落」，而是一处有储量的地方：
 * <ol>
 *   <li>有人靠近（半径内）且该产地有货 → 按 {@link ResourceSite#harvest} 采出若干，
 *       储量<b>真的减少</b>，然后把货放在地上（谁都能捡）。</li>
 *   <li>没人靠近就不会产出，储量顶到上限为止。</li>
 *   <li>采尽且没有恢复 → 变成 {@link ResourceSite.State#DEPLETED}（地荒了）。</li>
 *   <li>天意可以把玩家的产地「搅乱道痕」——储量被打散、恢复变慢，留可调查的原因。</li>
 * </ol>
 *
 * <h2>三条纪律</h2>
 * <ul>
 *   <li><b>不凭空生货</b>：吐出的数量完全由 {@code stock} 决定，所以刷不出来。</li>
 *   <li><b>不进背包</b>：只往地上放，谁捡到算谁的。</li>
 *   <li><b>不与本体结算重复</b>：只操作本模组自己的产地储量，不碰本体掉落表。</li>
 * </ul>
 */
public final class WorldSites {
    private WorldSites() {
    }

    /** 产出周期（刻）。[平衡参数] */
    public static final int PRODUCE_PERIOD = 2400;
    /** 单次最多放出的数量。[平衡参数] */
    private static final int PRODUCE_BATCH = 2;
    /** 产地一次性储量上限。[平衡参数] */
    private static final double SITE_MAX_STOCK = 12.0;
    /** 产地每游戏日恢复量。[平衡参数] */
    private static final double SITE_REGEN_PER_DAY = 2.0;
    /** 产地半径。[平衡参数] */
    public static final double SITE_RADIUS = 24.0;
    /** 物流运输时长（刻）。[平衡参数] */
    public static final long CARGO_TRANSIT_TICKS = 2400L;

    /** 野外蛊材池（id 已从本体 tags 核对，见 WorldExec）。 */
    private static final String[] WILD_GU = {
            "guzhenren:wei_lian_hua_cun_guang_yin",
            "guzhenren:wei_lian_hua_yu_she_gu",
            "guzhenren:wei_lianhua_shang_gu",
            "guzhenren:yuan_lao_gu_1",
    };
    /** 传承蛊方池。 */
    private static final String[] GU_FANG = {
            "guzhenren:suoshigu_gu_fang",
            "guzhenren:pan_shi_gu_gu_fang",
            "guzhenren:aibieligufang",
    };

    // ---------------------------------------------------------------- 创建

    /**
     * 在某个位置开一处产地（扶持渠道的落地形式）。
     *
     * @return 新产地；失败返回 null（位置未加载 / 池子异常 / 数量已满）
     */
    public static ResourceSite create(WillState state, ServerLevel level, BlockPos pos,
                                      ResourceSite.Kind kind, long now) {
        if (!EncounterSettings.legacyResources()) return null;
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        String dim = level.dimension().location().toString();
        // 同一处不重复开
        if (state.sites().at(dim, pos.asLong(), SITE_RADIUS) != null) {
            return null;
        }
        String id = pickId(kind, now);
        if (id.isEmpty()) {
            return null;
        }
        String siteId = "site-" + now + "-" + dim.replace(':', '_') + "-" + (pos.asLong() & 0xFFFF);
        ResourceSite site = new ResourceSite(siteId, kind, dim, pos.asLong(), SITE_RADIUS,
                id, SITE_MAX_STOCK, SITE_REGEN_PER_DAY, now);
        if (state.sites().add(site) == null) {
            return null;
        }
        state.setDirty();
        state.core().note("site.open " + siteId + " kind=" + kind + " id=" + id + " @dim=" + dim);
        return site;
    }

    private static String pickId(ResourceSite.Kind kind, long seed) {
        String[] pool = kind == ResourceSite.Kind.GU_FANG ? GU_FANG : WILD_GU;
        for (int i = 0; i < pool.length; i++) {
            String id = pool[(int) Math.floorMod(seed + i, pool.length)];
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item != null) {
                return id; // 只接受注册表里真的存在的 id
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- 产出

    /**
     * 推进一轮产出（每 {@link #PRODUCE_PERIOD} 刻调一次）。
     *
     * @return 本轮放到地上的总数
     */
    public static int produce(MinecraftServer server, WillState state, long now, Logger log) {
        if (!EncounterSettings.legacyResources()) return 0;
        // 恢复：按游戏日回补（24000 刻 = 1 游戏日）
        double days = (double) PRODUCE_PERIOD / 24_000.0;
        state.sites().regen(days);

        int spawned = 0;
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            List<ResourceSite> sites = state.sites().active(dim);
            if (sites.isEmpty()) {
                continue;
            }
            for (ResourceSite site : sites) {
                if (!site.harvestable()) {
                    continue;
                }
                BlockPos center = unpack(site.pos);
                if (!level.isLoaded(center)) {
                    continue; // 不为了产出强制加载区块
                }
                ServerPlayer near = nearest(level, center, site.radius);
                if (near == null) {
                    continue; // 没人靠近就不产出，储量攒到上限
                }
                int got = site.harvest(near.getUUID().toString(), PRODUCE_BATCH, now);
                if (got <= 0) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(site.registryId));
                if (item == null) {
                    continue;
                }

                // 有归属、且归属人不是眼前这个「路人」→ 走物流，把货发给他。
                // 这才有「运输」这一段可以被人切断/劫走。
                String ownerId = site.owner();
                if (!ownerId.isEmpty() && !ownerId.equals(near.getUUID().toString())) {
                    ServerPlayer consignee = playerByUuid(server, ownerId);
                    if (consignee != null) {
                        String batchId = "cargo-" + site.id + "-" + now;
                        var batch = state.cargo().open(batchId, dim, site.pos,
                                consignee.level().dimension().location().toString(),
                                consignee.blockPosition().asLong(), ownerId, now,
                                CARGO_TRANSIT_TICKS);
                        if (batch != null) {
                            batch.load(site.registryId, got);
                            state.core().note("cargo.open " + batchId + " goods=" + got);
                            state.setDirty();
                            continue;
                        }
                    }
                }

                ItemEntity drop = new ItemEntity(level,
                        center.getX() + 0.5, center.getY() + 0.6, center.getZ() + 0.5,
                        new ItemStack(item, got));
                drop.setDefaultPickUpDelay();
                drop.getPersistentData().putString("tianyi_site", site.id);
                level.addFreshEntity(drop);
                spawned += got;
                if (log != null && log.isDebugEnabled()) {
                    log.debug("[天意] 产地 {} 产出 {}（余量 {}/{}）", site.id, got,
                            String.format("%.1f", site.stock()),
                            String.format("%.0f", site.maxStock()));
                }
            }
        }
        if (spawned > 0) {
            state.core().note("site.produce total=" + spawned + " @" + now);
            state.setDirty();
        }
        return spawned;
    }

    // ---------------------------------------------------------------- 破坏

    /**
     * 天意搅乱目标名下的产地（原著：剿不掉目标时借天道改易道痕，把资源点搅烂）。
     *
     * @return 被影响的产地数
     */
    public static int scrambleOwnedBy(WillState state, String ownerId, long now) {
        int hit = 0;
        for (ResourceSite s : state.sites().ownedBy(ownerId)) {
            if (s.scramble(0.55, now)) {
                hit++;
            }
        }
        if (hit > 0) {
            state.core().note("site.scramble owner=" + ownerId + " n=" + hit);
            state.setDirty();
        }
        return hit;
    }

    /** 把目标名下的一处产地彻底毁掉（更重的手段；同样留原因）。 */
    public static boolean destroyOneOf(WillState state, String ownerId, String cause, long now) {
        for (ResourceSite s : state.sites().ownedBy(ownerId)) {
            if (s.state() != ResourceSite.State.DESTROYED) {
                s.destroy(cause, now);
                state.core().note("site.destroy " + s.id + " by=" + cause);
                state.setDirty();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 查询

    public static List<String> lines(WillState state, long now) {
        List<String> out = new ArrayList<>();
        out.add("资源产地：共 " + state.sites().size()
                + " 处（正常 " + state.sites().countState(ResourceSite.State.PRODUCTIVE)
                + " / 荒 " + state.sites().countState(ResourceSite.State.DEPLETED)
                + " / 毁 " + state.sites().countState(ResourceSite.State.DESTROYED) + "）");
        for (ResourceSite s : state.sites().all()) {
            out.add("  " + s.describe());
        }
        if (state.sites().size() == 0) {
            out.add("  （还没有产地：扶持渠道会在地表开出第一处）");
        }
        out.add("  规则：储量有上限、按游戏日恢复、采尽变荒、被毁不再生；产出只落地上，不进背包");
        return out;
    }

    // ---------------------------------------------------------------- 工具

    private static ServerPlayer nearest(ServerLevel level, BlockPos center, double radius) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double d = p.blockPosition().distSqr(center);
            if (d <= radius * radius * 2.25 && d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static BlockPos surfaceNear(ServerLevel level, BlockPos center, long seed) {
        double angle = (seed % 360L) * Math.PI / 180.0;
        int dist = 24 + (int) (Math.abs(Math.sin(seed * 0.00037)) * 48);
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) ? pos : null;
    }

    // ---------------------------------------------------------------- 物流结算

    /**
     * 推进物流并结算到点的批次。
     *
     * <p>幂等来自 {@link net.guzhenren.tianyi.will.CargoBatch.Book#tick}：
     * 同一批次只会被交付一次，即使这段时间里服务器重启过。
     *
     * @return 本轮真正送达的批次数
     */
    public static int deliver(MinecraftServer server, WillState state, long now, Logger log) {
        if (!EncounterSettings.legacyResources()) return 0;
        var deliveries = state.cargo().tick(now, id -> playerByUuid(server, id) != null);
        int delivered = 0;
        for (var d : deliveries) {
            var batch = d.batch();
            if (d.delivered()) {
                ServerPlayer p = playerByUuid(server, batch.consignee);
                if (p != null) {
                    ServerLevel level = (ServerLevel) p.level();
                    var pos = p.blockPosition();
                    for (var e : d.goods().entrySet()) {
                        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getKey()));
                        if (item == null) {
                            continue;
                        }
                        ItemEntity drop = new ItemEntity(level,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                new ItemStack(item, e.getValue()));
                        drop.setDefaultPickUpDelay();
                        drop.getPersistentData().putString("tianyi_cargo", batch.id);
                        level.addFreshEntity(drop);
                    }
                    delivered++;
                    state.core().note("cargo.delivered " + batch.id);
                }
            } else {
                // 路线断（收货人不在）→ 货退回起运产地，绝不吞掉
                var site = state.sites().at(batch.fromDim, batch.fromPos, 16.0);
                int count = d.goods().values().stream().mapToInt(Integer::intValue).sum();
                int back = site == null ? 0 : site.returnStock(count, now);
                state.core().note("cargo.blocked " + batch.id + " returned=" + back
                        + (site == null ? " (产地已不存在)" : ""));
            }
        }
        if (!deliveries.isEmpty()) {
            state.setDirty();
            if (log != null && log.isDebugEnabled()) {
                log.debug("[天意] 物流结算 {} 批，送达 {}", deliveries.size(), delivered);
            }
        }
        return delivered;
    }

    private static ServerPlayer playerByUuid(MinecraftServer server, String uuid) {
        try {
            return server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BlockPos unpack(long packed) {
        int x = (int) (packed >> 38);
        int z = (int) ((packed << 26) >> 38);
        int y = (int) ((packed << 52) >> 52);
        return new BlockPos(x, y, z);
    }
}
