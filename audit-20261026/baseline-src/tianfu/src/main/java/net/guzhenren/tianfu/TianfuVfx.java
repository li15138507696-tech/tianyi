package net.guzhenren.tianfu;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.guzhenren.tianfu.init.TianfuParticleTypes;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 体质特效（tianfu VFX）：服务端 {@code ServerLevel.sendParticles} 广播，
 * <b>附近所有玩家都能看到</b>（不依赖客户端 mod，装没装附属都看得见）。
 *
 * <p>设计约束：
 * <ul>
 *   <li><b>性能优先</b>：每 tick 只跑一个配方（1–3 个 sendParticles 调用），
 *       并用「错峰 tick」把 20 个体质分散到不同相位，避免同一 tick 全服广播；</li>
 *   <li><b>全部原版粒子</b>：不注册自定义 ParticleType、不带贴图，
 *       靠颜色/速度/位置组合出区分度（原版粒子无染色通道，故用「粒子种类 + 形态」区分）；</li>
 *   <li><b>玩家位置驱动</b>：跟人走，不建实体、不存状态，进游戏即生效、退游戏即消失。</li>
 * </ul>
 *
 * <p>形态分三档：
 * <ul>
 *   <li>{@link Shape#RING} 绕身环（水平圆环，半径呼吸）；</li>
 *   <li>{@link Shape#COLUMN} 上行柱（脚下往上飘）；</li>
 *   <li>{@link Shape#ORBIT} 环绕轨（竖直圆环 + 上下浮动）。</li>
 * </ul>
 */
public final class TianfuVfx {
    /** 环/轨的基础半径（格）。 */
    private static final double RADIUS = 0.65;
    /** 每种形态每 tick 发射的粒子数。 */
    private static final int DENSITY = 3;

    private TianfuVfx() {
    }

    /** 特效形态。 */
    private enum Shape {
        RING, COLUMN, ORBIT,
        /** 剑气环绕：多把剑沿身体旋转，带刃向速度（看得出「剑势」）。 */
        SWORD,
        /** 火焰环绕：火苗沿螺旋线上升，绕身一圈。 */
        FLAME,
        /** 冲击波：从脚底向外扩散的环形气浪，周期性脉冲。 */
        SHOCKWAVE,
        /** 符文阵：脚下旋转的法阵，多个符文点沿圆周排布。 */
        RUNE_CIRCLE,
        /** 螺旋锁链：绕身缠绕的螺旋线（血道锁链、藤蔓）。 */
        CHAIN,
        /** 悬浮环绕：悬浮在身周的多个点（飞剑、岩块、水珠）。 */
        FLOAT_ORBIT,
        /** 星辉垂落：从高处向下飘落的星尘。 */
        STARFALL,
        /** 毒雾扩散：贴地缓慢向外扩散的雾团。 */
        MIST
    }

    /**
     * 一个体质的特效配方。
     *
     * <p><b>v1.2.21 起支持多层</b>：原来一个配方只有一种粒子一个形态，
     * 做不出「冲击波 + 符文阵 + 碎石」这种复合画面。现在一个配方有：
     * <ul>
     *   <li>{@code main} 主粒子（必填，环绕身体的主体）；</li>
     *   <li>{@code rune} 脚下符文阵（可选，null = 无）；</li>
     *   <li>{@code accent} 点睛粒子（可选，爆散/火星/骨粉）；</li>
     *   <li>{@code shock} 是否周期发冲击波环。</li>
     * </ul>
     *
     * @param density 主粒子每次发射数（-1 = 用全局 {@link #DENSITY}）
     * @param accentRate 点睛粒子每几次触发一次（0 = 不触发）
     * @param shockRate  冲击波每几次触发一次（0 = 不触发）
     */
    private record Recipe(ParticleOptions particle, Shape shape, double speed, int phase,
                          int density, ParticleOptions rune, ParticleOptions accent,
                          int accentRate, int shockRate) {
        int d() {
            return density > 0 ? density : DENSITY;
        }
    }

    /** 建一个只有主粒子的配方（旧式写法，保留兼容）。 */
    private static Recipe simple(ParticleOptions p, Shape shape, double speed, int phase,
                                 int density) {
        return new Recipe(p, shape, speed, phase, density, null, null, 0, 0);
    }

    /** 建一个复合配方。 */
    private static Recipe multi(ParticleOptions main, Shape shape, double speed, int phase,
                                int density, ParticleOptions rune, ParticleOptions accent,
                                int accentRate, int shockRate) {
        return new Recipe(main, shape, speed, phase, density, rune, accent, accentRate, shockRate);
    }

    /**
     * 体质（tizhi）→ 特效配方。只列做了特效的体质；查不到返回 null（走默认无特效）。
     *
     * <p>{@code phase} 是错峰相位（0..PERIOD-1）：同帧只播 phase == tickCount % PERIOD 的配方，
     * 把广播压力摊到整个周期上。
     */
    private static final Map<Integer, Recipe> RECIPES = new HashMap<>();

    /** 错峰周期（tick）。20 个体质 × 每 tick 3 粒子 → 摊到 12 tick 后平均每 tick 5 次广播。 */
    private static final int PERIOD = 12;

    /**
     * 本体粒子类型的短名别名。
     *
     * <p>本体的类名是 {@code GuzhenrenModParticleTypes}，字段名又长
     * （{@code QIDAOLIZIJIANQI}），直接写会让配方表完全读不下去。
     * 这里集中别名一次，配方表只写 {@code P.XXX}。
     *
     * <p><b>为什么不直接 static import</b>：本体的字段是
     * {@code public static final DeferredHolder<...>}，static import 后
     * 每个字段仍要写全名；而且一旦本体改字段名，报错点会散落在配方表各处。
     * 集中在这里，改的时候只改一处。
     */
    private static final class P {
        // 力道 / 土道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZILIQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZILIQI;
        // 气道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIJIANQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIJIANQI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIYUANQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIYUANQI;
        // 本体没有 QIDAOLIZIQINGQI，气道青色用律道青色粒子代替
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIQINGQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.LVDAOQINGSELIZI;
        // 血道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIXUEQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIXUEQI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> XUEYULIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.XUEYULIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> XUEKUAILIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.XUEKUAILIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> HONGSEFANWEN =
                net.guzhenren.init.GuzhenrenModParticleTypes.HONGSEFANWEN;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> HONGSEFANWENJUDA =
                net.guzhenren.init.GuzhenrenModParticleTypes.HONGSEFANWENJUDA;
        // 骨道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIJINQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIJINQI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIJINGQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIJINGQI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> LUOXIAXUEYE =
                net.guzhenren.init.GuzhenrenModParticleTypes.LUOXIAXUEYE;
        // 炎道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIHUOQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIHUOQI;
        // 水道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHUIDI =
                net.guzhenren.init.GuzhenrenModParticleTypes.SHUIDI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHUIGUANGLIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.SHUIGUANGLIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHUILIANLZ =
                net.guzhenren.init.GuzhenrenModParticleTypes.SHUILIANLZ;
        // 木道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QITENGJIANYI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QITENGJIANYI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QINGFENLUNGULIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QINGFENLUNGULIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> LVDAOQINGSELIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.LVDAOQINGSELIZI;
        // 土道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> LVDAOHUANGSELIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.LVDAOHUANGSELIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> FEISHA =
                net.guzhenren.init.GuzhenrenModParticleTypes.FEISHA;
        // 金道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> DAOJIAN_1 =
                net.guzhenren.init.GuzhenrenModParticleTypes.DAOJIAN_1;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUJINLIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.DUJINLIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUJINLIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.MUJINLIZI;
        // 雷道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> LEI_DIAN =
                net.guzhenren.init.GuzhenrenModParticleTypes.LEI_DIAN;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> LEITINGLIZISHU =
                net.guzhenren.init.GuzhenrenModParticleTypes.LEITINGLIZISHU;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> XUNLEI =
                net.guzhenren.init.GuzhenrenModParticleTypes.XUNLEI;
        // 魂道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUIYAN_2 =
                net.guzhenren.init.GuzhenrenModParticleTypes.GUIYAN_2;
        // 本体没有 QIDAOLIZIHUNQI，魂道阵用鬼焰
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZIHUNQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.GUIYAN_2;
        // 信道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> QIDAOLIZISHENGQI =
                net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZISHENGQI;
        // 星道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> XINGXIU =
                net.guzhenren.init.GuzhenrenModParticleTypes.XINGXIU;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> XINGXIU_2 =
                net.guzhenren.init.GuzhenrenModParticleTypes.XINGXIU_2;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> XINGNIANLIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.XINGNIANLIZI;
        // 毒道
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUYU =
                net.guzhenren.init.GuzhenrenModParticleTypes.DUYU;
        // 其它
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> KUANGFENGLIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.KUANGFENGLIZI;
        static final DeferredHolder<ParticleType<?>, SimpleParticleType> ZHOUDAOLIZI =
                net.guzhenren.init.GuzhenrenModParticleTypes.ZHOUDAOLIZI;

        private P() {
        }
    }

    static {
        // ================================================================
        // 14 条道的复合特效
        //
        // 每条道用「主环绕 + 脚下符文阵 + 点睛粒子 + 冲击波」四层拼出
        // 画面感。粒子全部取自本体已注册的 82 种流派粒子，不新建贴图。
        // ================================================================

        // ---- 力道（28 搬山撼岳体）----
        // 赤红气浪炸开、碎石环形冲击波、肌肉金色符文
        RECIPES.put(28, multi(
                P.QIDAOLIZILIQI.get(), Shape.SHOCKWAVE, 0.04, 8, 16,
                P.QIDAOLIZILIQI.get(),      // 脚下力道符文阵
                P.FEISHA.get(),             // 点睛：飞沙碎石
                3, 14));

        // ---- 气道（31 太息养元体）----
        // 百道无形剑气凝成白色龙卷、青色符文明灭
        RECIPES.put(31, multi(
                P.QIDAOLIZIJIANQI.get(), Shape.ORBIT, 0.03, 10, 14,
                P.QIDAOLIZIQINGQI != null ? P.QIDAOLIZIQINGQI.get() : P.QIDAOLIZIYUANQI.get(),
                P.QIDAOLIZIYUANQI.get(),
                2, 0));

        // ---- 血道（33 修罗战体 + 53 血煞蜕变体）----
        // 血雾、鲜血锁链缠绕、脚下血色符文阵
        RECIPES.put(33, multi(
                P.QIDAOLIZIXUEQI.get(), Shape.CHAIN, 0.03, 11, 20,
                P.HONGSEFANWEN.get(),       // 血色符文阵
                P.XUEYULIZI.get(),          // 点睛：血雨
                2, 0));
        // v1.2.25：再加「血影流花」实体（血影炸开成花）
        RECIPES.put(53, multi(
                P.QIDAOLIZIXUEQI.get(), Shape.CHAIN, 0.03, 7, 14,
                P.HONGSEFANWEN.get(),
                P.XUEKUAILIZI.get(),        // 点睛：血块
                3, 0));

        // ---- 骨道（22 白玉铁骨体）----
        // 骨刺自地底连环刺出、骨粉如雪、墓地浓雾
        RECIPES.put(22, multi(
                P.QIDAOLIZIJINQI.get(), Shape.COLUMN, 0.02, 4, 12,
                P.QIDAOLIZIJINGQI.get(),    // 脚下精气道符文（骨白）
                P.LUOXIAXUEYE.get(),        // 点睛：飘落（骨粉如雪）
                2, 0));

        // ---- 炎道（5 炎煌雷泽体）----
        // 火浪席卷、烈焰披风、熔岩血脉、火星爆裂
        RECIPES.put(5, multi(
                TianfuParticleTypes.SHAREN_YAN.get(), Shape.FLAME, 0.035, 9, 16,
                P.HONGSEFANWENJUDA.get(),   // 脚下巨大血色符文阵（炎纹）
                P.QIDAOLIZIHUOQI.get(),     // 点睛：火气
                2, 20));

        // ---- 水道（19 寒渊素雪体）----
        // 水幕流转、水珠悬浮、水凝成刃
        RECIPES.put(19, multi(
                P.SHUIDI.get(), Shape.FLOAT_ORBIT, 0.02, 2, 14,
                P.SHUIGUANGLIZI.get(),      // 脚下水光阵
                P.SHUILIANLZ.get(),         // 点睛：水链
                2, 0));

        // ---- 木道（30 苍木回春体）----
        // 巨木破土、藤蔓蔓延、花粉光点；v1.2.25 起再加「妖藤」实体缠绕
        RECIPES.put(30, multi(
                P.QITENGJIANYI.get(), Shape.CHAIN, 0.025, 9, 14,
                P.QINGFENLUNGULIZI.get(),   // 脚下青色粉轮（花粉）
                P.LVDAOQINGSELIZI.get(),    // 点睛：青色粒子
                2, 0));

        // ---- 土道（17 九命不灭体）----
        // 大地隆起环形石墙、岩石巨拳、沙尘暴、悬浮岩块
        RECIPES.put(17, multi(
                P.QIDAOLIZILIQI.get(), Shape.SHOCKWAVE, 0.03, 0, 12,
                P.LVDAOHUANGSELIZI.get(),   // 脚下黄色符文（土）
                P.FEISHA.get(),             // 点睛：飞沙
                2, 18));

        // ---- 金道（26 百炼庚金体）----
        // 万柄飞剑自虚空浮现如归巢、金属光泽、银色裂痕
        RECIPES.put(26, multi(
                P.DAOJIAN_1.get(), Shape.FLOAT_ORBIT, 0.04, 7, 16,
                P.DUJINLIZI.get(),          // 脚下镀金阵
                P.MUJINLIZI.get(),          // 点睛：木金粒子（锐金）
                2, 0));

        // ---- 雷道（18 百战金戈体）----
        // 紫电灌顶、雷池脚下、连锁闪电、电弧缠身
        RECIPES.put(18, multi(
                P.LEI_DIAN.get(), Shape.ORBIT, 0.05, 1, 16,
                P.LEITINGLIZISHU.get(),     // 脚下雷树阵
                P.XUNLEI.get(),             // 点睛：迅雷
                2, 16));

        // ---- 魂道（24 幽魂凝识体）----
        // 魂魄离体、幽绿鬼火、黑雾、鬼面浮现；v1.2.25 起加「百鬼夜行」实体
        RECIPES.put(24, multi(
                P.GUIYAN_2.get(), Shape.ORBIT, 0.02, 5, 10,
                P.QIDAOLIZIHUNQI.get(),     // 脚下魂气阵
                TianfuParticleTypes.HUNHUO.get(),  // 点睛：魂火
                2, 0));

        // ---- 信道（42 慧心通明体）----
        // 金色契约卷轴、朱砂符印、发光文字化锁链
        RECIPES.put(42, multi(
                P.QIDAOLIZISHENGQI.get(), Shape.CHAIN, 0.02, 3, 20,
                P.HONGSEFANWEN.get(),       // 脚下朱砂符印阵
                TianfuParticleTypes.NIANTOU_GUANG.get(),  // 点睛：念头光
                2, 0));

        // ---- 星道（40 明月入怀体）----
        // 星斗连线垂落银辉、星光凝箭如雨、旋转星河；v1.2.25 起加「镜月」实体
        RECIPES.put(40, multi(
                P.XINGXIU.get(), Shape.STARFALL, 0.02, 2, 12,
                P.XINGNIANLIZI.get(),       // 脚下星念阵
                P.XINGXIU_2.get(),          // 点睛：星辉
                2, 0));

        // ---- 毒道（37 太虚化形体 → 借位；本体无独立毒系凡体）----
        // 翠绿毒瘴扩散、腐蚀白烟、毒虫虚影
        RECIPES.put(37, multi(
                P.DUYU.get(), Shape.MIST, 0.015, 0, 14,
                P.LVDAOQINGSELIZI.get(),    // 脚下青绿阵
                P.DUJINLIZI.get(),          // 点睛：毒金
                3, 0));

        // ================================================================
        // 其余体质：保留原有的单层形态
        // ================================================================

        // 断岳剑心体（25）：剑气环绕 —— 20 道剑气分两层切向飞行
        RECIPES.put(25, simple(TianfuParticleTypes.JIANQI.get(), Shape.SWORD, 0.05, 6, 8));

        // 南柯一梦体（48）：梦思轨
        RECIPES.put(48, simple(TianfuParticleTypes.NIANTOU_GUANG.get(), Shape.ORBIT, 0.02, 6, 12));

        // 长风破浪体（21）：风流柱
        RECIPES.put(21, simple(P.KUANGFENGLIZI.get(), Shape.COLUMN, 0.04, 3, 12));

        // 如影随形体（39）：影墨柱
        RECIPES.put(39, simple(ParticleTypes.SQUID_INK, Shape.COLUMN, 0.02, 1, 10));

        // 光阴逆转体（43）：时涡轨
        RECIPES.put(43, simple(P.ZHOUDAOLIZI.get(), Shape.ORBIT, 0.03, 4, 12));

        // 御空踏云体（45）：云光影环
        RECIPES.put(45, simple(ParticleTypes.END_ROD, Shape.RING, 0.03, 5, 10));

        // 杀神（54）：杀焰环绕
        RECIPES.put(54, simple(TianfuParticleTypes.SHAREN_YAN.get(), Shape.ORBIT, 0.02, 8, 12));
    }

    /** 该体质是否有特效。 */
    public static boolean has(int tizhi) {
        return RECIPES.containsKey(tizhi);
    }

    /**
     * 每 tick 调用：按错峰相位挑一个体质配方播放。
     *
     * <p><b>密集形态不受错峰限制</b>：{@link Shape#SWORD}、{@link Shape#FLAME}、
     * {@link Shape#CHAIN}、{@link Shape#MIST} 靠「连续多颗粒子连成线」表现剑痕、
     * 火焰、锁链、毒雾。若也每 12 tick 才发一批，会断成一段一段的，
     * 完全看不出环绕感。因此这些形态<b>每 tick</b> 都播。
     *
     * <p>代价：这类玩家每 tick 1–3 次 sendParticles（主环绕 + 符文阵 + 点睛），
     * 约为普通形态的几十倍。单次 12–20 粒子在服务端是常量级开销，可接受；
     * 但若将来把密度加到 50+，或同时有多个玩家在同一个区块，需要重新评估。
     *
     * @param level     服务端世界（广播给所有能看到该位置的玩家）
     * @param player    目标玩家（特效中心）
     * @param tizhi     体质值（{@code v.tizhi} 取整）
     * @param tickCount 玩家 tick 计数，用于错峰与动画相位
     */
    public static void play(ServerLevel level, ServerPlayer player, int tizhi, int tickCount) {
        Recipe recipe = RECIPES.get(tizhi);
        if (recipe == null || level == null) {
            return;
        }
        if (!isDense(recipe.shape())) {
            int phase = Math.floorMod(tickCount, PERIOD);
            if (recipe.phase() != phase) {
                return;
            }
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        RandomSource random = player.getRandom();

        // ---- 第一层：主环绕 ----
        switch (recipe.shape()) {
            case RING -> ring(level, x, y, z, random, recipe);
            case COLUMN -> column(level, x, y, z, random, recipe);
            case ORBIT -> orbit(level, x, y, z, random, recipe, tickCount);
            case SWORD -> sword(level, x, y, z, random, recipe, tickCount);
            case FLAME -> flame(level, x, y, z, random, recipe, tickCount);
            case SHOCKWAVE -> shockwave(level, x, y, z, random, recipe, tickCount);
            case RUNE_CIRCLE -> runeCircle(level, x, y, z, random, recipe, tickCount);
            case CHAIN -> chain(level, x, y, z, random, recipe, tickCount);
            case FLOAT_ORBIT -> floatOrbit(level, x, y, z, random, recipe, tickCount);
            case STARFALL -> starfall(level, x, y, z, random, recipe, tickCount);
            case MIST -> mist(level, x, y, z, random, recipe, tickCount);
            default -> {
            }
        }

        // ---- 第二层：脚下符文阵（每 2 tick 一次，够连续又不吵）----
        if (recipe.rune() != null && tickCount % 2 == 0) {
            runeCircle(level, x, y, z, random,
                    new Recipe(recipe.rune(), Shape.RUNE_CIRCLE, 0.0, 0, 10,
                            null, null, 0, 0), tickCount);
        }

        // ---- 第三层：点睛粒子（每 accentRate tick 一次）----
        if (recipe.accent() != null && recipe.accentRate() > 0
                && tickCount % recipe.accentRate() == 0) {
            accent(level, x, y, z, random, recipe);
        }

        // ---- 第四层：冲击波（每 shockRate tick 一次）----
        if (recipe.shockRate() > 0 && tickCount % recipe.shockRate() == 0) {
            shockRing(level, x, y, z, random, recipe);
        }
    }

    /** 靠连续粒子成形的形态，不受错峰限制。 */
    private static boolean isDense(Shape shape) {
        return switch (shape) {
            case SWORD, FLAME, CHAIN, MIST, STARFALL, SHOCKWAVE -> true;
            default -> false;
        };
    }

    /**
     * 冲击波：从脚底向外扩散的环形气浪。
     *
     * <p>用 {@code count=0} 的单粒子 + 随机偏移做「环形」，而不是
     * 一次发 20 个——因为 {@code sendParticles} 的 count 是「总量」，
     * 位置由 xDist/yDist/zDist 的<b>正态分布</b>决定，直接给大 count
     * 会得到一团高斯云而不是环。所以这里自己算圆周坐标。
     */
    private static void shockwave(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        // 每 20 tick 一个脉冲周期：环半径从 0 涨到 2.2 格
        double t = (tickCount % 20) / 20.0;
        double r = 0.3 + t * 1.9;
        // 越往外越淡（用更少的粒子模拟衰减）
        int count = Math.max(2, (int) (n * (1.0 - t * 0.6)));
        for (int i = 0; i < count; i++) {
            double a = (i / (double) count) * Mth.TWO_PI + random.nextDouble() * 0.15;
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) a) * r, y + 0.08, z + Mth.sin((float) a) * r,
                    1, 0.0, 0.01, 0.0, recipe.speed());
        }
    }

    /** 符文阵：脚下水平圆环，反向旋转，多圈。 */
    private static void runeCircle(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        double base = -(tickCount % 60) * (Mth.TWO_PI / 60.0);   // 反向慢转
        for (int ring = 0; ring < 2; ring++) {
            double rr = 0.55 + ring * 0.45;
            int cnt = Math.max(4, n / 2);
            for (int i = 0; i < cnt; i++) {
                double a = base * (ring == 0 ? 1.0 : -1.3)
                        + i * (Mth.TWO_PI / cnt);
                level.sendParticles(recipe.particle(),
                        x + Mth.cos((float) a) * rr, y + 0.03,
                        z + Mth.sin((float) a) * rr,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * 螺旋锁链：绕身缠绕的螺旋线（血道锁链、藤蔓）。
     *
     * <p>与 {@link #flame} 的区别：flame 是<b>上升</b>的螺旋，
     * chain 是<b>缠绕</b>——螺旋轴向是竖直的，但每圈高度几乎不变，
     * 看起来像绳子一圈圈绕在身上。
     *
     * <p><b>圈数与密度必须匹配</b>：一圈至少要有 8–10 颗粒子，螺旋才
     * 看得出是螺旋。v1.2.20 第一版用 14 粒子绕 3.5 圈（每圈 4 颗），
     * 侧视图上粒子排成几排横线，完全不像缠绕。现在改成 <b>2 圈</b>，
     * 配合密度 18–20（每圈 9–10 颗），侧视图会呈现清晰的正弦波。
     *
     * <p>半径也随圈相位起伏（相邻两圈一胖一瘦），进一步强化「一根绳
     * 在绕」而不是「一个柱体」的观感。
     */
    private static void chain(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        double base = (tickCount % 36) * (Mth.TWO_PI / 36.0);
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            // 2 圈缠绕，高度覆盖 0.15 → 1.72（略低于头顶）
            double a = base + t * Mth.TWO_PI * 2.0;
            double h = 0.15 + t * 1.57;
            // 半径随圈相位起伏：每圈一胖一瘦
            double wobble = 0.78 + Mth.sin((float) a) * 0.22;
            double r = RADIUS * wobble;
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) a) * r, y + h, z + Mth.sin((float) a) * r,
                    1, 0.0, recipe.speed() * 0.3, 0.0, recipe.speed() * 0.5);
        }
    }

    /**
     * 悬浮环绕：多个点悬浮在身周不同高度，缓慢公转（飞剑、岩块、水珠）。
     *
     * <p>与 {@link #orbit} 的区别：orbit 是<b>单层</b>竖直圆环（3 点），
     * 这里是<b>多层</b>（每个点有自己固定的高度与半径），更像「一群东西
     * 各自悬在那儿」而不是「一个环在转」。
     */
    private static void floatOrbit(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        double base = (tickCount % 48) * (Mth.TWO_PI / 48.0);
        for (int i = 0; i < n; i++) {
            // 每个点用 i 的固定偏移决定高度与半径，形成「随机但稳定」的悬浮群
            double h = 0.35 + ((i * 37) % 100) / 100.0 * 1.15;
            double rr = RADIUS * (0.9 + ((i * 53) % 100) / 100.0 * 0.7);
            double a = base + i * (Mth.TWO_PI / n);
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) a) * rr, y + h, z + Mth.sin((float) a) * rr,
                    1, 0.0, 0.005, 0.0, recipe.speed() * 0.4);
        }
    }

    /**
     * 星辉垂落：星尘从头顶上方向下飘，落到地面。
     *
     * <p><b>高度范围必须覆盖玩家本体</b>：v1.2.20 第一版用
     * {@code h = 2.0 + rnd*1.4}（2.0–3.4 格），结果粒子全飘在头顶上方，
     * 身体周围什么都没有——侧视图一眼就看出来了。
     *
     * <p>现在改成 0.2 → 2.4 格均匀分布，配 {@code vy = -0.02} 的向下速度，
     * 视觉上是「从头顶落下来的星尘」而不是「头顶的一团云」。
     */
    private static void starfall(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double rr = RADIUS * (0.4 + random.nextDouble() * 1.1);
            // 0.2 → 2.4：覆盖全身，最高的略高于头顶
            double h = 0.2 + random.nextDouble() * 2.2;
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) a) * rr, y + h, z + Mth.sin((float) a) * rr,
                    1, 0.0, -0.02, 0.0, recipe.speed());
        }
    }

    /** 毒雾扩散：贴地缓慢向外扩散的雾团。 */
    private static void mist(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        // 每 40 tick 一个扩散周期
        double t = (tickCount % 40) / 40.0;
        double r = 0.4 + t * 1.6;
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double rr = r * (0.7 + random.nextDouble() * 0.5);
            double h = 0.05 + random.nextDouble() * 0.45;
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) a) * rr, y + h, z + Mth.sin((float) a) * rr,
                    1, 0.0, 0.002, 0.0, recipe.speed());
        }
    }

    /** 点睛粒子：在身周随机爆散一小簇。 */
    private static void accent(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe) {
        level.sendParticles(recipe.accent(),
                x, y + 0.9, z, 4, 0.5, 0.7, 0.5, 0.08);
    }

    /** 冲击波环：一次性向外爆散（配合 {@code shockRate}）。 */
    private static void shockRing(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe) {
        level.sendParticles(recipe.particle(),
                x, y + 0.15, z, 20, 0.15, 0.02, 0.15, 0.35);
    }

    /**
     * 剑气环绕：多把剑气沿身体旋转，每把都带<b>切向速度</b>，
     * 飞出去时拉出一条剑痕，密集到能看出「剑势」。
     *
     * <p>与 {@link #orbit} 的区别：
     * <ul>
     *   <li>密度高得多（默认 12，orbit 是 3）；</li>
     *   <li>分两层高度（腰、胸），避免所有剑气挤在同一个平面；</li>
     *   <li>速度方向是切向而非零——这是「看得出是剑在飞」的关键，
     *       否则密集的粒子只会糊成一团雾；</li>
     *   <li>半径随相位呼吸，制造「剑阵收缩/扩张」的观感。</li>
     * </ul>
     */
    private static void sword(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        // 每 tick 转 1/30 圈：一圈 1.5 秒，比 orbit（2 秒）稍快，更凌厉
        double baseAngle = (tickCount % 30) * (Mth.TWO_PI / 30.0);
        // 半径呼吸：0.55 ~ 0.80
        double breathe = 0.675 + Mth.sin((float) (tickCount * 0.15)) * 0.125;

        for (int i = 0; i < n; i++) {
            double a = baseAngle + i * (Mth.TWO_PI / n);
            // 两层高度交替：偶数层腰（0.55）、奇数层胸（1.05）
            double h = (i % 2 == 0) ? 0.55 : 1.05;
            double r = breathe * (0.92 + random.nextDouble() * 0.16);
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;

            // 切向速度：沿圆周切线方向，让剑气「扫」过去而不是原地抖动
            double vx = -Mth.sin((float) a) * recipe.speed();
            double vz = Mth.cos((float) a) * recipe.speed();
            // 轻微上扬：剑气飞行时略微抬升，看起来像被甩出去
            double vy = recipe.speed() * 0.35;

            level.sendParticles(recipe.particle(), px, y + h, pz,
                    1, vx, vy, vz, recipe.speed());
        }
    }

    /**
     * 火焰环绕：火苗沿<b>螺旋线</b>从脚底升到头顶，同时绕身体转。
     *
     * <p>与 {@link #column} 的区别：column 是「随机分布在柱体内」，
     * 看起来是一团烟；这里是<b>沿螺旋线均匀分布</b>，每颗火苗都在
     * 圆周上有确定位置，所以能看出「一圈火在绕着你烧」。
     *
     * <p><b>高度必须覆盖全身</b>：玩家高 1.8 格，若螺旋只到 1.25
     * 就只是「脚下一圈火」。这里到 1.75（略低于头顶，避免火苗挡住视线），
     * 并且转 <b>2 圈</b>而不是 1.5 圈，让火线更密。
     *
     * <p>上升速度与旋转速度联动：转得越快，看起来烧得越旺。
     */
    private static void flame(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        int n = recipe.d();
        double baseAngle = (tickCount % 24) * (Mth.TWO_PI / 24.0);

        for (int i = 0; i < n; i++) {
            // 螺旋：角度与高度同时推进，绕两圈、从脚底到 1.75 格
            double t = (double) i / n;
            double a = baseAngle + t * Mth.TWO_PI * 2.0;
            double h = 0.05 + t * 1.70;                 // 0.05 → 1.75 格
            double r = RADIUS * (0.70 + t * 0.40);      // 越高越散开
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;

            // 火苗向上飘 + 轻微切向，模拟被气流带着走
            double vx = -Mth.sin((float) a) * recipe.speed() * 0.4;
            double vz = Mth.cos((float) a) * recipe.speed() * 0.4;

            level.sendParticles(recipe.particle(), px, y + h, pz,
                    1, vx, recipe.speed(), vz, recipe.speed() * 0.6);
        }
    }

    /** 水平圆环：半径随相位轻微呼吸，站在脚底偏上。 */
    private static void ring(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe) {
        double base = y + 0.1;
        for (int i = 0; i < DENSITY; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = RADIUS * (0.85 + random.nextDouble() * 0.3);
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) angle) * r, base, z + Mth.sin((float) angle) * r,
                    1, 0.0, 0.0, 0.0, recipe.speed());
        }
    }

    /** 上行柱：从脚底到胸口，速度向上。 */
    private static void column(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe) {
        for (int i = 0; i < DENSITY; i++) {
            double h = random.nextDouble() * 1.2;
            double ox = (random.nextDouble() - 0.5) * 0.6;
            double oz = (random.nextDouble() - 0.5) * 0.6;
            level.sendParticles(recipe.particle(),
                    x + ox, y + h, z + oz,
                    1, 0.0, recipe.speed(), 0.0, recipe.speed());
        }
    }

    /** 竖直圆环 + 上下浮动：绕身缓慢转一圈。 */
    private static void orbit(ServerLevel level, double x, double y, double z,
            RandomSource random, Recipe recipe, int tickCount) {
        double angle = (tickCount % 40) * (Mth.TWO_PI / 40.0);
        for (int i = 0; i < DENSITY; i++) {
            double a = angle + i * (Mth.TWO_PI / DENSITY);
            double r = RADIUS;
            double h = 0.6 + Mth.sin((float) (a * 2.0)) * 0.35;
            level.sendParticles(recipe.particle(),
                    x + Mth.cos((float) a) * r, y + h, z + Mth.sin((float) a) * r,
                    1, 0.0, recipe.speed() * 0.5, 0.0, recipe.speed());
        }
    }

    /**
     * 体质获取/变更时的一次性「爆发」特效：所有人可见。
     *
     * <p>比持续特效浓得多（一次 60–120 粒子），但只播一次，不占 tick 预算。
     * 用于购买体质成功、十绝体/杀神授予这类时刻。
     */
    public static void burst(ServerLevel level, ServerPlayer player, int tizhi) {
        Recipe recipe = RECIPES.get(tizhi);
        if (recipe == null || level == null) {
            return;
        }
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        // 三层爆发：主粒子浓雾 + END_ROD 上窜 + FLASH 白闪
        level.sendParticles(recipe.particle(), x, y, z, 60, 0.35, 0.8, 0.35, 0.14);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 24, 0.5, 0.4, 0.5, 0.06);
        level.sendParticles(ParticleTypes.FLASH, x, y + 0.2, z, 4, 0.0, 0.0, 0.0, 0.0);
        // 音效同步（音量 1、音高随体质微变，避免每个体质听起来都一样）
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F,
                0.85F + (tizhi % 7) * 0.05F);
    }

    /**
     * 购买词条成功时的一次性反馈特效。
     *
     * <p>能查到专属配方的词条就用自己的颜色炸开（买「神·战神临世」是血红、
     * 买「神·不死不灭」是翠绿），查不到则退回品质档位色。
     * 地/玄/黄/负不播：这些买得频繁，每次都炸一下会非常吵。
     */
    public static void buyBurst(ServerLevel level, ServerPlayer player, String quality) {
        if (level == null) {
            return;
        }
        Aura aura = TIER_AURAS.get(quality);
        // 「地」有常驻光环，但购买时保持静默：地级只要 3 点，买得频繁，每次都炸会很吵。
        if (aura == null || "地".equals(quality)) {
            return;
        }
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        // 品质越高越浓：极 48 / 神 32 / 天 20
        int count = switch (quality) {
            case "极" -> 48;
            case "神" -> 32;
            default -> 20;
        };
        level.sendParticles(aura.main(), x, y, z, count, 0.4, 0.6, 0.4, 0.10);
        level.sendParticles(aura.accent(), x, y + 0.2, z, Math.max(8, count / 3), 0.45, 0.5, 0.45, 0.06);
        if ("极".equals(quality)) {
            level.sendParticles(ParticleTypes.FLASH, x, y + 0.2, z, 2, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F,
                switch (quality) {
                    case "极" -> 1.20F;
                    case "神" -> 1.05F;
                    default -> 0.90F;
                });
    }

    /**
     * 按词条 id 触发购买特效：优先用该词条的专属色。
     *
     * @param id 已购词条 id
     */
    public static void buyBurstFor(ServerLevel level, ServerPlayer player, String id, String quality) {
        if (level == null) {
            return;
        }
        Aura own = AURAS.get(id);
        if (own == null) {
            buyBurst(level, player, quality);
            return;
        }
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        int count = switch (own.tier()) {
            case 0 -> 48;
            case 1 -> 32;
            default -> 20;
        };
        level.sendParticles(own.main(), x, y, z, count, 0.4, 0.6, 0.4, 0.10);
        level.sendParticles(own.accent(), x, y + 0.2, z, Math.max(8, count / 3), 0.45, 0.5, 0.45, 0.06);
        if (own.tier() == 0) {
            level.sendParticles(ParticleTypes.FLASH, x, y + 0.2, z, 2, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F,
                switch (own.tier()) {
                    case 0 -> 1.20F;
                    case 1 -> 1.05F;
                    default -> 0.90F;
                });
    }

    // ------------------------------------------------------------------ 词条专属特效

    /**
     * 词条光环：<b>每条高品质词条有自己专属的颜色与形态</b>，不再是「按品质一个样」。
     *
     * <p>核心手段是 {@link DustParticleOptions} / {@link DustColorTransitionOptions} ——
     * 它们支持任意 RGB 与缩放，而原版固定粒子（END_ROD / ENCHANT 等）没有染色通道。
     * 这才是做出区分度的关键，之前只用固定粒子是自我设限。
     *
     * <p>性能约束（与体质特效同思路）：
     * <ul>
     *   <li><b>只播最强的一条</b>：多条词条同时生效时按「档位」取最高者，
     *       否则买满 30 条 = 每 tick 几十次广播；</li>
     *   <li><b>相位错峰</b>：每条配方有自己的相位，同帧只播一条；</li>
     *   <li><b>每 tick 最多 2 次 sendParticles</b>：主环 + 点睛粒子。</li>
     * </ul>
     */
    private record Aura(int tier, DustColorTransitionOptions main, DustColorTransitionOptions accent,
                        Shape shape, double radius, int density) {
    }

    /** 已购词条 id → 专属光环。查不到则回退到按品质的档位光环。 */
    private static final Map<String, Aura> AURAS = new HashMap<>();

    /** 品质档位回退光环（地/玄/黄/负 无光环）。 */
    private static final Map<String, Aura> TIER_AURAS = new HashMap<>();

    /** 词条光环错峰周期：四档各占 1 个槽（极 0 / 神 1 / 天 2 / 地 3），每 8 tick 各播一次。 */
    private static final int AURA_PERIOD = 8;

    /** 造一个双色渐变尘粒子选项（from→to 随时间过渡，scale 控制大小）。 */
    private static DustColorTransitionOptions dust(int r1, int g1, int b1,
            int r2, int g2, int b2, float scale) {
        return new DustColorTransitionOptions(
                new Vector3f(r1 / 255.0F, g1 / 255.0F, b1 / 255.0F),
                new Vector3f(r2 / 255.0F, g2 / 255.0F, b2 / 255.0F),
                scale);
    }

    static {
        // ---- 极（tier 0）：金红双色，最大最密 ----
        AURAS.put("ji_zhongdao_zhizun", new Aura(0,
                dust(255, 215, 90, 255, 140, 40, 1.5F),
                dust(255, 245, 190, 255, 200, 90, 0.9F),
                Shape.ORBIT, 0.85, 4));
        AURAS.put("ji_wanshi_bumie", new Aura(0,
                dust(255, 230, 120, 200, 60, 60, 1.5F),
                dust(255, 250, 210, 255, 160, 120, 0.9F),
                Shape.RING, 0.85, 4));

        // ---- 神（tier 1）：各自专属色 ----
        AURAS.put("shen_qiyun_zhizi", new Aura(1,                        // 气运之子：紫金
                dust(190, 120, 255, 255, 215, 90, 1.3F),
                dust(230, 190, 255, 255, 240, 170, 0.8F),
                Shape.ORBIT, 0.75, 3));
        AURAS.put("shen_hunxi_zhuzai", new Aura(1,                       // 魂兮主宰：幽蓝
                dust(90, 170, 255, 40, 90, 200, 1.3F),
                dust(160, 220, 255, 90, 170, 255, 0.8F),
                Shape.COLUMN, 0.55, 3));
        AURAS.put("shen_busi_bumie", new Aura(1,                         // 不死不灭：翠绿生机
                dust(90, 235, 150, 40, 170, 100, 1.3F),
                dust(180, 255, 210, 120, 240, 170, 0.8F),
                Shape.RING, 0.75, 3));
        AURAS.put("shen_zhanshen_linshi", new Aura(1,                    // 战神临世：血红
                dust(255, 70, 60, 160, 20, 20, 1.4F),
                dust(255, 150, 130, 220, 60, 50, 0.9F),
                Shape.ORBIT, 0.80, 4));
        AURAS.put("shen_jingang_buhuai", new Aura(1,                     // 金刚不坏：钢青
                dust(180, 210, 230, 110, 140, 170, 1.3F),
                dust(230, 245, 255, 170, 200, 225, 0.8F),
                Shape.RING, 0.80, 3));
        AURAS.put("shen_xiulian_wuji", new Aura(1,                       // 修炼无极：青碧灵气
                dust(120, 255, 235, 60, 190, 200, 1.3F),
                dust(200, 255, 245, 140, 235, 225, 0.8F),
                Shape.COLUMN, 0.55, 3));
        AURAS.put("shen_liangu_tianshu", new Aura(1,                     // 炼蛊天术：蛊紫
                dust(200, 90, 235, 120, 40, 170, 1.3F),
                dust(240, 170, 255, 190, 110, 235, 0.8F),
                Shape.ORBIT, 0.70, 3));
        AURAS.put("shen_wandao_guizong", new Aura(1,                     // 万道归宗：七彩流转
                dust(255, 120, 120, 120, 200, 255, 1.4F),
                dust(255, 220, 130, 180, 140, 255, 0.9F),
                Shape.RING, 0.90, 4));
        AURAS.put("shen_liupai_jianji", new Aura(1,                      // 流派兼济：冰蓝转魂紫
                dust(120, 220, 255, 190, 130, 255, 1.3F),
                dust(200, 240, 255, 230, 190, 255, 0.8F),
                Shape.ORBIT, 0.78, 3));

        // ---- 天（tier 2）：专属色 ----
        AURAS.put("bingxue_daoti", new Aura(2,                           // 冰雪道体：霜白冰蓝
                dust(200, 240, 255, 130, 190, 240, 1.1F),
                dust(235, 250, 255, 180, 220, 250, 0.7F),
                Shape.COLUMN, 0.50, 2));
        AURAS.put("hundao_ronghe", new Aura(2,                           // 魂道融彻：幽蓝
                dust(110, 160, 255, 60, 90, 180, 1.1F),
                dust(180, 210, 255, 120, 160, 235, 0.7F),
                Shape.ORBIT, 0.62, 2));
        AURAS.put("qiyun_yizhi", new Aura(2,                             // 一掷乾坤：金
                dust(255, 210, 90, 220, 160, 40, 1.1F),
                dust(255, 240, 170, 240, 200, 110, 0.7F),
                Shape.RING, 0.70, 2));
        AURAS.put("jixing_ruying", new Aura(2,                           // 疾行如影：青白拖影
                dust(150, 255, 220, 80, 200, 190, 1.0F),
                dust(210, 255, 240, 150, 235, 215, 0.6F),
                Shape.RING, 0.65, 2));

        // ---- 天（tier 2）其余条目：按词条气质配色，保证每条天级都有专属色 ----
        AURAS.put("wuxing_daocang", new Aura(2,                          // 五行道藏：五色流转
                dust(255, 170, 120, 130, 200, 255, 1.1F),
                dust(200, 255, 190, 220, 190, 255, 0.7F),
                Shape.RING, 0.72, 2));
        AURAS.put("qiqiao_linglong", new Aura(2,                         // 七窍玲珑：玲珑金
                dust(255, 225, 140, 220, 180, 80, 1.0F),
                dust(255, 245, 200, 240, 215, 140, 0.6F),
                Shape.ORBIT, 0.58, 2));
        AURAS.put("hunpo_rugui", new Aura(2,                             // 魂魄如鬼：鬼火青
                dust(120, 230, 210, 60, 160, 170, 1.0F),
                dust(180, 250, 235, 110, 200, 200, 0.6F),
                Shape.COLUMN, 0.48, 2));
        AURAS.put("jingli_ruhai", new Aura(2,                            // 精力如海：海蓝
                dust(90, 180, 255, 50, 110, 200, 1.0F),
                dust(160, 220, 255, 100, 160, 235, 0.6F),
                Shape.COLUMN, 0.52, 2));
        AURAS.put("shouyuan_ruhe", new Aura(2,                           // 寿元如鹤：鹤白青
                dust(235, 250, 240, 180, 220, 210, 1.0F),
                dust(255, 255, 250, 220, 240, 235, 0.6F),
                Shape.ORBIT, 0.60, 2));
        AURAS.put("qiyun_ruhong", new Aura(2,                            // 气运如虹：虹彩
                dust(255, 160, 200, 160, 220, 255, 1.0F),
                dust(255, 220, 180, 200, 240, 255, 0.6F),
                Shape.RING, 0.68, 2));
        AURAS.put("zhenyuan_bujie", new Aura(2,                          // 真元不竭：真元青白
                dust(180, 240, 255, 120, 190, 230, 1.0F),
                dust(230, 250, 255, 180, 225, 245, 0.6F),
                Shape.COLUMN, 0.50, 2));
        AURAS.put("niantou_bujie", new Aura(2,                           // 念头不竭：念头金
                dust(255, 235, 160, 230, 200, 100, 1.0F),
                dust(255, 250, 210, 245, 225, 160, 0.6F),
                Shape.COLUMN, 0.50, 2));
        AURAS.put("huixue_rusheng", new Aura(2,                          // 回血如生：生机绿
                dust(130, 245, 160, 70, 190, 110, 1.0F),
                dust(200, 255, 215, 140, 235, 170, 0.6F),
                Shape.RING, 0.66, 2));
        AURAS.put("baoji_rushen", new Aura(2,                            // 暴击如神：橙红
                dust(255, 150, 70, 220, 90, 40, 1.1F),
                dust(255, 210, 150, 245, 150, 100, 0.7F),
                Shape.ORBIT, 0.70, 2));
        AURAS.put("xixue_rushi", new Aura(2,                             // 吸血如饕：暗红
                dust(220, 60, 80, 130, 20, 40, 1.1F),
                dust(255, 130, 150, 190, 60, 80, 0.7F),
                Shape.RING, 0.64, 2));
        AURAS.put("fanshang_rushan", new Aura(2,                         // 反伤如山：岩石棕
                dust(200, 170, 120, 140, 110, 70, 1.1F),
                dust(230, 205, 165, 180, 150, 110, 0.7F),
                Shape.RING, 0.68, 2));
        AURAS.put("jianjia_rushen", new Aura(2,                          // 坚甲如神：钢灰
                dust(190, 200, 210, 120, 135, 150, 1.1F),
                dust(225, 235, 245, 170, 185, 200, 0.7F),
                Shape.RING, 0.66, 2));
        AURAS.put("xiulian_rushen", new Aura(2,                          // 修炼如神：灵青
                dust(140, 230, 255, 80, 170, 220, 1.0F),
                dust(200, 245, 255, 130, 205, 240, 0.6F),
                Shape.COLUMN, 0.52, 2));
        AURAS.put("liangu_bujie", new Aura(2,                            // 炼蛊不竭：蛊紫
                dust(210, 110, 240, 140, 60, 190, 1.0F),
                dust(240, 180, 255, 190, 120, 235, 0.6F),
                Shape.ORBIT, 0.60, 2));

        // ---- 品质回退档（未列专属的天/神/极词条用这些）----
        TIER_AURAS.put("极", new Aura(0,
                dust(255, 215, 90, 255, 140, 40, 1.5F),
                dust(255, 245, 190, 255, 200, 90, 0.9F),
                Shape.ORBIT, 0.85, 4));
        TIER_AURAS.put("神", new Aura(1,
                dust(200, 140, 255, 130, 80, 220, 1.3F),
                dust(235, 200, 255, 180, 130, 240, 0.8F),
                Shape.RING, 0.78, 3));
        TIER_AURAS.put("天", new Aura(2,
                dust(150, 220, 255, 90, 170, 230, 1.1F),
                dust(200, 240, 255, 140, 200, 245, 0.7F),
                Shape.COLUMN, 0.55, 2));
        // 地：低强度淡金，密度 1、半径小 —— 有存在感但不抢天/神的风头
        TIER_AURAS.put("地", new Aura(3,
                dust(220, 200, 140, 170, 150, 100, 0.9F),
                dust(240, 230, 180, 200, 185, 130, 0.5F),
                Shape.RING, 0.50, 1));
    }

    /** 品质 → 档位（地/玄/黄/负 无光环，返回 MAX）。 */
    private static int tierOf(String quality) {
        return switch (quality) {
            case "极" -> 0;
            case "神" -> 1;
            case "天" -> 2;
            case "地" -> 3;
            default -> Integer.MAX_VALUE;
        };
    }

    /**
     * 每 tick 调用：取已生效词条里最强的一条，播它的专属光环。
     *
     * <p>「最强」= 档位数值最小（极 > 神 > 天）；同档内按 id 字典序稳定选一条，
     * 保证不会每 tick 闪烁切换。
     */
    public static void playAura(ServerLevel level, ServerPlayer player, Set<String> ids, int tickCount) {
        if (level == null || ids.isEmpty()) {
            return;
        }
        int phase = Math.floorMod(tickCount, AURA_PERIOD);
        // 相位预筛：四档各占一个槽（极=0 / 神=1 / 天=2 / 地=3），其余 tick 直接返回。
        // 这样绝大多数 tick 不做任何遍历 —— 买满 100 条也不会每 tick 100 次 map 查找。
        if (phase > 3) {
            return;
        }

        // 收集「最高档」的全部词条 id，按 tick 轮播 —— 只显示一条会让买了多条神级
        // 的玩家以为其余没生效；轮播则每条都能轮到，且每 tick 仍然只播 1 次。
        java.util.List<String> topIds = new java.util.ArrayList<>(4);
        int bestTier = Integer.MAX_VALUE;
        for (String id : ids) {
            int tier;
            if (AURAS.containsKey(id)) {
                tier = AURAS.get(id).tier();
            } else {
                TianfuPool.Spec spec = TianfuPool.byId(id);
                if (spec == null) {
                    continue;
                }
                tier = tierOf(spec.quality());
            }
            if (tier < bestTier) {
                bestTier = tier;
                topIds.clear();
                topIds.add(id);
            } else if (tier == bestTier) {
                topIds.add(id);
            }
        }
        if (topIds.isEmpty() || bestTier == Integer.MAX_VALUE) {
            return;
        }
        // 轮播：同档多条时按「周期序号」轮流播（用 tickCount / PERIOD 做索引，天然递增）
        int slot = Math.floorMod(tickCount / AURA_PERIOD, topIds.size());
        String bestId = topIds.get(slot);
        Aura aura = AURAS.get(bestId);
        if (aura == null) {
            TianfuPool.Spec spec = TianfuPool.byId(bestId);
            aura = spec == null ? null : TIER_AURAS.get(spec.quality());
        }
        if (aura == null) {
            return;
        }
        // 每档固定占一个相位槽：极在 phase 0、神在 1、天在 2。
        // 这样「有极档的人」每 8 tick 播一次，「只有天档的人」也每 8 tick 播一次，
        // 且同一 tick 全服最多只有一档在播（错峰）。
        if (phase != aura.tier()) {
            return;
        }

        emitAura(level, player, aura, tickCount);
    }

    /** 真正发射：主环/柱/轨 + 一个点睛粒子。 */
    private static void emitAura(ServerLevel level, ServerPlayer player, Aura aura, int tickCount) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        RandomSource random = player.getRandom();
        double radius = aura.radius();
        int density = aura.density();
        // 缓慢自转：周期 60 tick 转一圈
        double spin = (tickCount % 60) * (Mth.TWO_PI / 60.0);

        switch (aura.shape()) {
            case RING -> {
                // 水平双环（内外两圈反向转），只发 1 次 sendParticles，count = density
                level.sendParticles(aura.main(), x, y + 0.9, z, density,
                        radius * 0.55, 0.02, radius * 0.55, 0.0);
                // 点睛：顶点处一颗高亮
                level.sendParticles(aura.accent(),
                        x + Mth.cos((float) spin) * radius,
                        y + 1.15,
                        z + Mth.sin((float) spin) * radius,
                        1, 0.0, 0.01, 0.0, 0.0);
            }
            case COLUMN -> {
                // 螺旋上升：沿高度均匀撒，配合 count 扩散形成柱
                level.sendParticles(aura.main(), x, y + 0.7, z, density,
                        radius * 0.35, 0.75, radius * 0.35, 0.012);
                level.sendParticles(aura.accent(),
                        x + Mth.cos((float) (spin * 2)) * radius * 0.4,
                        y + 1.7,
                        z + Mth.sin((float) (spin * 2)) * radius * 0.4,
                        1, 0.0, 0.02, 0.0, 0.0);
            }
            case ORBIT -> {
                // 环绕轨：绕身一圈（用 count 沿圆周均布，靠 speed 让其自然飘散）
                level.sendParticles(aura.main(), x, y + 0.95, z, density,
                        radius * 0.6, 0.28, radius * 0.6, 0.0);
                level.sendParticles(aura.accent(),
                        x + Mth.cos((float) (spin + Mth.PI)) * radius,
                        y + 0.95 + Mth.sin((float) (spin * 1.5)) * 0.22,
                        z + Mth.sin((float) (spin + Mth.PI)) * radius,
                        1, 0.0, 0.01, 0.0, 0.0);
            }
            default -> {
            }
        }
        // 极档额外加一层「光晕脉冲」：每 20 tick 一次，便宜但有存在感
        if (aura.tier() == 0) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 3, 0.3, 0.4, 0.3, 0.02);
        }
    }
}
