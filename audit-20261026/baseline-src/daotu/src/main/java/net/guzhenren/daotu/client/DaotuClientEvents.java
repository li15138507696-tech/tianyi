package net.guzhenren.daotu.client;

import net.guzhenren.daotu.DaotuMod;
import net.guzhenren.daotu.net.DaotuRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端按键监听：M 请求状态 / 关闭界面。
 *
 * <p>按 M 只是「请求」而不是「直接开界面」：服务端要先校验开窍，
 * 未开窍的玩家按 M 只会收到一条提示。
 *
 * <p>不写 {@code bus = ...}：loader 4.0.42+ 的 {@code AutomaticEventSubscriber}
 * 已不再读取该成员，改为按事件类型自动选择 bus（{@code ClientTickEvent.Post} → 游戏 bus）。
 * 显式写反而触发「已弃用待删」编译警告。
 */
@EventBusSubscriber(modid = DaotuMod.MODID, value = Dist.CLIENT)
public final class DaotuClientEvents {

    private DaotuClientEvents() {
    }

    /** 按 M 后多久之内算「这次打开包是玩家主动要的」。 */
    private static final int REQUEST_WINDOW_TICKS = 60;

    /** 没有请求过的哨兵值。不能用极小负数：tickCount - 极小负数 会溢出。 */
    private static final int NO_REQUEST = -1000;

    /** 最近一次「玩家按 M 请求界面」的时刻（玩家 tick）。 */
    private static int lastRequestTick = NO_REQUEST;

    /**
     * 这个打开包是不是玩家自己按 M 要来的。
     *
     * <p>用来区分「玩家主动」与「服务端强弹」：强弹要受本地「自动弹窗」否决，
     * 主动请求则一定开。窗口给 60 tick（3 秒），足够服务端回包，又不会把
     * 之后的强弹误判成主动请求。
     */
    private static boolean recentRequest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        // 注意必须有 d >= 0：玩家实体换了以后 tickCount 从 0 重新数，
        // 而 lastRequestTick 是静态字段、会跨重生/重进保留。只判 <= 60 的话
        // 0 - 20000 = -20000 也会成立，结果把服务端强弹当成玩家按 M 放行。
        int d = mc.player.tickCount - lastRequestTick;
        return d >= 0 && d <= REQUEST_WINDOW_TICKS;
    }

    /** 下线时清掉请求窗口：新会话的 tickCount 从 0 起算，不能拿旧值比较。 */
    @SubscribeEvent
    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        lastRequestTick = NO_REQUEST;
        DaotuClientHandler.clearPending();
    }

    /**
     * 取走「这次打开包是玩家按 M 要的」这个判断，并立刻清空窗口。
     *
     * <p>必须取走而不是只读：否则按 M 之后的 3 秒内，服务端再来一次攒满强弹，
     * 也会被当成「玩家自己按的」，本地开关就白关了。
     */
    public static boolean consumeRequest() {
        boolean recent = recentRequest();
        lastRequestTick = NO_REQUEST;
        return recent;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (DaotuKeyMappings.VIEW.consumeClick()) {
            if (mc.player == null) {
                continue;
            }
            if (mc.screen instanceof DaotuViewScreen) {
                mc.setScreen(null);
                continue;
            }
            lastRequestTick = mc.player.tickCount;
            PacketDistributor.sendToServer(new DaotuRequestPayload());
        }
        // 之前因为开着箱子/聊天而挂起的自动弹窗，等界面关掉再开
        DaotuClientHandler.flushPending();
    }
}
