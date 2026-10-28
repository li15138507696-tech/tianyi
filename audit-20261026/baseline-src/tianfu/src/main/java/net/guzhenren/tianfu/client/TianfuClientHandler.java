// src/main/java/net/guzhenren/tianfu/client/TianfuClientHandler.java
package net.guzhenren.tianfu.client;

import net.guzhenren.tianfu.net.TianfuStatePayload;
import net.minecraft.client.Minecraft;

/**
 * 客户端分发（只在客户端被加载，因为 S2C 包只发给客户端）：合一天赋界面。
 *
 * <p>已开着天赋界面时<b>原地刷新</b>而不是重建 Screen——保留当前标签与滚动位置，
 * 解决"每次点完购买界面都被重置回顶部"的问题。
 */
public final class TianfuClientHandler {
    private TianfuClientHandler() {
    }

    public static void open(TianfuStatePayload payload) {
        net.guzhenren.tianfu.net.TianfuV2StatePayload.markStateReceived();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TianfuViewScreen screen) {
            screen.refresh(payload.kongqiao(), payload.points(), payload.difficulty(),
                    payload.purchased(), payload.confirmed(), payload.rolls(), payload.pending());
        } else {
            minecraft.setScreen(new TianfuViewScreen(
                    payload.kongqiao(), payload.points(), payload.difficulty(),
                    payload.purchased(), payload.confirmed(), payload.rolls(), payload.pending()));
        }
    }
}
