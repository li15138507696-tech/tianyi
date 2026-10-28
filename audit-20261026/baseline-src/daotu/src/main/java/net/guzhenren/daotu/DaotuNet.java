package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.daotu.net.DaotuStatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 便捷入口：把当前道途状态发给客户端。 */
public final class DaotuNet {
    private DaotuNet() {
    }

    /** 玩家主动请求（按 M）：客户端<b>一定</b>打开界面。 */
    public static void openState(ServerPlayer player) {
        send(player, true);
    }

    /**
     * 服务端主动推送：客户端<b>只刷新已开着的界面</b>。
     *
     * <p>历练是在打怪、走路、打坐时涨的。若这里也用 {@link #openState}，
     * 玩家每次涨历练都会在战斗中被强行弹出面板。
     */
    public static void sendState(ServerPlayer player) {
        send(player, false);
    }

    /**
     * 攒满历练、需要玩家做选择时的提醒。
     *
     * <p><b>v1.4.0</b>：直接强制弹窗——攒满一次不容易，且不选就无法继续积累，
     * 必须让玩家立刻看到；
     * <b>v1.4.1</b>：改成<b>看玩家开关</b>。开着就弹窗（老行为），关着只推状态并在
     * 聊天栏提示一次，不再从战斗里把玩家拽出来。开关在界面页脚，见
     * {@code DaotuViewScreen} 的「自动弹窗」。
     */
    public static void promptChoice(ServerPlayer player) {
        boolean auto = DaotuRun.autoPrompt(player);
        DaotuMod.LOGGER.debug("[道途] {} 攒满触发：autoPrompt={}（false 时只发聊天提示）",
                player.getScoreboardName(), auto);
        if (auto) {
            send(player, true);
            return;
        }
        // 不弹窗：把状态推过去（已开着界面就原地刷新），再给一条聊天提示
        send(player, false);
        player.displayClientMessage(Component.literal(
                "§6[道途] §e历练已满，按 §fM §e打开道途选择升级"), false);
    }

    private static void send(ServerPlayer player, boolean open) {
        // 待选候选
        List<DaotuStatePayload.Offer> offer = new ArrayList<>();
        for (DaotuDrawTable.Candidate c : DaotuDraw.pendingOffer(player)) {
            offer.add(new DaotuStatePayload.Offer(c.key(), c.label(), c.amount(), c.percent()));
        }

        PacketDistributor.sendToPlayer(player, new DaotuStatePayload(
                open,
                DaotuRun.pool(player),
                DaotuRun.thresholdOf(player),
                DaotuRun.draws(player),
                DaotuRun.runId(player),
                DaotuRun.zhuanshu(player),
                DaotuRun.attrs(player),
                offer,
                DaotuDraw.pendingDrawIndex(player),
                DaotuRun.history(player),
                DaotuRun.buffs(player),
                DaotuOpening.hasOpenedQiao(player),
                DaotuRun.autoPrompt(player),
                DaotuMainDao.of(player),
                DaotuDraw.capOf(player)));
    }
}
