package net.guzhenren.daotu.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.guzhenren.daotu.DaotuMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端键位：M 打开道途界面。
 *
 * <p>键位占用已全整合包扫描（51 个 jar）：J 被六合一的 pouch / shenshang 占用，
 * K 被天命占用，P 被流派熟练度查看占用，H/N/B/U/I 被六合一占用。
 * 空闲字母为 E F M Q T Y，取 M。
 *
 * <p>{@code RegisterKeyMappingsEvent} 属于 mod bus，自动推断即可，故不写已弃用的 {@code bus}。
 */
@EventBusSubscriber(modid = DaotuMod.MODID, value = Dist.CLIENT)
public final class DaotuKeyMappings {

    /** 道途界面键。GLFW_KEY_M = 77。 */
    public static final KeyMapping VIEW = new KeyMapping(
            "key.daotu.view",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.daotu");

    private DaotuKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(VIEW);
    }
}
