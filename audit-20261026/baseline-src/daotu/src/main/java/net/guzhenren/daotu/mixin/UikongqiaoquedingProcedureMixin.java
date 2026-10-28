package net.guzhenren.daotu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.guzhenren.daotu.DaotuMod;
import net.guzhenren.daotu.DaotuOpening;
import net.guzhenren.procedures.UikongqiaoquedingProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

/**
 * 开窍收尾的 TAIL 拦截：{@link DaotuOpening#onCloseOpenQiao}。
 *
 * <p><b>刻意不弹窗</b>：天命附属已在此注入并自动 {@code setScreen}，两个界面会互相覆盖。
 *
 * <p><b>为什么是 {@code queding}</b>：本体 10.x 的收尾在 {@code UikongqiaoguanbiProcedure}，
 * 9.8 没有那个类、收尾就在 {@code UikongqiaoquedingProcedure}；10.x 的 {@code queding}
 * 又会转调 {@code guanbi}。注入 {@code queding} 两版都成立，一份 jar 通吃。
 *
 * <p>{@code require = 0} + {@code remap = false} + try-catch 静默：本体升级不崩。
 */
@Mixin(value = UikongqiaoquedingProcedure.class, remap = false)
public class UikongqiaoquedingProcedureMixin {
    @Inject(method = "execute", at = @At("TAIL"), require = 0, remap = false)
    private static void daotu$onCloseOpenQiao(LevelAccessor world, Entity entity, CallbackInfo ci) {
        try {
            DaotuOpening.onCloseOpenQiao(entity);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu 开窍收尾钩子异常（已忽略）", t);
        }
    }
}
