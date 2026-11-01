package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.guzhenren.tianyi.TianyiMod;
import net.guzhenren.tianyi.journal.SiteNotice;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.guzhenren.tianyi.resource.SiteBudget;
import net.guzhenren.tianyi.resource.SiteCatalog;
import net.guzhenren.tianyi.resource.SiteTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 有限资源点的<b>世界侧调度器</b>：每 200 刻在服务端主线程上跑一轮，
 * 把 {@link SiteBudget} 的纯判断翻译成真实的区块/方块/实体操作。
 *
 * <h2>它做什么</h2>
 * <ol>
 *   <li><b>对账</b>：只处理<b>已加载</b>的主世界点。真实方块/容器是唯一权威——
 *       数出来的比账本少，就是被取走了（{@link FiniteSite#observeRemaining}）；
 *       载体或根基没了，就 {@link FiniteSite#destroyCarrier()} 永久结束。
 *       不补货、不按账本发货、不因为“少了”就再生成一批。</li>
 *   <li><b>物化</b>：未完成的槽位逐轮补完（每轮每个点最多一个槽位），
 *       重载后不会把整批重投一遍；一切写世界都走 {@link SitePlacement}。</li>
 *   <li><b>再生</b>：只有植物、只有已加载、只有根基与环境合法、只有还有空槽位时，
 *       才累计 200 刻；满 72000 刻由模型长出 1 份，终身预算由模型封顶。</li>
 *   <li><b>生成</b>：每轮最多 4 个候选预筛、最多落地 1 点；8×8 区块至多 1 点；
 *       全局上限 64；<b>从不</b>强制加载区块，没有玩家时不在野地里凭空造点。</li>
 *   <li><b>见闻</b>：只把玩家亲眼所见交给 {@link SiteEvidence}（复用既有同步，不新增 payload）。</li>
 * </ol>
 *
 * <h2>它刻意不做的事</h2>
 * <ul>
 *   <li>不读 NPC / 势力 / 任务 / 关系 / 追捕的任何类；不调用外层 {@code com.guzhenrenlibrary.*}。</li>
 *   <li>不给击杀奖励：守护兽的掉落完全由本体自己的死亡 procedure 决定。</li>
 *   <li>不补生守护兽：实体缺失/卸载一律 UNKNOWN，既不算死也不再生成一只。</li>
 *   <li>不设竞技场边界、不传送、不押注、不因离场惩罚。</li>
 *   <li>不把后台评分或全图信息写给玩家。</li>
 * </ul>
 *
 * <p><b>未实机验证</b>：本类的世界行为（真实区块、真实容器、真实守护兽）只能在游戏里看到；
 * 离线能证明的只有 {@link SiteBudget}/{@link SiteNotice} 的纯决策与静态接线（见 {@code _pack/audit_sites.py}）。
 */
public final class FiniteSiteService {
    /** 站点 UUID → 最近一次动手的玩家（破坏/开桶）。只用于见闻归属，上限是站点数。 */
    private static final Map<UUID, UUID> PENDING_WITNESS = new HashMap<>();
    /** 一次性告警去重，避免每 10 秒刷同一条日志。 */
    private static final Set<String> REPORTED = new HashSet<>();
    private static String lastFailure = "";

    private FiniteSiteService() {}

    public static void started() {
        PENDING_WITNESS.clear();
        REPORTED.clear();
        lastFailure = "";
    }

    /** 停服：只落一次盘并清静态状态。账本本身由 SavedData 负责，这里不伪造写入。 */
    public static void stopping(MinecraftServer server) {
        if (server != null) FiniteSiteState.get(server).setDirty();
        PENDING_WITNESS.clear();
        REPORTED.clear();
        lastFailure = "";
        SiteEvidence.forget();
    }

    /**
     * 最近一次<b>被去重记录</b>的服务失败原因（空串＝本进程还没记过失败）。
     *
     * <p>3.14.2 管理命令 {@code /tianyi site status} 用；只读，不改任何状态，
     * 也不把失败原因写进玩家可见的任何通道（命令层回话由命令自己负责）。
     */
    public static String lastFailure() {
        return lastFailure;
    }

    // ---------------------------------------------------------------- 主循环

    public static void tick(MinecraftServer server) {
        if (server == null || !SiteSettings.active()) return;
        // 统一用主世界 game time（与既有 EncounterService/JournalNet 同一口径），不另起计时器。
        ServerLevel level = server.overworld();
        if (!SiteBudget.due(level.getGameTime())) return;
        FiniteSiteState ledger = FiniteSiteState.get(server);
        if (!ledger.mutationReady()) {
            report("ledger", "有限资源点账本处于隔离/未识别状态：本轮不生成、不发货、不改写（" + ledger.failureReason() + "）");
            return;
        }
        try {
            reconcile(level, ledger);
            generate(level, ledger);
        } catch (RuntimeException failure) {
            // 单轮失败不吞证据、不回滚已提交的槽位；下一轮重新对账。
            report("tick:" + failure.getClass().getName() + ":" + failure.getMessage(),
                    "有限资源点本轮检查中止（不补货、不重投）: " + failure);
        }
    }

    // ---------------------------------------------------------------- 对账

    private static void reconcile(ServerLevel level, FiniteSiteState ledger) {
        String dim = level.dimension().location().toString();
        for (FiniteSite site : new ArrayList<>(ledger.sites().values())) {
            if (!dim.equals(site.dimension())) continue;
            if (site.phase() == FiniteSite.Phase.DEPLETED || site.phase() == FiniteSite.Phase.DESTROYED) {
                // 采空/毁掉之后退役成墓碑：记号留着，同一区域不再刷第二处。
                if (ledger.retire(site.siteId())) PENDING_WITNESS.remove(site.siteId());
                continue;
            }
            if (site.terminal()) continue; // QUARANTINED：停发，等人工处理，不自动复活
            if (!level.hasChunkAt(new BlockPos(site.x(), site.y(), site.z()))) continue; // 未加载：不计时、不判定
            try {
                reconcileOne(level, ledger, site);
            } catch (RuntimeException failure) {
                quarantine(ledger, site.siteId());
            }
        }
        evidence(level, ledger);
    }

    private static void reconcileOne(ServerLevel level, FiniteSiteState ledger, FiniteSite site) {
        Optional<SitePlacement.Selection> found = SitePlacement.selection(site);
        if (found.isEmpty()) {
            quarantine(ledger, site.siteId()); // 绑定指纹坏了：停点，不猜材料
            return;
        }
        SitePlacement.Selection selection = found.get();

        if (site.phase() == FiniteSite.Phase.RESERVED || site.phase() == FiniteSite.Phase.MATERIALIZING) {
            finishMaterialization(level, ledger, site, selection);
            return;
        }

        SitePlacement.Observation observation = SitePlacement.observe(level, site, selection);
        if (!observation.loaded()) return;
        if (!observation.carrierIntact()) {
            loseCarrier(level, ledger, site);
            return;
        }
        if (observation.remaining() < 0) {
            quarantine(ledger, site.siteId()); // 未知去向/意外多出的东西：不补货，停点
            return;
        }

        FiniteSite current = site;
        int before = site.inventory().physicalRemaining();
        if (observation.remaining() != before) {
            current = site.observeRemaining(observation.remaining(), false);
            if (!ledger.replace(site, current)) return;
            // 见闻用「取走之前」的快照：最后一次采集会直接把点变成 DEPLETED，
            // 但玩家确实看见了自己取走的那一份，这一条不能丢。
            if (observation.remaining() < before) harvested(level, site, observation.remaining());
        }

        if (current.kind() == FiniteSite.Kind.GUARDED) {
            // 只看“还是不是我们的那只”。缺失/卸载什么都不做，绝不补生。
            if (SitePlacement.guardianStatus(level, current) == SitePlacement.GuardianStatus.CONFLICT) {
                quarantine(ledger, current.siteId());
            }
            return;
        }

        if (current.kind() == FiniteSite.Kind.PLANT
                && (current.phase() == FiniteSite.Phase.ACTIVE || current.phase() == FiniteSite.Phase.DORMANT)) {
            boolean loaded = level.hasChunkAt(new BlockPos(current.x(), current.y(), current.z()));
            boolean environment = SitePlacement.growthConditions(level, current, selection);
            if (!SiteBudget.mayAccrueGrowth(loaded, environment, current.rootIntact(), freeSlot(level, current))) return;
            FiniteSite next = current.growLoaded(SiteSettings.CHECK_INTERVAL_TICKS, true, environment);
            if (next != current && !ledger.replace(current, next)) {
                report("growth:" + current.siteId(), "有限资源点的生长记账与预期不一致：本轮放弃，下一轮重新对账。");
            }
        }
    }

    /** 逐槽位补完物化。每轮每个点最多一个槽位；植物挂账增长也在这里落地。 */
    private static void finishMaterialization(ServerLevel level, FiniteSiteState ledger, FiniteSite site,
            SitePlacement.Selection selection) {
        SitePlacement.Observation observation = SitePlacement.observe(level, site, selection);
        if (!observation.loaded()) return;
        if (!observation.carrierIntact()) {
            loseCarrier(level, ledger, site);
            return;
        }
        if (observation.remaining() < 0) {
            quarantine(ledger, site.siteId());
            return;
        }

        boolean growth = site.kind() == FiniteSite.Kind.PLANT && site.inventory().unmaterialized() == 1
                && site.slots().stream().allMatch(FiniteSite.Slot::materialized);
        if (growth) {
            // 生长出来的一份必须落进一个已经空出来的槽位；没有空位就先挂着，不凭空造位置。
            for (FiniteSite.Slot slot : site.slots()) {
                BlockPos pos = new BlockPos(slot.x(), slot.y(), slot.z());
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) continue;
                SitePlacement.Result result = SitePlacement.materialize(level, ledger, site.siteId(),
                        slot.index(), selection, null, null);
                if (result == SitePlacement.Result.APPLIED || result == SitePlacement.Result.ALREADY_RECORDED) return;
                if (result == SitePlacement.Result.REJECTED) continue;
                return; // PAUSED / QUARANTINED：本轮到此为止
            }
            return;
        }

        for (FiniteSite.Slot slot : site.slots()) {
            if (slot.materialized()) continue;
            SitePlacement.materialize(level, ledger, site.siteId(), slot.index(), selection, null, null);
            return;
        }
    }

    private static boolean freeSlot(ServerLevel level, FiniteSite site) {
        for (FiniteSite.Slot slot : site.slots()) {
            if (!slot.materialized()) continue;
            BlockPos pos = new BlockPos(slot.x(), slot.y(), slot.z());
            if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir()) return true;
        }
        return false;
    }

    private static void loseCarrier(ServerLevel level, FiniteSiteState ledger, FiniteSite site) {
        FiniteSite next = site.destroyCarrier();
        if (!ledger.replace(site, next)) return;
        PENDING_WITNESS.remove(site.siteId());
        // 只有当时在场的人知道这里绝了；没人在场就没人知道。
        for (ServerPlayer player : level.players()) {
            if (nearSite(player, site)) SiteEvidence.carrierLost(player, site);
        }
    }

    private static void harvested(ServerLevel level, FiniteSite site, int remaining) {
        UUID watcher = PENDING_WITNESS.remove(site.siteId());
        if (watcher != null) {
            ServerPlayer actor = level.getServer().getPlayerList().getPlayer(watcher);
            if (actor != null && level.equals(actor.serverLevel()) && SiteEvidence.harvested(actor, site, remaining)) {
                return;
            }
        }
        // 没有直接归属（例如漏斗/别人开的桶）：只记当时确实在近处的人。
        for (ServerPlayer player : level.players()) {
            if (nearSite(player, site)) SiteEvidence.harvested(player, site, remaining);
        }
    }

    private static boolean nearSite(ServerPlayer player, FiniteSite site) {
        double radius = SiteBudget.WITNESS_RADIUS;
        return player.distanceToSqr(site.x() + 0.5, site.y() + 0.5, site.z() + 0.5) <= radius * radius;
    }

    private static void quarantine(FiniteSiteState ledger, UUID siteId) {
        FiniteSite current = ledger.sites().get(siteId);
        if (current == null || current.terminal()) return;
        try {
            if (ledger.replace(current, current.quarantine())) {
                report("quarantine:" + siteId, "有限资源点因物理状态不可确认而停发（隔离，不补货）: " + siteId);
            }
        } catch (RuntimeException failure) {
            report("quarantine-failed:" + siteId, "有限资源点隔离失败，账本保持不变: " + failure);
        }
    }

    // ---------------------------------------------------------------- 生成

    private static void generate(ServerLevel level, FiniteSiteState ledger) {
        if (!SiteBudget.hasRoom(ledger.sites().size())) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return; // 没有玩家时不在野地里凭空造点
        int placed = 0;
        for (int attempt = 0; attempt < SiteBudget.CANDIDATES_PER_CHECK
                && SiteBudget.placementsLeft(placed); attempt++) {
            ServerPlayer anchor = players.get(level.getRandom().nextInt(players.size()));
            int distance = SiteSettings.MIN_PLAYER_DISTANCE + level.getRandom()
                    .nextInt(SiteSettings.MAX_PLAYER_DISTANCE - SiteSettings.MIN_PLAYER_DISTANCE + 1);
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue; // 不强制加载区块
            if (!SiteBudget.regionAvailable(!ledger.regions().containsKey(
                    SiteBudget.regionKey("minecraft:overworld", x, z)))) continue;
            if (!playersFarEnough(level, x, z)) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos origin = new BlockPos(x, y, z);
            // 先看地形再挑模板（「找材料先找地形」）：候选点所在群系决定这里允许哪些模板。
            String terrain = SitePlacement.biomeId(level, origin);

            SiteCatalog.Candidate candidate = pickTemplate(level, terrain);
            if (candidate == null) {
                // 地形不匹配 = 这个候选位置没有可用模板，换下一个候选；不是全局禁用，也不换地形/材料。
                // 只对「流派地形 / 拿不到群系」告警一次，原版群系不匹配属于正常情况，不刷日志。
                if (terrain.isEmpty() || terrain.startsWith("guzhenren:")) {
                    report("terrain:" + terrain,
                            "有限资源点在群系「" + (terrain.isEmpty() ? "未知" : terrain)
                                    + "」没有已启用的模板，本轮跳过该候选（模板仍启用，换位置再试）。");
                }
                continue;
            }
            String material = pick(level, candidate.itemCandidates());
            String guardian = candidate.kind() == FiniteSite.Kind.GUARDED
                    ? SiteTerrain.guardianFor(terrain, candidate.guardianCandidates()) : "";
            SitePlacement.Selection selection = new SitePlacement.Selection(candidate.template(), material, guardian);
            if (!SitePlacement.verified(level, selection)) {
                // 运行时核验没过：本轮不生成。配置保持启用时会持续重试——不做永久禁用。
                report("verify:" + candidate.template(),
                        "有限资源点模板「" + candidate.template() + "」运行时核验未通过（注册/方块类/loot 不符），本轮不生成。");
                continue;
            }
            int units = SiteBudget.units(candidate.minimumInitial(), candidate.maximumInitial(),
                    level.getRandom().nextInt(candidate.maximumInitial() - candidate.minimumInitial() + 1));
            Optional<FiniteSite> reserved = SitePlacement.candidate(level, origin, selection, units, null, null);
            if (reserved.isEmpty()) continue;
            if (!ledger.reserve(reserved.get())) continue;
            placed++;
            // 只落地第一个槽位；剩下的逐轮补完，重载后不会重投全批。
            SitePlacement.materialize(level, ledger, reserved.get().siteId(), 0, selection, null, null);
        }
    }

    /** 只从「地形允许 + 已启用 + 证据通过」的模板里挑。地形不允许的模板直接不参与，不是全局禁用。
     * 纯映射在 {@link SiteCatalog#templatesForBiome} 与 {@link SiteTerrain#biomeAllowed}，可离线测试。 */
    private static SiteCatalog.Candidate pickTemplate(ServerLevel level, String biomeId) {
        List<SiteCatalog.Candidate> enabled = new ArrayList<>();
        for (SiteCatalog.Candidate candidate : SiteCatalog.templatesForBiome(biomeId)) {
            if (candidate.verifiedForEnablement()
                    && SiteSettings.templateEnabled(candidate.template(), candidate.kind())) enabled.add(candidate);
        }
        return enabled.isEmpty() ? null : enabled.get(level.getRandom().nextInt(enabled.size()));
    }

    private static String pick(ServerLevel level, List<String> values) {
        return values.isEmpty() ? "" : values.get(level.getRandom().nextInt(values.size()));
    }

    private static boolean playersFarEnough(ServerLevel level, int x, int z) {
        double min = SiteSettings.MIN_PLAYER_DISTANCE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (x + 0.5);
            double dz = player.getZ() - (z + 0.5);
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- 见闻

    private static void evidence(ServerLevel level, FiniteSiteState ledger) {
        if (level.players().isEmpty()) return;
        String dim = level.dimension().location().toString();
        List<FiniteSite> live = new ArrayList<>();
        for (FiniteSite site : ledger.sites().values()) {
            if (!site.terminal() && dim.equals(site.dimension())) live.add(site);
        }
        if (live.isEmpty()) return;
        for (ServerPlayer player : level.players()) {
            for (FiniteSite site : live) {
                if (!SiteNotice.witnessed(player.distanceToSqr(
                        site.x() + 0.5, site.y() + 0.5, site.z() + 0.5))) continue;
                SiteEvidence.arrived(player, site);
                SiteEvidence.guardianSeen(player, site);
            }
        }
    }

    // ---------------------------------------------------------------- 事件入口

    /**
     * 玩家破坏方块：如果他动的是一处资源点的实物，记下“是谁”，等下一轮对账把减少量算在他头上。
     * 这里<b>不改世界、不改账本、不取消事件</b>——取消由别的模组决定，我们只观察。
     */
    public static boolean onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return remember(player, level, pos);
    }

    /** 玩家开桶/点方块：同上，只用于把取物归属到人。 */
    public static boolean onBlockUsed(ServerPlayer player, BlockPos pos) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return false;
        return remember(player, level, pos);
    }

    private static boolean remember(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null || !SiteSettings.active()) return false;
        FiniteSiteState ledger = FiniteSiteState.get(level.getServer());
        if (!ledger.mutationReady()) return false;
        FiniteSite site = siteAt(ledger, level, pos);
        if (site == null || !nearSite(player, site)) return false;
        PENDING_WITNESS.put(site.siteId(), player.getUUID());
        return true;
    }

    private static FiniteSite siteAt(FiniteSiteState ledger, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        for (FiniteSite site : ledger.sites().values()) {
            if (site.terminal() || !dim.equals(site.dimension())) continue;
            for (FiniteSite.Slot slot : site.slots()) {
                if (slot.materialized() && slot.x() == pos.getX() && slot.y() == pos.getY()
                        && slot.z() == pos.getZ()) return site;
            }
        }
        return null;
    }

    /**
     * 守护兽死亡：<b>只有</b> UUID 匹配本站绑定的那次死亡算确认。
     *
     * <p>确认之后什么都不改：不补生、不给奖励、不动库存。掉落完全交给本体自己的死亡 procedure。
     * 缺失或未加载的实体走不到这里，也永远不会被当成“已死”。
     */
    public static void onGuardianDeath(Entity entity) {
        if (entity == null || !SiteSettings.active()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.getPersistentData().hasUUID(SitePlacement.MARKER)) return;
        UUID siteId = entity.getPersistentData().getUUID(SitePlacement.MARKER);
        FiniteSiteState ledger = FiniteSiteState.get(level.getServer());
        FiniteSite site = ledger.sites().get(siteId);
        if (site == null || site.kind() != FiniteSite.Kind.GUARDED) return;
        if (!site.matchesGuardianDeath(entity.getUUID())) return;
        double radius = SiteBudget.WITNESS_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ()) <= radius * radius) {
                SiteEvidence.guardianSlain(player, site);
            }
        }
    }

    // ---------------------------------------------------------------- 日志

    private static void report(String key, String message) {
        if (REPORTED.size() > 64) REPORTED.clear();
        if (key.equals(lastFailure) || !REPORTED.add(key)) return;
        lastFailure = key;
        TianyiMod.LOGGER.warn("[天意] {}", message);
    }
}
