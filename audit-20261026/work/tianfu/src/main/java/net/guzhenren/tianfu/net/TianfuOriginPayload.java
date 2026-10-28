// src/main/java/net/guzhenren/tianfu/net/TianfuOriginPayload.java
package net.guzhenren.tianfu.net;

import net.guzhenren.tianfu.TianfuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S 出身重选意图；客户端不上传成本或本体字段。 */
public record TianfuOriginPayload(String regionId, String backgroundId, long expectedRevision) implements CustomPacketPayload {
    public static final Type<TianfuOriginPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "v2_origin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuOriginPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.regionId(), 32);
                buf.writeUtf(payload.backgroundId(), 32);
                buf.writeLong(payload.expectedRevision());
            }, buf -> new TianfuOriginPayload(buf.readUtf(32), buf.readUtf(32), buf.readLong()));
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    /**
     * 逻辑服务端线程核对开窍、确认锁、revision、两组ID和统一24上限，成功后发旧状态及V2状态。
     * @param payload 不可信客户端意图；@param context NeoForge网络上下文
     * @throws IllegalStateException 仅内部损坏；普通拒绝应返回消息并回发权威状态
     */
    public static void handle(TianfuOriginPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (!net.guzhenren.tianfu.TianfuOpening.hasOpenedQiao(player)) {
                return;
            }
            if (net.guzhenren.tianfu.TianfuData.isConfirmed(player)
                    || net.guzhenren.tianfu.TianfuData.isSettled(player)) {
                net.guzhenren.tianfu.TianfuNet.sendAll(player);
                return;
            }
            net.guzhenren.tianfu.v2.TianfuV2Store.migrate(player);
            net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                    net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
            if (snapshot.revision() != payload.expectedRevision()) {
                net.guzhenren.tianfu.TianfuNet.sendAll(player);
                return;
            }
            net.guzhenren.tianfu.v2.TianfuSelection.Result result =
                    net.guzhenren.tianfu.v2.TianfuEconomy.selectOrigin(snapshot,
                            new net.guzhenren.tianfu.v2.TianfuSelection.Origin(
                                    payload.regionId(), payload.backgroundId()));
            if (result.ok()) {
                net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                        new net.guzhenren.tianfu.v2.TianfuSelection.Change(snapshot.revision(), result.snapshot()));
            }
            net.guzhenren.tianfu.TianfuNet.sendAll(player);
        });    }
}
