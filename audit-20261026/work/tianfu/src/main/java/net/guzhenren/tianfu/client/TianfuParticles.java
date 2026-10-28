package net.guzhenren.tianfu.client;

import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.client.particle.HunHuoParticle;
import net.guzhenren.tianfu.client.particle.JianQiParticle;
import net.guzhenren.tianfu.client.particle.NiantouGuangParticle;
import net.guzhenren.tianfu.client.particle.SharenYanParticle;
import net.guzhenren.tianfu.client.particle.XueQiParticle;
import net.guzhenren.tianfu.client.particle.ZhenyuanQiParticle;
import net.guzhenren.tianfu.init.TianfuParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * 客户端粒子 provider 注册（本体 {@code GuzhenrenModParticles} 同款写法）。
 *
 * <p>{@code registerSpriteSet} 会把 {@link net.minecraft.client.particle.SpriteSet} 注入 provider，
 * 由粒子在 {@code tick()} 里按 age 取帧——因此**帧动画完全在客户端算，服务端只发位置**。
 */
@EventBusSubscriber(modid = TianfuMod.MODID, bus = Bus.MOD, value = Dist.CLIENT)
public final class TianfuParticles {
    private TianfuParticles() {
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        // 与本体 GuzhenrenModParticles 完全同款写法：DeferredHolder<ParticleType<?>, SimpleParticleType>
        // 强转成 ParticleType<SimpleParticleType> 再交给 registerSpriteSet。
        // 试过用 <T extends ParticleOptions> 泛型 helper 会编译失败——
        // DeferredHolder 的第二个类型参数受 R(=ParticleType<?>) 约束，与 ParticleOptions 对不上。
        event.registerSpriteSet((ParticleType<SimpleParticleType>) TianfuParticleTypes.ZHENYUAN_QI.get(),
                ZhenyuanQiParticle::provider);
        event.registerSpriteSet((ParticleType<SimpleParticleType>) TianfuParticleTypes.NIANTOU_GUANG.get(),
                NiantouGuangParticle::provider);
        event.registerSpriteSet((ParticleType<SimpleParticleType>) TianfuParticleTypes.SHAREN_YAN.get(),
                SharenYanParticle::provider);
        event.registerSpriteSet((ParticleType<SimpleParticleType>) TianfuParticleTypes.HUNHUO.get(),
                HunHuoParticle::provider);
        event.registerSpriteSet((ParticleType<SimpleParticleType>) TianfuParticleTypes.XUEQI.get(),
                XueQiParticle::provider);
        event.registerSpriteSet((ParticleType<SimpleParticleType>) TianfuParticleTypes.JIANQI.get(),
                JianQiParticle::provider);
    }
}
