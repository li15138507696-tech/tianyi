package net.guzhenren.tianyi.world;

import net.guzhenren.tianyi.will.WillForce;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 天意强度档位的服务端配置（3.14）。
 *
 * <h2>只有一个开关</h2>
 * 这里刻意只放一个 {@code intensity}（0 关闭 / 1 温和 / 2 严厉 / 3 天倾）：
 * 其余所有倍率与阈值都由纯逻辑的 {@link WillForce} 按档位推导，避免出现
 * 「配置文件里五十个数，没人知道哪一个真的被读了」。
 *
 * <h2>与旧开关的关系</h2>
 * 旧开关 {@code legacyAutomaticPressure} 没有被删除，也没有改名：
 * {@link #pressureOn()} 是「旧开关 <b>或</b> 强度 ≥ 2」。旧整合包把强度留在 0/1、
 * 显式打开旧开关，行为与 3.13 一致。
 *
 * <p>本类只读配置，不碰世界：所有实际行为都在 {@link WillServer}、
 * {@link WorldExec}、{@link WillPressure} 里，且都读 {@link #force()}。
 */
public final class WillForceSettings {
    /** 服务端配置文件名。{@code TianyiMod} 用它注册 SPEC。 */
    public static final String CONFIG_FILE = "tianyi-force-server.toml";

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue INTENSITY;

    static {
        var b = new ModConfigSpec.Builder();
        INTENSITY = b.comment(
                        "天意强度：0=关闭（零干预）/ 1=温和（默认：只有天象、产地枯损、兽群迁徙）/ "
                                + "2=严厉（放开既有压制渠道，灾劫真的会发生）/ 3=天倾（压制更密、灾劫更重、允许兽潮）。"
                                + " The world only acts through real systems (weather, real sites, real herd"
                                + " population, calamity phases); nothing is ever granted into an inventory.")
                .defineInRange("intensity", 1, 0, 3);
        SPEC = b.build();
    }

    private WillForceSettings() {
    }

    /** 当前档位（配置未加载时按默认温和处理）。 */
    public static int intensity() {
        return SPEC.isLoaded() ? INTENSITY.get() : WillForce.GENTLE;
    }

    /** 当前档位对应的纯逻辑参数集。 */
    public static WillForce force() {
        return WillForce.of(intensity());
    }

    /**
     * 原先被关掉的压制渠道是否放开：旧开关 <b>或</b> 强度 ≥ 2。
     *
     * <p>旧开关保留原样，所以「显式打开旧开关」的老整合包不会因为本版本改行为；
     * 反之把强度调到 2 及以上时，也可以在旧开关关闭的状态下真的被压制。
     */
    public static boolean pressureOn() {
        return force().pressureEnabled() || EncounterSettings.legacyPressure();
    }

    /** 与 {@link #pressureOn()} 同口径的资源渠道（只读旧开关，档位不解锁实物产地）。 */
    public static boolean resourcesOn() {
        return EncounterSettings.legacyResources();
    }
}
