package net.guzhenren.tianyi.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 宿命蛊取得路径<b>自己的</b>小账本：守护兽击杀证据 + 幂等键。
 *
 * <h2>为什么另开一份，而不是塞进既有账本</h2>
 * <p>{@code tianyi_finite_sites} 的 schema 被审计锁死（{@code SCHEMA_VERSION = 1}），
 * {@code tianyi_destiny_custody} 的旧键也不许动。取得路径需要的是<b>两类新事实</b>：
 * 「这只守护兽被谁打死」和「这次取得是否已经发生过」。它们既不属于资源点的库存账，
 * 也不属于宿命蛊的保管账，所以单独一个存档键 {@link #STORAGE_KEY}。
 *
 * <h2>纪律</h2>
 * <ol>
 *   <li><b>有界</b>：击杀最多 {@link #MAX_KILLS} 条、幂等键最多 {@link #MAX_CLAIMS} 条，
 *       到顶就不再写（宁可少记，也不无界增长）。</li>
 *   <li><b>fail-closed</b>：整份存档版本不认识时原样保留并拒绝一切写入，
 *       绝不「猜着读」——猜错的代价是多发一只世界唯一的蛊。</li>
 *   <li><b>只记录、不发放</b>：本类不产生物品、不碰世界、不推进任何阶段。</li>
 *   <li><b>旧档静默忽略</b>：本键之前不存在；读到不认识的字段一律忽略，不回写、不迁移。</li>
 * </ol>
 *
 * <p><b>未实机</b>：NBT 往返的真实落盘顺序（setDirty 之后由 SavedData 调度写入）只能在游戏里观察。
 */
public final class DestinySeekState extends SavedData {
    /** 新存档键（既有旧键一律不改）。 */
    public static final String STORAGE_KEY = "tianyi_destiny_seek";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_KILLS = 64, MAX_CLAIMS = 256;
    public static final String SCHEMA = "schemaVersion", KILLS = "kills", CLAIMS = "claims",
            SITE = "site", KILLER = "killer", KEY = "key";

    /** 站点 UUID → 亲手打死其守护兽的玩家 UUID。 */
    private final Map<UUID, String> killers = new LinkedHashMap<>();
    /** 已发放过的幂等键（{@code resource/DestinySeek#key}）。 */
    private final Map<String, String> claims = new LinkedHashMap<>();
    /** 认不出来的整份存档：原样留着，一个字节都不改。 */
    private CompoundTag preserved;
    private String failure = "";

    public DestinySeekState() {
    }

    public static DestinySeekState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DestinySeekState::new, DestinySeekState::load, null), STORAGE_KEY);
    }

    /** 存档信封认不认得出来（不认得就别写）。 */
    public boolean recognizedEnvelope() {
        return preserved == null;
    }

    public boolean mutationReady() {
        return preserved == null;
    }

    public String failureReason() {
        return failure;
    }

    public Map<UUID, String> killers() {
        return Map.copyOf(killers);
    }

    public Map<String, String> claims() {
        return Map.copyOf(claims);
    }

    /** 这个点记着的击杀者（没有记录返回空串；空串不等于「没打死过」。 */
    public String killerOf(UUID siteId) {
        if (siteId == null) return "";
        String killer = killers.get(siteId);
        return killer == null ? "" : killer;
    }

    /**
     * 记一次击杀。只在「死亡实体确实是本站绑定的守护兽，且是玩家亲手打死」时调用。
     *
     * @return 真的写进去了（或本来就是同一个击杀者）
     */
    public boolean recordKill(UUID siteId, String killerUuid) {
        if (!mutationReady() || siteId == null || killerUuid == null || killerUuid.isBlank()) return false;
        String old = killers.get(siteId);
        if (killerUuid.equals(old)) return true;
        // 同一个点被换了击杀者：只有原本没有记录时才写；先到先得，不覆盖既有证据。
        if (old != null && !old.isBlank()) return false;
        if (killers.size() >= MAX_KILLS) return false;
        killers.put(siteId, killerUuid);
        setDirty();
        return true;
    }

    public boolean claimed(String key) {
        return key != null && claims.containsKey(key);
    }

    /** 记下幂等键。到上限就拒绝（返回 false = 这一次不算「已发放」，但上层仍会因账本已有正品而拒绝）。 */
    public boolean claim(String key) {
        if (!mutationReady() || key == null || key.isBlank()) return false;
        if (claims.containsKey(key)) return true;
        if (claims.size() >= MAX_CLAIMS) return false;
        claims.put(key, Long.toString(System.currentTimeMillis()));
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag destination, HolderLookup.Provider registries) {
        if (preserved != null) return preserved.copy();
        CompoundTag out = new CompoundTag();
        out.putInt(SCHEMA, SCHEMA_VERSION);
        ListTag killRows = new ListTag();
        for (Map.Entry<UUID, String> e : killers.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID(SITE, e.getKey());
            row.putString(KILLER, e.getValue());
            killRows.add(row);
        }
        out.put(KILLS, killRows);
        ListTag claimRows = new ListTag();
        for (Map.Entry<String, String> e : claims.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString(KEY, e.getKey());
            row.putString("at", e.getValue());
            claimRows.add(row);
        }
        out.put(CLAIMS, claimRows);
        return out;
    }

    /**
     * 读档。版本不认识时保留原始 NBT 并停写；单行坏了只丢这一行（不整份作废）。
     * 任何一个字段缺失都按「没有记录」处理——旧档读到旧键静默忽略。
     */
    public static DestinySeekState load(CompoundTag tag, HolderLookup.Provider registries) {
        DestinySeekState state = new DestinySeekState();
        if (tag == null || tag.isEmpty()) return state;
        if (tag.contains(SCHEMA) && tag.getInt(SCHEMA) != SCHEMA_VERSION) {
            state.preserved = tag.copy();
            state.failure = "取得路径账本版本不认识（" + tag.getInt(SCHEMA) + "，只认 " + SCHEMA_VERSION + "）";
            return state;
        }
        ListTag killRows = tag.getList(KILLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < killRows.size() && state.killers.size() < MAX_KILLS; i++) {
            CompoundTag row = killRows.getCompound(i);
            if (!row.hasUUID(SITE)) continue;
            String killer = row.getString(KILLER);
            if (killer == null || killer.isBlank()) continue;
            state.killers.putIfAbsent(row.getUUID(SITE), killer);
        }
        ListTag claimRows = tag.getList(CLAIMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < claimRows.size() && state.claims.size() < MAX_CLAIMS; i++) {
            CompoundTag row = claimRows.getCompound(i);
            String key = row.getString(KEY);
            if (key == null || key.isBlank()) continue;
            state.claims.putIfAbsent(key, row.getString("at"));
        }
        return state;
    }
}
