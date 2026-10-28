package net.guzhenren.daotu.client;

import net.guzhenren.daotu.net.DaotuSettingPayload;
import net.guzhenren.daotu.net.DaotuStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端分发（只在客户端被加载，因为 S2C 包只发给客户端）：道途主界面。
 *
 * <p><b>打开 vs 刷新</b>由 {@link DaotuStatePayload#open()} 区分：
 * <ul>
 *   <li>{@code open}——要建界面：可能是玩家按 M（一定开），也可能是服务端攒满强弹
 *       （受本地「自动弹窗」偏好否决）；</li>
 *   <li>{@code !open}——服务端因属性变化主动推送，<b>只刷新已开着的界面</b>。</li>
 * </ul>
 *
 * <p>已开着时<b>原地刷新</b>而不是重建 Screen——保留当前页签与滚动位置。
 *
 * <p><b>不抢焦点</b>：强弹时若玩家正开着箱子 / 聊天等界面，先挂起，等关掉再开；
 * 按 M 触发时若已有别的界面（Xaero 地图默认键也是 M）抢先打开，则直接丢弃。
 */
public final class DaotuClientHandler {

    /** 强弹时玩家正开着别的界面 → 先存着，等界面关掉再开。 */
    private static DaotuStatePayload pendingOpen;

    private DaotuClientHandler() {
    }

    public static void receive(DaotuStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DaotuViewScreen screen) {
            pendingOpen = null;
            screen.applyState(payload);
            return;
        }
        if (!payload.open()) {
            return; // 没开界面、也不是要打开：静默丢弃，不打断当前操作
        }

        boolean requested = DaotuClientEvents.consumeRequest();
        if (!requested && !DaotuClientPrefs.autoPrompt()) {
            // 服务端强弹，但本地开关是关：不弹。
            // 1) 给一条提示，否则玩家会以为「攒满没反应」；
            // 2) 把本地偏好推回服务端，让服务端那份值收敛（否则它永远认为你还想弹）。
            hint(minecraft);
            PacketDistributor.sendToServer(new DaotuSettingPayload(false));
            return;
        }
        if (minecraft.screen != null) {
            if (requested) {
                return; // 按 M 时地图抢先打开了：不抢焦点
            }
            pendingOpen = payload; // 强弹：等玩家关掉手上的界面再开
            return;
        }
        minecraft.setScreen(new DaotuViewScreen(payload));
    }

    /** 每客户端 tick 调用：挂起的强弹，等界面关掉再开。 */
    public static void flushPending() {
        if (pendingOpen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            pendingOpen = null;
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        DaotuStatePayload payload = pendingOpen;
        pendingOpen = null;
        minecraft.setScreen(new DaotuViewScreen(payload));
    }

    /** 下线时丢弃挂起状态。 */
    public static void clearPending() {
        pendingOpen = null;
    }

    private static void hint(Minecraft minecraft) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(
                    "§6[道途] §e历练已满，按 §fM §e打开道途选择升级"), false);
        }
    }
}
