package net.guzhenren.tianfu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.guzhenren.item.YizhuanrendaoxiwangguItem;
import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.TianfuOpening;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 希望蛊 = 天赋系统的唯一开窍开关。
 *
 * <p>HEAD 拦截本体 {@code YizhuanrendaoxiwangguItem.use()} 并整体取消本体开窍链
 * （主 procedure、10 tick 延迟链、随机 kongqiao 1–10、本体资质面板 openMenu、档位随机结算），
 * 改由 {@link TianfuOpening#onUseHopeGu} 复刻开窍仪式后保持 kongqiao=0 直接调用本体收尾，
 * 由既有 {@code UikongqiaoguanbiProcedureMixin} 的 TAIL 钩子弹出附属天赋界面。
 *
 * <p>{@code require = 0} + try-catch：本体升级导致注入点消失时静默放行，
 * 客户端 {@code TianfuClientEvents} 的本体面板拦截仍可兜底。
 */
@Mixin(value = YizhuanrendaoxiwangguItem.class, remap = false)
public class YizhuanrendaoxiwangguItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tianfu$openQiao(Level level, Player player, InteractionHand hand,
                                 CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        try {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                TianfuOpening.onUseHopeGu(serverPlayer, stack, hand);
            }
        } catch (Throwable t) {
            TianfuMod.LOGGER.error("[天赋] 希望蛊开窍拦截异常，已静默放行", t);
        }
        cir.setReturnValue(InteractionResultHolder.success(stack));
    }
}
