package net.guzhenren.tianyi.world;

import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.InsightState;
import net.guzhenren.tianyi.ascension.AscensionModel.KnowledgeGrant;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.GuEscrow;
import net.guzhenren.tianyi.ascension.GuEscrow.Action;
import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.GuEscrow.State;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AscensionStoreNbtTest {
    private static final UUID OWNER = UUID.fromString("14fda6e2-2d5f-4ce7-b2b4-c6707cc52c55");
    private static final UUID SESSION_ID = UUID.fromString("20c4ed93-83c5-41ee-a8df-28e75086cd60");
    private static final UUID ESCROW_ID = UUID.fromString("2c158483-1320-4553-84e1-ea5333bc8e83");
    private static net.minecraft.core.HolderLookup.Provider registries;

    private AscensionStoreNbtTest() {
    }

    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var encoded = new ItemStack(Items.STONE).save(registries);
        check(ItemStack.parse(registries, encoded).orElseThrow().getCount() == 1,
                "real registered vanilla stack decodes");
        roundTripsEscrowAndStackTag();
        roundTripsInsightLedger();
        rejectsInvalidSerializedStack();
        quarantinesDuplicateSessions();
        quarantinesConflictsAndInvalidStacks();
        migratesSchemaOneAndQuarantinesUnrecoverableRecords();
        migratesSchemaTwoWithEmptyInsightLedger();
        migratesSchemaThreeQiSnapshot();
        preservesFutureSchemaReadOnly();
        quarantinesSchemaThreeMissingInsightLedger();
        quarantinesSchemaFourMissingQiFlowFields();
        System.out.println("PASS ascension store schema, migration, and item NBT");
    }

    private static void quarantinesConflictsAndInvalidStacks() {
        Session session = preparing(SESSION_ID, OWNER, ESCROW_ID.toString());
        Record escrow = new Record(ESCROW_ID, OWNER, SESSION_ID, "source", "fingerprint", State.PREPARED, 0);
        AscensionStore fixture = new AscensionStore();
        check(fixture.createWithEscrow(session, escrow, 1, stackTag(), registries), "fixture");
        CompoundTag original = fixture.save(new CompoundTag(), registries);
        CompoundTag rawSession = original.getList("sessions", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag rawEscrow = original.getList("escrows", Tag.TAG_COMPOUND).getCompound(0);
        for (int kind = 0; kind < 3; kind++) {
            for (boolean reverse : new boolean[]{false, true}) {
                CompoundTag data = original.copy();
                ListTag sessions = new ListTag();
                ListTag escrows = new ListTag();
                for (int i = 0; i < 3; i++) {
                    int n = reverse ? 2 - i : i;
                    CompoundTag s = rawSession.copy();
                    CompoundTag e = rawEscrow.copy();
                    s.putInt("evidence", n); e.putInt("evidence", n);
                    if (kind == 1) s.putString("session", UUID.nameUUIDFromBytes(new byte[]{(byte)n}).toString());
                    if (kind == 2) { if (i == 0) sessions.add(rawSession.copy()); escrows.add(e); }
                    else { sessions.add(s); if (i == 0) escrows.add(rawEscrow.copy()); }
                }
                data.put("sessions", sessions); data.put("escrows", escrows);
                AscensionStore loaded = AscensionStore.load(data, registries);
                check(loaded.all().isEmpty() && loaded.findEscrow(ESCROW_ID).isEmpty(), "conflicts unavailable");
                ListTag quarantine = loaded.save(new CompoundTag(), registries).getList("quarantined", Tag.TAG_COMPOUND);
                for (Tag raw : sessions) check(containsExact(quarantine, (CompoundTag) raw), "session evidence retained");
                for (Tag raw : escrows) check(containsExact(quarantine, (CompoundTag) raw), "escrow evidence retained");
            }
        }
        for (int variant = 0; variant < 5; variant++) {
            CompoundTag stack = stackTag();
            switch (variant) {
                case 0 -> stack.remove("id");
                case 1 -> stack.putString("id", "missing:unknown");
                case 2 -> stack.putInt("count", 2);
                case 3 -> stack.putInt("count", 0);
                case 4 -> { CompoundTag components = new CompoundTag(); components.putString("minecraft:damage", "bad"); stack.put("components", components); }
            }
            check(!new AscensionStore().createWithEscrow(session, escrow, 1, stack, registries), "invalid creation " + variant);
            CompoundTag data = original.copy();
            CompoundTag badEscrow = rawEscrow.copy(); badEscrow.put("stack", stack);
            ListTag entries = new ListTag(); entries.add(badEscrow); data.put("escrows", entries);
            AscensionStore loaded = AscensionStore.load(data, registries);
            ListTag quarantine = loaded.save(new CompoundTag(), registries).getList("quarantined", Tag.TAG_COMPOUND);
            check(loaded.find(SESSION_ID).isEmpty() && loaded.findEscrow(ESCROW_ID).isEmpty()
                    && containsExact(quarantine, rawSession) && containsExact(quarantine, badEscrow), "invalid load retained " + variant);
        }
        check(!new AscensionStore().createWithEscrow(session, escrow, 1, stackTag(), null), "missing provider rejected");
        AscensionStore noProvider = AscensionStore.load(original, null);
        check(noProvider.find(SESSION_ID).isEmpty() && noProvider.findEscrow(ESCROW_ID).isEmpty(), "missing provider quarantines");
    }

    private static void rejectsInvalidSerializedStack() {
        Session session = preparing(SESSION_ID, OWNER, ESCROW_ID.toString());
        Record escrow = new Record(ESCROW_ID, OWNER, SESSION_ID, "source", "fingerprint", State.PREPARED, 0L);
        CompoundTag bad = new CompoundTag();
        bad.putString("id", "missing:unknown");
        bad.putInt("count", 1);
        check(!new AscensionStore().createWithEscrow(session, escrow, 1, bad, registries),
                "unknown registered item must be rejected on creation");
    }

    private static void quarantinesDuplicateSessions() {
        Session session = preparing(SESSION_ID, OWNER, ESCROW_ID.toString());
        CompoundTag raw = encodeSession(session);
        ListTag sessions = new ListTag();
        sessions.add(raw.copy()); sessions.add(raw.copy()); sessions.add(raw.copy());
        CompoundTag schema = new CompoundTag();
        schema.putInt("schema", 3); schema.put("sessions", sessions);
        AscensionStore fixture = new AscensionStore();
        Record escrow = new Record(ESCROW_ID, OWNER, SESSION_ID, "source", "fingerprint", State.PREPARED, 0);
        check(fixture.createWithEscrow(session, escrow, 1, stackTag(), registries), "fixture created");
        schema.put("escrows", fixture.save(new CompoundTag(), registries).getList("escrows", Tag.TAG_COMPOUND));
        schema.put("quarantined", new ListTag());
        AscensionStore store = AscensionStore.load(schema, registries);
        check(store.find(SESSION_ID).isEmpty(), "duplicate sessions unavailable");
        check(store.quarantinedCount() == 4, "all three duplicate session records and escrow quarantined");
    }

    private static void roundTripsEscrowAndStackTag() {
        Session session = preparing(SESSION_ID, OWNER, ESCROW_ID.toString());
        Record escrow = new Record(ESCROW_ID, OWNER, SESSION_ID, "menu-slot:0", "sha256:test",
                State.PREPARED, 0L);
        CompoundTag itemStack = stackTag();

        AscensionStore store = new AscensionStore();
        check(store.createWithEscrow(session, escrow, 1, itemStack, registries),
                "session and escrow are created together");

        Session invalidSession = preparing(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());
        Record invalidEscrow = new Record(UUID.fromString(invalidSession.guEscrowId()),
                invalidSession.ownerId(), invalidSession.sessionId(), "source", "fingerprint", State.PREPARED, 0L);
        AscensionStore invalidCountStore = new AscensionStore();
        check(!invalidCountStore.createWithEscrow(invalidSession, invalidEscrow, 2, itemStack, registries),
                "non-singular source stack is rejected");

        CompoundTag saved = store.save(new CompoundTag(), null);
        check(saved.getInt("schema") == 4, "schema is 4");
        check(saved.contains("sessions", Tag.TAG_LIST), "sessions list is saved");
        check(saved.contains("escrows", Tag.TAG_LIST), "escrows list is saved");
        AscensionStore loaded = AscensionStore.load(saved, registries);
        check(loaded.find(SESSION_ID).orElseThrow().equals(session), "session round-trips");
        check(loaded.findEscrow(ESCROW_ID).orElseThrow().equals(escrow), "escrow round-trips");
        CompoundTag firstCopy = loaded.findEscrowStack(ESCROW_ID).orElseThrow();
        check(firstCopy.equals(itemStack), "complete stack tag round-trips");
        firstCopy.putString("mutatedByCaller", "yes");
        check(!loaded.findEscrowStack(ESCROW_ID).orElseThrow().contains("mutatedByCaller"),
                "stack lookup returns a defensive copy");

        var transition = GuEscrow.advance(escrow, Action.BEGIN_REMOVAL, 0L, OWNER,
                Evidence.EXACT_SOURCE, false, true);
        check(transition.accepted(), "valid escrow transition is produced");
        check(loaded.compareAndSetEscrow(ESCROW_ID, 0L, transition.record()),
                "next escrow revision is accepted");
        var snapshot = new net.guzhenren.tianyi.ascension.ProgressionSnapshot(OWNER, 5, 4, 0, 100, 80, 120, 20);
        var held = net.guzhenren.tianyi.ascension.AscensionRecovery.reconcile(session, transition.record(),
                Evidence.EMPTY_SOURCE, snapshot);
        check(!loaded.compareAndSetPair(SESSION_ID, 0, session, ESCROW_ID, 1, transition.record()),
                "pair rejects no-op");
        Session changedWithoutRevision = new Session(session.sessionId(), session.ownerId(), session.apertureId(),
                session.profileId(), session.phase(), session.outcome(), session.phaseStartedAt(),
                session.brokenCommitted(), new QiState(4, 5, 6), session.guEscrowId(), "", session.revision());
        check(!loaded.compareAndSetPair(SESSION_ID, 0, changedWithoutRevision, ESCROW_ID, 1, held.escrow()),
                "pair rejects changed session at same revision");
        check(loaded.compareAndSetPair(SESSION_ID, 0, session, ESCROW_ID, 1, held.escrow()),
                "escrow-only recovery accepted atomically");
        var reloaded = AscensionStore.load(loaded.save(new CompoundTag(), registries), registries);
        check(reloaded.find(SESSION_ID).orElseThrow().equals(session)
                && reloaded.findEscrow(ESCROW_ID).orElseThrow().state() == State.HELD,
                "escrow-only recovery persists with session unchanged");
        // Keep the cancellation checks independent from the recovered pair.
        loaded = AscensionStore.load(saved, registries);
        check(loaded.compareAndSetEscrow(ESCROW_ID, 0L, transition.record()), "restore pending fixture");
        check(!loaded.compareAndSetEscrow(ESCROW_ID, 0L, escrow),
                "stale escrow revision is rejected");

        Session currentSession = loaded.find(SESSION_ID).orElseThrow();
        var sessionCancel = net.guzhenren.tianyi.ascension.AscensionMachine.advance(currentSession,
                new net.guzhenren.tianyi.ascension.AscensionModel.Command(
                        net.guzhenren.tianyi.ascension.AscensionModel.Action.CANCEL_PREPARATION,
                        currentSession.revision(), 11L, null,
                        new net.guzhenren.tianyi.ascension.AscensionModel.Validation(true, false, false, false),
                        currentSession.guEscrowId(), ""));
        var escrowCancel = GuEscrow.advance(transition.record(), Action.CANCEL, 1L, OWNER,
                Evidence.EXACT_SOURCE, false, true);
        check(!loaded.compareAndSetPair(SESSION_ID, 0L, sessionCancel.session(),
                ESCROW_ID, 0L, escrowCancel.record()),
                "pair update rejects a stale escrow revision");
        check(loaded.find(SESSION_ID).orElseThrow().equals(currentSession)
                        && loaded.findEscrow(ESCROW_ID).orElseThrow().equals(transition.record()),
                "stale pair update changes neither record");
        check(loaded.compareAndSetPair(SESSION_ID, 0L, sessionCancel.session(),
                ESCROW_ID, 1L, escrowCancel.record()),
                "session and escrow cancellation commits together");
    }

    private static void migratesSchemaOneAndQuarantinesUnrecoverableRecords() {
        Session completed = new Session(UUID.randomUUID(), OWNER, "old-aperture", "old-profile",
                Phase.FAILED, Outcome.FAILED_ALIVE, 18L, false, QiState.empty(), "", "", 3L);
        Session activeWithoutEscrow = preparing(UUID.randomUUID(), OWNER, "");
        CompoundTag completedTag = encodeSession(completed);
        CompoundTag activeTag = encodeSession(activeWithoutEscrow);
        completedTag.remove("insight");
        activeTag.remove("insight");
        CompoundTag malformedTag = new CompoundTag();
        CompoundTag existingQuarantine = new CompoundTag();
        existingQuarantine.putString("reason", "old evidence");

        CompoundTag schemaOne = new CompoundTag();
        schemaOne.putInt("schema", 1);
        ListTag sessions = new ListTag();
        sessions.add(completedTag.copy());
        sessions.add(activeTag.copy());
        sessions.add(malformedTag.copy());
        schemaOne.put("sessions", sessions);
        ListTag quarantine = new ListTag();
        quarantine.add(existingQuarantine.copy());
        schemaOne.put("quarantined", quarantine);

        AscensionStore migrated = AscensionStore.load(schemaOne, null);
        check(migrated.find(completed.sessionId()).orElseThrow().equals(completed),
                "terminal schema 1 history is retained");
        check(migrated.find(activeWithoutEscrow.sessionId()).isEmpty(),
                "active schema 1 session without an escrow cannot resume");
        check(!migrated.isReadOnly(), "schema 1 migration remains writable");
        CompoundTag saved = migrated.save(new CompoundTag(), null);
        check(saved.getInt("schema") == 4, "migrated save uses schema 4");
        ListTag migratedQuarantine = saved.getList("quarantined", Tag.TAG_COMPOUND);
        check(migratedQuarantine.size() == 3, "legacy, malformed, and prior quarantine records remain");
        check(containsExact(migratedQuarantine, activeTag), "active legacy evidence is retained exactly");
        check(containsExact(migratedQuarantine, malformedTag), "malformed legacy evidence is retained exactly");
        check(containsExact(migratedQuarantine, existingQuarantine), "existing quarantine evidence is retained");
    }

    private static void migratesSchemaTwoWithEmptyInsightLedger() {
        Session session = preparing(SESSION_ID, OWNER, ESCROW_ID.toString());
        Record escrow = new Record(ESCROW_ID, OWNER, SESSION_ID, "source", "fingerprint", State.PREPARED, 0L);
        AscensionStore fixture = new AscensionStore();
        check(fixture.createWithEscrow(session, escrow, 1, stackTag(), registries), "schema 2 fixture");
        CompoundTag schemaTwo = fixture.save(new CompoundTag(), registries);
        schemaTwo.putInt("schema", 2);
        schemaTwo.getList("sessions", Tag.TAG_COMPOUND).getCompound(0).remove("insight");

        AscensionStore loaded = AscensionStore.load(schemaTwo, registries);
        Session migrated = loaded.find(SESSION_ID).orElseThrow();
        check(migrated.insight().totalBudget() == 0.0 && migrated.insight().grants().isEmpty(),
                "schema 2 session receives an empty insight ledger");
        check(loaded.findEscrow(ESCROW_ID).isPresent(), "schema 2 escrow remains linked");
        check(loaded.save(new CompoundTag(), registries).getInt("schema") == 4,
                "schema 2 is rewritten as schema 4");
    }

    private static void migratesSchemaThreeQiSnapshot() {
        Session session = preparing(SESSION_ID, OWNER, ESCROW_ID.toString());
        Record escrow = new Record(ESCROW_ID, OWNER, SESSION_ID, "source", "fingerprint", State.PREPARED, 0L);
        AscensionStore fixture = new AscensionStore();
        check(fixture.createWithEscrow(session, escrow, 1, stackTag(), registries), "schema 3 fixture");
        CompoundTag schemaThree = fixture.save(new CompoundTag(), registries);
        schemaThree.putInt("schema", 3);
        CompoundTag qi = schemaThree.getList("sessions", Tag.TAG_COMPOUND).getCompound(0).getCompound("qi");
        qi.remove("externalTian");
        qi.remove("externalDi");
        qi.remove("humanReserve");
        qi.remove("imbalanceTicks");
        qi.remove("lastTick");

        AscensionStore loaded = AscensionStore.load(schemaThree, registries);
        QiState migratedQi = loaded.find(SESSION_ID).orElseThrow().qi();
        check(migratedQi.tian() == 1.0 && migratedQi.di() == 2.0 && migratedQi.ren() == 3.0
                        && migratedQi.externalTian() == 0.0 && migratedQi.externalDi() == 0.0
                        && migratedQi.humanReserve() == 0.0 && migratedQi.lastTick() == 0L,
                "schema 3 gathered qi receives empty external reservoirs and tick state");
        check(loaded.save(new CompoundTag(), registries).getInt("schema") == 4,
                "schema 3 is rewritten as schema 4");
    }

    private static void preservesFutureSchemaReadOnly() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 99);
        future.putString("futureField", "do not rewrite");
        ListTag unknown = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("futureValue", 42);
        unknown.add(entry);
        future.put("unknownRecords", unknown);

        AscensionStore loaded = AscensionStore.load(future, null);
        check(loaded.isReadOnly(), "future schema is read-only");
        check(loaded.quarantinedCount() == 1, "future schema is represented as one quarantine item");
        check(loaded.save(new CompoundTag(), null).equals(future), "future schema is preserved exactly");
        check(!loaded.create(preparing(UUID.randomUUID(), OWNER, "")), "future schema refuses writes");
    }

    private static void quarantinesSchemaThreeMissingInsightLedger() {
        CompoundTag schemaTwo = new CompoundTag();
        schemaTwo.putInt("schema", 3);
        ListTag sessions = new ListTag();
        CompoundTag corrupt = encodeSession(preparing(SESSION_ID, OWNER, ESCROW_ID.toString()));
        corrupt.remove("insight");
        sessions.add(corrupt.copy());
        schemaTwo.put("sessions", sessions);
        schemaTwo.put("escrows", new ListTag());
        schemaTwo.put("quarantined", new ListTag());

        AscensionStore loaded = AscensionStore.load(schemaTwo, null);
        check(loaded.quarantinedCount() == 1,
                "corrupt record is isolated without rejecting other schema data");
        check(containsExact(loaded.save(new CompoundTag(), null).getList("quarantined", Tag.TAG_COMPOUND), corrupt),
                "corrupt record remains available for inspection");
    }

    private static void quarantinesSchemaFourMissingQiFlowFields() {
        CompoundTag schemaFour = new CompoundTag();
        schemaFour.putInt("schema", 4);
        ListTag sessions = new ListTag();
        CompoundTag corrupt = encodeSession(preparing(SESSION_ID, OWNER, ESCROW_ID.toString()));
        corrupt.getCompound("qi").remove("humanReserve");
        sessions.add(corrupt.copy());
        schemaFour.put("sessions", sessions);
        schemaFour.put("escrows", new ListTag());
        schemaFour.put("quarantined", new ListTag());

        AscensionStore loaded = AscensionStore.load(schemaFour, null);
        check(loaded.quarantinedCount() == 1,
                "schema 4 session without required qi flow state is isolated");
        check(containsExact(loaded.save(new CompoundTag(), null).getList("quarantined", Tag.TAG_COMPOUND), corrupt),
                "schema 4 qi evidence remains available for inspection");
    }

    private static void roundTripsInsightLedger() {
        KnowledgeGrant grant = new KnowledgeGrant(0L, "study_strength_path", "knowledge.strength_path.topic",
                "第三卷第二百二十三章《枭雄黑楼兰》", 0.5, 0.5, 25L);
        InsightState insight = new InsightState(2.0, 0.5, 1L, 25L, java.util.List.of(grant));
        UUID sessionId = UUID.randomUUID();
        UUID escrowId = UUID.randomUUID();
        Session preparing = new Session(sessionId, OWNER, "insight-aperture", "test", Phase.PREPARING,
                Outcome.RUNNING, 10L, false, QiState.empty(), escrowId.toString(), "", 0L,
                InsightState.initial(2.0, 10L));
        Record escrow = new Record(escrowId, OWNER, sessionId, "source", "fingerprint", State.PREPARED, 0L);
        AscensionStore store = new AscensionStore();
        check(store.createWithEscrow(preparing, escrow, 1, stackTag(), registries), "linked session fixture");
        Session session = new Session(sessionId, OWNER, "insight-aperture", "test", Phase.ABSORBING,
                Outcome.RUNNING, 10L, true, new QiState(3.0, 2.0, 1.0, 7.0, 8.0,
                9.0, 2L, 24L), escrowId.toString(), "", 1L, insight);
        check(store.compareAndSet(sessionId, 0L, session), "insight session revision is stored");
        CompoundTag saved = store.save(new CompoundTag(), registries);
        Session loaded = AscensionStore.load(saved, registries).find(session.sessionId()).orElseThrow();
        check(loaded.equals(session), "insight budget, sequence, topic and completeness round-trip");
        check(loaded.qi().equals(session.qi()), "external reservoirs, human reserve, and qi tick round-trip");
        var profile = new net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile(
                "test", "test evidence", true, java.util.Set.of(), 2.0, 0.75);
        var questions = net.guzhenren.tianyi.ascension.InsightCatalog.evidenceBackedTopics();
        var replay = net.guzhenren.tianyi.ascension.InsightCatalog.resolve(loaded,
                new net.guzhenren.tianyi.ascension.InsightCatalog.Request(1L, 0L, 26L, "study_strength_path"),
                questions, profile, true);
        check(replay.rejection() == net.guzhenren.tianyi.ascension.InsightCatalog.Rejection.INVALID_SEQUENCE,
                "reload does not reset the insight request sequence");
        var duplicate = net.guzhenren.tianyi.ascension.InsightCatalog.resolve(loaded,
                new net.guzhenren.tianyi.ascension.InsightCatalog.Request(1L, 1L, 26L, "study_strength_path"),
                questions, profile, true);
        check(duplicate.rejection()
                == net.guzhenren.tianyi.ascension.InsightCatalog.Rejection.QUESTION_ALREADY_GRANTED,
                "reload preserves the already granted topic");
    }

    private static Session preparing(UUID sessionId, UUID ownerId, String escrowId) {
        return new Session(sessionId, ownerId, "aperture", "ordinary", Phase.PREPARING,
                Outcome.RUNNING, 10L, false, new QiState(1.0, 2.0, 3.0), escrowId, "", 0L);
    }

    private static CompoundTag stackTag() {
        return (CompoundTag) new ItemStack(Items.STONE).save(registries);
    }

    private static CompoundTag encodeSession(Session session) {
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
        insightTag.put("grants", new ListTag());
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

    private static boolean containsExact(ListTag records, CompoundTag expected) {
        for (Tag record : records) {
            if (record.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
