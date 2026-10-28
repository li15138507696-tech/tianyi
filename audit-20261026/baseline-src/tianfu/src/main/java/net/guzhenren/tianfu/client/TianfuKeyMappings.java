package net.guzhenren.tianfu.client;

import org.lwjgl.glfw.GLFW;

import net.guzhenren.tianfu.TianfuMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端快捷键：默认 K 打开「天赋查看」界面。
 *
 * <p>只在客户端加载（{@code value = Dist.CLIENT}），专用服务端不会触碰 {@link KeyMapping}。
 *
 * <p><b>顺带在这里挂客户端 tick 监听</b>：{@link TianfuClientEvents} 以前靠
 * {@code @EventBusSubscriber(bus = Bus.GAME)}，那是 NeoForge 已标记待删除的写法，
 * 失效时会让「键注册了但 tick 不跑」，表现是 K 完全没反应且没有任何报错。
 * 现在改成在按键注册这一刻显式 {@code register}，两者绑在同一条依赖上：
 * 要么按键和监听都在，要么按键根本不存在，不会半死。
 */
@EventBusSubscriber(modid = TianfuMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TianfuKeyMappings {
    /** 打开天赋查看界面。 */
    public static final KeyMapping VIEW =
            new KeyMapping("key.tianfu.view", GLFW.GLFW_KEY_K, "key.categories.misc");

    private TianfuKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(VIEW);
        // 显式挂 game 总线监听（替代 @EventBusSubscriber(bus = GAME)）
        NeoForge.EVENT_BUS.register(TianfuClientEvents.class);
        TianfuMod.LOGGER.info("[天赋] K 键与客户端 tick 监听已注册");
    }
}
