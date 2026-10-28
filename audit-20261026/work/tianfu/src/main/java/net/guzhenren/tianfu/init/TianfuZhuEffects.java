package net.guzhenren.tianfu.init;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianfu.TianfuZhu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 19 个「X烛」效果的注册表。
 *
 * <h2>为什么每个烛都要一个真 MobEffect</h2>
 * 本体的宙侵之所以能「按已侵蚀时长结算伤害」，是因为它作为一个 MobEffect
 * 挂在目标身上，能读 {@code getDuration()} / {@code getAmplifier()}。
 * 要让 19 个流派各自结算，就必须各自有一个能挂、能读时长的效果对象——
 * 用 PersistentData 记标记做不到「目标身上有可见图标 + 时长自然衰减」。
 *
 * <h2>类别为什么是 NEUTRAL</h2>
 * 与宙侵一致。NEUTRAL 既不会被「清除负面效果」清掉，
 * 也不会被玩家的抗性保护挡掉。而且这些烛是<b>玩家自己造成的</b>，
 * 挂在怪物身上时用 HARMFUL 才合理，挂在玩家身上（PVP）用 HARMFUL 会被净化——
 * 统一 NEUTRAL 最稳。
 *
 * <h2>效果本身做什么</h2>
 * 只做「每 tick 发粒子」这一件事（与宙侵一致）。
 * <b>伤害不在这里结算</b>——伤害由 {@link net.guzhenren.tianfu.TianfuZhuCombat}
 * 在「再次命中」时主动调本体的 {@code ShangHaiLiuCheng1Procedure} 结算。
 * 这样不会和本体已有的宙道结算双计。
 */
public final class TianfuZhuEffects {

    /** 注册表。 */
    public static final DeferredRegister<MobEffect> REGISTRY =
            DeferredRegister.create(Registries.MOB_EFFECT, "tianfu");

    /** 注册顺序固定，供 lang 与审计脚本按序核对。 */
    private static final List<DeferredHolder<MobEffect, ? extends MobEffect>> ALL =
            new ArrayList<>();

    private static DeferredHolder<MobEffect, ? extends MobEffect> reg(TianfuZhu.Candle c) {
        DeferredHolder<MobEffect, ? extends MobEffect> h =
                REGISTRY.register(c.id(), () -> new CandleEffect(c));
        ALL.add(h);
        return h;
    }

    // ---- 19 支烛 ----
    public static final DeferredHolder<MobEffect, ? extends MobEffect> TU = reg(TianfuZhu.TU);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> BING = reg(TianfuZhu.BING);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> BINGXUE = reg(TianfuZhu.BINGXUE);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> FENG = reg(TianfuZhu.FENG);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> GU = reg(TianfuZhu.GU);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> HUN = reg(TianfuZhu.HUN);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> JIAN = reg(TianfuZhu.JIAN);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> JIN = reg(TianfuZhu.JIN);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> LI = reg(TianfuZhu.LI);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> MU = reg(TianfuZhu.MU);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> QI = reg(TianfuZhu.QI);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> XUE = reg(TianfuZhu.XUE);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> XU = reg(TianfuZhu.XU);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> YING = reg(TianfuZhu.YING);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> YUE = reg(TianfuZhu.YUE);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> ZHI = reg(TianfuZhu.ZHI);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> ZHOU = reg(TianfuZhu.ZHOU);
    public static final DeferredHolder<MobEffect, ? extends MobEffect> MENG = reg(TianfuZhu.MENG);

    /** 全部已注册的烛（顺序与 {@link TianfuZhu#all()} 一致）。 */
    public static List<DeferredHolder<MobEffect, ? extends MobEffect>> all() {
        return List.copyOf(ALL);
    }

    private TianfuZhuEffects() {
    }

    /**
     * 单个「烛」的效果实现。
     *
     * <p>只覆写 {@link #applyEffectTick} 发粒子。注意签名是
     * {@code applyEffectTick(LivingEntity, int amplifier)}——1.21.1 里
     * {@code shouldApplyEffectTickThisTick} 的默认实现已经保证每 tick 调用，
     * 不需要额外覆写（宙侵也没覆写，它只覆写了 {@code applyEffectTick}）。
     *
     * <p><b>不在这里造成伤害</b>：伤害由命中事件驱动，见类注释。
     */
    private static final class CandleEffect extends MobEffect {
        private final TianfuZhu.Candle candle;

        CandleEffect(TianfuZhu.Candle candle) {
            super(MobEffectCategory.NEUTRAL, candle.color());
            this.candle = candle;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            // 与宙侵同款：每 tick 在实体中部撒一点粒子，让玩家看得见「身上有烛」
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && entity.tickCount % 4 == 0) {
                sl.sendParticles(
                        net.guzhenren.init.GuzhenrenModParticleTypes.ZHOUDAOLIZI_2.get(),
                        entity.getX(),
                        entity.getY() + entity.getBbHeight() * 0.5,
                        entity.getZ(),
                        3, 0.25, 0.35, 0.25, 0.01);
            }
            return true;
        }

        /** 供审计脚本读取。 */
        TianfuZhu.Candle candle() {
            return candle;
        }
    }
}
