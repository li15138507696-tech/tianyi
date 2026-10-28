// src/main/java/net/guzhenren/tianfu/client/vfx/TianfuVfxBridge.java
package net.guzhenren.tianfu.client.vfx;

import net.guzhenren.tianfu.TianfuMod;
import net.neoforged.fml.ModList;

/**
 * Veil 后处理的软依赖入口。
 *
 * <p>Veil 只是可选依赖：没装时本类立刻返回，绝不触碰 {@link StarGoldDomainFx}，
 * 因此 JVM 不会去加载它引用的 {@code foundry.veil.*} 类，不会 NoClassDefFoundError。
 */
public final class TianfuVfxBridge {
    private static boolean bootstrapped;
    private static boolean enabled;

    private TianfuVfxBridge() {
    }

    /** 客户端首次进服时调用一次；幂等。 */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        if (!ModList.get().isLoaded("veil")) {
            TianfuMod.LOGGER.info("[天命] 未检测到 Veil，跳过全屏后处理特效");
            return;
        }
        try {
            StarGoldDomainFx.register();
            enabled = true;
            TianfuMod.LOGGER.info("[天命] Veil 后处理已注册：star_gold_domain");
        } catch (Throwable t) {
            enabled = false;
            TianfuMod.LOGGER.warn("[天命] Veil 后处理注册失败，已降级为纯粒子表现", t);
        }
    }

    /** 后处理是否可用。 */
    public static boolean enabled() {
        return enabled;
    }

    /** 推强度与进度；未启用时零开销。 */
    public static void pulse(float strength, float progress) {
        if (!enabled) {
            return;
        }
        try {
            StarGoldDomainFx.setStarGoldDomainStrength(strength);
            StarGoldDomainFx.setStarGoldDomainProgress(progress);
            StarGoldDomainFx.setStarGoldDomainPhase(progress * 6.2831855f);
        } catch (Throwable ignored) {
            enabled = false;
        }
    }
}