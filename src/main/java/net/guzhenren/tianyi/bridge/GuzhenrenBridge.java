package net.guzhenren.tianyi.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.guzhenrenlibrary.faction.FactionDef;
import com.guzhenrenlibrary.faction.FactionPool;
import com.guzhenrenlibrary.faction.FactionStance;
import com.guzhenrenlibrary.faction.FactionTerritory;
import com.guzhenrenlibrary.faction.journey.FactionJourney;
import com.guzhenrenlibrary.faction.journey.FactionJourneys;
import com.guzhenrenlibrary.faction.journey.JourneyKind;
import com.guzhenrenlibrary.identity.IdentityManager;
import com.guzhenrenlibrary.npc.NpcStock;
import com.guzhenrenlibrary.npc.NpcTrade;
import com.guzhenrenlibrary.npc.life.Resident;
import com.guzhenrenlibrary.npc.life.ResidentRegistry;
import com.guzhenrenlibrary.npc.life.ResidentService;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * 本体桥接层：<b>唯一</b>允许 import {@code com.guzhenrenlibrary.*} 的类。
 *
 * <h2>为什么要有这一层</h2>
 * 势力、NPC 生活、任务、身份、商贸全在外层扩展 {@code guzhenren_library}（{@code com.guzhenrenlibrary.*}），
 * 而这些签名会随本体版本变。把全部调用收敛到一个类里，本体升级时只需要改这里；
 * 天意的决策层（{@code will/}、{@code fact/}、{@code action/}）因此永远不 import 本体类型，
 * 也就能在没有 Minecraft 的环境里跑离线测试。
 *
 * <h2>失败语义</h2>
 * 桥接方法<b>不抛异常</b>穿透 tick：本体没装、对象为 null、参数非法，一律返回
 * {@code null} / {@code false} / {@code -1} / 空集合，由调用方判断「这条路走不通」。
 * 天意的布局必须能因为桥接失败而<b>真的失败</b>，而不是静默变成空操作。
 *
 * <h2>已核对的签名来源</h2>
 * {@code _pack/refs/API_SLIM.txt} 与 {@code _pack/refs/extra.javap.txt}，
 * 由 {@code _pack/dump_refs.py} / {@code dump_refs_extra.py} 用 javap 从
 * {@code guzhenren_library-api.jar}（外层 10.20 包抽出，sha256 {@code e1de1ab2…}）直接读出。
 * <b>改动这里的任何调用前，先重跑那三个脚本核对。</b>
 */
public final class GuzhenrenBridge {
    private GuzhenrenBridge() {
    }

    /** 外层扩展的 modId。 */
    public static final String LIBRARY_MODID = "guzhenren_library";
    /** 内层本体的 modId。 */
    public static final String BASE_MODID = "guzhenren";

    // ------------------------------------------------------------ 数据标签

    /**
     * 本体真实的蛊材分类标签（内层 jar 的 {@code data/guzhenren/tags/item/*.json}，已逐个核过）。
     * <ul>
     *   <li>{@code zhiwu} —— 蛊材植物（36 项，含 {@code gu_caidanxincao} 蛊材丹心草、
     *       {@code gucaileimingcao} 蛊材雷鸣草）</li>
     *   <li>{@code jinshu} —— 蛊材金属（3 项：{@code gu_cai_xuan_jin} 蛊材玄金、
     *       {@code gu_cai_chi_lian_jin} 蛊材赤炼金、{@code wen_ding_shi}）</li>
     *   <li>{@code z1}~{@code z5} —— 一转到五转</li>
     * </ul>
     * 用标签而不是硬编码 id：本体换物品名时这里不用改，附属也不会因为 id 写错而静默失效。
     */
    public static final TagKey<Item> TAG_GU_MATERIAL_PLANT = tag("zhiwu");
    public static final TagKey<Item> TAG_GU_MATERIAL_METAL = tag("jinshu");
    public static final TagKey<Item> TAG_RANK_1 = tag("z1");
    public static final TagKey<Item> TAG_RANK_2 = tag("z2");
    public static final TagKey<Item> TAG_RANK_3 = tag("z3");
    public static final TagKey<Item> TAG_RANK_4 = tag("z4");
    public static final TagKey<Item> TAG_RANK_5 = tag("z5");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(BASE_MODID, path));
    }

    // ------------------------------------------------------------ 可用性

    /** 外层扩展在不在。天意只在本体齐全时启用世界侧功能。 */
    public static boolean libraryAvailable() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(LIBRARY_MODID);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 内外两层都在。 */
    public static boolean available() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(LIBRARY_MODID)
                    && ModList.get().isLoaded(BASE_MODID);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ 物品判定

    /**
     * 是不是本体认可的蛊材（植物类或金属类）。
     *
     * <p>标签未加载时（数据包还没就绪）返回 {@code false}，不误判。
     */
    public static boolean isGuMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(TAG_GU_MATERIAL_PLANT) || stack.is(TAG_GU_MATERIAL_METAL);
    }

    /** 一转~五转（凡人可及的转数区间）。 */
    public static int mortalRank(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        // 不用 TagKey 数组：泛型数组会触发 unchecked 警告，这里顺序判断更直白
        if (stack.is(TAG_RANK_1)) {
            return 1;
        }
        if (stack.is(TAG_RANK_2)) {
            return 2;
        }
        if (stack.is(TAG_RANK_3)) {
            return 3;
        }
        if (stack.is(TAG_RANK_4)) {
            return 4;
        }
        if (stack.is(TAG_RANK_5)) {
            return 5;
        }
        return 0;
    }

    // ------------------------------------------------------------ 势力

    /** 某个维度上的势力账本；本体没装或维度为空时返回 null。 */
    public static FactionTerritory territory(Level level) {
        if (level == null) {
            return null;
        }
        try {
            return FactionTerritory.of(level);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 势力状态（资金 / 实力 / 补给 / 存亡 / 归属）。
     *
     * @return 本体对象；取不到时 null
     */
    public static FactionTerritory.State state(Level level, FactionDef def) {
        FactionTerritory t = territory(level);
        if (t == null || def == null) {
            return null;
        }
        try {
            return t.state(def);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 补给存量；不可用时 -1。 */
    public static int supplies(Level level, FactionDef def) {
        FactionTerritory.State s = state(level, def);
        return s == null ? -1 : s.supplies;
    }

    /** 实力；不可用时 -1。 */
    public static int power(Level level, FactionDef def) {
        FactionTerritory.State s = state(level, def);
        return s == null ? -1 : s.power;
    }

    /** 国库；不可用时 -1。 */
    public static long treasury(Level level, FactionDef def) {
        FactionTerritory.State s = state(level, def);
        return s == null ? -1L : s.treasury;
    }

    /** 势力还在不在；不可用时 false。 */
    public static boolean alive(Level level, FactionDef def) {
        FactionTerritory.State s = state(level, def);
        return s != null && s.alive;
    }

    /** 加补给，返回加完后的值；不可用 -1。真实调用本体入口，不写自家账本。 */
    public static int addSupplies(Level level, FactionDef def, int amount) {
        FactionTerritory t = territory(level);
        if (t == null || def == null) {
            return -1;
        }
        try {
            return t.addSupplies(def, amount);
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 加实力，返回加完后的值；不可用 -1。 */
    public static int addPower(Level level, FactionDef def, int amount) {
        FactionTerritory t = territory(level);
        if (t == null || def == null) {
            return -1;
        }
        try {
            return t.addPower(def, amount);
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 加国库，返回加完后的值；不可用 -1。 */
    public static long addTreasury(Level level, FactionDef def, long amount) {
        FactionTerritory t = territory(level);
        if (t == null || def == null) {
            return -1L;
        }
        try {
            return t.addTreasury(def, amount);
        } catch (Throwable e) {
            return -1L;
        }
    }

    /** 两个势力的立场；不可用时 null。 */
    public static FactionStance stance(Level level, FactionDef from, FactionDef to) {
        FactionTerritory t = territory(level);
        if (t == null || from == null || to == null) {
            return null;
        }
        try {
            return t.stance(from, to);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 改立场分数，返回改完后的分数；不可用 {@link Integer#MIN_VALUE}。
     *
     * <p>这是「天意让两个势力互相敌对/和解」唯一的合法入口 —— 不直接改 combat，
     * 也不改 NPC 的 permanent hate。
     */
    public static int addStance(Level level, FactionDef from, FactionDef to, int delta) {
        FactionTerritory t = territory(level);
        if (t == null || from == null || to == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return t.addStance(from, to, delta);
        } catch (Throwable e) {
            return Integer.MIN_VALUE;
        }
    }

    /** 按名字取势力；取不到 null。 */
    public static FactionDef faction(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return FactionDef.byName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 这一维度上「真实存在」的势力集合：玩家自建的 + {@link FactionPool#ALL} 里的原生势力。
     *
     * <p>{@code FactionPool.ALL} 是 {@code List<Seed>}，Seed 只有 {@code name()}；
     * 必须再经 {@code FactionDef.byName} 解析。解析不到的直接跳过 —— 本体的名字表可能变，
     * 我们不能因此让整个决策崩掉。
     */
    public static List<FactionDef> factions(Level level) {
        FactionTerritory t = territory(level);
        if (t == null) {
            return List.of();
        }
        Set<FactionDef> out = new LinkedHashSet<>();
        try {
            out.addAll(t.playerFactions().values());
        } catch (Throwable ignored) {
            // 玩家势力取不到不算致命
        }
        try {
            for (FactionPool.Seed seed : FactionPool.ALL) {
                FactionDef def = faction(seed.name());
                if (def != null) {
                    out.add(def);
                }
            }
        } catch (Throwable ignored) {
            // 原生势力表取不到，就只用玩家势力
        }
        return new ArrayList<>(out);
    }

    // ------------------------------------------------------------ 行程（商队 / 使者 / 军队）

    /** 这一维度的全部行程；不可用返回空表。 */
    public static List<FactionJourney> journeys(Level level) {
        FactionTerritory t = territory(level);
        if (t == null) {
            return List.of();
        }
        try {
            FactionJourneys js = t.journeys();
            return js == null ? List.of() : js.all();
        } catch (Throwable e) {
            return List.of();
        }
    }

    /** 按 id 取行程；取不到 null。 */
    public static FactionJourney journey(Level level, int id) {
        FactionTerritory t = territory(level);
        if (t == null) {
            return null;
        }
        try {
            return t.journeys().byId(id);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 记一条行程。真正的实体化交给本体 {@code JourneyMaterializer.tick}，我们只登记。
     *
     * @return 新行程；不可用 null
     */
    public static FactionJourney startJourney(ServerLevel level, FactionDef from, FactionDef to,
                                              JourneyKind kind, long now, int duration) {
        FactionTerritory t = territory(level);
        if (t == null || from == null || to == null || kind == null) {
            return null;
        }
        try {
            return t.journeys().record(level, from, to, kind, now, duration);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 把行程标记为失败（被截获 / 被击退 / 路线被断）。不可用返回 false。 */
    public static boolean failJourney(Level level, FactionJourney journey, long now) {
        FactionTerritory t = territory(level);
        if (t == null || journey == null) {
            return false;
        }
        try {
            t.journeys().markFailed(journey, t, now);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 让本体的行程 tick 一轮（天意不自己搬商队）。 */
    public static boolean tickJourneys(ServerLevel level, long now) {
        FactionTerritory t = territory(level);
        if (t == null) {
            return false;
        }
        try {
            t.journeys().tick(level, t, now);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 行程剩余比例（0..1）；取不到 -1。 */
    public static float journeyProgress(FactionJourney journey, long now) {
        if (journey == null) {
            return -1.0f;
        }
        try {
            return journey.progress(now);
        } catch (Throwable e) {
            return -1.0f;
        }
    }

    // ------------------------------------------------------------ NPC

    /** 这一维度的居民登记表；不可用 null。 */
    public static ResidentRegistry residents(Level level) {
        if (level == null) {
            return null;
        }
        try {
            return ResidentRegistry.of(level);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 全部居民（含已故）；不可用空表。 */
    public static Collection<Resident> allResidents(Level level) {
        ResidentRegistry r = residents(level);
        if (r == null) {
            return List.of();
        }
        try {
            return r.all();
        } catch (Throwable e) {
            return List.of();
        }
    }

    /** 实体对应的居民；不是居民返回 null。 */
    public static Resident residentOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return ResidentService.residentOf(entity);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 居民当前活着的实体；不在加载区块里返回 null。 */
    public static Mob liveEntity(ServerLevel level, Resident resident) {
        if (level == null || resident == null) {
            return null;
        }
        try {
            return ResidentService.findLive(level, resident);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 让居民动身去某处（本体会自己处理寻路/装载）。不可用返回 false。 */
    public static boolean travelTo(Resident resident, double x, double y, double z) {
        if (resident == null) {
            return false;
        }
        try {
            resident.travelTo(x, y, z);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 给居民写一条生活日志（本体自己的 log，不是我们另建一套）。 */
    public static boolean remember(Resident resident, String line, long now) {
        if (resident == null || line == null) {
            return false;
        }
        try {
            resident.remember(line, now);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 把居民与某身份结仇（本体 feud 表）。 */
    public static boolean addFeud(Resident resident, UUID other) {
        if (resident == null || other == null) {
            return false;
        }
        try {
            resident.addFeud(other);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // ------------------------------------------------------------ 身份

    /** 玩家当前激活的身份 UUID；没有身份返回 null。 */
    public static UUID activeIdentity(Player player) {
        if (player == null) {
            return null;
        }
        try {
            return IdentityManager.activeId(player);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 玩家当前身份显示名；没有身份返回 null。 */
    public static String activeIdentityTitle(Player player) {
        if (player == null) {
            return null;
        }
        try {
            return IdentityManager.activeTitle(player);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 玩家全部身份 UUID。 */
    public static Set<UUID> identities(Player player) {
        if (player == null) {
            return Set.of();
        }
        Set<UUID> out = new LinkedHashSet<>();
        try {
            IdentityManager.identities(player).forEach(i -> out.add(i.id()));
        } catch (Throwable ignored) {
            // 取不到就当没身份
        }
        return out;
    }

    /**
     * 这个玩家用某个身份做过事吗 —— 用于「身份切换不能抹除证据」。
     *
     * <p>注意语义：<b>它只回答「这个身份是否属于该玩家」</b>，
     * 不回答「证据是否成立」。证据成立与否由 {@code fact.FactLedger} 按身份 UUID 记，
     * 换身份不会改已经写下的事实。
     */
    public static boolean ownsIdentity(Player player, UUID identityId) {
        if (player == null || identityId == null) {
            return false;
        }
        return identities(player).contains(identityId);
    }

    // ------------------------------------------------------------ 商贸与寿蛊

    /** 本体认可的元石类货币。 */
    public static Item currency() {
        try {
            return NpcTrade.currency();
        } catch (Throwable e) {
            return null;
        }
    }

    /** Reuse the base mod payment rules, including stored yuanlao-gu value. */
    public static boolean consumeCurrency(Player player, int amount) {
        if (player == null || amount < 0) return false;
        try { return NpcTrade.consumeCurrency(player, amount); }
        catch (RuntimeException | LinkageError ex) { return false; }
    }

    /** 本体价目：物品多少元石。取不到 -1。 */
    public static int priceOf(Item item) {
        if (item == null) {
            return -1;
        }
        try {
            return NpcTrade.priceOf(item);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * 本体认定的<b>全部寿蛊 id</b>。
     *
     * <p>寿蛊供给必须按这份清单审计 —— 不是按名字含“寿”字自己猜。
     */
    public static Set<String> longevityIds() {
        try {
            return new LinkedHashSet<>(NpcStock.longevityIds());
        } catch (Throwable e) {
            return Set.of();
        }
    }

    /** 这件物品是不是本体认定的寿蛊。 */
    public static boolean isLongevity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return NpcStock.isLongevityGu(stack);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 该不补货了？（本体自己的节拍判定） */
    public static boolean dueForRestock(long now, long last) {
        try {
            return NpcStock.dueForRestock(now, last);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * <b>本体原生的补货入口</b>。
     *
     * <p>需求第四条要求接管它：接管的含义是<b>优先走我们的真实物流</b>，
     * 只有物流走不通时才退回本体的候选池。这里<b>本身不做接管</b>，
     * 只暴露原入口供 {@code world.RestockGuard} 决定是否调用。
     *
     * @return 补了几种；不可用 -1
     */
    public static int nativeRestock(Mob mob) {
        if (mob == null) {
            return -1;
        }
        try {
            return NpcStock.restock(mob);
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 本体自己的上架逻辑（货架）—— 只读，用于核对接管后库存是否还在。 */
    public static int nativeTradeableKinds(Mob mob) {
        if (mob == null) {
            return -1;
        }
        try {
            return NpcStock.tradeableKinds(mob);
        } catch (Throwable e) {
            return -1;
        }
    }

    // ------------------------------------------------------------ 诊断

    /** 一行式状态，只进服务端日志（普通玩家看不到）。 */
    public static String describe(Level level) {
        if (!libraryAvailable()) {
            return "guzhenren_library 未加载";
        }
        FactionTerritory t = territory(level);
        ResidentRegistry r = residents(level);
        int factions = factions(level).size();
        int journeys = journeys(level).size();
        int residents = 0;
        try {
            residents = r == null ? 0 : r.aliveCount();
        } catch (Throwable ignored) {
            residents = -1;
        }
        return "territory=" + (t != null) + " factions=" + factions
                + " journeys=" + journeys + " residents=" + residents
                + " longevityIds=" + longevityIds().size()
                + " currency=" + (currency() == null ? "?" : currency().toString());
    }

    /** 空值安全的身份 id 描述（用于日志，不参与判定）。 */
    public static String idOf(ServerPlayer player) {
        UUID id = activeIdentity(player);
        return id == null ? "-" : id.toString();
    }
}
