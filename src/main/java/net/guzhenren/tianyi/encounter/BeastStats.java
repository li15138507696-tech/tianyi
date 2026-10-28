package net.guzhenren.tianyi.encounter;

import java.util.List;

/**
 * 蛊兽原生属性表（<b>由脚本生成，勿手改</b>）。
 *
 * <h2>数据从哪来</h2>
 * 逐个读本体 jar 里实体类的 {@code registerAttributes()} 字节码，取
 * {@code Attributes.MAX_HEALTH} / {@code ARMOR} / {@code ATTACK_DAMAGE} 的常量。
 * 生成器：{@code _pack/gen_beast_stats.py}；复核：{@code _pack/audit_bestiary.py}。
 *
 * <h2>这些数值的含义</h2>
 * 是 {@code createAttributes()} 的<b>初始值</b>，<b>不含</b>本体词条、转数威压与状态效果 ——
 * 实机数值会更高，所以面板/劝告里写的是"原生"数值。
 */
public final class BeastStats {
    /** 一只兽的原生属性。 */
    public record Row(String id, String name, int tier, String dao,
                      double hp, double armor, double atk) {}

    private static final List<Row> ROWS = List.of(
            new Row("guzhenren:bao", "豹", 1, "YU", 1000, 60, 100),
            new Row("guzhenren:biao", "彪", 1, "JIN", 1000, 60, 100),
            new Row("guzhenren:bing_jiao_sha", "冰鲛鲨", 1, "BING", 80, 5, 10),
            new Row("guzhenren:bing_po_long_wen_sha", "冰魄龙纹鲨", 1, "BING", 1024, 70, 200),
            new Row("guzhenren:dian_lang", "电狼", 1, "LEI", 60, 3, 5),
            new Row("guzhenren:gong_bing_yi", "工兵蚁", 1, "TU", 20, 5, 3),
            new Row("guzhenren:hong_xiong", "红熊", 1, "YAN", 40, 10, 10),
            new Row("guzhenren:hui_lang", "灰狼", 1, "NONE", 20, 0, 3),
            new Row("guzhenren:hui_xiong", "灰熊", 1, "NONE", 40, 10, 10),
            new Row("guzhenren:huli_0", "赤狐", 1, "JIN", 1000, 60, 100),
            new Row("guzhenren:hunshou_1", "魂兽", 1, "NONE", 1000, 60, 100),
            new Row("guzhenren:jiang_jun_yi", "将军蚁", 1, "TU", 400, 100, 80),
            new Row("guzhenren:lu", "鹿", 1, "LIAN", 760, 75, 65),
            new Row("guzhenren:mumeishujing_1", "木魅树精", 1, "MU", 760, 75, 65),
            new Row("guzhenren:qiliu_he", "气流鹤", 1, "QI", 1024, 70, 200),
            new Row("guzhenren:shi_ying_she", "时影蛇", 1, "ZHOU", 1024, 0, 200),
            new Row("guzhenren:tu_lang", "土狼", 1, "NONE", 20, 0, 3),
            new Row("guzhenren:wang_yi", "王蚁", 1, "TU", 1024, 70, 200),
            new Row("guzhenren:xing_jun_yi", "行军蚁", 1, "TU", 100, 35, 30),
            new Row("guzhenren:xiong", "熊", 1, "NONE", 40, 10, 10),
            new Row("guzhenren:xuefu", "血蝠", 1, "XUE", 1024, 70, 200),
            new Row("guzhenren:yingkegui", "硬壳龟", 1, "LV", 20, 20, 10),
            new Row("guzhenren:anduodiao", "暗夺雕", 2, "TOU", 1024, 70, 200),
            new Row("guzhenren:bing_lin_sha", "冰鳞鲨", 2, "BING", 300, 30, 80),
            new Row("guzhenren:bishuiling", "碧水羚", 2, "SHUI", 260, 13, 15),
            new Row("guzhenren:feng_huo_ying", "风火鹰", 2, "FENG", 1024, 70, 200),
            new Row("guzhenren:fengyun_he", "风云鹤", 2, "QI", 1024, 70, 200),
            new Row("guzhenren:gangjiagui", "刚甲龟", 2, "LV", 1000, 60, 100),
            new Row("guzhenren:hao_dian_lang", "豪电狼", 2, "LEI", 250, 10, 26),
            new Row("guzhenren:hu", "虎", 2, "JIN", 480, 20, 60),
            new Row("guzhenren:huli_1", "金思狐", 2, "ZHI", 1000, 60, 100),
            new Row("guzhenren:hunshou_2", "魂兽·百兽王", 2, "HUN", 1000, 60, 100),
            new Row("guzhenren:huoyanxiong", "火焰熊", 2, "YAN", 380, 15, 35),
            new Row("guzhenren:ke_wen_she", "刻纹蛇", 2, "ZHOU", 1024, 5, 200),
            new Row("guzhenren:mumeishujing_2", "木魅树精-百兽王", 2, "MU", 760, 75, 65),
            new Row("guzhenren:rongwenlu", "熔纹鹿", 2, "LIAN", 760, 75, 65),
            new Row("guzhenren:xunjiejinbao", "巡界金豹", 2, "YU", 1000, 60, 100),
            new Row("guzhenren:xuxuyifu", "血翼蝠", 2, "XUE", 1024, 70, 200),
            new Row("guzhenren:yan_xu_quan", "岩戌犬", 2, "TU", 280, 25, 23),
            new Row("guzhenren:chuanyuqingbao", "穿宇青豹", 3, "YU", 1000, 60, 100),
            new Row("guzhenren:daochixuefu", "刀翅血蝠", 3, "XUE", 1024, 70, 200),
            new Row("guzhenren:huli_3", "妄谋狐", 3, "ZHI", 1000, 60, 100),
            new Row("guzhenren:hunshou_3", "魂兽·千兽王", 3, "HUN", 1000, 60, 100),
            new Row("guzhenren:jinfenghu", "金锋虎", 3, "JIN", 1000, 60, 100),
            new Row("guzhenren:lei_dian_lang", "狂电狼", 3, "LEI", 1000, 50, 90),
            new Row("guzhenren:lianchenlu", "炼尘鹿", 3, "LIAN", 760, 75, 65),
            new Row("guzhenren:lie_gang_feng_ying", "雷罡风鹰", 3, "FENG", 1024, 70, 200),
            new Row("guzhenren:lieyanxiong", "烈焰熊", 3, "YAN", 1000, 60, 100),
            new Row("guzhenren:lingbohe", "凌波鹤", 3, "QI", 1024, 70, 200),
            new Row("guzhenren:lueshiying", "掠食鹰", 3, "TOU", 1024, 70, 200),
            new Row("guzhenren:mumeishujing_3", "木魅树精-千兽王", 3, "MU", 760, 75, 65),
            new Row("guzhenren:shangjianxuangui", "伤坚玄龟", 3, "LV", 1000, 60, 100),
            new Row("guzhenren:su_hui_she", "溯洄蛇", 3, "ZHOU", 1024, 20, 200),
            new Row("guzhenren:xuan_shuang_bing_sha", "玄霜鲛鲨", 3, "BING", 950, 70, 130),
            new Row("guzhenren:yao_ya_quan", "垚犽犬", 3, "TU", 800, 100, 100),
            new Row("guzhenren:yuanyuelin", "渊跃羚", 3, "SHUI", 760, 75, 65),
            new Row("guzhenren:cangyuelingjun", "苍月羚君", 4, "SHUI", 760, 75, 65),
            new Row("guzhenren:han_yuan_bing_sha", "寒渊冰鲨", 4, "BING", 1024, 70, 200),
            new Row("guzhenren:huishangao", "毁伤鳌", 4, "LV", 1000, 60, 100),
            new Row("guzhenren:huli_4", "绮云幻狐", 4, "ZHI", 1000, 60, 100),
            new Row("guzhenren:hunshou_4", "魂兽·万兽王", 4, "HUN", 1000, 60, 100),
            new Row("guzhenren:jinrenwanghu", "金刃王虎", 4, "JIN", 1000, 60, 100),
            new Row("guzhenren:lei_guan_tou_lang", "雷冠头狼", 4, "LEI", 1024, 70, 200),
            new Row("guzhenren:liandingjielu", "炼鼎劫鹿", 4, "LIAN", 760, 75, 65),
            new Row("guzhenren:liaoyuanhuoxiong", "燎原火熊", 4, "YAN", 1000, 90, 250),
            new Row("guzhenren:mumeishujing_4", "木魅树精-万兽王", 4, "MU", 760, 75, 65),
            new Row("guzhenren:qiebaoxuanying", "窃宝玄枭", 4, "TOU", 1024, 70, 200),
            new Row("guzhenren:xueyingxionghe", "血影凶鹤", 4, "QI", 1024, 70, 200),
            new Row("guzhenren:xueyuxingfu", "血雨腥蝠", 4, "XUE", 1024, 70, 200),
            new Row("guzhenren:yao_guang_feng_sun", "曜光风隼", 4, "FENG", 1024, 70, 200),
            new Row("guzhenren:yue_ao_yan_quan", "岳獒岩犬", 4, "TU", 1024, 70, 200),
            new Row("guzhenren:zhou_shi_ling_ran", "宙蚀灵蚺", 4, "ZHOU", 1024, 40, 200),
            new Row("guzhenren:zixiaojiebao", "紫霄界豹", 4, "YU", 1000, 60, 100),
            new Row("guzhenren:bumiexuanwu", "不灭玄武", 5, "LV", 1000, 60, 100),
            new Row("guzhenren:fengleidianlanghuang", "风雷电狼皇", 5, "LEI", 1024, 70, 200),
            new Row("guzhenren:hhongtaofuhailing", "洪涛覆海羚", 5, "SHUI", 760, 75, 65),
            new Row("guzhenren:huli_5", "胭霞万象狐", 5, "ZHI", 1000, 60, 100),
            new Row("guzhenren:hunshou", "魂兽·兽皇", 5, "HUN", 1000, 60, 100),
            new Row("guzhenren:ji_feng_lve_yun_diao", "疾风掠云雕", 5, "FENG", 1024, 70, 200),
            new Row("guzhenren:jinhuomoyanxiong", "烬火魔焰熊", 5, "YAN", 1000, 90, 250),
            new Row("guzhenren:lixuemofu", "沥血魔蝠", 5, "XUE", 1024, 70, 200),
            new Row("guzhenren:mumeishujing_5", "木魅树精-兽皇", 5, "MU", 760, 75, 65),
            new Row("guzhenren:qianlianguichenlu", "千炼归尘鹿", 5, "LIAN", 760, 75, 65),
            new Row("guzhenren:shan_mai_di_yan_quan", "山脉地岩犬", 5, "TU", 1024, 70, 200),
            new Row("guzhenren:shentouguiduoxiao", "神偷诡夺枭", 5, "TOU", 1024, 70, 200),
            new Row("guzhenren:shi_kong_zhen_yu_mang", "时空镇狱蟒", 5, "ZHOU", 1024, 70, 200),
            new Row("guzhenren:xiongshabaihu", "凶煞白虎", 5, "JIN", 1000, 60, 100),
            new Row("guzhenren:yinyangshengsihe", "阴阳生死鹤", 5, "QI", 1024, 70, 200),
            new Row("guzhenren:ziyaojiexingbao", "紫曜界行豹", 5, "YU", 1000, 60, 100)
    );

    private BeastStats() {
    }

    /** 全部行（按阶、id 排序）。 */
    public static List<Row> all() {
        return ROWS;
    }

    public static int size() {
        return ROWS.size();
    }
}
