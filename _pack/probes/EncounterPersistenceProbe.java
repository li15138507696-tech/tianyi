import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.guzhenren.tianyi.encounter.EncounterRun;
import net.guzhenren.tianyi.world.EncounterState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Real Minecraft NBT persistence, without bootstrapping registries or constructing a world. */
public final class EncounterPersistenceProbe {
    private static final UUID OWNER = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    public static void main(String[] args) throws Exception {
        EncounterRun run = new EncounterRun(OWNER.toString());
        run.open("b0000000-0000-0000-0000-000000000002", "minecraft:overworld", 42,
                "guzhenren:hui_lang", "灰狼", "近战追击", 32, 32, 128);
        run.investigate();
        run.startBattle("c0000000-0000-0000-0000-000000000003", 3600);
        run.elapse(380); run.outside(true, 40);
        CompoundTag row = new CompoundTag();
        run.snapshot().forEach(row::putString);
        ListTag rows = new ListTag(); rows.add(row);
        CompoundTag root = new CompoundTag(); root.putInt("schema", 1); root.put("players", rows);
        EncounterState state = EncounterState.load(root, null);
        EncounterState loaded = roundtrip(state);
        check(loaded.existing(OWNER).snapshot().equals(run.snapshot()), "fight fields and Unicode survive real compressed NBT save/load");
        check(loaded.existing(UUID.randomUUID()) == null, "unknown owner cannot read another player's run");
        EncounterRun loss = loaded.existing(OWNER);
        check(loss.lose("挑战中死亡", 600), "loss after reload");
        loss.elapse(600);
        EncounterRun pending = roundtrip(loaded).existing(OWNER);
        check(pending.penaltyPending && !pending.due(), "death penalty survives restart and blocks next encounter");
        check(pending.consumePenalty() && !pending.consumePenalty(), "penalty consumed once");
        EncounterState consumed = EncounterState.load(single(pending), null);
        check(!roundtrip(consumed).existing(OWNER).consumePenalty(), "consumed penalty stays consumed after a second restart");
        check(roundtrip(consumed).existing(OWNER).remainingTicks == 0, "offline save/load does not advance time");
        CompoundTag future = single(pending); future.putInt("schema", 999);
        future.putString("futureEvidence", "必须原样保留");
        preserved(future, "future schema");
        CompoundTag bad = single(pending);
        bad.getList("players", 10).getCompound(0).putString("stake", "-1");
        preserved(bad, "corrupt row");
        CompoundTag missing = single(pending);
        missing.getList("players", 10).getCompound(0).remove("baseGranted");
        preserved(missing, "missing transaction flag");
        CompoundTag duplicate = single(pending);
        duplicate.getList("players", 10).add(duplicate.getList("players", 10).getCompound(0).copy());
        preserved(duplicate, "duplicate owner");
        CompoundTag wrongList = new CompoundTag();
        ListTag strings = new ListTag(); strings.add(net.minecraft.nbt.StringTag.valueOf("raw evidence"));
        wrongList.put("players", strings); preserved(wrongList, "wrong row type");
        for (String field : new String[] {"warning", "message"}) {
            CompoundTag wrongField = single(pending);
            wrongField.getList("players", 10).getCompound(0).putInt(field, 17);
            preserved(wrongField, "non-string " + field);
        }
        CompoundTag waiting = single(new EncounterRun(OWNER.toString()));
        waiting.getList("players", 10).getCompound(0).putInt("id", 17);
        preserved(waiting, "non-string empty-allowed waiting id");
        check(loaded.save(new CompoundTag(), null).getInt("schema") == 2, "known schema migrates to 2 without effects");
        System.out.println("EncounterPersistenceProbe: PASS (real Minecraft CompoundTag + compressed NbtIo)");
    }
    private static void preserved(CompoundTag root, String reason) throws Exception {
        EncounterState state = EncounterState.load(root, null);
        check(state.quarantined() && state.of(OWNER) == null && state.existing(OWNER) == null,
                reason + " blocks runtime effects and new records");
        check(state.save(new CompoundTag(), null).equals(root), reason + " retains original NBT");
        check(roundtrip(state).save(new CompoundTag(), null).equals(root), reason + " survives compressed roundtrip");
    }
    private static CompoundTag single(EncounterRun r) {
        CompoundTag row = new CompoundTag(); r.snapshot().forEach(row::putString);
        ListTag rows = new ListTag(); rows.add(row);
        CompoundTag root = new CompoundTag(); root.put("players", rows); return root;
    }
    private static EncounterState roundtrip(EncounterState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(state.save(new CompoundTag(), null), bytes);
        CompoundTag disk = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
        return EncounterState.load(disk, null);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
}
