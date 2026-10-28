package net.guzhenren.daotu.net;

import net.guzhenren.daotu.DaotuDraw;
import net.guzhenren.daotu.DaotuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：玩家在待选面板里选定了第 {@code candidateIndex} 个候选。
 *
 * <p><b>v1.4.0</b>：语义从「重选」改成「一次抉择」。选定即扣池、发奖、锁定，
 * 之后无法更改（见 {@link DaotuDraw#choose}）。
 *
 * <p>{@code drawIndex} 仍然带上，用于<b>防止旧包乱序</b>：若玩家连点两次、
 * 或网络延迟导致旧包后到，服务端发现 {@code drawIndex} 与当前
 * {@code offerDraw} 不符就丢弃，不会把新一批候选误当成旧的选掉。
 *
 * <p><b>不信任客户端数值</b>：服务端从 {@code run/offer} 读回候选列表，
 * 客户端发来的只有下标。
 */
public record DaotuPickPayload(int drawIndex, int candidateIndex)
        implements CustomPacketPayload {

    public static final Type<DaotuPickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DaotuMod.MODID, "pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DaotuPickPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.drawIndex);
                        buf.writeVarInt(p.candidateIndex);
                    },
                    buf -> new DaotuPickPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DaotuPickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                // 抽次对不上说明是过期的包，直接丢弃
                if (payload.drawIndex() != DaotuDraw.pendingDrawIndex(sp)) {
                    DaotuMod.LOGGER.debug("丢弃过期选择包：包内 {} / 当前 {}",
                            payload.drawIndex(), DaotuDraw.pendingDrawIndex(sp));
                    return;
                }
                DaotuDraw.choose(sp, payload.candidateIndex());
            }
        });
    }
}
