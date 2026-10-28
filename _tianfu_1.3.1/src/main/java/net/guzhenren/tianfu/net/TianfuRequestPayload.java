// src/main/java/net/guzhenren/tianfu/net/TianfuRequestPayload.java
package net.guzhenren.tianfu.net;

import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuNet;
import net.guzhenren.tianfu.TianfuOpening;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：请求打开「天赋」合一界面。无负载，服务端按当前状态回发 {@link TianfuStatePayload}。
 *
 * <p>门禁：未以希望蛊开窍（本体 scoreboard {@code kaiqiao} == 0）时拒绝，仅回一条提示——
 * 希望蛊是天赋系统的唯一开窍开关。
 */
public record TianfuRequestPayload() implements CustomPacketPayload {
    public static final Type<TianfuRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            }, buf -> new TianfuRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** V2 契约：验证希望蛊开窍门禁，读迁移后的权威快照并成对发送旧state和v2_state。未填充时拒绝执行，不写入玩家状态。 */
    public static void handle(TianfuRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }            if (!net.guzhenren.tianfu.TianfuOpening.hasOpenedQiao(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("需以希望蛊开窍后才能选择天赋"), true);
                return;
            }
            net.guzhenren.tianfu.v2.TianfuV2Store.migrate(player);
            net.guzhenren.tianfu.TianfuNet.sendAll(player);
        });    }
}
