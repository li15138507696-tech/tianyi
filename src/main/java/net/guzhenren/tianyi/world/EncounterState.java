package net.guzhenren.tianyi.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.guzhenren.tianyi.TianyiMod;
import net.guzhenren.tianyi.encounter.EncounterRun;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One independent state per player, stored with the overworld, not client data. */
public final class EncounterState extends SavedData {
    private final Map<UUID, EncounterRun> players = new LinkedHashMap<>();
    private CompoundTag preserved;
    public boolean quarantined() { return preserved != null; }
    public static EncounterState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EncounterState::new, EncounterState::load, null), "tianyi_encounters");
    }
    public EncounterRun of(UUID owner) {
        if (quarantined()) return null;
        return players.computeIfAbsent(owner, key -> {
            EncounterRun r = new EncounterRun(key.toString());
            r.remainingTicks = EncounterSettings.firstTicks();
            setDirty(); return r;
        });
    }
    public EncounterRun existing(UUID owner) { return players.get(owner); }
    public Iterable<EncounterRun> all() { return players.values(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (preserved != null) return preserved.copy();
        tag.putInt("schema", 2);
        ListTag list = new ListTag();
        for (EncounterRun r : players.values()) {
            CompoundTag row = new CompoundTag();
            r.snapshot().forEach(row::putString);
            list.add(row);
        }
        tag.put("players", list); return tag;
    }
    public static EncounterState load(CompoundTag tag, HolderLookup.Provider registries) {
        EncounterState s = new EncounterState();
        if ((tag.contains("schema") && (!tag.contains("schema", Tag.TAG_INT)
                || tag.getInt("schema") < 1 || tag.getInt("schema") > 2))
                || !tag.contains("players", Tag.TAG_LIST)
                || ((ListTag) tag.get("players")).size() != tag.getList("players", Tag.TAG_COMPOUND).size()) {
            s.preserved = tag.copy();
            TianyiMod.LOGGER.error("[天意机缘] 未知或损坏账本，原样保留并停止全部奖惩");
            return s;
        }
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) t;
            Map<String,String> values = new LinkedHashMap<>();
            try {
                for (String key : row.getAllKeys()) {
                    if (!row.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("Non-string encounter field: " + key);
                    values.put(key, row.getString(key));
                }
                EncounterRun r = EncounterRun.restore(values);
                UUID owner = UUID.fromString(r.owner);
                if (s.players.putIfAbsent(owner, r) != null) throw new IllegalArgumentException("Duplicate owner");
            } catch (RuntimeException ex) {
                s.players.clear(); s.preserved = tag.copy();
                TianyiMod.LOGGER.error("[天意机缘] 损坏记录，原账本保留并隔离，不执行奖惩：{}", ex.toString());
                return s;
            }
        }
        return s;
    }
}
