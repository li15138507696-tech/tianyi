package net.guzhenren.tianfu;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「X烛」的施加与伤害结算。
 *
 * <h2>照抄宙侵的机制</h2>
 * 本体宙侵（{@code zhoudaobuff}）的爆发逻辑是：
 * <ol>
 *   <li>命中目标时，<b>若目标已有宙侵</b> → 按「已侵蚀时长 × 0.1」结算一次伤害，
 *       基数是 {@code dahen_zhoudao}，倍率是 {@code zhoudao_shijian_baifenbi}；</li>
 *   <li>然后重新施加宙侵，时长 {@code 200 × 倍率}。</li>
 * </ol>
 *
 * <p>本类对 19 支烛做同样的事，只有两处不同：
 * <ul>
 *   <li>基数换成<b>各自流派</b>的道痕字段；</li>
 *   <li>结算时传给 {@code ShangHaiLiuCheng1Procedure} 的流派标记也是各自的，
 *       从而吃到各自的流派增幅 buff（剑道伤害增幅、炎道增幅…）。</li>
 * </ul>
 *
 * <h2>为什么调 {@code ShangHaiLiuCheng1Procedure} 而不是自己 {@code hurt()}</h2>
 * 那个方法是本体<b>唯一</b>的流派伤害结算入口，内部会处理
 * 流派 buff 加成、威力翻倍、真实伤害等一整套逻辑。
 * 自己 {@code hurt()} 会绕过它们，还会和本体已有的结算双计。
 * 已用 {@code probe_shanghai} 编译验证过 8 参签名可调用。
 *
 * <h2>为什么只给「已有烛」的目标结算</h2>
 * 这正是宙侵的设计：第一下只是「点燃」，第二下才「爆」。
 * 改成第一下就爆会让伤害翻倍且失去节奏。
 */
public final class TianfuZhuCombat {

    /** 同一目标的最小重施间隔（tick），与宙侵一致。 */
    private static final int REFRESH_TICKS = 2;

    /** 施加等级恒为 0（与宙侵一致：等级越高目标后续时长越短，取 0 时该运算为恒等）。 */
    private static final int AMPLIFIER = 0;

    /** 目标 UUID → 上次施加的 tick，用于节流。 */
    private static final Map<java.util.UUID, Integer> LAST_APPLY = new ConcurrentHashMap<>();

    private TianfuZhuCombat() {
    }

    /**
     * 解析某个烛的效果 holder。
     *
     * <p>必须走注册表真实 holder：holder 身份不同会让
     * {@code hasEffect} / {@code getEffect} 全部失效。
     */
    private static Holder<MobEffect> holderOf(TianfuZhu.Candle candle) {
        var opt = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                .getHolder(net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath("tianfu", candle.id()));
        return opt.map(h -> (Holder<MobEffect>) h).orElse(null);
    }

    /**
     * 命中时调用：先结算（若已有烛），再重新点燃。
     *
     * @param source 造成伤害的玩家
     * @param target 被打的目标
     * @return 是否真的施加了
     */
    public static boolean onHit(ServerPlayer source, LivingEntity target) {
        if (source == null || target == null || !target.isAlive() || source == target) {
            return false;
        }
        PlayerVariables v = source.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        TianfuZhu.Candle candle = TianfuZhu.of((int) Math.round(v.tizhi));
        if (candle == null) {
            return false;   // 这个体质没有烛
        }
        return apply(source, target, candle, v);
    }

    /**
     * 施加一支烛；若目标已有，先按已侵蚀时长结算伤害。
     */
    public static boolean apply(ServerPlayer source, LivingEntity target,
                                TianfuZhu.Candle candle, PlayerVariables v) {
        Holder<MobEffect> effect = holderOf(candle);
        if (effect == null) {
            return false;
        }

        // ---- 节流：同一 tick 内多个伤害源只处理一次 ----
        int now = target.tickCount;
        Integer last = LAST_APPLY.get(target.getUUID());
        if (last != null && now - last >= 0 && now - last < REFRESH_TICKS) {
            return false;
        }
        LAST_APPLY.put(target.getUUID(), now);

        MobEffectInstance existing = target.getEffect(effect);

        // ---- 1. 已有烛 → 按已侵蚀时长结算一次伤害 ----
        if (existing != null && existing.getDuration() > 0) {
            settle(source, target, candle, v, existing);
        }

        // ---- 2. 重新点燃 ----
        // 时长口径照宙侵：200 × 目标的宙道时间倍率（只有玩家有真实数据，怪物按 1.0）
        double mult = 1.0;
        if (target instanceof net.minecraft.world.entity.player.Player tp) {
            PlayerVariables tv = tp.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            if (tv != null && tv.zhoudao_shijian_baifenbi > 0.0) {
                mult = tv.zhoudao_shijian_baifenbi;
            }
        }
        int duration = (int) Math.max(20.0, TianfuZhu.BASE_DURATION * mult);

        // 无限时长的别覆盖成有限（那是降级）
        if (existing != null && existing.getDuration() == -1) {
            return false;
        }
        int amp = existing == null ? AMPLIFIER
                : Math.max(AMPLIFIER, existing.getAmplifier());
        // 只延长不缩短
        if (existing != null && existing.getDuration() > duration) {
            duration = existing.getDuration();
        }

        boolean added = target.addEffect(
                new MobEffectInstance(effect, duration, amp, false, true));

        if (added && target.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.guzhenren.init.GuzhenrenModParticleTypes.ZHOUDAOLIZI_2.get(),
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10, 0.3, 0.4, 0.3, 0.02);
        }
        return added;
    }

    /**
     * 按已侵蚀时长结算一次伤害。
     *
     * <p>公式与宙侵一致：
     * <pre>
     *   基数   = 玩家的流派道痕（daohen_jiandao 等）
     *   倍率   = 目标的 zhoudao_shijian_baifenbi（默认 1.0）
     *   加成   = 已侵蚀时长 × 0.1
     * </pre>
     * 然后交给 {@code ShangHaiLiuCheng1Procedure} 按该流派通道结算。
     */
    private static void settle(ServerPlayer source, LivingEntity target,
                               TianfuZhu.Candle candle, PlayerVariables v,
                               MobEffectInstance existing) {
        try {
            double base = daohenOf(v, candle.daohenKey());
            if (base <= 0.0) {
                return;   // 没有道痕就没有伤害，不白打
            }
            double mult = 1.0;
            if (target instanceof net.minecraft.world.entity.player.Player tp) {
                PlayerVariables tv = tp.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
                if (tv != null && tv.zhoudao_shijian_baifenbi > 0.0) {
                    mult = tv.zhoudao_shijian_baifenbi;
                }
            }
            double elapsed = existing.getDuration() * TianfuZhu.ELAPSED_SCALE;

            net.guzhenren.procedures.ShangHaiLiuCheng1Procedure.execute(
                    source.level(), source, target, base, mult, elapsed, candle.liupai());
        } catch (Throwable t) {
            TianfuMod.LOGGER.warn("tianfu {} 结算失败（已忽略）", candle.name(), t);
        }
    }

    /** 按字段名读道痕。字段名来自 {@link TianfuZhu.Candle#daohenKey()}。 */
    private static double daohenOf(PlayerVariables v, String key) {
        return switch (key) {
            case "daohen_tudao" -> v.daohen_tudao;
            case "daohen_bingdao" -> v.daohen_bingdao;
            case "daohen_bingxuedao" -> v.daohen_bingxuedao;
            case "daohen_fengdao" -> v.daohen_fengdao;
            case "daohen_gudao" -> v.daohen_gudao;
            case "daohen_hundao" -> v.daohen_hundao;
            case "daohen_jiandao" -> v.daohen_jiandao;
            case "daohen_jindao" -> v.daohen_jindao;
            case "daohen_lidao" -> v.daohen_lidao;
            case "daohen_mudao" -> v.daohen_mudao;
            case "dahen_qidao" -> v.dahen_qidao;
            case "daohen_xuedao" -> v.daohen_xuedao;
            case "daohen_xudao" -> v.daohen_xudao;
            case "daohen_yingdao" -> v.daohen_yingdao;
            case "daohen_yuedao" -> v.daohen_yuedao;
            case "dahen_zhidao" -> v.dahen_zhidao;
            case "dahen_zhoudao" -> v.dahen_zhoudao;
            case "daohen_mengdao" -> v.daohen_mengdao;
            default -> 0.0;
        };
    }

    /** 清理离场目标的节流表，防止记录无限增长。 */
    public static void forget(Entity entity) {
        LAST_APPLY.remove(entity.getUUID());
    }
}
