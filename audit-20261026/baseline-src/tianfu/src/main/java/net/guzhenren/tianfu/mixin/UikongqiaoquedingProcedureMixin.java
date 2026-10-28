package net.guzhenren.tianfu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.guzhenren.procedures.UikongqiaoquedingProcedure;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuOpening;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

/**
 * 开窍收尾的 TAIL 拦截：{@link TianfuOpening#onCloseOpenQiao}——
 * 标记本世开窍已结算 + 提示 K 键 + 未选难度时弹出附属天赋界面。
 *
 * <p><b>为什么注入 {@code queding} 而不是 {@code guanbi}</b>：
 * 本体 10.x 把收尾结算拆到了新的 {@code UikongqiaoguanbiProcedure}，
 * 而 <b>9.8 根本没有这个类</b>，收尾就在 {@code UikongqiaoquedingProcedure} 里；
 * 10.x 的 {@code queding} 则是「先调 {@code guanbi.execute}、再 {@code closeContainer}」的薄壳。
 * 所以注入 {@code queding} 的 TAIL 在<b>两个版本都成立</b>——
 * 一份 jar 同时跑 9.8 与 10.x，不需要额外的兼容补丁。
 *
 * <p>希望蛊开窍时附属保持 kongqiao=0，本体各档位随机结算分支天然不命中。
 *
 * {@code require = 0} + try-catch：本体升级导致注入点消失时静默放行，不影响本体。
 */
@Mixin(value = UikongqiaoquedingProcedure.class, remap = false)
public class UikongqiaoquedingProcedureMixin {
    @Inject(method = "execute", at = @At("TAIL"), require = 0, remap = false)
    private static void tianfu$onCloseOpenQiao(LevelAccessor world, Entity entity, CallbackInfo ci) {
        try {
            TianfuOpening.onCloseOpenQiao(entity);
        } catch (Throwable t) {
            TianfuMod.LOGGER.error("[天赋] 开窍收尾钩子异常，已静默放行", t);
        }
    }
}
