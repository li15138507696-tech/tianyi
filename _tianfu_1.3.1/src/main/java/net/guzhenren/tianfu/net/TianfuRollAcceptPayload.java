// src/main/java/net/guzhenren/tianfu/net/TianfuRollAcceptPayload.java
package net.guzhenren.tianfu.net;

import java.util.List;

import net.guzhenren.tianfu.TianfuApply;
import net.guzhenren.tianfu.TianfuData;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuNet;
import net.guzhenren.tianfu.TianfuRoll;
import net.guzhenren.tianfu.TianfuDifficulty;
import net.guzhenren.tianfu.TianfuOpening;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：接受随机候选，仅整批追加购买账；随后仍需玩家最终确认才生效并锁定。
 *
 * <p>随机与手动选择共享 V2 预算、互斥与补点限制，接受不会隐式选择难度或重复发点。
 *
 * <p>服务端兜底校验（包可被伪造）：
 * <ol>
 *   <li>待定区非空、本世未确认；</li>
 *   <li>结果基于最新账本逐条重新校验，不能信任生成时的净成本；</li>
 *   <li>净成本不得超过当前余额。</li>
 * </ol>
 */
public record TianfuRollAcceptPayload() implements CustomPacketPayload {
    public static final Type<TianfuRollAcceptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "roll_accept"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuRollAcceptPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            }, buf -> new TianfuRollAcceptPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** V2 契约：服务端重新验证整批和出身，使用Economy.acceptRoll原子提交且确认幂等，禁止只信netCost。未填充时拒绝执行，不写入玩家状态。 */
    public static void handle(TianfuRollAcceptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!TianfuOpening.hasOpenedQiao(player)) {
                player.displayClientMessage(Component.literal("需以希望蛊开窍后才能接受随机候选"), true);
                return;
            }
            if (TianfuData.isConfirmed(player) || TianfuData.isSettled(player)
                    || TianfuDifficulty.byIndex(TianfuData.difficulty(player)) == null) {
                TianfuNet.sendAll(player);
                return;
            }
            net.guzhenren.tianfu.v2.TianfuV2Store.migrate(player);
            List<String> pending = TianfuData.rollPending(player);
            if (!pending.isEmpty()) {
                if (TianfuData.applyRoll(player, pending, 0)) {
                    TianfuData.markDeferred(player);
                    TianfuData.clearRollPending(player);
                    player.displayClientMessage(Component.literal("随机候选已加入选择；请核对后最终确认，本世才会生效并锁定"), false);
                } else {
                    player.displayClientMessage(Component.literal("随机候选与当前选择或预算冲突，未扣点，候选已保留"), true);
                }
            }
            TianfuNet.sendAll(player);
        });
    }
}
