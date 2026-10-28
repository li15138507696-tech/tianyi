// src/main/java/net/guzhenren/tianfu/client/TianfuClientEvents.java
package net.guzhenren.tianfu.client;

import net.guzhenren.client.gui.UIKAIQIAOMIANBANScreen;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.net.TianfuRequestPayload;
import net.guzhenren.tianfu.net.TianfuV2StatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端：监听查看快捷键，发包请求服务端回发查看状态。
 *
 * <p>同时<b>覆盖本体天赋选择界面</b>：本体开窍资质面板（{@code UIKAIQIAOMIANBAN}）一打开
 * 即被替换为本附属的天赋界面。关闭容器会触发本体 {@code UIKAIQIAOMIANBANMenu.removed()}
 * 自动完成开窍收尾（资质/真元结算），随后请求服务端回发状态打开 K 键同款界面——
 * 本体抽卡式资质面板不再出现，一切养成走天赋购买。
 *
 * <h2>为什么不再用 {@code @EventBusSubscriber(bus = Bus.GAME)}</h2>
 * 该写法在 NeoForge 21.1 里已<b>标记为待删除</b>（编译期 6 条 {@code [removal]} 警告就来自它）。
 * 一旦某个 NeoForge 构建不再认这个注解属性，本类会<b>静默地</b>一个事件都收不到——
 * 表现是「按 K 完全没反应、不崩溃、日志也没有报错」，极难排查。
 *
 * <p>所以改成由 {@link TianfuKeyMappings#onRegisterKeys} 显式
 * {@code NeoForge.EVENT_BUS.register(TianfuClientEvents.class)}：
 * 按键注册（MOD 总线，标准写法）与 tick 监听<b>绑在同一条依赖上</b>——
 * 要么两者都成立，要么按键根本不存在，不会出现「键有、监听没挂」的半死状态。
 * 这与 {@code TianfuMod} 里 {@code NeoForge.EVENT_BUS.register(TianfuEvents.class)}
 * 的做法一致。
 */
public final class TianfuClientEvents {
    private static int stateWaitTicks;
    private static long requestStateSequence;

    private TianfuClientEvents() {
    }

    /** A revision belongs to one client player lifecycle, not to every save ever opened. */
    private static void clearSelectionView() {
        stateWaitTicks = 0;
        TianfuV2StatePayload.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TianfuViewScreen) {
            minecraft.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        clearSelectionView();
        net.guzhenren.tianfu.client.vfx.TianfuVfxBridge.bootstrap();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearSelectionView();
    }

    /** Death and dimension respawns replace LocalPlayer; neither proves reincarnation. */
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        clearSelectionView();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        driveVfx();
        Minecraft minecraft = Minecraft.getInstance();

        if (stateWaitTicks > 0) {
            if (minecraft.player == null || TianfuV2StatePayload.receivedStates() != requestStateSequence) stateWaitTicks = 0;
            else if (--stateWaitTicks == 0) TianfuMod.LOGGER.warn("[天赋] 等待服务端状态超时");
        }

        // 覆盖本体开窍资质面板：一打开就关掉容器（服务端 removed 钩子自动跑开窍收尾），
        // 再请求状态打开附属天赋界面顶替。
        if (minecraft.screen instanceof UIKAIQIAOMIANBANScreen && minecraft.player != null) {
            minecraft.setScreen(null);
            minecraft.player.closeContainer();
            TianfuMod.LOGGER.info("[天赋] 客户端拦下本体开窍资质面板，改请求附属界面");
            beginStateWait();
            PacketDistributor.sendToServer(new TianfuRequestPayload());
        }

        while (TianfuKeyMappings.VIEW.consumeClick()) {
            // 仅在没有其它界面时请求，避免覆盖当前 UI。
            if (minecraft.screen == null && minecraft.player != null) {
                // 诊断日志：K 真的被收到了才打这一行。
                // 若玩家「按 K 没反应」而日志里连这行都没有，说明按键/监听没挂上，
                // 与开窍、门禁、服务端全无关——能一眼分开两类故障。
                TianfuMod.LOGGER.info("[天赋] 客户端收到 K 键，请求天赋界面");
                beginStateWait();
                PacketDistributor.sendToServer(new TianfuRequestPayload());
            } else {
                TianfuMod.LOGGER.debug("[天赋] K 被按下但有界面开着，已忽略");
            }
        }
    }
    /** Start one bounded wait; repeated K presses cannot postpone an outstanding deadline. */
    private static void beginStateWait() {
        if (stateWaitTicks == 0) {
            requestStateSequence = TianfuV2StatePayload.receivedStates();
            stateWaitTicks = 200;
        }
    }

    // ------------------------------------------------------------------ 「天命落定」演出
    /** 正在播放的变体号；-1 = 空闲。 */
    private static int vfxPlaying = -1;
    private static int vfxTicks;
    /** 演出总长（tick）：70 ≈ 3.5 秒。 */
    private static final int VFX_DURATION = 70;

    /**
     * 由服务端触发包驱动：强度按 sin 曲线 0 → 0.9 → 0，进度线性 0 → 1。
     *
     * <p>未装 Veil 时 {@code TianfuVfxBridge.pulse} 直接返回，这里等于空转。
     */
    private static void driveVfx() {
        int incoming = net.guzhenren.tianfu.net.TianfuVfxPayload.consume();
        if (incoming >= 0) {
            vfxPlaying = incoming;
            vfxTicks = 0;
        }
        if (vfxPlaying < 0) {
            return;
        }
        vfxTicks++;
        float progress = Math.min(1.0f, vfxTicks / (float) VFX_DURATION);
        float strength = (float) (Math.sin(Math.PI * progress) * 0.9);
        net.guzhenren.tianfu.client.vfx.TianfuVfxBridge.pulse(strength, progress);
        if (vfxTicks >= VFX_DURATION) {
            vfxPlaying = -1;
            net.guzhenren.tianfu.client.vfx.TianfuVfxBridge.pulse(0.0f, 1.0f);
        }
    }
}
