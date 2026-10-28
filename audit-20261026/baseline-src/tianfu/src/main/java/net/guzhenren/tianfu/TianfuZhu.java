package net.guzhenren.tianfu;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「X烛」——19 个凡体各自的流派侵蚀效果。
 *
 * <h2>这是什么</h2>
 * 本体有一个「宙侵」（{@code guzhenren:zhoudaobuff}）：命中时挂到目标身上，
 * <b>再次命中时按已侵蚀时长结算一次伤害</b>，是宙道爆发的核心。
 * 原先的 TianfuZhoushi 把<b>所有</b>带流派道痕的体质都挂同一个宙侵，
 * 结果无论你是剑修还是炎修，打出来都是「宙道」的伤害——流派没有区别。
 *
 * <p>本类把它改成<b>每个凡体挂自己的烛</b>：剑道体质挂「剑烛」、炎道挂「炎烛」……
 * 机制完全照抄宙侵（时长 200 tick、按已侵蚀时长 × 0.1 结算），
 * 只有两处不同：
 * <ul>
 *   <li>伤害基数用<b>各自的</b>流派道痕（{@code daohen_jiandao} / {@code daohen_yandao}…）；</li>
 *   <li>结算时传给 {@code ShangHaiLiuCheng1Procedure} 的流派标记也是各自的
 *       （{@code "JianDao"} / {@code "YanDao"}…），从而吃各自的流派增幅 buff。</li>
 * </ul>
 *
 * <h2>为什么用「烛」这个字</h2>
 * 沿用本体已有的命名意象（{@code 瞬现烛蛊}、{@code 火光烛天蛊}）：
 * 烛是「点燃后持续燃烧」的东西，正好对应「挂上后按时间结算」的侵蚀机制。
 * 宙道那个叫「宙烛」——它就是这个模式的原始样本。
 *
 * <h2>为什么 19 个而不是 20 个</h2>
 * 御空踏云体（{@code tizhi=45}）只写 {@code liupai_feixingdao}，
 * 本体<b>没有</b> {@code daohen_feixingdao} 字段，没有伤害基数，做不出「烛」。
 *
 * <p>修罗战体与血煞蜕变体都走血道，共用「血烛」一个效果。
 */
public final class TianfuZhu {

    /**
     * 一支烛。
     *
     * @param id        效果注册名（{@code tianfu_zhu_<流派>}）
     * @param name      中文名（「剑烛」）
     * @param liupai    传给 {@code ShangHaiLiuCheng1Procedure} 的流派标记（{@code "JianDao"}）
     * @param daohenKey 伤害基数所在的 {@code PlayerVariables} 字段名
     * @param color     效果粒子/图标颜色（ARGB）
     * @param bodyTizhi 该烛对应的体质 {@code tizhi} 值
     */
    public record Candle(String id, String name, String liupai, String daohenKey,
                         int color, int bodyTizhi) {
    }

    /** 全部 19 支烛，键为体质 {@code tizhi}。 */
    private static final Map<Integer, Candle> BY_TIZHI = new LinkedHashMap<>();

    /** 按效果 id 反查。 */
    private static final Map<String, Candle> BY_ID = new LinkedHashMap<>();

    private static Candle c(String id, String name, String liupai, String daohenKey,
                            int color, int tizhi) {
        Candle k = new Candle(id, name, liupai, daohenKey, color, tizhi);
        BY_TIZHI.put(tizhi, k);
        BY_ID.put(id, k);
        return k;
    }

    // ------------------------------------------------------------------ 19 支烛
    //
    // 颜色按流派意象取：剑=霜白、炎=赤红、雷=亮紫、木=青绿、水=湛蓝…

    /** 17 九命不灭体 → 土烛（土黄）。 */
    public static final Candle TU = c("tianfu_zhu_tu", "土烛", "TuDao",
            "daohen_tudao", 0xFFC9A227, 17);
    /** 18 百战金戈体 → 兵烛（铁灰）。 */
    public static final Candle BING = c("tianfu_zhu_bing", "兵烛", "BingDao",
            "daohen_bingdao", 0xFF9AA5B1, 18);
    /** 19 寒渊素雪体 → 冰雪烛（冰蓝）。 */
    public static final Candle BINGXUE = c("tianfu_zhu_bingxue", "冰雪烛", "BingXueDao",
            "daohen_bingxuedao", 0xFF9FE0F0, 19);
    /** 21 长风破浪体 → 风烛（淡青）。 */
    public static final Candle FENG = c("tianfu_zhu_feng", "风烛", "FengDao",
            "daohen_fengdao", 0xFFA8D8C0, 21);
    /** 22 白玉铁骨体 → 骨烛（骨白）。 */
    public static final Candle GU = c("tianfu_zhu_gu", "骨烛", "GuDao",
            "daohen_gudao", 0xFFE8E0D0, 22);
    /** 24 幽魂凝识体 → 魂烛（幽蓝）。 */
    public static final Candle HUN = c("tianfu_zhu_hun", "魂烛", "HunDao",
            "daohen_hundao", 0xFF6F8FD8, 24);
    /** 25 断岳剑心体 → 剑烛（霜白）。 */
    public static final Candle JIAN = c("tianfu_zhu_jian", "剑烛", "JianDao",
            "daohen_jiandao", 0xFFD8E8F0, 25);
    /** 26 百炼庚金体 → 金烛（金黄）。 */
    public static final Candle JIN = c("tianfu_zhu_jin", "金烛", "JinDao",
            "daohen_jindao", 0xFFF0C848, 26);
    /** 28 搬山撼岳体 → 力烛（土褐）。 */
    public static final Candle LI = c("tianfu_zhu_li", "力烛", "LiDao",
            "daohen_lidao", 0xFFB08050, 28);
    /** 30 苍木回春体 → 木烛（青绿）。 */
    public static final Candle MU = c("tianfu_zhu_mu", "木烛", "MuDao",
            "daohen_mudao", 0xFF6FBF6F, 30);
    /** 31 太息养元体 → 气烛（浅金）。 */
    public static final Candle QI = c("tianfu_zhu_qi", "气烛", "QiDao",
            "dahen_qidao", 0xFFE0D8A0, 31);
    /** 33 修罗战体 + 53 血煞蜕变体 → 血烛（暗红）。 */
    public static final Candle XUE = c("tianfu_zhu_xue", "血烛", "XueDao",
            "daohen_xuedao", 0xFFC03030, 33);
    /** 37 太虚化形体 → 虚烛（淡紫）。 */
    public static final Candle XU = c("tianfu_zhu_xu", "虚烛", "XuDao",
            "daohen_xudao", 0xFFB8A0D8, 37);
    /** 39 如影随形体 → 影烛（深灰紫）。 */
    public static final Candle YING = c("tianfu_zhu_ying", "影烛", "YingDao",
            "daohen_yingdao", 0xFF7A7090, 39);
    /** 40 明月入怀体 → 月烛（月白）。 */
    public static final Candle YUE = c("tianfu_zhu_yue", "月烛", "YueDao",
            "daohen_yuedao", 0xFFE8E8F8, 40);
    /** 42 慧心通明体 → 智烛（青蓝）。 */
    public static final Candle ZHI = c("tianfu_zhu_zhi", "智烛", "ZhiDao",
            "dahen_zhidao", 0xFF70C0D8, 42);
    /** 43 光阴逆转体 → 宙烛（本体宙侵的原始样本）。 */
    public static final Candle ZHOU = c("tianfu_zhu_zhou", "宙烛", "ZhouDao",
            "dahen_zhoudao", 0xFFC8A44E, 43);
    /** 48 南柯一梦体 → 梦烛（雾紫）。 */
    public static final Candle MENG = c("tianfu_zhu_meng", "梦烛", "MengDao",
            "daohen_mengdao", 0xFFC0A0E0, 48);

    /** 血烛同时服务 53 血煞蜕变体（与 33 修罗战体共用）。 */
    static {
        BY_TIZHI.put(53, XUE);
    }

    private TianfuZhu() {
    }

    /**
     * 体质 → 它的烛；没有对应烛的体质返回 {@code null}。
     *
     * <p>45 御空踏云体、54 杀神、十绝体 1–10 都返回 {@code null}
     * （前两个没有伤害基数，十绝体本次不参与）。
     */
    public static Candle of(int tizhi) {
        return BY_TIZHI.get(tizhi);
    }

    public static Candle byId(String id) {
        return BY_ID.get(id);
    }

    public static Map<Integer, Candle> all() {
        return BY_TIZHI;
    }

    /** 唯一烛的数量（血烛只算一次）。 */
    public static int count() {
        return BY_ID.size();
    }

    /** 效果分类：中性（与宙侵一致，可安全挂在玩家或怪物身上）。 */
    public static MobEffectCategory category() {
        return MobEffectCategory.NEUTRAL;
    }

    /**
     * 基准时长（tick），与宙侵的 200 一致。
     *
     * <p>10 秒。配合「再次命中按已侵蚀时长 × 0.1 结算」，
     * 满时长重击约等于 20 次道痕单位的伤害。
     */
    public static final int BASE_DURATION = 200;

    /** 已侵蚀时长折算系数（宙侵是 ×0.1）。 */
    public static final double ELAPSED_SCALE = 0.1;
}
