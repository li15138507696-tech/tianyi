package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天赋词条池 —— 从「天命」（tianfu）附属并入的 72 条词条。
 *
 * <p><b>本文件由 {@code _crash3/gen_talent_java.py} 生成，不要手改。</b>
 * 改数据请改 tianfu 源码后重跑脚本。
 *
 * <p><b>只取「词条」</b>：天命池里的凡体 / 十绝体 / 机缘都会写本体 {@code tizhi}，
 * 那是天命的领地，道途绝不碰。词条只给属性、道痕、buff、战斗乘区，
 * 全部存在 {@code run/attrs} 里，转世自动清空。
 *
 * <p><b>刻意丢弃的效果</b>（见生成脚本注释）：
 * <ul>
 *   <li>{@code FIELD_MULT} / {@code FIELD_ADD}——直接写 PlayerVariables，
 *       转世无法回滚，会破坏肉鸽循环；</li>
 *   <li>{@code XIULIAN} / {@code BREAKTHROUGH} / {@code LIANGU} / {@code GU_CD} /
 *       {@code SHAZHAO_CD}——要接本体修炼与炼蛊流水线，超出「加进抽选池」的范围；</li>
 *   <li>{@code JUMP_FLAT}——原版没有跳跃高度属性。</li>
 * </ul>
 *
 * <p><b>效果编码</b>（紧凑字符串，静态解析一次）：
 * <pre>
 *   ATTR,键,值,0            属性加成（百分比或平加由键决定）
 *   COMBAT,c_键,值,副值    战斗/回复乘区，由 DaotuCombatEffects 读取
 *   DAOHEN,字段,值,0        单个道痕
 *   DAOHEN_ALL,,值,0        全道痕
 *   BUFF,效果id,0,0         常驻增益
 * </pre>
 */
public final class DaotuTalent {

    /** 一条效果。{@code arg} 对属性类是键名，对道痕是字段名，对 buff 是效果 id。 */
    public record Effect(String kind, String arg, double a, double b) {
    }

    /** 一条词条。{@code summary} 是给界面候选行用的一行短说明。 */
    public record Entry(String id, String name, String quality,
                        String desc, String summary, List<Effect> fx) {
    }

    private static final List<Entry> ALL = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    static {
        t("ji_wanshi_bumie", "极·万世不灭", "极",
                "生命上限 +150%、每 5 秒回复 40% 生命、非护甲减伤 +40%",
                "生命+150% · 持续回复",
                "ATTR,max_health,1.500000,0", "COMBAT,c_regen_hp,0.400000,0.000000", "COMBAT,c_reduction,0.400000,0.000000");
        t("ji_zhongdao_zhizun", "极·众道至尊", "极",
                "全流派道痕各 +3000、修炼速度 +200%、突破瓶颈 −80%",
                "全道痕 +3000",
                "DAOHEN_ALL,,3000.000000,0");
        t("shen_busi_bumie", "神·不死不灭", "神",
                "每 5 秒回复 12% 生命、非护甲减伤 +30%",
                "持续回复 · 减伤+30%",
                "COMBAT,c_regen_hp,0.120000,0.000000", "COMBAT,c_reduction,0.300000,0.000000");
        t("shen_jingang_buhuai", "神·金刚不坏", "神",
                "击退抗性 +1、非护甲减伤 +35%",
                "击退 +1.0 · 减伤+35%",
                "ATTR,knockback_resistance,1.000000,0", "COMBAT,c_reduction,0.350000,0.000000");
        t("shen_wandao_guizong", "神·万道归宗", "神",
                "全流派道痕各 +1500",
                "全道痕 +1500",
                "DAOHEN_ALL,,1500.000000,0");
        t("shen_zhanshen_linshi", "神·战神临世", "神",
                "攻击伤害 +60%、攻击速度 +25%",
                "力量+60% · 攻速+25%",
                "ATTR,attack_damage,0.600000,0", "ATTR,attack_speed,0.250000,0");
        t("baoji_rushen", "暴击如神", "天",
                "暴击率 +25%、暴击伤害 +120%",
                "暴击+25% · 暴伤+120%",
                "COMBAT,c_crit_chance,0.250000,0.000000", "COMBAT,c_crit_damage,1.200000,0.000000");
        t("huixue_rusheng", "回血如生", "天",
                "每 5 秒回复 5% 生命",
                "持续回复",
                "COMBAT,c_regen_hp,0.050000,0.000000");
        t("jianjia_rushen", "坚甲如神", "天",
                "非护甲减伤 +25%",
                "减伤+25%",
                "COMBAT,c_reduction,0.250000,0.000000");
        t("niantou_bujie", "念头不竭", "天",
                "念头回复 +120%",
                "持续回复",
                "COMBAT,c_regen_nt,1.200000,0.000000");
        t("wuxing_daocang", "五行道藏", "天",
                "全流派道痕各 +300",
                "全道痕 +300",
                "DAOHEN_ALL,,300.000000,0");
        t("xixue_rushi", "吸血如饕", "天",
                "攻击吸血 +10%",
                "吸血+10%",
                "COMBAT,c_leech,0.100000,0.000000");
        t("zhenyuan_bujie", "真元不竭", "天",
                "真元回复 +150%",
                "持续回复",
                "COMBAT,c_regen_zy,1.500000,0.000000");
        t("baoji_rulie", "暴击如裂", "地",
                "暴击率 +15%",
                "暴击+15%",
                "COMBAT,c_crit_chance,0.150000,0.000000");
        t("baoshang_rufeng", "暴伤如锋", "地",
                "暴击伤害 +80%",
                "暴伤+80%",
                "COMBAT,c_crit_damage,0.800000,0.000000");
        t("canshen_rulang", "残身如狼", "地",
                "残血（<30%）时出伤 +25%",
                "残血增伤+25%",
                "COMBAT,c_out_low,0.250000,0.000000");
        t("jiandao_dacheng", "剑道大成", "地",
                "剑道道痕 +800",
                "剑道道痕 +800",
                "DAOHEN,daohen_jiandao,800.000000,0");
        t("jianjia_ruyan", "坚甲如岩", "地",
                "非护甲减伤 +15%",
                "减伤+15%",
                "COMBAT,c_reduction,0.150000,0.000000");
        t("leidao_dacheng", "雷道大成", "地",
                "雷道道痕 +800",
                "雷道道痕 +800",
                "DAOHEN,daohen_leidao,800.000000,0");
        t("lidao_dacheng", "力道大成", "地",
                "力道道痕 +800",
                "力道道痕 +800",
                "DAOHEN,daohen_lidao,800.000000,0");
        t("niantou_ruyuan", "念头如渊", "地",
                "念头回复 +60%",
                "持续回复",
                "COMBAT,c_regen_nt,0.600000,0.000000");
        t("quansheng_ruhong", "全盛如洪", "地",
                "满血时出伤 +18%",
                "满血增伤+18%",
                "COMBAT,c_out_full,0.180000,0.000000");
        t("shenfa_ruyan", "身法如燕", "地",
                "闪避率 +12%",
                "闪避+12%",
                "COMBAT,c_dodge,0.120000,0.000000");
        t("xieshi_ziyu", "血食自愈", "地",
                "每 5 秒回复 3% 生命",
                "持续回复",
                "COMBAT,c_regen_hp,0.030000,0.000000");
        t("xixue_rumo", "吸血入魔", "地",
                "攻击吸血 +6%",
                "吸血+6%",
                "COMBAT,c_leech,0.060000,0.000000");
        t("zhenyuan_ruquan", "真元如泉", "地",
                "真元回复 +80%",
                "持续回复",
                "COMBAT,c_regen_zy,0.800000,0.000000");
        t("bianhuadao_qinhe", "变化道亲和", "玄",
                "变化道道痕 +500",
                "变化道道痕 +500",
                "DAOHEN,daohen_bianhuadao,500.000000,0");
        t("bingdao_qinhe", "兵道亲和", "玄",
                "兵道道痕 +500",
                "兵道道痕 +500",
                "DAOHEN,daohen_bingdao,500.000000,0");
        t("dandao_qinhe", "丹道亲和", "玄",
                "丹道道痕 +500",
                "丹道道痕 +500",
                "DAOHEN,daohen_dandao,500.000000,0");
        t("daodao_qinhe", "刀道亲和", "玄",
                "刀道道痕 +500",
                "刀道道痕 +500",
                "DAOHEN,daohen_daodao,500.000000,0");
        t("guangdao_qinhe", "光道亲和", "玄",
                "光道道痕 +500",
                "光道道痕 +500",
                "DAOHEN,daohen_guangdao,500.000000,0");
        t("huandao_qinhe", "幻道亲和", "玄",
                "幻道道痕 +500",
                "幻道道痕 +500",
                "DAOHEN,daohen_huandao,500.000000,0");
        t("liandao_qinhe", "炼道亲和", "玄",
                "炼道道痕 +500",
                "道道痕 +500",
                "DAOHEN,dahen_liandao,500.000000,0");
        t("lijin_ruquan", "力进如泉", "玄",
                "攻击伤害 +8%",
                "力量+8%",
                "ATTR,attack_damage,0.080000,0");
        t("quanru_jifeng", "拳如疾风", "玄",
                "攻击速度 +10%",
                "攻速+10%",
                "ATTR,attack_speed,0.100000,0");
        t("ruwei_dingshen", "入微定神", "玄",
                "非护甲减伤 +8%",
                "减伤+8%",
                "COMBAT,c_reduction,0.080000,0.000000");
        t("shanbu_suifeng", "闪步随风", "玄",
                "闪避率 +6%",
                "闪避+6%",
                "COMBAT,c_dodge,0.060000,0.000000");
        t("songyue_jianli", "耸岳坚立", "玄",
                "击退抗性 +0.4",
                "击退 +0.4",
                "ATTR,knockback_resistance,0.400000,0");
        t("xingdao_qinhe", "星道亲和", "玄",
                "星道道痕 +500",
                "道道痕 +500",
                "DAOHEN,dahen_xingdao,500.000000,0");
        t("xueqi_rulin", "血气如林", "玄",
                "生命上限 +20%",
                "生命+20%",
                "ATTR,max_health,0.200000,0");
        t("yindao_qinhe", "音道亲和", "玄",
                "音道道痕 +500",
                "阴道道痕 +500",
                "DAOHEN,daohen_yindao,500.000000,0");
        t("zhendao_qinhe", "阵道亲和", "玄",
                "阵道道痕 +500",
                "道道痕 +500",
                "DAOHEN,dahen_zhendao,500.000000,0");
        t("zhenyuan_huiliu", "真元回流", "玄",
                "真元回复 +35%",
                "持续回复",
                "COMBAT,c_regen_zy,0.350000,0.000000");
        t("bingxuedao_qinhe", "冰雪道亲和", "黄",
                "冰雪道道痕 +200",
                "冰雪道道痕 +200",
                "DAOHEN,daohen_bingxuedao,200.000000,0");
        t("dudao_qinhe", "毒道亲和", "黄",
                "毒道道痕 +200",
                "毒道道痕 +200",
                "DAOHEN,daohen_dudao,200.000000,0");
        t("fengdao_qinhe", "风道亲和", "黄",
                "风道道痕 +200",
                "风道道痕 +200",
                "DAOHEN,daohen_fengdao,200.000000,0");
        t("gudao_qinhe", "骨道亲和", "黄",
                "骨道道痕 +200",
                "骨道道痕 +200",
                "DAOHEN,daohen_gudao,200.000000,0");
        t("hundao_qinhe", "魂道亲和", "黄",
                "魂道道痕 +200",
                "魂道道痕 +200",
                "DAOHEN,daohen_hundao,200.000000,0");
        t("jiandao_qinhe", "剑道亲和", "黄",
                "剑道道痕 +200",
                "剑道道痕 +200",
                "DAOHEN,daohen_jiandao,200.000000,0");
        t("jindao_qinhe", "金道亲和", "黄",
                "金道道痕 +200",
                "金道道痕 +200",
                "DAOHEN,daohen_jindao,200.000000,0");
        t("leidao_qinhe", "雷道亲和", "黄",
                "雷道道痕 +200",
                "雷道道痕 +200",
                "DAOHEN,daohen_leidao,200.000000,0");
        t("lidao_qinhe", "力道亲和", "黄",
                "力道道痕 +200",
                "力道道痕 +200",
                "DAOHEN,daohen_lidao,200.000000,0");
        t("mengdao_qinhe", "梦道亲和", "黄",
                "梦道道痕 +200",
                "梦道道痕 +200",
                "DAOHEN,daohen_mengdao,200.000000,0");
        t("mudao_qinhe", "木道亲和", "黄",
                "木道道痕 +200",
                "木道道痕 +200",
                "DAOHEN,daohen_mudao,200.000000,0");
        t("niantou_zisheng", "念头自生", "黄",
                "念头回复 +20%",
                "持续回复",
                "COMBAT,c_regen_nt,0.200000,0.000000");
        t("shenfa_qingjie", "身法轻捷", "黄",
                "移动速度 +5%",
                "速度+5%",
                "ATTR,movement_speed,0.050000,0");
        t("shuidao_qinhe", "水道亲和", "黄",
                "水道道痕 +200",
                "水道道痕 +200",
                "DAOHEN,daohen_shuidao,200.000000,0");
        t("tiepi_rougu", "铁皮肉骨", "黄",
                "非护甲减伤 +4%",
                "减伤+4%",
                "COMBAT,c_reduction,0.040000,0.000000");
        t("tudao_qinhe", "土道亲和", "黄",
                "土道道痕 +200",
                "土道道痕 +200",
                "DAOHEN,daohen_tudao,200.000000,0");
        t("xudao_qinhe", "虚道亲和", "黄",
                "虚道道痕 +200",
                "虚道道痕 +200",
                "DAOHEN,daohen_xudao,200.000000,0");
        t("xuedao_qinhe", "血道亲和", "黄",
                "血道道痕 +200",
                "血道道痕 +200",
                "DAOHEN,daohen_xuedao,200.000000,0");
        t("yandao_qinhe", "炎道亲和", "黄",
                "炎道道痕 +200",
                "炎道道痕 +200",
                "DAOHEN,daohen_yandao,200.000000,0");
        t("yingdao_qinhe", "影道亲和", "黄",
                "影道道痕 +200",
                "影道道痕 +200",
                "DAOHEN,daohen_yingdao,200.000000,0");
        t("yuedao_qinhe", "月道亲和", "黄",
                "月道道痕 +200",
                "月道道痕 +200",
                "DAOHEN,daohen_yuedao,200.000000,0");
        t("zhaoqi_yinlu", "朝气饮露", "黄",
                "每 5 秒回复 1% 生命",
                "持续回复",
                "COMBAT,c_regen_hp,0.010000,0.000000");
        t("zhidao_qinhe", "智道亲和", "黄",
                "智道道痕 +200",
                "道道痕 +200",
                "DAOHEN,dahen_zhidao,200.000000,0");
        t("zhoudao_qinhe", "宙道亲和", "黄",
                "宙道道痕 +200",
                "道道痕 +200",
                "DAOHEN,dahen_zhoudao,200.000000,0");
        t("niantou_ganhe", "念头干涸", "负",
                "念头回复 −30%",
                "持续回复",
                "COMBAT,c_regen_nt,-0.300000,0.000000");
        t("quanshi_piruan", "拳势疲软", "负",
                "攻击伤害 −10%",
                "力量-10%",
                "ATTR,attack_damage,-0.100000,0");
        t("routi_chanruo", "肉体孱弱", "负",
                "生命上限 −20%",
                "生命-20%",
                "ATTR,max_health,-0.200000,0");
        t("shenfa_chidun", "身法迟钝", "负",
                "移动速度 −8%",
                "速度-8%",
                "ATTR,movement_speed,-0.080000,0");
        t("zhenyuan_kujie", "真元枯竭", "负",
                "真元回复 −30%",
                "持续回复",
                "COMBAT,c_regen_zy,-0.300000,0.000000");
    }

    private DaotuTalent() {
    }

    /** 全部词条（按品质从高到低）。 */
    public static List<Entry> all() {
        return ALL;
    }

    public static Entry byId(String id) {
        return BY_ID.get(id);
    }

    public static int size() {
        return ALL.size();
    }

    private static void t(String id, String name, String quality,
                           String desc, String summary, String... specs) {
        List<Effect> fx = new ArrayList<>(specs.length);
        for (String s : specs) {
            String[] f = s.split(",", 4);
            if (f.length < 4) {
                continue;
            }
            fx.add(new Effect(f[0], f[1].isEmpty() ? null : f[1],
                    Double.parseDouble(f[2]), Double.parseDouble(f[3])));
        }
        Entry e = new Entry(id, name, quality, desc, summary, List.copyOf(fx));
        ALL.add(e);
        BY_ID.put(id, e);
    }
}
