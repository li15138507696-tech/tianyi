// src/main/java/net/guzhenren/tianfu/net/TianfuRollPayload.java
package net.guzhenren.tianfu.net;

import java.util.List;
import java.util.Random;

import net.guzhenren.tianfu.TianfuData;
import net.guzhenren.tianfu.TianfuDifficulty;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuNet;
import net.guzhenren.tianfu.TianfuOpening;
import net.guzhenren.tianfu.TianfuRoll;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：「命运由天」摇一次。首次摇与重摇共用本包。
 *
 * <p>V2 先选择难度，再以当前余额生成候选；本包不选择难度、不发放点数。
 * {@code difficultyId} 仅为旧协议兼容字段，不能用来更改已经选择的难度。
 *
 * <p>门禁：已开窍；未最终确认；重摇次数未用尽（{@link TianfuRoll#MAX_ROLLS}）。
 * 已购内容保留，候选需能与当前账本一起通过 V2 整批校验。
 *
 * <p>结果只写进 {@code rollpending} 待定区，<b>不</b>落 {@code purchased}：
 * 十绝体 / 机缘不可撤销，落库后重摇就没有退路了。
 */
public record TianfuRollPayload(String difficultyId) implements CustomPacketPayload {
    public static final Type<TianfuRollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "roll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuRollPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.difficultyId, 32),
            buf -> new TianfuRollPayload(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Only a valid generated candidate consumes one of the three server-stored rolls. */
    public static void handle(TianfuRollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!TianfuOpening.hasOpenedQiao(player)) {
                player.displayClientMessage(Component.literal("需以希望蛊开窍后才能随机天赋"), true);
                return;
            }
            if (TianfuData.isConfirmed(player) || TianfuData.isSettled(player)) {
                TianfuNet.sendAll(player);
                return;
            }
            if (TianfuDifficulty.byIndex(TianfuData.difficulty(player)) == null) {
                player.displayClientMessage(Component.literal("请先选择开局难度，再生成随机候选"), true);
                TianfuNet.sendAll(player);
                return;
            }
            int used = Math.max(0, TianfuData.rollCount(player));
            if (used >= TianfuRoll.MAX_ROLLS) {
                player.displayClientMessage(Component.literal("本世随机次数已用尽，现有候选仍可接受"), true);
                TianfuNet.sendAll(player);
                return;
            }
            net.guzhenren.tianfu.v2.TianfuV2Store.migrate(player);
            var snapshot = net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
            List<String> plan = net.guzhenren.tianfu.v2.TianfuEconomy.planRoll(snapshot,
                    new Random(player.getRandom().nextLong()));
            if (plan.isEmpty()) {
                player.displayClientMessage(Component.literal("当前余额与选择无法组成随机方案，已保留原候选，未消耗次数"), true);
            } else {
                TianfuData.setRollPending(player, plan);
                TianfuData.setRollCount(player, used + 1);
            }
            TianfuNet.sendAll(player);
        });
    }
}
