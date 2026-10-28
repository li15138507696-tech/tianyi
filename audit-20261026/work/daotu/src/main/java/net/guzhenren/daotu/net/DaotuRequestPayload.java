package net.guzhenren.daotu.net;

import net.guzhenren.daotu.DaotuMod;
import net.guzhenren.daotu.DaotuNet;
import net.guzhenren.daotu.DaotuOpening;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：请求道途状态（按 M 时发）。服务端校验开窍后回发 {@link DaotuStatePayload}。
 *
 * <p>门禁：未以希望蛊开窍（本体 scoreboard {@code kaiqiao} == 0）时拒绝，
 * 只回一条提示——希望蛊是唯一开窍开关。
 */
public record DaotuRequestPayload() implements CustomPacketPayload {
    public static final Type<DaotuRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DaotuMod.MODID, "request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DaotuRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            }, buf -> new DaotuRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DaotuRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                if (!DaotuOpening.hasOpenedQiao(sp)) {
                    sp.displayClientMessage(
                            Component.literal("§7需以希望蛊开窍，方可踏上仙途"), false);
                    return;
                }
                // 玩家按 M 主动请求：客户端一定打开界面
                DaotuNet.openState(sp);
            }
        });
    }
}
