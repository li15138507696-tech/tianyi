package net.guzhenren.tianfu.mixin;

import net.guzhenren.init.GuzhenrenModGameRules;
import net.guzhenren.init.GuzhenrenModMobEffects;
import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.procedures.JingJieBaoCunProcedure;
import net.guzhenren.tianfu.TianfuAttributes;
import net.guzhenren.tianfu.TianfuData;
import net.guzhenren.tianfu.TianfuLiupai;
import net.guzhenren.tianfu.TianfuOpening;
import net.guzhenren.tianfu.TianfuTalents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 本体转世入口分流。{@code JingJieBaoCunProcedure} 的私有
 * {@code execute(Event, LevelAccessor, Entity)} 由死亡事件直接调用。
 *
 * <p>判定口径与本体完全一致：{@code !修炼gamerule || shouyuan <= 0} 走转世；
 * {@code FUHUO} 复活效果不触发。命中时在本体重置之前清空本模组的 {@code tianfu}
 * 数据根标签（点数 / 已购 / 难度 / 全部标记），并把本体 scoreboard 的 {@code kaiqiao}
 * 归零，实现「寿元耗尽 = 总结算，下世须重新用希望蛊开窍，开启新一轮」。
 *
 * <p>转世<b>不</b>自动发送状态包（不弹出天赋界面）；界面由希望蛊开窍收尾的 TAIL 钩子弹出。
 *
 * <p>普通死亡（修炼 gamerule 开启且寿元 &gt; 0）不在此结算，只走本体原有逻辑。
 */
@Mixin(value = JingJieBaoCunProcedure.class, remap = false)
public abstract class JingJieBaoCunProcedureMixin {
    @Inject(
            method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), require = 0)
    @SuppressWarnings("deprecation")
    private static void tianfu$onReincarnation(Event event, LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (player.hasEffect(GuzhenrenModMobEffects.FUHUO)) {
            return; // 复活效果：本体不转世，本模组也不结算
        }
        boolean xiuwei = world.getLevelData().getGameRules().getBoolean(GuzhenrenModGameRules.XIUWEI);
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (xiuwei && v.shouyuan > 0.0) {
            return; // 普通死亡：不结算天赋
        }

        // 御空踏云体随购买记录一并回收：必须在 wipe 之前读标记，
        // 否则 wipe 后 updateFlight 看不到 fly_granted，会误判为「非本模组授予」而残留飞行。
        boolean flyGranted = TianfuData.getBoolean(player, "fly_granted", false);

        // 特效开关是「玩家习惯」，不该跟着转世重置（和道途的 autoPrompt 同款处理）：
        // wipeAll 会删整个 tianfu 根，所以先读快照、wipe 之后写回。
        boolean vfxOn = TianfuData.vfxEnabled(player);

        // ---- 转世必须回收「写进玩家身上」的东西，不能只删 tianfu 标签 ----
        // wipeAll 只删 PersistentData 根标签，但本模组此前已经把以下内容落到实体上：
        //   ① 体质自带流派 buff（无限时长 MobEffect）；
        //   ② 词条 / 体质的 MOB_EFFECT 常驻药水（无限时长）；
        //   ③ 属性修正（MAX_HEALTH / ATTACK_DAMAGE / MOVEMENT_SPEED 等 transient modifier）。
        // 这些都不在 tianfu 标签里，不显式清理就会跨世残留：下世未选天赋却仍带
        // 剑道增幅、生命上限翻倍、移速加成，且因为 modifier id 固定，重新购买时还会
        // 被 addOrUpdate 覆盖成「旧值 + 新值」的错觉，实际数值早已错乱。
        // 顺序很关键：
        //   ① 回收流派 buff 需要旧 tizhi → 必须在 wipe 前；
        //   ② 清空词条常驻药水遍历的是静态词条表、与已购无关 → wipe 前后都行，放前面更安全；
        //   ③ 属性修正必须【在 wipe 之后】重建：sync 走 TianfuApply.effective()，
        //      确认状态下它返回旧已购集合，若在 wipe 前调用等于把旧加成原样重挂一遍。
        int oldTizhi = (int) Math.round(v.tizhi);
        TianfuLiupai.revoke(player, oldTizhi);
        TianfuTalents.revokeEffects(player, java.util.Set.of());

        TianfuData.wipeAll(player);
        TianfuData.setVfxEnabled(player, vfxOn);

        // wipe 后 purchased 为空且 confirmed 已清 → effective() 返回空集 → 全部 modifier 被移除
        TianfuAttributes.sync(player);
        if (flyGranted && !player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        // 本体转世的 kaiqiao 标记不重置，附属显式归零：希望蛊重新成为唯一开窍入口
        // （K 键重新被门禁拦截），玩家须重新用希望蛊开窍才算开启新一轮。
        // 注意：此处**不**发状态包——转世不自动弹出天赋界面（界面由希望蛊开窍收尾弹出）。
        TianfuOpening.resetKaiqiao(player);

        player.displayClientMessage(Component.literal(
                "§6[天命] 寿元耗尽·总结算：天赋点、已购天赋与开局难度已重置；请重新使用希望蛊开窍，开启新一轮"), false);
    }
}
