// src/main/java/net/guzhenren/tianfu/TianfuNet.java
package net.guzhenren.tianfu;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.tianfu.net.TianfuStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 便捷入口：把当前天赋状态（档位 / 点数余额 / 已购）发给客户端。 */
public final class TianfuNet {
    private TianfuNet() {
    }

    /** K 键请求 / 购买或选难度成功后的刷新，统一走这里。 */
    public static void sendState(ServerPlayer player) {
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        PacketDistributor.sendToPlayer(player, new TianfuStatePayload(
                (int) Math.round(v.kongqiao), TianfuData.points(player),
                TianfuData.difficulty(player), TianfuData.purchased(player),
                TianfuData.isConfirmed(player),
                TianfuData.rollCount(player), TianfuData.rollPending(player)));
    }

    /**
     * V2 契约：任何成功变更后**成对**回发旧 state 与 v2_state。
     *
     * <p>两条通道并存是过渡期的要求：旧界面继续吃 {@link TianfuStatePayload}，
     * 七页界面吃 V2 快照；只发一条会让其中一端停在旧数据上。
     */
    public static void sendAll(ServerPlayer player) {
        sendState(player);
        try {
        PacketDistributor.sendToPlayer(player,
                new net.guzhenren.tianfu.net.TianfuV2StatePayload(
                        net.guzhenren.tianfu.v2.TianfuV2Store.read(player)));
        } catch (Throwable e) {
            TianfuMod.LOGGER.error("天赋 V2 状态发送失败，旧状态已先行发送", e);
            throw e;
        }
    }
}
