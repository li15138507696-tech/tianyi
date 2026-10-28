// src/main/java/net/guzhenren/tianfu/net/TianfuRevokePayload.java
package net.guzhenren.tianfu.net;

import net.guzhenren.tianfu.TianfuApply;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：撤销单个已购天赋条目（服务端权威门禁：凡体 / 词条可撤销；十绝体 / 机缘不可撤销）。
 */
public record TianfuRevokePayload(String id) implements CustomPacketPayload {
    public static final Type<TianfuRevokePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "revoke"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuRevokePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.id, 64),
            buf -> new TianfuRevokePayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** V2 契约：服务端线程只走Apply.revoke原价退款事务，成功成对回发状态。未填充时拒绝执行，不写入玩家状态。 */
    public static void handle(TianfuRevokePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }            net.guzhenren.tianfu.TianfuApply.revoke(player, payload.id());
            net.guzhenren.tianfu.TianfuNet.sendAll(player);
        });    }
}
