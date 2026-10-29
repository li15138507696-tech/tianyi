package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.InsightState;
import net.guzhenren.tianyi.ascension.AscensionModel.KnowledgeGrant;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.GuEscrow;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.GuEscrow.State;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界级升仙账本。会话和蛊虫快照写入同一份 SavedData；跨玩家文件和物品容器的副作用
 * 仍由上层服务记录恢复，遇到不能证明的物品状态时必须隔离。
 */
public final class AscensionStore extends SavedData {
    public static final String NAME = "tianyi_ascension";
    public static final int SCHEMA = 4;
    private static final int LEGACY_SCHEMA = 1;
    private static final int ESCROW_SCHEMA = 2;
    private static final int INSIGHT_SCHEMA = 3;

    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, EscrowEntry> escrows = new LinkedHashMap<>();
    private final List<CompoundTag> quarantinedRecords = new ArrayList<>();
    private CompoundTag futureSchemaSnapshot;

    private record EscrowEntry(Record record, CompoundTag stackTag) {
        private EscrowEntry {
            stackTag = stackTag.copy();
        }

        @Override
        public CompoundTag stackTag() {
            return stackTag.copy();
        }
    }

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

    public Optional<Record> findEscrow(UUID escrowId) {
        EscrowEntry entry = escrows.get(escrowId);
        return entry == null ? Optional.empty() : Optional.of(entry.record());
    }

    /** Returns a deep copy so callers cannot mutate dirty SavedData without a revision update. */
    public Optional<CompoundTag> findEscrowStack(UUID escrowId) {
        EscrowEntry entry = escrows.get(escrowId);
        return entry == null ? Optional.empty() : Optional.of(entry.stackTag());
    }

    /** New active sessions must be inserted together with an escrow record and its complete stack tag. */
    public boolean create(Session session) {
        if (futureSchemaSnapshot != null || session == null || sessions.containsKey(session.sessionId())
                || (!session.terminal() && !hasLinkedEscrow(session))
                || hasActiveSession(session.ownerId(), session.apertureId())) {
            return false;
        }
        sessions.put(session.sessionId(), session);
        setDirty();
        return true;
    }

    public boolean createWithEscrow(Session session, Record escrow, int serverStackCount,
                                    CompoundTag itemStackTag, HolderLookup.Provider registries) {
        if (futureSchemaSnapshot != null || session == null || escrow == null || itemStackTag == null
                || !validStack(itemStackTag, registries) || serverStackCount != 1
                || session.phase() != Phase.PREPARING || session.outcome() != Outcome.RUNNING
                || session.brokenCommitted() || escrow.state() != State.PREPARED || escrow.revision() != 0L
                || !session.guEscrowId().equals(escrow.escrowId().toString())
                || !session.sessionId().equals(escrow.sessionId())
                || !session.ownerId().equals(escrow.ownerId())
                || sessions.containsKey(session.sessionId()) || escrows.containsKey(escrow.escrowId())
                || hasActiveSession(session.ownerId(), session.apertureId())) {
            return false;
        }
        sessions.put(session.sessionId(), session);
        escrows.put(escrow.escrowId(), new EscrowEntry(escrow, itemStackTag));
        setDirty();
        return true;
    }

    /**
     * Replaces a session at the next revision. This atomicity is limited to this SavedData record;
     * disk persistence and player inventory persistence are not one transaction.
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
                || !current.guEscrowId().equals(replacement.guEscrowId())
                || current.revision() != expectedRevision
                || replacement.revision() != expectedRevision + 1L) {
            return false;
        }
        if (!replacement.terminal()
                && (hasOtherActiveSession(replacement, sessionId) || !hasLinkedEscrow(replacement))) {
            return false;
        }
        sessions.put(sessionId, replacement);
        setDirty();
        return true;
    }

    public boolean compareAndSetEscrow(UUID escrowId, long expectedRevision, Record replacement) {
        if (futureSchemaSnapshot != null || replacement == null) {
            return false;
        }
        EscrowEntry entry = escrows.get(escrowId);
        if (entry == null) {
            return false;
        }
        Record current = entry.record();
        if (!current.escrowId().equals(replacement.escrowId())
                || !current.ownerId().equals(replacement.ownerId())
                || !current.sessionId().equals(replacement.sessionId())
                || !current.sourceSlot().equals(replacement.sourceSlot())
                || !current.itemFingerprint().equals(replacement.itemFingerprint())
                || current.revision() != expectedRevision
                || replacement.revision() != expectedRevision + 1L
                || !GuEscrow.isLegalStateTransition(current.state(), replacement.state())) {
            return false;
        }
        escrows.put(escrowId, new EscrowEntry(replacement, entry.stackTag()));
        setDirty();
        return true;
    }

    /**
     * Reconciles one session and its escrow as a single in-memory SavedData update. Both records
     * must still identify the same transaction and independently match their expected revisions.
     */
    public boolean compareAndSetPair(UUID sessionId, long expectedSessionRevision,
                                     Session replacementSession, UUID escrowId,
                                     long expectedEscrowRevision, Record replacementEscrow) {
        if (futureSchemaSnapshot != null || replacementSession == null || replacementEscrow == null) {
            return false;
        }
        Session currentSession = sessions.get(sessionId);
        EscrowEntry currentEntry = escrows.get(escrowId);
        if (currentSession == null || currentEntry == null) {
            return false;
        }
        Record currentEscrow = currentEntry.record();
        String escrowIdText = escrowId.toString();
        if (!currentSession.sessionId().equals(sessionId)
                || !currentSession.guEscrowId().equals(escrowIdText)
                || !currentSession.ownerId().equals(currentEscrow.ownerId())
                || !currentSession.sessionId().equals(currentEscrow.sessionId())
                || !replacementSession.sessionId().equals(sessionId)
                || !replacementSession.ownerId().equals(currentSession.ownerId())
                || !replacementSession.apertureId().equals(currentSession.apertureId())
                || !replacementSession.profileId().equals(currentSession.profileId())
                || !replacementSession.guEscrowId().equals(escrowIdText)
                || currentSession.revision() != expectedSessionRevision
                || !(replacementSession.equals(currentSession)
                     || replacementSession.revision() == expectedSessionRevision + 1L)
                || !currentEscrow.escrowId().equals(escrowId)
                || !replacementEscrow.escrowId().equals(escrowId)
                || !replacementEscrow.ownerId().equals(currentEscrow.ownerId())
                || !replacementEscrow.sessionId().equals(sessionId)
                || !replacementEscrow.sourceSlot().equals(currentEscrow.sourceSlot())
                || !replacementEscrow.itemFingerprint().equals(currentEscrow.itemFingerprint())
                || currentEscrow.revision() != expectedEscrowRevision
                || !(replacementEscrow.equals(currentEscrow)
                     || (replacementEscrow.revision() == expectedEscrowRevision + 1L
                         && GuEscrow.isLegalStateTransition(currentEscrow.state(), replacementEscrow.state())))
                || (replacementSession.equals(currentSession) && replacementEscrow.equals(currentEscrow))) {
            return false;
        }

        sessions.put(sessionId, replacementSession);
        escrows.put(escrowId, new EscrowEntry(replacementEscrow, currentEntry.stackTag()));
        setDirty();
        return true;
    }

    public int quarantinedCount() {
        return quarantinedRecords.size() + (futureSchemaSnapshot == null ? 0 : 1);
    }

    public boolean isReadOnly() {
        return futureSchemaSnapshot != null;
    }

    private boolean hasLinkedEscrow(Session session) {
        if (session.guEscrowId().isBlank()) {
            return false;
        }
        try {
            UUID escrowId = UUID.fromString(session.guEscrowId());
            EscrowEntry entry = escrows.get(escrowId);
            return entry != null && entry.record().sessionId().equals(session.sessionId())
                    && entry.record().ownerId().equals(session.ownerId());
        } catch (IllegalArgumentException invalidId) {
            return false;
        }
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

        ListTag storedEscrows = new ListTag();
        for (EscrowEntry entry : escrows.values()) {
            storedEscrows.add(encode(entry));
        }
        tag.put("escrows", storedEscrows);

        ListTag quarantined = new ListTag();
        for (CompoundTag record : quarantinedRecords) {
            quarantined.add(record.copy());
        }
        tag.put("quarantined", quarantined);
        return tag;
    }

    public static AscensionStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AscensionStore store = new AscensionStore();
        if (!tag.contains("schema", Tag.TAG_INT)) {
            store.futureSchemaSnapshot = tag.copy();
            return store;
        }
        int schema = tag.getInt("schema");
        if (schema == LEGACY_SCHEMA) {
            store.loadSchemaOne(tag);
        } else if (schema == ESCROW_SCHEMA) {
            store.loadSchemaTwo(tag, registries);
        } else if (schema == INSIGHT_SCHEMA) {
            store.loadSchemaThree(tag, registries);
        } else if (schema == SCHEMA) {
            store.loadSchemaFour(tag, registries);
        } else {
            store.futureSchemaSnapshot = tag.copy();
        }
        return store;
    }

    private void loadSchemaOne(CompoundTag tag) {
        if (!hasCompoundList(tag, "sessions") || !hasCompoundList(tag, "quarantined")) {
            futureSchemaSnapshot = tag.copy();
            return;
        }
        for (Tag raw : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            try {
                Session session = decode(record, true, true);
                if (session.terminal() && !sessions.containsKey(session.sessionId())) {
                    sessions.put(session.sessionId(), session);
                } else {
                    // Schema 1 has no escrow table, so no active record can prove physical item custody.
                    quarantinedRecords.add(record.copy());
                }
            } catch (RuntimeException invalidRecord) {
                quarantinedRecords.add(record.copy());
            }
        }
        copyQuarantine(tag);
        setDirty();
    }

    private void loadSchemaTwo(CompoundTag tag, HolderLookup.Provider registries) {
        loadSchema(tag, registries, true, true);
        setDirty();
    }

    private void loadSchemaThree(CompoundTag tag, HolderLookup.Provider registries) {
        loadSchema(tag, registries, false, true);
        setDirty();
    }

    private void loadSchemaFour(CompoundTag tag, HolderLookup.Provider registries) {
        loadSchema(tag, registries, false, false);
    }

    private void loadSchema(CompoundTag tag, HolderLookup.Provider registries,
                            boolean allowMissingInsight, boolean allowMissingQiFlow) {
        if (!hasCompoundList(tag, "sessions") || !hasCompoundList(tag, "escrows")
                || !hasCompoundList(tag, "quarantined")) {
            futureSchemaSnapshot = tag.copy();
            return;
        }
        copyQuarantine(tag);
        Map<UUID, CompoundTag> rawSessions = new LinkedHashMap<>();
        Map<UUID, CompoundTag> rawEscrows = new LinkedHashMap<>();
        Map<UUID, Integer> sessionCounts = new java.util.HashMap<>();
        Map<String, Integer> activeCounts = new java.util.HashMap<>();
        Map<UUID, Integer> escrowCounts = new java.util.HashMap<>();
        for (Tag raw : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            try { sessionCounts.merge(UUID.fromString(record.getString("session")), 1, Integer::sum); }
            catch (RuntimeException ignored) { }
            try {
                Session session = decode(record, allowMissingInsight, allowMissingQiFlow);
                if (!session.terminal()) activeCounts.merge(session.ownerId() + "\u0000" + session.apertureId(), 1, Integer::sum);
            } catch (RuntimeException ignored) { }
        }
        for (Tag raw : tag.getList("escrows", Tag.TAG_COMPOUND)) {
            try { escrowCounts.merge(UUID.fromString(((CompoundTag) raw).getString("escrow")), 1, Integer::sum); }
            catch (RuntimeException ignored) { }
        }
        for (Tag raw : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            try {
                Session session = decode(record, allowMissingInsight, allowMissingQiFlow);
                if (sessionCounts.getOrDefault(session.sessionId(), 0) > 1
                        || (!session.terminal() && activeCounts.getOrDefault(session.ownerId() + "\u0000" + session.apertureId(), 0) > 1)) {
                    quarantinedRecords.add(record.copy());
                } else {
                    sessions.put(session.sessionId(), session);
                    rawSessions.put(session.sessionId(), record.copy());
                }
            } catch (RuntimeException invalidRecord) {
                quarantinedRecords.add(record.copy());
            }
        }
        for (Tag raw : tag.getList("escrows", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            try {
                EscrowEntry entry = decodeEscrow(record, registries);
                UUID id = entry.record().escrowId();
                if (escrowCounts.getOrDefault(id, 0) > 1) {
                    quarantinedRecords.add(record.copy());
                } else {
                    escrows.put(id, entry);
                    rawEscrows.put(id, record.copy());
                }
            } catch (RuntimeException invalidRecord) {
                quarantinedRecords.add(record.copy());
            }
        }
        validateLoadedLinks(rawSessions, rawEscrows);
    }

    private void validateLoadedLinks(Map<UUID, CompoundTag> rawSessions,
                                     Map<UUID, CompoundTag> rawEscrows) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if (!session.terminal() && !hasLinkedEscrow(session)) {
                sessions.remove(session.sessionId());
                CompoundTag raw = rawSessions.get(session.sessionId());
                if (raw != null) {
                    quarantinedRecords.add(raw.copy());
                }
            }
        }
        Iterator<Map.Entry<UUID, EscrowEntry>> iterator = escrows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, EscrowEntry> entry = iterator.next();
            Record escrow = entry.getValue().record();
            Session session = sessions.get(escrow.sessionId());
            boolean linked = session != null && session.ownerId().equals(escrow.ownerId())
                    && session.guEscrowId().equals(escrow.escrowId().toString());
            if (!linked) {
                iterator.remove();
                CompoundTag raw = rawEscrows.get(entry.getKey());
                if (raw != null) {
                    quarantinedRecords.add(raw.copy());
                }
            }
        }
    }

    private void copyQuarantine(CompoundTag tag) {
        for (Tag raw : tag.getList("quarantined", Tag.TAG_COMPOUND)) {
            quarantinedRecords.add(((CompoundTag) raw).copy());
        }
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

        InsightState insight = session.insight();
        CompoundTag insightTag = new CompoundTag();
        insightTag.putDouble("totalBudget", insight.totalBudget());
        insightTag.putDouble("spentBudget", insight.spentBudget());
        insightTag.putLong("nextSequence", insight.nextSequence());
        insightTag.putLong("lastRequestTick", insight.lastRequestTick());
        ListTag grants = new ListTag();
        for (KnowledgeGrant grant : insight.grants()) {
            CompoundTag grantTag = new CompoundTag();
            grantTag.putLong("sequence", grant.sequence());
            grantTag.putString("question", grant.questionId());
            grantTag.putString("knowledge", grant.knowledgeId());
            grantTag.putString("evidence", grant.evidenceRef());
            grantTag.putDouble("budgetSpent", grant.budgetSpent());
            grantTag.putDouble("completeness", grant.completeness());
            grantTag.putLong("grantedAt", grant.grantedAt());
            grants.add(grantTag);
        }
        insightTag.put("grants", grants);
        record.put("insight", insightTag);

        CompoundTag qi = new CompoundTag();
        qi.putDouble("tian", session.qi().tian());
        qi.putDouble("di", session.qi().di());
        qi.putDouble("ren", session.qi().ren());
        qi.putDouble("externalTian", session.qi().externalTian());
        qi.putDouble("externalDi", session.qi().externalDi());
        qi.putDouble("humanReserve", session.qi().humanReserve());
        qi.putLong("imbalanceTicks", session.qi().imbalanceTicks());
        qi.putLong("lastTick", session.qi().lastTick());
        record.put("qi", qi);
        return record;
    }

    private static CompoundTag encode(EscrowEntry entry) {
        Record escrow = entry.record();
        CompoundTag record = new CompoundTag();
        record.putString("escrow", escrow.escrowId().toString());
        record.putString("owner", escrow.ownerId().toString());
        record.putString("session", escrow.sessionId().toString());
        record.putString("sourceSlot", escrow.sourceSlot());
        record.putString("fingerprint", escrow.itemFingerprint());
        record.putString("state", escrow.state().name());
        record.putLong("revision", escrow.revision());
        record.put("stack", entry.stackTag());
        return record;
    }

    private static Session decode(CompoundTag record, boolean allowMissingInsight,
                                  boolean allowMissingQiFlow) {
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
        String[] qiFlowDoubleFields = {"externalTian", "externalDi", "humanReserve"};
        String[] qiFlowLongFields = {"imbalanceTicks", "lastTick"};
        int qiFlowFields = 0;
        for (String field : qiFlowDoubleFields) {
            if (qi.contains(field, Tag.TAG_DOUBLE)) qiFlowFields++;
            else if (qi.contains(field)) throw new IllegalArgumentException("invalid qi flow field: " + field);
        }
        for (String field : qiFlowLongFields) {
            if (qi.contains(field, Tag.TAG_LONG)) qiFlowFields++;
            else if (qi.contains(field)) throw new IllegalArgumentException("invalid qi flow field: " + field);
        }
        QiState qiState;
        if (qiFlowFields == 5) {
            qiState = new QiState(qi.getDouble("tian"), qi.getDouble("di"), qi.getDouble("ren"),
                    qi.getDouble("externalTian"), qi.getDouble("externalDi"), qi.getDouble("humanReserve"),
                    qi.getLong("imbalanceTicks"), qi.getLong("lastTick"));
        } else if (qiFlowFields == 0 && allowMissingQiFlow) {
            qiState = new QiState(qi.getDouble("tian"), qi.getDouble("di"), qi.getDouble("ren"));
        } else {
            throw new IllegalArgumentException("schema 4 session is missing qi flow state");
        }
        InsightState insight;
        if (record.contains("insight", Tag.TAG_COMPOUND)) {
            insight = decodeInsight(record.getCompound("insight"));
        } else if (allowMissingInsight) {
            insight = InsightState.initial(0.0, record.getLong("phaseStartedAt"));
        } else {
            throw new IllegalArgumentException("session is missing insight ledger");
        }
        return new Session(sessionId, ownerId, record.getString("aperture"), record.getString("profile"),
                Phase.valueOf(record.getString("phase")), Outcome.valueOf(record.getString("outcome")),
                record.getLong("phaseStartedAt"), record.getBoolean("brokenCommitted"),
                qiState,
                record.getString("guEscrow"), record.getString("apertureResult"),
                record.getLong("revision"), insight);
    }

    private static InsightState decodeInsight(CompoundTag tag) {
        requireTag(tag, "totalBudget", Tag.TAG_DOUBLE);
        requireTag(tag, "spentBudget", Tag.TAG_DOUBLE);
        requireTag(tag, "nextSequence", Tag.TAG_LONG);
        requireTag(tag, "lastRequestTick", Tag.TAG_LONG);
        requireTag(tag, "grants", Tag.TAG_LIST);
        Tag allGrantTags = tag.get("grants");
        if (!(allGrantTags instanceof ListTag allGrants)) {
            throw new IllegalArgumentException("insight grants must be a list");
        }
        ListTag rawGrants = tag.getList("grants", Tag.TAG_COMPOUND);
        if (rawGrants.size() != allGrants.size()) {
            throw new IllegalArgumentException("insight grants must be compounds");
        }
        List<KnowledgeGrant> grants = new ArrayList<>();
        for (Tag raw : rawGrants) {
            CompoundTag grant = (CompoundTag) raw;
            requireTag(grant, "sequence", Tag.TAG_LONG);
            requireTag(grant, "question", Tag.TAG_STRING);
            requireTag(grant, "knowledge", Tag.TAG_STRING);
            requireTag(grant, "evidence", Tag.TAG_STRING);
            requireTag(grant, "budgetSpent", Tag.TAG_DOUBLE);
            requireTag(grant, "completeness", Tag.TAG_DOUBLE);
            requireTag(grant, "grantedAt", Tag.TAG_LONG);
            grants.add(new KnowledgeGrant(grant.getLong("sequence"), grant.getString("question"),
                    grant.getString("knowledge"), grant.getString("evidence"), grant.getDouble("budgetSpent"),
                    grant.getDouble("completeness"), grant.getLong("grantedAt")));
        }
        return new InsightState(tag.getDouble("totalBudget"), tag.getDouble("spentBudget"),
                tag.getLong("nextSequence"), tag.getLong("lastRequestTick"), grants);
    }

    private static EscrowEntry decodeEscrow(CompoundTag record, HolderLookup.Provider registries) {
        requireTag(record, "escrow", Tag.TAG_STRING);
        requireTag(record, "owner", Tag.TAG_STRING);
        requireTag(record, "session", Tag.TAG_STRING);
        requireTag(record, "sourceSlot", Tag.TAG_STRING);
        requireTag(record, "fingerprint", Tag.TAG_STRING);
        requireTag(record, "state", Tag.TAG_STRING);
        requireTag(record, "revision", Tag.TAG_LONG);
        requireTag(record, "stack", Tag.TAG_COMPOUND);
        CompoundTag stack = record.getCompound("stack");
        if (!validStack(stack, registries)) {
            throw new IllegalArgumentException("invalid escrow stack");
        }
        Record escrow = new Record(UUID.fromString(record.getString("escrow")),
                UUID.fromString(record.getString("owner")), UUID.fromString(record.getString("session")),
                record.getString("sourceSlot"), record.getString("fingerprint"),
                State.valueOf(record.getString("state")), record.getLong("revision"));
        return new EscrowEntry(escrow, stack);
    }

    private static boolean validStack(CompoundTag stack, HolderLookup.Provider registries) {
        if (stack == null || registries == null
                || !stack.contains("count", Tag.TAG_INT) || stack.getInt("count") != 1) return false;
        try {
            // ItemStack.parse may return a partial result after a component decoding error.
            var complete = ItemStack.CODEC.parse(registries.createSerializationContext(
                    net.minecraft.nbt.NbtOps.INSTANCE), stack).result();
            return complete.filter(item -> !item.isEmpty() && item.getCount() == 1).isPresent()
                    && ItemStack.parse(registries, stack)
                    .filter(item -> !item.isEmpty() && item.getCount() == 1).isPresent();
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static void requireTag(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("invalid ascension save field: " + key);
        }
    }
}
