package net.guzhenren.tianfu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天赋池（单张数据表）：20 个凡体（体质类）+ 10 个十绝体与杀神（本体原生体质入池）+ 4 条机缘（甲乙丙丁资质入池）
 * + 129 条词条（原有 22 + 扩展 107，扩展表见 {@link TianfuTalents}；鸡肋条目已筛除）。
 *
 * <p>凡体删除规则沿用：效果无法在本体落地的条目整删，编号保留空洞不重排；
 * 17/33 为通用战斗体质。体质（凡体/十绝体/杀神）<b>限购一个</b>（本体 tizhi 非 0 即视为已有体质）；
 * 十绝体购买后写 {@code kongqiao=3} + 复刻本体开窍字段 → KE2 原生生效；
 * 机缘购买后写真元上限 → KE1 自动反推档位（见 {@link TianfuNative}）。
 *
 * <p>纯觅长生式：无随机候选、无抽取、无锁定、无重掷；用自由点购买（开局按难度发放，见 {@link TianfuDifficulty}）。
 * 成本：凡体 3 / 十绝体 16（杀神同价）/ 丁机缘 5 / 丙机缘 8 / 乙机缘 12 / 甲机缘 16；
 * 词条极 16 / 神 12（纯价格门槛）/ 天 5 / 地 3 / 玄 2 / 黄 1 / 负分项 −2（返还 2 点）。
 */
public final class TianfuPool {
    public enum Kind { BODY, ENTRY, SIJUE, JIYUAN }

    /**
     * @param tizhi 体质类写入本体的 tizhi 值（凡体 17–53、十绝体 1–10、杀神 54）；词条 / 机缘为 0
     */
    public record Spec(String id, String name, Kind kind, String quality, int cost, int tizhi, String desc) {
    }

    public static final int BODY_COST = 3;
    /** 神级天赋品质标记。 */
    public static final String GOD_QUALITY = "神";
    /** 神级天赋成本：12 点（无资质门禁，攒够即买）。 */
    public static final int GOD_COST = 12;
    /** 十绝体成本：16 点（体质唯一限购，价格随之下调）。 */
    public static final int SIJUE_COST = 16;

    private static final List<Spec> BODIES = List.of(
            body("tudao", "九命不灭体", 17, "濒死（血量 <10%）闪避率 +25%；土道道痕 +1000"),
            body("bingdao", "百战金戈体", 18, "兵道道痕 +1000；兵刃伤害由本体流水线自动体现"),
            body("bingxuedao", "寒渊素雪体", 19, "冰雪道道痕 +1000；冰系攻防由本体流水线自动体现"),
            body("fengdao", "长风破浪体", 21, "风道道痕 +1000；移速+8%，风系伤害由本体流水线自动体现"),
            body("gudao", "白玉铁骨体", 22, "骨道道痕 +1000；附加非护甲减伤（每千 10%，上限 50%）"),
            body("hundao", "幽魂凝识体", 24, "魂魄上限 +25%、承载极限 +25%"),
            body("jiandao", "断岳剑心体", 25, "剑道道痕 +1000；自带「剑道伤害增幅」常驻"),
            body("jindao", "百炼庚金体", 26, "金道道痕 +1000；自带「镀金」常驻"),
            body("lidao", "搬山撼岳体", 28, "力道道痕 +1000；力道属性（原版怪物）+12%；精力+15%"),
            body("mudao", "苍木回春体", 30, "生命上限 +10%"),
            body("qidao", "太息养元体", 31, "真元回复 +15%（不破 999 上限）；自带「气道蛊虫威力增幅」常驻"),
            body("xiuluo", "修罗战体", 33, "每损 10% 血出伤 +5%（最多 +25%）；血道道痕 +1000"),
            body("xudao", "太虚化形体", 37, "受击 15% 概率虚化免伤（内置 5 秒 CD）"),
            body("yingdao", "如影随形体", 39, "移速 +8%、闪避 +5%"),
            body("yuedao", "明月入怀体", 40, "夜间真元回复 +80%"),
            body("zhidao", "慧心通明体", 42, "念头之道 +25%、念头容量 +25%"),
            body("zhoudao", "光阴逆转体", 43, "宙道时间 +50%、CD −12.5%"),
            body("feixingdao", "御空踏云体", 45, "直接获得飞行能力（常驻）"),
            body("mengdao", "南柯一梦体", 48, "念头回复 +30%"),
            body("xueshi", "血煞蜕变体", 53, "攻击吸血 5%；血量低于 30% 时攻击 +12%")
    );

    private static List<Spec> buildEntries() {
        List<Spec> out = new ArrayList<>();
        out.addAll(List.of(
            entry("wanfa_jietong", "万法皆通", "极", 16, "所有流派熟练度获取速度 ×3（各流派权重增长 ×3）"),
            entry("shen_tianji_suan", "神·天机神算", GOD_QUALITY, 12, "暴击率 +50%、暴击伤害 +240%、闪避 +24%"),
            entry("shen_bumie_jinshen", "神·不灭金身", GOD_QUALITY, 12, "生命上限 +100%、受击减伤 +20%、每秒回复 5% 生命"),
            entry("shen_tuntian_shidi", "神·吞天噬地", GOD_QUALITY, 12, "吸血 +12%、反伤 +15%、气运上限 +50；击杀怪物累积道痕"),
            entry("suhui", "宿慧", "天", 5, "每转数提供额外修炼基数与道痕获取"),
            entry("shazhao_tiancheng", "杀招天成", "地", 3, "杀招成功率 +5%、10 槽磨损 −1"),
            entry("shiyuan_zhixing", "噬元之性", "地", 3, "击杀灵体回复真元/念头"),
            entry("fuze_mianchang", "福泽绵长", "地", 3, "气运上限 +15（上限越高，本体气运回复空间越大）"),
            entry("zhenyuan_ninglian", "真元凝练", "玄", 2, "真元消耗 −10%"),
            entry("niantou_ninglian", "念头凝练", "玄", 2, "念头消耗 −10%"),
            entry("xinshen_ruyuan", "心神如渊", "玄", 2, "沉默时长减半"),
            entry("kuxiu_buchuo", "苦修不辍", "玄", 2, "修炼速度 +20%"),
            entry("houji_bofa", "厚积薄发", "玄", 2, "突破瓶颈 −20%"),
            entry("suiyue_chendian", "岁月沉淀", "玄", 2, "已耗寿元越多、修炼越快"),
            entry("youdou_gaoshou", "游斗高手", "玄", 2, "移动中受击 −6%"),
            entry("jianbi_qingfeng", "剑指偏锋", "玄", 2, "暴击伤害 +30%（需配合暴击精通）"),
            entry("shouyuan_mianchang", "寿元绵长", "黄", 1, "寿元 +50"),
            entry("qiyun_weiguang", "气运微光", "黄", 1, "气运上限 +5"),
            entry("jingu_qiangjian", "筋骨强健", "黄", 1, "生命上限 +10%"),
            entry("baoji_jingtong", "暴击精通", "黄", 1, "暴击率 +5%、暴击伤害 +20%（叠加）"),
            entry("zhanyi_angran", "战意盎然", "黄", 1, "满血时出伤 +8%"),
            entry("youshou_nengkai", "右守能铠", "黄", 1, "受击 4% 概率反弹 10% 伤害")
        ));
        out.addAll(TianfuTalents.specs());
        return out;
    }

    private static final List<Spec> ENTRIES = buildEntries();

    /**
     * 十绝体入池（本体 Uikongqiaoguanbi kongqiao==3 分支的逐号写入，购买后 KE2 原生生效），
     * 末位附杀神（tizhi=54，不给开局道痕，效果走 {@link TianfuSlay}，仅购买获得）。
     * 3 号随机魂道/冰雪道、5 号随机炎道/雷道为本体同款随机，购买时在 {@link TianfuNative} 中 roll。
     */
    private static final List<Spec> SIJUES = List.of(
            sijue(1, "太日阳莽体", "余道道痕 2000；本体十绝体效果原生生效"),
            sijue(2, "古月阴荒体", "周道道痕 2000；宙道时间 +300%、CD −25%"),
            sijue(3, "北冥冰魄体", "魂魄上限 500、承载 50000；随机魂道/冰雪道道痕 2000"),
            sijue(4, "森海轮回体", "木道道痕 2000；寿蟽数值 +20"),
            sijue(5, "炎煌雷泽体", "随机炎道/雷道道痕 2000；自带对应流派增幅常驻"),
            sijue(6, "万金妙华体", "金道道痕 2000；自带「镀金」常驻"),
            sijue(7, "大力真武体", "力道道痕 2000；精力上限 200、骨骼力 +20"),
            sijue(8, "逍遥智心体", "智道道痕 2000；念头之道 2000、念头容量 20000"),
            sijue(9, "厚土元央体", "土道道痕 2000"),
            sijue(10, "宇宙大衍体", "律道道痕 2000；自带「律道增幅」常驻"),
            new Spec("shashen", "杀神", Kind.SIJUE, "十绝体", SIJUE_COST, 54,
                    "杀戮值累积伤害成长（封顶 5 倍伤害）+ 杀怪噬道（击杀积道痕）；不给开局道痕，愈杀愈强")
    );

    /** 甲乙丙丁机缘入池：写真元上限等字段，KE1 自动反推档位。 */
    private static final List<Spec> JIYUANS = List.of(
            new Spec("jiyuan_ding", "丁等机缘", Kind.JIYUAN, "机缘", 3, 0,
                    "真元上限 200-390、气运上限 1-10、90% 命格 −1（否则天资流畅）"),
            new Spec("jiyuan_bing", "丙等机缘", Kind.JIYUAN, "机缘", 5, 0,
                    "真元上限 400-590、气运上限 1-3、70% 命格 −1（否则天资流畅）"),
            new Spec("jiyuan_yi", "乙等机缘", Kind.JIYUAN, "机缘", 8, 0,
                    "真元上限 600-790、气运上限 10-20、50% 命格 −1（否则天资流畅）"),
            new Spec("jiyuan_jia", "甲等机缘", Kind.JIYUAN, "机缘", 12, 0,
                    "真元上限 800-990、气运 1-10、1/3 命格 −2 且气运上限 35-45（否则天资流畅）")
    );

    private static final Map<String, Spec> BY_ID = new LinkedHashMap<>();

    static {
        for (Spec s : BODIES) {
            BY_ID.put(s.id(), s);
        }
        for (Spec s : ENTRIES) {
            BY_ID.put(s.id(), s);
        }
        for (Spec s : SIJUES) {
            BY_ID.put(s.id(), s);
        }
        for (Spec s : JIYUANS) {
            BY_ID.put(s.id(), s);
        }
        // 恶意联动词条：购买/撤销必须认得它们（未装恶意时池子里查不到，但已购仍要能撤销）
        for (Spec s : TianfuTalents.maliceSpecs()) {
            BY_ID.put(s.id(), s);
        }
    }

    private TianfuPool() {
    }

    public static List<Spec> bodies() {
        return BODIES;
    }

    public static List<Spec> entries() {
        return ENTRIES;
    }

    public static List<Spec> sijues() {
        return SIJUES;
    }

    public static List<Spec> jiyuans() {
        return JIYUANS;
    }

    /**
     * 恶意联动分页的条目（界面「恶意」页）。
     *
     * <p>每次都向 {@link TianfuTalents#maliceSpecs()} 现取，而不是缓存成
     * {@code static final}：它依赖 {@code ModList} 判定 l2hostility 是否安装，
     * 而类加载期读 mod 列表有失败风险（读不到就会把整组永久隐藏）。
     */
    public static List<Spec> malice() {
        return TianfuTalents.maliceSpecs();
    }

    public static Spec byId(String id) {
        return BY_ID.get(id);
    }

    private static Spec body(String id, String name, int tizhi, String desc) {
        return new Spec(id, name, Kind.BODY, "凡体", BODY_COST, tizhi, desc);
    }

    private static Spec entry(String id, String name, String quality, int cost, String desc) {
        return new Spec(id, name, Kind.ENTRY, quality, cost, 0, desc);
    }

    private static Spec sijue(int n, String name, String desc) {
        return new Spec("sijue_" + n, name, Kind.SIJUE, "十绝体", SIJUE_COST, n, desc);
    }
}
