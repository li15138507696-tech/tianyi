package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.guzhenren.tianyi.resource.DestinySeek;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.guzhenren.tianyi.resource.SiteBudget;
import net.guzhenren.tianyi.resource.SiteTerrain;
import net.guzhenren.tianyi.will.DestinyCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 宿命蛊<b>唯一的取得路径</b>：流派地形的守护资源地 + 亲手击败该点守护兽 + 真实材料。
 *
 * <h2>为什么需要它</h2>
 * <p>3.13.0 起宿命蛊只能在管理员 {@code custody recover} 时掉到地上，正常玩法根本拿不到；
 * 而「管理员扔一只」既不是玩法，也没有任何前置与防刷。这条路径补的是<b>正常玩法入口</b>，
 * 不是削弱保管纪律：实体化仍然只有 {@link DestinyCustodyService#materializeAt} 一条路，
 * 全存档仍然只有一只。
 *
 * <h2>流程（顺序固定，对应 {@link DestinySeek#firstReject}）</h2>
 * <ol>
 *   <li><b>点在不在</b>：玩家 24 格内（{@link SiteBudget#WITNESS_RADIUS}）有已物化、非终态、
 *       容器仍然属于该点的 {@link FiniteSite.Kind#GUARDED} 点（用 {@link SitePlacement#clue} 核对），
 *       取最近的一个；同距按站点 UUID 定序，避免「先到手的先赢」这种不确定性。</li>
 *   <li><b>地形</b>：该点所在地形必须是四道流派地形之一，且该点绑定的守护兽必须正是
 *       {@link SiteTerrain#GUARDIAN_BY_BIOME} 为这片地形指定的那一只。</li>
 *   <li><b>守护兽</b>：账本上绑着守护兽 UUID，且 {@link #onGuardianDeath} 记着「被<b>本人</b>亲手打死」。
 *       只认 UUID 匹配的死亡事件；实体缺失/未加载<b>不算</b>打死，也永远不会被当成打死。</li>
 *   <li><b>材料</b>：{@link DestinySeek#COSTS} 全部够数，从背包扣除（{@code setItem(slot, ItemStack.EMPTY)}
 *       是消耗，不是发放）。</li>
 *   <li><b>账本</b>：宿命保管账本可写、且没有正品。</li>
 *   <li><b>幂等</b>：{@link DestinySeek#key} 没被用过。</li>
 * </ol>
 *
 * <p>全部满足后：先做<b>权威实体化事务</b>（{@link DestinyCustodyService#materializeAt}，掉落物落在
 * 该点容器上方），再扣材料，再记幂等键、写一条本人见闻录（{@code JournalStore} + 既有同步）。
 * 顺序的理由写在 {@code docs/3.14.2-取得路径与站点命令.md}：实体化是唯一不可重复的原子动作，
 * 它先落；材料在扣除前已校验完，扣的是纯内存背包，不会「扣了却不给」。
 *
 * <h2>它刻意不做的事</h2>
 * <ul>
 *   <li>不往玩家背包放任何东西（实物落世界；背包只被扣材料）。</li>
 *   <li>不在世界层发聊天消息：结果与原因由命令层转述（既有审计硬约束）。</li>
 *   <li>不扫世界、不强制加载区块、不给守护兽任何击杀奖励、不补生守护兽。</li>
 *   <li>不按玩家名/寿命/转数改命运，不提供跳过前置的开关。</li>
 * </ul>
 *
 * <p><b>未实机</b>：真实群系是否命中、真实守护兽死亡事件、真实背包扣除与真实掉落物，
 * 都只能在游戏里看见；离线只能证明判定顺序、材料口径、幂等键与静态接线。
 */
public final class DestinySeekService {
    private DestinySeekService() {
    }

    /** 一次取得尝试的结果。世界层不回话，命令层照这个结果转述。 */
    public record Result(boolean ok, DestinySeek.Reject reject, String detail, List<String> lines) {
        static Result fail(DestinySeek.Reject reject, String detail) {
            return new Result(false, reject, detail, List.of());
        }

        static Result done(List<String> lines) {
            return new Result(true, DestinySeek.Reject.NONE, "", List.copyOf(lines));
        }
    }

    // ---------------------------------------------------------------- 击杀证据

    /**
     * 守护兽死亡：<b>只认</b>「死亡实体是本站绑定的那只」且「玩家亲手打死」的那次事件。
     *
     * <p>与 {@link FiniteSiteService#onGuardianDeath} 同一套证据门槛（同一 MARKER、同一站点账本、
     * 同一 UUID 匹配），区别只是这里要把击杀者记进取得路径自己的账本——因为等玩家来取的时候，
     * 实体早就被移除（{@code UNLOADED_OR_ABSENT}），现场已经查不出来了。
     *
     * <p>环境伤害、别的生物打死、无主伤害（{@code getEntity()} 为空）一律不记：
     * 那不是「亲手击败」。记不进去时什么都不改，绝不猜。
     */
    public static void onGuardianDeath(Entity dead, DamageSource source) {
        if (dead == null || source == null) return;
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        if (!dead.getPersistentData().hasUUID(SitePlacement.MARKER)) return;
        UUID siteId = dead.getPersistentData().getUUID(SitePlacement.MARKER);
        FiniteSiteState ledger = FiniteSiteState.get(level.getServer());
        if (!ledger.mutationReady()) return;
        FiniteSite site = ledger.sites().get(siteId);
        if (site == null || site.kind() != FiniteSite.Kind.GUARDED || site.terminal()) return;
        if (!site.matchesGuardianDeath(dead.getUUID())) return;
        DestinySeekState state = DestinySeekState.get(level.getServer());
        if (state.recordKill(siteId, killer.getUUID().toString())) {
            state.setDirty();
        }
    }

    // ---------------------------------------------------------------- 取得

    /** 玩家入口。所有校验都在服务端，返回的东西只用于命令层回话。 */
    public static Result seek(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (player == null || server == null || !(player.level() instanceof ServerLevel level)) {
            return Result.fail(DestinySeek.Reject.NO_PLAYER, DestinySeek.explain(DestinySeek.Reject.NO_PLAYER));
        }
        FiniteSiteState ledger = FiniteSiteState.get(server);
        DestinySeekState seekState = DestinySeekState.get(server);
        DestinyCustodyState custody = DestinyCustodyService.state(server);

        FiniteSite site = nearestGuardedSite(level, ledger, player);
        boolean guarded = false, stream = false, matches = false, bound = false, slain = false;
        String biome = "", guardian = "";
        if (site != null) {
            BlockPos at = new BlockPos(site.x(), site.y(), site.z());
            biome = SitePlacement.biomeId(level, at);
            guardian = SitePlacement.selection(site).map(SitePlacement.Selection::guardian).orElse("");
            guarded = site.kind() == FiniteSite.Kind.GUARDED;
            stream = SiteTerrain.hasTerrainGuardian(biome);
            matches = DestinySeek.terrainGuardianMatches(biome, guardian);
            bound = !site.guardians().isEmpty();
            slain = player.getUUID().toString().equals(seekState.killerOf(site.siteId()));
        }
        boolean materials = DestinySeek.materialsComplete(DestinySeek.COSTS,
                id -> countOf(player, id));
        boolean writable = custody.mutationReady() && !custody.custody().quarantined();
        boolean empty = !custody.custody().exists();
        boolean claimed = site != null
                && seekState.claimed(DestinySeek.key(site.siteId(), player.getUUID()));

        DestinySeek.Facts facts = new DestinySeek.Facts(true, site != null, guarded, stream, matches,
                bound, slain, materials, writable, empty, claimed);
        DestinySeek.Reject reject = DestinySeek.firstReject(facts);
        if (reject != DestinySeek.Reject.NONE) {
            return Result.fail(reject, failureText(reject, player, custody, ledger, site));
        }

        // 权威事务：先实体化。账本说已有正品就一定会在这里被拒，世界里不会多出任何东西。
        BlockPos at = new BlockPos(site.x(), site.y(), site.z());
        String note = "取得：守护兽已倒（" + biome + "），供物 " + costText();
        DestinyCustody.Result issued = DestinyCustodyService.materializeAt(player, note, level,
                at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
        if (!issued.ok()) {
            // 账本在判定与实体化之间被改动（同一条服务端线程里极罕见）：不扣材料、不记幂等键。
            return Result.fail(DestinySeek.Reject.CUSTODY_LOCKED,
                    "实体化被拒绝（" + issued.reject() + "）：" + issued.detail() + "；材料未扣除");
        }

        // 材料在判定阶段已确认足够；这里是纯内存扣除，不会写回任何物品。
        List<String> consumed = consume(player);

        // 幂等键先于见闻写入：即使见闻那条写重了，第二次也会被键拦住。
        String key = DestinySeek.key(site.siteId(), player.getUUID());
        boolean keyed = seekState.claim(key);
        String dim = level.dimension().location().toString();
        long now = level.getGameTime();
        boolean noted = JournalStore.noteDestinySeek(player, site.siteId().toString(),
                dim, player.blockPosition().asLong(), now);
        if (noted) {
            net.guzhenren.tianyi.net.JournalNet.sync(player);
        }

        List<String> lines = new ArrayList<>();
        lines.add("宿命蛊已实体化：" + issued.detail());
        lines.add("实物落点：" + dim + " " + (at.getX() + 0.5) + " " + (at.getY() + 1.0) + " "
                + (at.getZ() + 0.5) + "（该点容器上方的掉落物，拾取才进背包）");
        lines.add("已消耗：" + String.join("、", consumed) + "（材料是平衡参数，见 docs/3.14.2）");
        lines.add("幂等键：" + key + (keyed ? "（已记下：同一地点同一人只发放一次）"
                : "（幂等键已满，本次仍受「账本已有正品」保护）"));
        lines.add(noted ? "见闻录：已写入本人一条（既有 JournalStore + 既有同步，无新 payload）"
                : "见闻录：本次没有新增条目（同 key 已存在或已过期）");
        lines.add("守护兽：" + guardian + " @ " + biome
                + "；这只蛊仍然只有一只，保管、隔离与阶段语义一概未改");
        return Result.done(lines);
    }

    // ---------------------------------------------------------------- 现场取证

    /**
     * 取玩家脚下最近的、确实还物化着的守护资源点（24 格内）。
     *
     * <p>「确实」用 {@link SitePlacement#clue} 核对：方块/容器必须仍然属于这个点（记号、类型、指纹都对），
     * 容器被替换掉、槽位没物化、区块没加载的一律不算。同距按站点 UUID 定序，结果确定。
     * 只在玩家主动触发时调用，不进任何 tick 路径。
     */
    private static FiniteSite nearestGuardedSite(ServerLevel level, FiniteSiteState ledger,
            ServerPlayer player) {
        String dim = level.dimension().location().toString();
        double limit = (double) SiteBudget.WITNESS_RADIUS * SiteBudget.WITNESS_RADIUS;
        double best = limit;
        FiniteSite found = null;
        for (FiniteSite site : ledger.sites().values()) {
            if (site.terminal() || site.kind() != FiniteSite.Kind.GUARDED) continue;
            if (!dim.equals(site.dimension())) continue;
            BlockPos at = new BlockPos(site.x(), site.y(), site.z());
            if (SitePlacement.clue(level, site, at).isEmpty()) continue;
            double distance = player.distanceToSqr(site.x() + 0.5, site.y() + 0.5, site.z() + 0.5);
            if (distance > best) continue;
            if (found != null && distance == best && site.siteId().compareTo(found.siteId()) >= 0) continue;
            best = distance;
            found = site;
        }
        return found;
    }

    /** 失败原因：判定给的通用说法，再补上这一次的具体证据（差哪几样、账本为什么不可写）。 */
    private static String failureText(DestinySeek.Reject reject, ServerPlayer player,
            DestinyCustodyState custody, FiniteSiteState ledger, FiniteSite site) {
        String base = DestinySeek.explain(reject);
        return switch (reject) {
            case MATERIALS_MISSING -> {
                String lack = DestinySeek.shortfall(DestinySeek.COSTS, id -> countOf(player, id));
                yield base + "：" + (lack.isEmpty() ? "材料清单不可用" : lack);
            }
            case CUSTODY_LOCKED -> base + "：" + (custody.mutationReady()
                    ? custody.custody().quarantineReason() : custody.failureReason());
            case NO_SITE -> ledger.mutationReady() ? base
                    : base + "；资源点账本当前不可写：" + ledger.failureReason();
            case ALREADY_CLAIMED -> site == null ? base
                    : base + "（站点 " + site.siteId() + "）";
            default -> base;
        };
    }

    // ---------------------------------------------------------------- 材料

    /** 背包里某件材料的现有总数（只读）。 */
    private static int countOf(ServerPlayer player, String itemId) {
        Item item = itemOf(itemId);
        if (player == null || item == null) return 0;
        var inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 真正扣材料。调用前 {@link DestinySeek#materialsComplete} 必须已为真。
     *
     * <p>扣法只有两种：整格清空（{@code setItem(slot, ItemStack.EMPTY)}）与同格减少（{@code shrink}），
     * 都不发放任何东西。返回实际扣掉的清单，供命令层回话。
     */
    private static List<String> consume(ServerPlayer player) {
        List<String> consumed = new ArrayList<>();
        var inventory = player.getInventory();
        for (DestinySeek.Cost cost : DestinySeek.COSTS) {
            Item item = itemOf(cost.item());
            if (item == null) continue;
            int remaining = cost.count();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || !stack.is(item)) continue;
                int take = Math.min(remaining, stack.getCount());
                remaining -= take;
                if (take >= stack.getCount()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                } else {
                    stack.shrink(take);
                }
            }
            consumed.add(cost.cn() + " ×" + (cost.count() - remaining));
        }
        inventory.setChanged();
        return consumed;
    }

    private static Item itemOf(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    private static String costText() {
        return String.join(" + ", DestinySeek.describeCosts());
    }
}
