// src/main/java/net/guzhenren/tianfu/net/TianfuConfirmPayload.java
package net.guzhenren.tianfu.net;

import java.util.Set;

import net.guzhenren.tianfu.TianfuApply;
import net.guzhenren.tianfu.TianfuData;
import net.guzhenren.tianfu.TianfuDifficulty;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuNet;
import net.guzhenren.tianfu.TianfuPool;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：最终确认本世天赋选择。无负载。
 *
 * <p>一经确认，本世的购买 / 撤销 / 难度全部冻结，直到寿元为0转世（{@code wipeAll} 清除标记）。
 * 门禁：必须已选开局难度（未选难度就锁定会让玩家永远无法购买）。
 */
public record TianfuConfirmPayload() implements CustomPacketPayload {
    public static final Type<TianfuConfirmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "confirm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuConfirmPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            }, buf -> new TianfuConfirmPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** V2 契约：预检出身与硬代价后幂等结算再锁定；确认认可原生资质、已购SIJUE或历史机缘。 */
    public static void handle(TianfuConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (!net.guzhenren.tianfu.TianfuOpening.hasOpenedQiao(player)) {
                player.displayClientMessage(Component.literal("需以希望蛊开窍后才能最终确认"), true);
                return;
            }
            net.guzhenren.tianfu.v2.TianfuV2Store.migrate(player);
            net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot = net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
            if (snapshot.confirmed() && net.guzhenren.tianfu.TianfuData.isSettled(player)) {
                net.guzhenren.tianfu.TianfuNet.sendAll(player);
                return;
            }
            var rejection = net.guzhenren.tianfu.TianfuApply.confirmFinal(player);
            if (rejection == net.guzhenren.tianfu.v2.TianfuSelection.Rejection.NONE) {
                player.displayClientMessage(Component.literal("本世命格已确认，所选天赋正式生效，本世不能再更改"), false);
                // 结算成功：请客户端播一次「天命落定」全屏后处理（未装 Veil 时客户端自动忽略）。
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new net.guzhenren.tianfu.net.TianfuVfxPayload(0));
            } else {
                String reason = switch (rejection) {
                    case NOT_OPENED -> "请先以希望蛊开窍";
                    case NO_DIFFICULTY -> "请先选择开局难度";
                    case CONFLICT -> "需要本体资质、十绝体或历史机缘，且选择不能互斥";
                    case UNSAFE_LIFESPAN -> "寿元不足以承受命烛残尽，请撤销该项后重试";
                    case STALE_REVISION -> "选择已更新，请核对后重试";
                    case LOCKED -> "本世已锁定";
                    default -> "存在无法安全兑现的选择，未锁定，请检查已购项目";
                };
                player.displayClientMessage(Component.literal("无法最终确认：" + reason), true);
            }
            net.guzhenren.tianfu.TianfuNet.sendAll(player);
        });    }
}
