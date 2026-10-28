package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蛊兽名录：把本体真实存在的蛊兽按<b>流派 × 规模</b>编目。
 *
 * <h2>依据</h2>
 * 名录来自整合包知识库第 6.2 节「蛊兽名录（流派 × 规模）」的四列表格
 * （百兽 / 千兽 / 万兽 / 兽皇），外加同节的「特殊种（无规模分级）」。
 * 每个实体的注册 id 都由 {@code _pack/audit_bestiary.py} 拿本体 jar 的
 * {@code assets/guzhenren/lang/en_us.json} 逐条核对：<b>id 必须存在，名字必须对得上</b>。
 *
 * <h2>阶数怎么定</h2>
 * 本体<b>没有</b>妖兽等级字段（全库扫 {@code yaoshou/beast/dengji} 零命中），
 * 所以阶数只能由名录给：
 * <pre>
 *   1 阶 = 特殊种（无规模分级：魂兽、木魅树精、豹、虎、鹿、电狼…）
 *   2 阶 = 百兽
 *   3 阶 = 千兽
 *   4 阶 = 万兽
 *   5 阶 = 兽皇
 * </pre>
 * 这张表与旧实现（只看「魂兽／木魅树精」两族）兼容：两族的 5 只仍在原阶位。
 *
 * <h2>为什么不用血量</h2>
 * 本体 {@code MAX_HEALTH} 完全不能代表强弱（魂兽与魂兽·万兽王同为 1000 血），
 * 知识库给的「不过万 / 不过十万 / 不过百万」是另一套口径，与本体的血量数值对不上，
 * 硬套会把兽皇判成杂兵。所以这里只认名录。
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class BeastCatalog {
    /** 流派（道）。{@link #NONE} 表示特殊种/未归类。 */
    public enum Dao {
        LV("律道"), LEI("雷道"), YAN("炎道"), JIN("金道"), SHUI("水道"), TU("土道"),
        FENG("风道"), YU("宇道"), ZHOU("宙道"), QI("气道"), LIAN("炼道"), ZHI("智道"),
        HUN("魂道"), XUE("血道"), MU("木道"), BING("冰雪道"), TOU("偷道"), NONE("无");

        public final String cn;

        Dao(String cn) {
            this.cn = cn;
        }
    }

    /** 一条名录项。 */
    public record Entry(int tier, Dao dao, String id, String name) {
    }

    /** 最大阶。 */
    public static final int MAX_TIER = 5;

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final List<Entry> ALL = new ArrayList<>();

    private static void add(int tier, Dao dao, String path, String cn) {
        Entry e = new Entry(tier, dao, "guzhenren:" + path, cn);
        BY_ID.put(e.id(), e);
        ALL.add(e);
    }

    static {
        // ---- 律道 ----
        add(2, Dao.LV, "gangjiagui", "刚甲龟");
        add(3, Dao.LV, "shangjianxuangui", "伤坚玄龟");
        add(4, Dao.LV, "huishangao", "毁伤鳌");
        add(5, Dao.LV, "bumiexuanwu", "不灭玄武");
        // ---- 雷道 ----
        add(2, Dao.LEI, "hao_dian_lang", "豪电狼");
        add(3, Dao.LEI, "lei_dian_lang", "狂电狼");
        add(4, Dao.LEI, "lei_guan_tou_lang", "雷冠头狼");
        add(5, Dao.LEI, "fengleidianlanghuang", "风雷电狼皇");
        // ---- 炎道 ----
        add(2, Dao.YAN, "huoyanxiong", "火焰熊");
        add(3, Dao.YAN, "lieyanxiong", "烈焰熊");
        add(4, Dao.YAN, "liaoyuanhuoxiong", "燎原火熊");
        add(5, Dao.YAN, "jinhuomoyanxiong", "烬火魔焰熊");
        // ---- 金道 ----
        add(2, Dao.JIN, "hu", "虎");
        add(3, Dao.JIN, "jinfenghu", "金锋虎");
        add(4, Dao.JIN, "jinrenwanghu", "金刃王虎");
        add(5, Dao.JIN, "xiongshabaihu", "凶煞白虎");
        // ---- 水道 ----
        add(2, Dao.SHUI, "bishuiling", "碧水羚");
        add(3, Dao.SHUI, "yuanyuelin", "渊跃羚");
        add(4, Dao.SHUI, "cangyuelingjun", "苍月羚君");
        add(5, Dao.SHUI, "hhongtaofuhailing", "洪涛覆海羚");
        // ---- 土道 ----
        add(2, Dao.TU, "yan_xu_quan", "岩戌犬");
        add(3, Dao.TU, "yao_ya_quan", "垚犽犬");
        add(4, Dao.TU, "yue_ao_yan_quan", "岳獒岩犬");
        add(5, Dao.TU, "shan_mai_di_yan_quan", "山脉地岩犬");
        // ---- 风道 ----
        add(2, Dao.FENG, "feng_huo_ying", "风火鹰");
        add(3, Dao.FENG, "lie_gang_feng_ying", "雷罡风鹰");
        add(4, Dao.FENG, "yao_guang_feng_sun", "曜光风隼");
        add(5, Dao.FENG, "ji_feng_lve_yun_diao", "疾风掠云雕");
        // ---- 宇道 ----
        add(2, Dao.YU, "xunjiejinbao", "巡界金豹");
        add(3, Dao.YU, "chuanyuqingbao", "穿宇青豹");
        add(4, Dao.YU, "zixiaojiebao", "紫霄界豹");
        add(5, Dao.YU, "ziyaojiexingbao", "紫曜界行豹");
        // ---- 宙道 ----
        add(2, Dao.ZHOU, "ke_wen_she", "刻纹蛇");
        add(3, Dao.ZHOU, "su_hui_she", "溯洄蛇");
        add(4, Dao.ZHOU, "zhou_shi_ling_ran", "宙蚀灵蚺");
        add(5, Dao.ZHOU, "shi_kong_zhen_yu_mang", "时空镇狱蟒");
        // ---- 气道 ----
        add(2, Dao.QI, "fengyun_he", "风云鹤");
        add(3, Dao.QI, "lingbohe", "凌波鹤");
        add(4, Dao.QI, "xueyingxionghe", "血影凶鹤");
        add(5, Dao.QI, "yinyangshengsihe", "阴阳生死鹤");
        // ---- 炼道 ----
        add(2, Dao.LIAN, "rongwenlu", "熔纹鹿");
        add(3, Dao.LIAN, "lianchenlu", "炼尘鹿");
        add(4, Dao.LIAN, "liandingjielu", "炼鼎劫鹿");
        add(5, Dao.LIAN, "qianlianguichenlu", "千炼归尘鹿");
        // ---- 智道 ----
        add(2, Dao.ZHI, "huli_1", "金思狐");
        add(3, Dao.ZHI, "huli_3", "妄谋狐");
        add(4, Dao.ZHI, "huli_4", "绮云幻狐");
        add(5, Dao.ZHI, "huli_5", "胭霞万象狐");
        // ---- 魂道 ----
        add(2, Dao.HUN, "hunshou_2", "魂兽·百兽王");
        add(3, Dao.HUN, "hunshou_3", "魂兽·千兽王");
        add(4, Dao.HUN, "hunshou_4", "魂兽·万兽王");
        add(5, Dao.HUN, "hunshou", "魂兽·兽皇");
        // ---- 血道 ----
        add(2, Dao.XUE, "xuxuyifu", "血翼蝠");
        add(3, Dao.XUE, "daochixuefu", "刀翅血蝠");
        add(4, Dao.XUE, "xueyuxingfu", "血雨腥蝠");
        add(5, Dao.XUE, "lixuemofu", "沥血魔蝠");
        // ---- 木道 ----
        add(2, Dao.MU, "mumeishujing_2", "木魅树精-百兽王");
        add(3, Dao.MU, "mumeishujing_3", "木魅树精-千兽王");
        add(4, Dao.MU, "mumeishujing_4", "木魅树精-万兽王");
        add(5, Dao.MU, "mumeishujing_5", "木魅树精-兽皇");
        // ---- 冰雪道（名录里没有兽皇一档） ----
        add(2, Dao.BING, "bing_lin_sha", "冰鳞鲨");
        add(3, Dao.BING, "xuan_shuang_bing_sha", "玄霜鲛鲨");
        add(4, Dao.BING, "han_yuan_bing_sha", "寒渊冰鲨");
        // ---- 偷道 ----
        add(2, Dao.TOU, "anduodiao", "暗夺雕");
        add(3, Dao.TOU, "lueshiying", "掠食鹰");
        add(4, Dao.TOU, "qiebaoxuanying", "窃宝玄枭");
        add(5, Dao.TOU, "shentouguiduoxiao", "神偷诡夺枭");

        // ---- 特殊种（无规模分级 → 1 阶） ----
        add(1, Dao.NONE, "hunshou_1", "魂兽");
        add(1, Dao.MU, "mumeishujing_1", "木魅树精");
        add(1, Dao.JIN, "biao", "彪");
        add(1, Dao.JIN, "huli_0", "赤狐");
        add(1, Dao.LEI, "dian_lang", "电狼");
        add(1, Dao.LV, "yingkegui", "硬壳龟");
        add(1, Dao.YAN, "hong_xiong", "红熊");
        add(1, Dao.XUE, "xuefu", "血蝠");
        add(1, Dao.ZHOU, "shi_ying_she", "时影蛇");
        add(1, Dao.QI, "qiliu_he", "气流鹤");
        add(1, Dao.YU, "bao", "豹");
        add(1, Dao.LIAN, "lu", "鹿");
        add(1, Dao.BING, "bing_po_long_wen_sha", "冰魄龙纹鲨");
        add(1, Dao.BING, "bing_jiao_sha", "冰鲛鲨");
        add(1, Dao.TU, "xing_jun_yi", "行军蚁");
        add(1, Dao.TU, "jiang_jun_yi", "将军蚁");
        add(1, Dao.TU, "gong_bing_yi", "工兵蚁");
        add(1, Dao.TU, "wang_yi", "王蚁");
        // ---- 名录未列、但本体确实存在的常见野外围兽 ----
        // 知识库 6.2 的特殊种只点了部分，这四只（灰狼/土狼/熊/灰熊）在整合包里是
        // 最常见的野外兽，奇遇系统原本就依赖它们，所以按 1 阶收录，道归「无」。
        add(1, Dao.NONE, "hui_lang", "灰狼");
        add(1, Dao.NONE, "tu_lang", "土狼");
        add(1, Dao.NONE, "xiong", "熊");
        add(1, Dao.NONE, "hui_xiong", "灰熊");
    }

    private BeastCatalog() {
    }

    /** 全部名录项。 */
    public static List<Entry> all() {
        return new ArrayList<>(ALL);
    }

    /** 名录规模。 */
    public static int size() {
        return ALL.size();
    }

    /** 某个注册 id 的名录项（不在名录里返回 {@code null}）。 */
    public static Entry get(String id) {
        return BY_ID.get(id);
    }

    /**
     * 阶数。
     *
     * @return 1–5；不在名录里返回 0（调用方可自行决定当几阶）
     */
    public static int tierOf(String id) {
        Entry e = BY_ID.get(id);
        return e == null ? 0 : e.tier();
    }

    /** 流派（不在名录里返回 {@link Dao#NONE}）。 */
    public static Dao daoOf(String id) {
        Entry e = BY_ID.get(id);
        return e == null ? Dao.NONE : e.dao();
    }

    /** 中文名（不在名录里返回空串）。 */
    public static String nameOf(String id) {
        Entry e = BY_ID.get(id);
        return e == null ? "" : e.name();
    }

    /** 某一阶的全部实体。 */
    public static List<Entry> byTier(int tier) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.tier() == tier) {
                out.add(e);
            }
        }
        return out;
    }

    /** 某一流派的全部实体。 */
    public static List<Entry> byDao(Dao dao) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.dao() == dao) {
                out.add(e);
            }
        }
        return out;
    }

    /** 某一阶 + 某一流派的实体（可能为空）。 */
    public static List<Entry> byTierAndDao(int tier, Dao dao) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.tier() == tier && e.dao() == dao) {
                out.add(e);
            }
        }
        return out;
    }

    /** 阶名。 */
    public static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "普通蛊兽";
            case 2 -> "百兽";
            case 3 -> "千兽";
            case 4 -> "万兽";
            case 5 -> "兽皇";
            default -> "蛊兽";
        };
    }

    /** 摘要（日志/命令用）。 */
    public static String summary() {
        StringBuilder sb = new StringBuilder("蛊兽名录{");
        sb.append(ALL.size()).append(" 只 / ");
        for (int t = 1; t <= MAX_TIER; t++) {
            sb.append(tierName(t)).append('=').append(byTier(t).size());
            if (t < MAX_TIER) {
                sb.append(' ');
            }
        }
        sb.append(" / 流派=").append(Dao.values().length - 1).append('}');
        return sb.toString();
    }
}
