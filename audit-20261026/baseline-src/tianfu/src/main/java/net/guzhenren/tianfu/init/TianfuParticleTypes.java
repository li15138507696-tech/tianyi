package net.guzhenren.tianfu.init;

import java.lang.reflect.Constructor;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 附属自定义粒子类型注册表。
 *
 * <p>走本体同款通道（{@code DeferredRegister<ParticleType<?>>} + {@code SimpleParticleType}），
 * 客户端 provider 在 {@link net.guzhenren.tianfu.client.TianfuParticles} 的
 * {@code RegisterParticleProvidersEvent} 里用 {@code registerSpriteSet} 挂上。
 *
 * <p>资源三件套（每个类型一套）：
 * <ul>
 *   <li>{@code assets/tianfu/particles/<id>.json} → {@code {"textures":["tianfu:<id>"]}}</li>
 *   <li>{@code assets/tianfu/textures/particle/<id>.png} → 竖向帧条（N 帧 × 单帧宽）</li>
 *   <li>{@code .../<id>.png.mcmeta} → {@code {"animation":{"frametime":2,"frames":[...]}}</li>
 * </ul>
 *
 * <p><b>为什么要反射</b>：1.21.1 的 {@link SimpleParticleType} 构造函数是 {@code protected}
 * （MCreator 生成的本体代码能写 {@code new SimpleParticleType(false)} 是因为它生成在
 * {@code net.guzhenren.init} 之外但仍能访问——实测在附属包下编译被拒），
 * 且该类没有公开静态工厂。故用一次反射拿到构造器，之后正常 newInstance。
 */
public final class TianfuParticleTypes {
    /** {@link SimpleParticleType#SimpleParticleType(boolean)}（protected，反射取一次）。 */
    private static final Constructor<SimpleParticleType> CTOR = ctor();

    private static Constructor<SimpleParticleType> ctor() {
        try {
            Constructor<SimpleParticleType> c = SimpleParticleType.class.getDeclaredConstructor(boolean.class);
            c.setAccessible(true);
            return c;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 造一个 {@link SimpleParticleType}；{@code overrideSpawnLimit} 传 false（不放开生成上限）。 */
    private static SimpleParticleType simple(boolean overrideSpawnLimit) {
        try {
            return CTOR.newInstance(overrideSpawnLimit);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final DeferredRegister<ParticleType<?>> REGISTRY =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "tianfu");

    /** 真元气：青白色能量光点（太息养元体 / 真元类特效）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ZHENYUAN_QI =
            REGISTRY.register("tianfu_zhenyuan_qi", () -> simple(false));

    /** 念头光：金色光点（慧心通明体 / 南柯一梦体 / 念头类特效）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NIANTOU_GUANG =
            REGISTRY.register("tianfu_niantou_guang", () -> simple(false));

    /** 杀焰：血红火苗（杀神）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHAREN_YAN =
            REGISTRY.register("tianfu_sharen_yan", () -> simple(false));

    /** 魂火：幽蓝火苗（九命不灭体 / 幽魂凝识体）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HUNHUO =
            REGISTRY.register("tianfu_hunhuo", () -> simple(false));

    /** 血气：暗红雾团（血煞蜕变体 / 修罗战体）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> XUEQI =
            REGISTRY.register("tianfu_xueqi", () -> simple(false));

    /** 剑气：霜白弧形刃光（断岳剑心体 / 兵道类）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> JIANQI =
            REGISTRY.register("tianfu_jianqi", () -> simple(false));

    private TianfuParticleTypes() {
    }
}
