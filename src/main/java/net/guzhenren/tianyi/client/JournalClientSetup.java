package net.guzhenren.tianyi.client;

import net.guzhenren.tianyi.item.DestinyGuItem;
import net.guzhenren.tianyi.item.TianyiItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端初始化入口：<b>只会在客户端被调用</b>。
 *
 * <p>{@code TianyiMod} 用 {@code FMLEnvironment.dist == Dist.CLIENT} 把这里挡住，
 * 所以独立服务器不会加载任何客户端类（按键、界面、渲染都在这个包里）。
 */
public final class JournalClientSetup {
    private JournalClientSetup() {
    }

    public static void init(IEventBus modBus) {
        // mod bus：按键注册
        modBus.addListener(RegisterKeyMappingsEvent.class, JournalKeys::onRegister);
        modBus.addListener(FMLClientSetupEvent.class, JournalClientSetup::onClientSetup);
        // game bus：tick + 断线
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                JournalClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                JournalClientEvents::onLoggingOut);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(TianyiItems.SUMINGGU.get(),
                ResourceLocation.fromNamespaceAndPath("tianyi", "scarred"),
                (stack, level, entity, seed) -> DestinyGuItem.isScarred(stack) ? 1.0F : 0.0F));
    }
}
