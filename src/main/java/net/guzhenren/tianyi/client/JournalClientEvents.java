package net.guzhenren.tianyi.client;

import net.guzhenren.tianyi.net.JournalActionPayload;
import net.guzhenren.tianyi.net.JournalNet;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端接线（按键开面板、断线清理）。
 *
 * <p>不用 {@code @EventBusSubscriber(bus = MOD)}：那条注解在 21.1 里已标记为待移除，
 * 改成由 {@link JournalClientSetup} 在客户端分支里显式注册。
 */
public final class JournalClientEvents {
    private JournalClientEvents() {
    }

    /** game bus：每 tick 检查按键。 */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.screen != null) {
            // 有别的界面开着时不抢键：把这次按键吞掉，避免在聊天/背包里误触
            while (JournalKeys.OPEN_JOURNAL.consumeClick()) {
                // 丢弃
            }
            return;
        }
        while (JournalKeys.OPEN_JOURNAL.consumeClick()) {
            // 先向服务端要一次最新数据（服务端会重新校验归属与可见性）
            JournalNet.send(JournalActionPayload.Action.REQUEST, "");
            mc.setScreen(new JournalScreen());
        }
    }

    /** game bus：断线清空，避免串到下一个角色。 */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        JournalClientState.clear();
    }
}
