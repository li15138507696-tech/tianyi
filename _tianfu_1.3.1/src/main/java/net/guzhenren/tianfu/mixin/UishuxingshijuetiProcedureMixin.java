package net.guzhenren.tianfu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.procedures.UishuxingshijuetiProcedure;
import net.guzhenren.tianfu.TianfuNames;
import net.minecraft.world.entity.Entity;

/**
 * 本体只认 tizhi 1–16，其余一律显示「体质:无」。凡体（17–53）与杀神（54）由附属自建名表替换。
 */
@Mixin(value = UishuxingshijuetiProcedure.class, remap = false)
public class UishuxingshijuetiProcedureMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void tianfu$bodyName(Entity entity, CallbackInfoReturnable<String> cir) {
        if (entity == null) {
            return;
        }
        try {
            double tizhi = entity.getData(GuzhenrenModVariables.PLAYER_VARIABLES).tizhi;
            String name = TianfuNames.bodyName(tizhi);
            if (name != null) {
                cir.setReturnValue("体质:" + name);
            }
        } catch (Throwable ignored) {
            // 非玩家实体：交回本体处理
        }
    }
}
