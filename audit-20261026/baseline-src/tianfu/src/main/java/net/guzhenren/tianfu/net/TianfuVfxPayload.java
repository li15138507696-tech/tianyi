// src/main/java/net/guzhenren/tianfu/net/TianfuVfxPayload.java
package net.guzhenren.tianfu.net;

import net.guzhenren.tianfu.TianfuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：请求客户端播放一次「天命落定」演出。
 *
 * <p>本类在<b>公共侧</b>，只记录一个待消费的变体号，不引用任何客户端类；
 * 客户端 tick 时自己取走并驱动 {@code TianfuVfxBridge}。
 *
 * @param variant 0 = 天命落定（确认结算完成）
 */
public record TianfuVfxPayload(int variant) implements CustomPacketPayload {
    public static final Type<TianfuVfxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuVfxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.variant),
            buf -> new TianfuVfxPayload(buf.readVarInt()));

    /** 待播放的变体号；-1 表示没有。 */
    private static volatile int pending = -1;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端线程登记一次待播放。 */
    public static void handle(TianfuVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> pending = payload.variant);
    }

    /** 客户端 tick 取走并清空；没有则返回 -1。 */
    public static int consume() {
        int value = pending;
        pending = -1;
        return value;
    }
}