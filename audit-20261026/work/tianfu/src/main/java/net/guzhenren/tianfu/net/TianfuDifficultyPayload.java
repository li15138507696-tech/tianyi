// src/main/java/net/guzhenren/tianfu/net/TianfuDifficultyPayload.java
package net.guzhenren.tianfu.net;

import net.guzhenren.tianfu.TianfuData;
import net.guzhenren.tianfu.TianfuDifficulty;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuNet;
import net.guzhenren.tianfu.TianfuOpening;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：选择开局难度。仅可选择一次，选定即发放自由点。
 */
public record TianfuDifficultyPayload(String id) implements CustomPacketPayload {
    public static final Type<TianfuDifficultyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "difficulty"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuDifficultyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.id, 32),
            buf -> new TianfuDifficultyPayload(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** V2 契约：服务端开窍门禁和确认锁后一次发放8或6或4或2，已选老档不重发，成对同步状态。未填充时拒绝执行，不写入玩家状态。 */
    public static void handle(TianfuDifficultyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!TianfuOpening.hasOpenedQiao(player)) {
                return;
            }
            if (TianfuData.isConfirmed(player) || TianfuData.isSettled(player)
                    || TianfuData.difficulty(player) != TianfuDifficulty.NOT_CHOSEN) {
                TianfuNet.sendAll(player);
                return;
            }
            TianfuDifficulty.Option option = TianfuDifficulty.byId(payload.id());
            if (option == null) {
                TianfuNet.sendAll(player);
                return;
            }
            var previous = net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
            if (previous.points() < 0 || previous.points() > Integer.MAX_VALUE - option.points()) {
                return; // Invalid balances must not wrap or partially select a difficulty.
            }
            var current = net.guzhenren.tianfu.v2.TianfuV2Store.migrate(player);
            var next = new net.guzhenren.tianfu.v2.TianfuSelection.Snapshot(
                    net.guzhenren.tianfu.v2.TianfuSelection.SCHEMA, current.revision() + 1,
                    current.points() + option.points(), TianfuDifficulty.OPTIONS.indexOf(option), false,
                    current.origin(), current.originReceipt(), current.purchased(), current.receipts(),
                    current.negativeGrant(), current.originEffectKeys());
            net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                    new net.guzhenren.tianfu.v2.TianfuSelection.Change(current.revision(), next));
            TianfuNet.sendAll(player);
        });
    }
}
