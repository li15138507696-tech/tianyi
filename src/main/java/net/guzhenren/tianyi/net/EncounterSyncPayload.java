package net.guzhenren.tianyi.net;

import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner-specific current encounter. No global encounters are sent to the client. */
public record EncounterSyncPayload(String id, String phase, String dimension, long position,
        int secondsLeft, String enemyId, String enemyName, int baseReward, int stake, int bonus,
        String message) implements CustomPacketPayload {
    private static final Set<String> PHASES = Set.of("WAITING", "SEEK", "READY", "FIGHT", "WON", "LOST", "FINISHED");
    public static final Type<EncounterSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tianyi", "encounter"));
    public static final StreamCodec<FriendlyByteBuf, EncounterSyncPayload> CODEC =
            StreamCodec.of(EncounterSyncPayload::write, EncounterSyncPayload::read);

    public EncounterSyncPayload {
        check(id, 80); check(phase, 16); check(dimension, 128);
        check(enemyId, 128); check(enemyName, 128); check(message, 1024);
        if (!PHASES.contains(phase)) throw new IllegalArgumentException("Unknown encounter phase");
        if (secondsLeft < 0 || baseReward < 0 || stake < 0 || bonus < 0)
            throw new IllegalArgumentException("Negative encounter amount or duration");
    }
    private static void check(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("Invalid encounter string");
    }
    private static void write(FriendlyByteBuf b, EncounterSyncPayload p) {
        b.writeUtf(p.id, 80); b.writeUtf(p.phase, 16); b.writeUtf(p.dimension, 128);
        b.writeLong(p.position); b.writeVarInt(p.secondsLeft);
        b.writeUtf(p.enemyId, 128); b.writeUtf(p.enemyName, 128);
        b.writeVarInt(p.baseReward); b.writeVarInt(p.stake); b.writeVarInt(p.bonus);
        b.writeUtf(p.message, 1024);
    }
    private static EncounterSyncPayload read(FriendlyByteBuf b) {
        return new EncounterSyncPayload(b.readUtf(80), b.readUtf(16), b.readUtf(128),
                b.readLong(), b.readVarInt(), b.readUtf(128), b.readUtf(128),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(1024));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
