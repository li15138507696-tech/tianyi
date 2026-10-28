// src/main/java/net/guzhenren/tianfu/net/TianfuBuyPayload.java
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
 * C2S：购买单个天赋条目。服务端权威校验（余额 / 槽位 / 去重）后扣点生效，
 * 成功即回发最新 {@link TianfuStatePayload} 刷新界面。
 */
public record TianfuBuyPayload(String id) implements CustomPacketPayload {
    public static final Type<TianfuBuyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "buy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuBuyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.id, 64),
            buf -> new TianfuBuyPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** V2 契约：服务端线程只走Apply.buy统一V2事务，失败零写入，成功成对回发旧state和v2_state。未填充时拒绝执行，不写入玩家状态。 */
    public static void handle(TianfuBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }            net.guzhenren.tianfu.TianfuApply.buy(player, payload.id());
            net.guzhenren.tianfu.TianfuNet.sendAll(player);
        });    }
}
