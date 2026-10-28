// src/main/java/net/guzhenren/tianfu/TianfuLiupai.java
package net.guzhenren.tianfu;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import net.guzhenren.init.GuzhenrenModMobEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 体质自带的「流派增益 buff」。
 *
 * <p>设计口径（用户确认）：<b>只给本体确实存在对应流派 buff 的体质挂</b>，
 * 不硬造新效果；挂上的 buff 一律<b>无限时长常驻</b>，持有体质期间不掉、不需要玩家反复吃蛊。
 *
 * <h2>为什么只能挂这几种</h2>
 * 本体的「流派通用增益」只有 8 个 {@code *ZENGFU / *ZENGSHANG} 效果，且都由
 * {@code ShangHaiLiuCheng1Procedure}（伤害流程）通过 {@code hasEffect} + {@code getAmplifier}
 * 读取，{@code amplifier} 直接作为<b>加算百分比</b>进伤害乘区。其余流派（土/骨/魂/影/月/梦/
 * 兵/冰雪/风/血/虚…）本体没有对应 buff，故这些体质保持现状（仍靠道痕 + 本体流水线体现）。
 *
 * <p>刻意<b>不</b>把 {@code 宙道} 之外的 buff 写进 {@code PlayerVariables}：字段加减由本体
 * 自己的 buff 开始/结束回调负责，本类只负责「让 buff 不掉」，避免与本体回调重复计数。
 *
 * @see TianfuEffects#maintainBodyBuffs(ServerPlayer, int)
 */
public final class TianfuLiupai {

    /**
     * buff 生效等级（amplifier）。
     *
     * <p>量纲已由字节码确认：{@code ShangHaiLiuCheng1Procedure} 末尾是
     * {@code damage × (1 + 累计值)}，累计值即各 buff 的 {@code amplifier} 直接相加，
     * 所以 <b>amplifier = 1 就是 +1%</b>。
     *
     * <p>取 1 是为了对齐本体：本体自己发流派 buff（如剑道）时同样用 amplifier = 1、
     * 时长 600 tick。我们把它换成无限时长，但<b>不放大数值</b>——
     * 「常驻」是本次改动的目的，不是「变强」。想加强应该改体质描述里的数值，而不是偷偷拉高 buff 等级。
     */
    private static final int AMPLIFIER = 1;

    /**
     * 体质 tizhi → 本体流派 buff。
     *
     * <p>键为本体 {@code PlayerVariables.tizhi}，值为 {@link GuzhenrenModMobEffects} 里的字段名。
     * 只收录「本体确有该流派<b>增益</b>buff」的组合：
     * <ul>
     *   <li>25 断岳剑心体（剑道）→ {@code JIANDAOSHANGHAIZENGFU}「剑道伤害增幅」</li>
     *   <li>26 百炼庚金体（金道）→ {@code JINDAOZENGFU}「镀金」</li>
     *   <li>31 太息养元体（气道）→ {@code QIDAOGUCHONGWEILIZENGFU}「气道蛊虫威力增幅」</li>
     *   <li>6 万金妙华体（十绝体·金道）→ {@code JINDAOZENGFU}</li>
     *   <li>10 宇宙大衍体（十绝体·律道）→ {@code LVDAOZENGFU}「律道增幅」</li>
     * </ul>
     *
     * <p>刻意<b>不</b>收录的：
     * <ul>
     *   <li>5 炎煌雷泽体：购买时随机炎道/雷道，写死会挂错，改由 {@link #maintainSijueRoll} 按道痕判断</li>
     *   <li>28 搬山撼岳体：力道，本体没有力道增幅 buff</li>
     *   <li>43 光阴逆转体 / 2 古月阴荒体：宙道 buff 的显示名是「宙侵」，
     *       类别是 <b>NEUTRAL</b>（不是 DEBUFF）。它做的是
     *       {@code zhoudao_shijian_baifenbi -= 0.1 × amplifier}，即按等级压缩目标的
     *       宙侵时长——在 {@code amplifier > 0} 时确实会削弱宙道爆发，所以不挂常驻。
     *       宙道体质的时间加速由 {@code TianfuEffects.applyOnce} 直接写
     *       {@code baifenbi += 1.5} 体现；「宙蚀」玩法走 {@link TianfuZhuCombat}（amplifier 恒为 0）</li>
     *   <li>其余流派（土/骨/魂/影/月/梦/兵/冰雪/风/血/虚…）：本体没有对应 buff，不硬造</li>
     * </ul>
     */
    /**
     * 十绝体的流派候选数据；不是 buff 注册名，也不承担购买、撤销或持久化。
     *
     * @param name 知识库 16.2 的体质名称
     * @param daohenFields 真实道痕字段，固定顺序；单选恰好一项，二选一恰好两项
     * @param seedSalt 固定版本盐；发布后不得修改，以免同一玩家改变分支
     */
    public record BodyPath(String name, List<String> daohenFields, long seedSalt) {
    }

    /**
     * 完整十绝体映射；体质编号已对照现有 TianfuNative.applySijue。
     * 候选列表使用 List.of，不得把二选一解释为同时授予两个流派。
     * 原 buff 表下移为 BUFF_BY_TIZHI 投影，运行期接线交给编码阶段。
     */
    public static final Map<Integer, BodyPath> BY_TIZHI = Map.ofEntries(
            Map.entry(1, new BodyPath("太日阳莽体", List.of("daohen_yudao"), 0x5449414E465531L)),
            Map.entry(2, new BodyPath("古月阴荒体", List.of("dahen_zhoudao"), 0x5449414E465531L)),
            Map.entry(3, new BodyPath("北冥冰魄体", List.of("daohen_hundao", "daohen_bingxuedao"), 0x5449414E465531L)),
            Map.entry(4, new BodyPath("森海轮回体", List.of("daohen_mudao"), 0x5449414E465531L)),
            Map.entry(5, new BodyPath("炎煌雷泽体", List.of("daohen_leidao", "daohen_yandao"), 0x5449414E465531L)),
            Map.entry(6, new BodyPath("万金妙华体", List.of("daohen_jindao"), 0x5449414E465531L)),
            Map.entry(7, new BodyPath("大力真武体", List.of("daohen_lidao"), 0x5449414E465531L)),
            Map.entry(8, new BodyPath("逍遥智心体", List.of("dahen_zhidao"), 0x5449414E465531L)),
            Map.entry(9, new BodyPath("厚土元央体", List.of("daohen_tudao"), 0x5449414E465531L)),
            Map.entry(10, new BodyPath("宇宙大衍体", List.of("daohen_lvdao"), 0x5449414E465531L))
    );

    /**
     * 解析玩家稳定的十绝体流派；此骨架当前未接入运行期。
     *
     * <p>契约：只读 BY_TIZHI。种子为 UUID 高位异或低位循环左移 17 位，
     * 再异或 seedSalt 与 tizhi；使用 java.util.Random(seed).nextInt(候选数)。
     * 不使用玩家姓名、世界时间、全局随机、当前道痕大小或重登次数。
     * 不写玩家变量、不挂 buff、不修改 NBT，也不授予第二个候选。
     *
     * @param tizhi 本体体质编号；未知编号返回 null
     * @param playerId 非 null 的玩家 UUID；跨登录、重启和维度必须稳定
     * @return 一个真实道痕字段名；未知体质返回 null
     * @throws NullPointerException playerId 为 null 时
     */
    public static String selectPath(int tizhi, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId 不可为 null");
        BodyPath mapping = BY_TIZHI.get(tizhi);
        if (mapping == null) {
            return null;
        }
        List<String> candidates = mapping.daohenFields();
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        long seed = playerId.getMostSignificantBits()
                ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
                ^ mapping.seedSalt()
                ^ (long) tizhi;
        return candidates.get(new Random(seed).nextInt(candidates.size()));
    }

    private static final Map<Integer, String> BUFF_BY_TIZHI = new HashMap<>();

    static {
        BUFF_BY_TIZHI.put(25, "JIANDAOSHANGHAIZENGFU");
        BUFF_BY_TIZHI.put(26, "JINDAOZENGFU");
        BUFF_BY_TIZHI.put(31, "QIDAOGUCHONGWEILIZENGFU");
        // 十绝体 6 万金妙华体：固定金道（见 TianfuNative case 6）→ 金道增幅
        BUFF_BY_TIZHI.put(6, "JINDAOZENGFU");
        // 十绝体 10 宇宙大衍体：律道（daohen_lvdao）→ 律道增幅
        // 注意 lvdaozengfu 的显示名是「律道增幅」，不是「力道」；本体没有力道增幅 buff
        BUFF_BY_TIZHI.put(10, "LVDAOZENGFU");
        // 十绝体 5 炎煌雷泽体：购买时随机炎道/雷道，不能写死，走 {@link #maintainSijueRoll}
        //
        // 28 搬山撼岳体是**力道**（daohen_lidao），本体无 lidao 增幅 buff → 不挂
        //
        // 宙道（43 光阴逆转体 / 2 古月阴荒体）**刻意不挂常驻**：
        // 本体 zhoudaobuff 的显示名是「宙侵」，类别 NEUTRAL（不是 DEBUFF）。
        // 它的 onEffectStarted 做 zhoudao_shijian_baifenbi -= 0.1 × amplifier，
        // 即按等级压缩目标宙侵时长；amplifier > 0 时等于削弱宙道爆发。
        // 宙道体质的时间加速由 TianfuEffects.applyOnce 直接写 baifenbi += 1.5 体现；
        // 「宙蚀」玩法（命中附加宙侵）走 TianfuZhuCombat，那里 amplifier 恒为 0，不压缩时长。
    }

    private TianfuLiupai() {
    }

    /** 该体质是否有本体流派 buff 可挂（5 号按道痕动态判断，故恒为 true）。 */
    public static boolean hasBuff(int tizhi) {
        return tizhi == 5 || BUFF_BY_TIZHI.containsKey(tizhi);
    }

    /**
     * 十绝体 5「炎煌雷泽体」：购买时随机走炎道或雷道，故按实际道痕字段判断该挂哪个 buff。
     *
     * <p>炎道有 {@code YANDAOZENGFU}、雷道有 {@code LEIDAOZENGSHANG}，两者都存在，可以挂。
     * 若玩家两个道痕都没有（异常存档），返回 false 不挂。
     */
    private static boolean maintainSijueRoll(ServerPlayer player,
            net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v) {
        if (v.daohen_yandao > 0.0) {
            return apply(player, GuzhenrenModMobEffects.YANDAOZENGFU);
        }
        if (v.daohen_leidao > 0.0) {
            return apply(player, GuzhenrenModMobEffects.LEIDAOZENGSHANG);
        }
        return false;
    }

    /**
     * 让玩家身上带上本体质对应的流派 buff（无限时长）。
     *
     * <p>幂等：已有无限时长同等级 buff 时直接返回，避免每 tick 重建 {@code MobEffectInstance}
     * 造成无谓的同步包。仅在「缺失」或「剩余时长不足」时才补。
     *
     * @return 是否发生了写入（供调用方决定是否置脏）
     */
    public static boolean maintain(ServerPlayer player, int tizhi,
            net.guzhenren.network.GuzhenrenModVariables.PlayerVariables v) {
        if (tizhi == 5) {
            return maintainSijueRoll(player, v);
        }
        String name = BUFF_BY_TIZHI.get(tizhi);
        if (name == null) {
            return false;
        }
        return apply(player, resolve(name));
    }

    /** 幂等地把某个流派 buff 续成无限时长。 */
    private static boolean apply(ServerPlayer player, Holder<MobEffect> effect) {
        return apply(player, effect, AMPLIFIER);
    }

    /** 幂等地把某个流派 buff 续成无限时长（指定等级）。 */
    private static boolean apply(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        if (effect == null) {
            return false;
        }
        MobEffectInstance existing = player.getEffect(effect);
        // -1 = 无限；已有无限时长且等级一致就不用再写
        if (existing != null && existing.getDuration() == -1
                && existing.getAmplifier() == amplifier) {
            return false;
        }
        // ambient=false 隐藏粒子、showIcon=true 保留图标，让玩家能在状态栏看到自己有什么
        player.addEffect(new MobEffectInstance(effect, -1, amplifier, false, false, true));
        return true;
    }

    /**
     * 回收本模组挂上的流派 buff。
     *
     * <p>只在<b>确认是本模组给的</b>（无限时长）时移除，避免误删玩家自己吃蛊获得的同名限时 buff。
     */
    public static void revoke(ServerPlayer player, int tizhi) {
        if (tizhi == 5) {
            revokeEffect(player, GuzhenrenModMobEffects.YANDAOZENGFU);
            revokeEffect(player, GuzhenrenModMobEffects.LEIDAOZENGSHANG);
            return;
        }
        String name = BUFF_BY_TIZHI.get(tizhi);
        if (name == null) {
            return;
        }
        revokeEffect(player, resolve(name));
    }

    private static void revokeEffect(ServerPlayer player, Holder<MobEffect> effect) {
        if (effect == null) {
            return;
        }
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getDuration() == -1) {
            player.removeEffect(effect);
        }
    }

    /**
     * 效果字段名 → 注册表 holder。
     *
     * <p>这些字段是本体 {@code GuzhenrenModMobEffects} 的 {@code public static final DeferredHolder}，
     * 直接静态引用即可（本体未混淆，字段名稳定）。用 {@code switch} 而非反射，
     * 让编译期就能发现字段名拼错。
     */
    private static Holder<MobEffect> resolve(String name) {
        return switch (name) {
            case "JIANDAOSHANGHAIZENGFU" -> GuzhenrenModMobEffects.JIANDAOSHANGHAIZENGFU;
            case "JINDAOZENGFU" -> GuzhenrenModMobEffects.JINDAOZENGFU;
            case "LVDAOZENGFU" -> GuzhenrenModMobEffects.LVDAOZENGFU;
            case "YANDAOZENGFU" -> GuzhenrenModMobEffects.YANDAOZENGFU;
            case "LEIDAOZENGSHANG" -> GuzhenrenModMobEffects.LEIDAOZENGSHANG;
            case "QIDAOGUCHONGWEILIZENGFU" -> GuzhenrenModMobEffects.QIDAOGUCHONGWEILIZENGFU;
            default -> null;
        };
    }
}
