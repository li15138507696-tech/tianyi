package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 升仙会话的世界级存档。会话以 UUID 为键并保留空窍 ID；更新采用 revision 比较，
 * 未来 schema 与无法解码的记录原样隔离，避免旧代码把未知进度覆盖为空数据。
 */
public final class AscensionStore extends SavedData {
    public static final String NAME = "tianyi_ascension";
    public static final int SCHEMA = 1;

    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final List<CompoundTag> quarantinedRecords = new ArrayList<>();
    private CompoundTag futureSchemaSnapshot;

    public AscensionStore() {
    }

    public static AscensionStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AscensionStore::new, AscensionStore::load, null), NAME);
    }

    public Optional<Session> find(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public List<Session> all() {
        return List.copyOf(sessions.values());
    }

    public List<Session> forOwner(UUID ownerId) {
        return sessions.values().stream().filter(s -> s.ownerId().equals(ownerId)).toList();
    }

    /** 只允许同一主人、同一空窍存在一个未结束会话；不同空窍分别记账。 */
    public boolean create(Session session) {
        if (futureSchemaSnapshot != null || session == null || sessions.containsKey(session.sessionId())
                || hasActiveSession(session.ownerId(), session.apertureId())) {
            return false;
        }
        sessions.put(session.sessionId(), session);
        setDirty();
        return true;
    }

    /**
     * 以单调 revision 原子替换会话快照。该原子性限于 SavedData 内存记录；磁盘落盘与
     * 玩家物品文件并非同一事务，物品副作用必须由上层服务另外记录和恢复。
     */
    public boolean compareAndSet(UUID sessionId, long expectedRevision, Session replacement) {
        if (futureSchemaSnapshot != null || replacement == null) {
            return false;
        }
        Session current = sessions.get(sessionId);
        if (current == null || !current.sessionId().equals(replacement.sessionId())
                || !current.ownerId().equals(replacement.ownerId())
                || !current.apertureId().equals(replacement.apertureId())
                || !current.profileId().equals(replacement.profileId())
                || current.revision() != expectedRevision
                || replacement.revision() != expectedRevision + 1) {
            return false;
        }
        if (!replacement.terminal() && hasOtherActiveSession(replacement, sessionId)) {
            return false;
        }
        sessions.put(sessionId, replacement);
        setDirty();
        return true;
    }

    public int quarantinedCount() {
        return quarantinedRecords.size() + (futureSchemaSnapshot == null ? 0 : 1);
    }

    public boolean isReadOnly() {
        return futureSchemaSnapshot != null;
    }

    private boolean hasActiveSession(UUID ownerId, String apertureId) {
        return sessions.values().stream().anyMatch(s -> !s.terminal()
                && s.ownerId().equals(ownerId) && s.apertureId().equals(apertureId));
    }

    private boolean hasOtherActiveSession(Session replacement, UUID ignoredSessionId) {
        return sessions.values().stream().anyMatch(s -> !s.sessionId().equals(ignoredSessionId)
                && !s.terminal() && s.ownerId().equals(replacement.ownerId())
                && s.apertureId().equals(replacement.apertureId()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (futureSchemaSnapshot != null) {
            return futureSchemaSnapshot.copy();
        }

        tag.putInt("schema", SCHEMA);
        ListTag storedSessions = new ListTag();
        for (Session session : sessions.values()) {
            storedSessions.add(encode(session));
        }
        tag.put("sessions", storedSessions);

        ListTag quarantined = new ListTag();
        for (CompoundTag record : quarantinedRecords) {
            quarantined.add(record.copy());
        }
        tag.put("quarantined", quarantined);
        return tag;
    }

    public static AscensionStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AscensionStore store = new AscensionStore();
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA) {
            store.futureSchemaSnapshot = tag.copy();
            return store;
        }

        if (!hasCompoundList(tag, "sessions") || !hasCompoundList(tag, "quarantined")) {
            store.futureSchemaSnapshot = tag.copy();
            return store;
        }

        for (Tag raw : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            try {
                Session session = decode(record);
                if (store.sessions.containsKey(session.sessionId())
                        || (!session.terminal() && store.hasActiveSession(session.ownerId(), session.apertureId()))) {
                    store.quarantinedRecords.add(record.copy());
                } else {
                    store.sessions.put(session.sessionId(), session);
                }
            } catch (RuntimeException invalidRecord) {
                store.quarantinedRecords.add(record.copy());
            }
        }

        for (Tag raw : tag.getList("quarantined", Tag.TAG_COMPOUND)) {
            store.quarantinedRecords.add(((CompoundTag) raw).copy());
        }
        return store;
    }

    private static boolean hasCompoundList(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return true;
        }
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag original)) {
            return false;
        }
        ListTag compounds = tag.getList(key, Tag.TAG_COMPOUND);
        return original.size() == compounds.size();
    }

    private static CompoundTag encode(Session session) {
        CompoundTag record = new CompoundTag();
        record.putString("session", session.sessionId().toString());
        record.putString("owner", session.ownerId().toString());
        record.putString("aperture", session.apertureId());
        record.putString("profile", session.profileId());
        record.putString("phase", session.phase().name());
        record.putString("outcome", session.outcome().name());
        record.putLong("phaseStartedAt", session.phaseStartedAt());
        record.putBoolean("brokenCommitted", session.brokenCommitted());
        record.putLong("revision", session.revision());
        record.putString("guEscrow", session.guEscrowId());
        record.putString("apertureResult", session.apertureResultId());

        CompoundTag qi = new CompoundTag();
        qi.putDouble("tian", session.qi().tian());
        qi.putDouble("di", session.qi().di());
        qi.putDouble("ren", session.qi().ren());
        record.put("qi", qi);
        return record;
    }

    private static Session decode(CompoundTag record) {
        requireTag(record, "session", Tag.TAG_STRING);
        requireTag(record, "owner", Tag.TAG_STRING);
        requireTag(record, "aperture", Tag.TAG_STRING);
        requireTag(record, "profile", Tag.TAG_STRING);
        requireTag(record, "phase", Tag.TAG_STRING);
        requireTag(record, "outcome", Tag.TAG_STRING);
        requireTag(record, "phaseStartedAt", Tag.TAG_LONG);
        requireTag(record, "brokenCommitted", Tag.TAG_BYTE);
        requireTag(record, "revision", Tag.TAG_LONG);
        requireTag(record, "guEscrow", Tag.TAG_STRING);
        requireTag(record, "apertureResult", Tag.TAG_STRING);
        requireTag(record, "qi", Tag.TAG_COMPOUND);

        UUID sessionId = UUID.fromString(record.getString("session"));
        UUID ownerId = UUID.fromString(record.getString("owner"));
        CompoundTag qi = record.getCompound("qi");
        requireTag(qi, "tian", Tag.TAG_DOUBLE);
        requireTag(qi, "di", Tag.TAG_DOUBLE);
        requireTag(qi, "ren", Tag.TAG_DOUBLE);
        return new Session(sessionId, ownerId, record.getString("aperture"), record.getString("profile"),
                Phase.valueOf(record.getString("phase")), Outcome.valueOf(record.getString("outcome")),
                record.getLong("phaseStartedAt"), record.getBoolean("brokenCommitted"),
                new QiState(qi.getDouble("tian"), qi.getDouble("di"), qi.getDouble("ren")),
                record.getString("guEscrow"), record.getString("apertureResult"),
                record.getLong("revision"));
    }

    private static void requireTag(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("invalid ascension save field: " + key);
        }
    }
}
