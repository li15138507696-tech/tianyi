package net.guzhenren.daotu.mixin;

import net.guzhenren.daotu.DaotuOpening;
import net.guzhenren.init.GuzhenrenModGameRules;
import net.guzhenren.init.GuzhenrenModMobEffects;
import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.procedures.JingJieBaoCunProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 本体转世入口分流。判定口径与本体完全一致：
 * {@code !修炼gamerule || shouyuan <= 0} 走转世；{@code FUHUO} 复活效果不触发。
 *
 * <p>命中时清空本模组的 {@code daotu} 数据根标签（出身 / 势力 / 档位 / 节点 / 觉醒），
 * 实现「寿元耗尽 = 总结算，下世重新选出身」。
 *
 * <p>与天命附属注入同一方法：两者都幂等、互不依赖对方状态，顺序无关。
 */
@Mixin(value = JingJieBaoCunProcedure.class, remap = false)
public abstract class JingJieBaoCunProcedureMixin {

    @Inject(
            method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;"
                    + "Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), require = 0, remap = false)
    @SuppressWarnings("deprecation")
    private static void daotu$onReincarnation(Event event, LevelAccessor world, Entity entity,
                                              CallbackInfo ci) {
        try {
            if (!(entity instanceof ServerPlayer player) || player.level().isClientSide()) {
                return;
            }
            if (player.hasEffect(GuzhenrenModMobEffects.FUHUO)) {
                return; // 复活效果：本体不转世
            }
            boolean xiuwei = world.getLevelData().getGameRules()
                    .getBoolean(GuzhenrenModGameRules.XIUWEI);
            PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            if (xiuwei && v.shouyuan > 0.0) {
                return; // 普通死亡
            }
            DaotuOpening.onReincarnation(player);
        } catch (Throwable t) {
            net.guzhenren.daotu.DaotuMod.LOGGER.warn("daotu 转世钩子失败（已忽略）", t);
        }
    }
}
