package net.guzhenren.tianyi.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Intent only; ownership, phase and costs are checked by the server. */
public record EncounterActionPayload(Action action, String encounterId) implements CustomPacketPayload {
    public enum Action { INVESTIGATE, WAGER, FINISH }
    public static final Type<EncounterActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tianyi", "encounter_action"));
    public static final StreamCodec<FriendlyByteBuf, EncounterActionPayload> CODEC =
            StreamCodec.of(EncounterActionPayload::write, EncounterActionPayload::read);
    public EncounterActionPayload {
        if (action == null || encounterId == null || encounterId.length() > 80)
            throw new IllegalArgumentException("Invalid encounter action");
    }
    private static void write(FriendlyByteBuf b, EncounterActionPayload p) {
        b.writeVarInt(p.action.ordinal()); b.writeUtf(p.encounterId, 80);
    }
    private static EncounterActionPayload read(FriendlyByteBuf b) {
        int ordinal = b.readVarInt();
        if (ordinal < 0 || ordinal >= Action.values().length)
            throw new IllegalArgumentException("Unknown encounter action");
        return new EncounterActionPayload(Action.values()[ordinal], b.readUtf(80));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
