package net.guzhenren.tianfu.net;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianfu.TianfuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：天赋界面状态（档位 / 自由点余额 / 本世难度 / 已购列表 / 是否已最终确认 /
 * 已摇次数 / 待接受的随机结果）。
 *
 * <p>池子与难度定义是代码常量，客户端直接读 {@code TianfuPool} / {@code TianfuDifficulty}。
 */
public record TianfuStatePayload(int kongqiao, int points, int difficulty, List<String> purchased,
                                 boolean confirmed, int rolls, List<String> pending)
        implements CustomPacketPayload {
    public static final Type<TianfuStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.kongqiao);
                buf.writeVarInt(payload.points);
                buf.writeVarInt(payload.difficulty);
                buf.writeCollection(payload.purchased, (b, s) -> b.writeUtf(s));
                buf.writeBoolean(payload.confirmed);
                buf.writeVarInt(payload.rolls);
                buf.writeCollection(payload.pending, (b, s) -> b.writeUtf(s));
            },
            buf -> {
                int kongqiao = buf.readVarInt();
                int points = buf.readVarInt();
                int difficulty = buf.readVarInt();
                List<String> purchased = new ArrayList<>(buf.readCollection(ArrayList::new, b -> b.readUtf()));
                boolean confirmed = buf.readBoolean();
                int rolls = buf.readVarInt();
                List<String> pending = new ArrayList<>(buf.readCollection(ArrayList::new, b -> b.readUtf()));
                return new TianfuStatePayload(kongqiao, points, difficulty, purchased, confirmed, rolls, pending);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TianfuStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.guzhenren.tianfu.client.TianfuClientHandler.open(payload));
    }
}
