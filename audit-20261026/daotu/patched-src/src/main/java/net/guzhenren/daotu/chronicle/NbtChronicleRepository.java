// Path: src/main/java/net/guzhenren/daotu/chronicle/NbtChronicleRepository.java
package net.guzhenren.daotu.chronicle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;
import net.guzhenren.daotu.chronicle.ChronicleModels.FactionJournal;
import net.guzhenren.daotu.chronicle.ChronicleModels.MeritArchive;
import net.guzhenren.daotu.chronicle.ChronicleModels.ReputationJournal;

/**
 * 独立 NBT 根 {@code daotu_chronicle} 的仓储。
 *
 * <p>只碰自己这一个根：不读也不写 {@code daotu} 与 {@code tianfu}。
 * 写入一律「构好整棵树再一次性 put」，因此提交要么全成要么全不成。
 */
public final class NbtChronicleRepository implements ChronicleRepository {
    private static final String ROOT = ChronicleModels.STORAGE_ROOT;
    private static final String K_SCHEMA = "schema";
    private static final String K_REVISION = "revision";
    private static final String K_RUN = "run";
    private static final String K_FACTION = "faction";
    private static final String K_MERIT = "merit";
    private static final String K_REPUTATION = "reputation";

    private static void writeJournal(CompoundTag parent, String key,
            Set<String> pairs, String lastA, String lastB, long tick) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (String value : pairs) {
            list.add(StringTag.valueOf(value));
        }
        tag.put("pairs", list);
        tag.putString("lastA", lastA == null ? "" : lastA);
        tag.putString("lastB", lastB == null ? "" : lastB);
        tag.putLong("tick", tick);
        parent.put(key, tag);
    }

    private static Set<String> readStrings(CompoundTag parent, String key) {
        Set<String> out = new LinkedHashSet<>();
        ListTag list = parent.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    private static void writeCounter(CompoundTag parent, String key, double last, double peak,
            Set<String> unlocked, long tick) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("last", last);
        tag.putDouble("peak", peak);
        ListTag list = new ListTag();
        for (String value : unlocked) {
            list.add(StringTag.valueOf(value));
        }
        tag.put("unlocked", list);
        tag.putLong("tick", tick);
        parent.put(key, tag);
    }

    private static MeritArchive readMerit(CompoundTag parent) {
        if (!parent.contains(K_MERIT, Tag.TAG_COMPOUND)) {
            return new MeritArchive(-1.0, -1.0, Set.of(), -1L);
        }
        CompoundTag tag = parent.getCompound(K_MERIT);
        return new MeritArchive(tag.getDouble("last"), tag.getDouble("peak"),
                Set.copyOf(readStrings(tag, "unlocked")), tag.getLong("tick"));
    }

    private static ReputationJournal readReputation(CompoundTag parent) {
        if (!parent.contains(K_REPUTATION, Tag.TAG_COMPOUND)) {
            return new ReputationJournal(-1.0, -1.0, Set.of(), -1L);
        }
        CompoundTag tag = parent.getCompound(K_REPUTATION);
        return new ReputationJournal(tag.getDouble("last"), tag.getDouble("peak"),
                Set.copyOf(readStrings(tag, "unlocked")), tag.getLong("tick"));
    }

    /** {@inheritDoc} 缺失返回空；schema 高于本版本直接拒绝且不修改原数据。 */
    @Override
    public Optional<ChronicleState> load(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains(ROOT, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = pd.getCompound(ROOT);
        int schema = tag.getInt(K_SCHEMA);
        if (schema > ChronicleModels.SCHEMA) {
            throw new IllegalStateException("纪事 schema " + schema + " 高于本版本 " + ChronicleModels.SCHEMA);
        }
        if (schema < 0) {
            throw new IllegalStateException("纪事 schema 非法: " + schema);
        }
        CompoundTag factionTag = tag.contains(K_FACTION, Tag.TAG_COMPOUND)
                ? tag.getCompound(K_FACTION) : new CompoundTag();
        FactionJournal faction = new FactionJournal(
                Set.copyOf(readStrings(factionTag, "pairs")),
                factionTag.getString("lastA"),
                factionTag.getString("lastB"),
                factionTag.getLong("tick"));
        return Optional.of(new ChronicleState(
                player.getUUID(),
                schema,
                tag.getLong(K_REVISION),
                tag.getInt(K_RUN),
                faction,
                readMerit(tag),
                readReputation(tag)));
    }

    /** {@inheritDoc} 所有者与 revision 双校验通过才写；冲突返回 false 且不落盘。 */
    @Override
    public boolean save(ServerPlayer player, long expectedRevision, ChronicleState next) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(next, "next");
        if (!player.getUUID().equals(next.playerId())) {
            throw new IllegalArgumentException("所有者不匹配");
        }
        if (next.revision() != expectedRevision + 1L) {
            throw new IllegalArgumentException("revision 必须为 expectedRevision + 1");
        }
        if (next.faction().discoveredPairs().size() > ChronicleTables.MAX_FACTION_PAIRS) {
            throw new IllegalArgumentException("宗门见闻组合超过上限");
        }
        long current = load(player).map(ChronicleState::revision).orElse(-1L);
        if (current != expectedRevision) {
            return false;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(K_SCHEMA, ChronicleModels.SCHEMA);
        tag.putLong(K_REVISION, next.revision());
        tag.putInt(K_RUN, next.runId());
        writeJournal(tag, K_FACTION, next.faction().discoveredPairs(),
                next.faction().lastFaction(), next.faction().lastIdentity(),
                next.faction().lastObservedTick());
        writeCounter(tag, K_MERIT, next.merit().lastObserved(), next.merit().peakObserved(),
                next.merit().unlockedEntries(), next.merit().lastObservedTick());
        writeCounter(tag, K_REPUTATION, next.reputation().lastObserved(),
                next.reputation().peakObserved(), next.reputation().unlockedEntries(),
                next.reputation().lastObservedTick());
        player.getPersistentData().put(ROOT, tag);
        return true;
    }

    /** {@inheritDoc} 深拷贝；新玩家已有更高 revision 时保留新的，不覆盖。 */
    @Override
    public void copyForClone(ServerPlayer original, ServerPlayer replacement) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacement, "replacement");
        if (!original.getUUID().equals(replacement.getUUID())) {
            throw new IllegalArgumentException("克隆双方 UUID 不同");
        }
        CompoundTag source = original.getPersistentData();
        if (!source.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag incoming = source.getCompound(ROOT).copy();
        CompoundTag target = replacement.getPersistentData();
        if (target.contains(ROOT, Tag.TAG_COMPOUND)) {
            CompoundTag existing = target.getCompound(ROOT);
            if (existing.getLong(K_REVISION) >= incoming.getLong(K_REVISION)) {
                return;
            }
        }
        target.put(ROOT, incoming);
    }
}