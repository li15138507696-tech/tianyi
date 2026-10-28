// src/main/java/net/guzhenren/tianfu/TianfuTalents.java
package net.guzhenren.tianfu;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 天赋词条扩展表（107 条）：纯数据驱动 —— 每条词条只声明「效果原语 + 数值」，
 * 由本类的通用引擎落到对应的子系统，避免为 107 条各写一遍 switch。
 *
 * <p>原语落地位置：
 * <ul>
 *   <li>{@link Op#HEALTH_MULT} 等属性类 → {@link TianfuAttributes}（transient AttributeModifier，幂等重建）</li>
 *   <li>{@link Op#MOB_EFFECT} → 本类 {@link #maintainEffects}，常驻无限时长、隐藏粒子</li>
 *   <li>{@link Op#FIELD_MULT}/{@link Op#FIELD_ADD}/{@link Op#DAOHEN}/{@link Op#DAOHEN_ALL}
 *       → 购买时一次性写入本体 {@code PlayerVariables} 字段</li>
 *   <li>{@link Op#REGEN_HP_5S} 等周期类 → {@link TianfuEffects} 每 tick / 每 5 秒结算</li>
 *   <li>{@link Op#CRIT_CHANCE} 等战斗乘区 → {@link TianfuCombat}（挂在本体伤害流水线末端，与既有 7 条词条同乘区叠加）</li>
 * </ul>
 *
 * <p>道痕写入沿用本体「成对写」口径：{@code daohen_*}/{@code dahen_*} 加值，对应
 * {@code liupai_*}/{@code Liupai_*} 加值的 1/10（本体十绝体即 daohen 2000 / liupai 200）。
 * 字段名经 javap 实测，本体确实混用 {@code daohen_} 与 {@code dahen_} 两套前缀；走反射以规避拼写风险。
 */
public final class TianfuTalents {
    /** 效果原语。 */
    public enum Op {
        HEALTH_MULT, ATTACK_MULT, SPEED_MULT, ATTACK_SPEED_MULT,
        JUMP_FLAT, KNOCKBACK_FLAT, FALL_FLAT,
        MOB_EFFECT, FIELD_MULT, FIELD_ADD, DAOHEN, DAOHEN_ALL,
        REGEN_HP_5S, REGEN_ZY, REGEN_NT, XIULIAN, BREAKTHROUGH, LIANGU, GU_CD, SHAZHAO_CD,
        CRIT_CHANCE, CRIT_DAMAGE, DODGE, LEECH, REDUCTION, REFLECT, OUT_FULL, OUT_LOW,
        // ---- 恶意联动（1.2.30）：出伤附带 / 同类记忆减伤 / 受击后强化 / 免死 / 光环 ----
        ON_HIT_EFFECT, ADAPTIVE, COUNTER, UNDYING, AURA,
        /** 只对「吃护甲」的伤害生效（恶意·摄魂）。 */
        REDUCTION_PHYS,
        /** 只对「穿透护甲」的伤害生效（恶意·破魔）。 */
        REDUCTION_MAGIC,
        /** 摔落伤害减免（恶意·月步）。 */
        FALL_REDUCE,
        /** V2 约束原语；只声明，执行通道由适配层填充。 */
        V2_APTITUDE_LOCK, V2_LUCK_ZERO, V2_LIFESPAN_SCAR
    }

    /**
     * 单条效果原语实例。
     *
     * @param op  原语种类
     * @param arg 目标名（{@link Op#MOB_EFFECT}=效果注册名、{@link Op#FIELD_MULT}/{@link Op#FIELD_ADD}/{@link Op#DAOHEN}=本体字段名；其余为 null）
     * @param a   主数值
     * @param b   副数值（仅 {@link Op#REFLECT} 用：反弹比例）
     */
    public record Fx(Op op, String arg, double a, double b) {
    }

    /** 词条（id / 名 / 品质 / 成本 / 描述 / 效果列表）。 */
    public record Talent(String id, String name, String quality, int cost, String desc, List<Fx> fx) {
    }

    /** 属性类聚合值（供 {@link TianfuAttributes} 一次性套用）。 */
    public record Attrs(double healthMult, double attackMult, double speedMult,
                        double attackSpeedMult, double jumpFlat, double knockbackFlat, double fallFlat) {
    }

    // ------------------------------------------------------------------ 效果构造快捷函数

    /** 常驻效果「授予时等级」的记录前缀（值 = 实际 amplifier）。 */
    private static final String AMP_KEY = "amp:";

    private static Fx f(Op op, double a) {
        return new Fx(op, null, a, 0.0);
    }

    private static Fx dao(String field, double a) {
        return new Fx(Op.DAOHEN, field, a, 0.0);
    }

    private static Fx fld(String field, double a) {
        return new Fx(Op.FIELD_MULT, field, a, 0.0);
    }

    private static Fx fldAdd(String field, double a) {
        return new Fx(Op.FIELD_ADD, field, a, 0.0);
    }

    private static Fx mob(String name, double amp) {
        return new Fx(Op.MOB_EFFECT, name, amp, 0.0);
    }

    private static Fx reflect(double chance, double ratio) {
        return new Fx(Op.REFLECT, null, chance, ratio);
    }

    private static Fx onHit(String effect, int ticks, int amp) {
        return new Fx(Op.ON_HIT_EFFECT, effect, ticks, amp);
    }

    private static Fx adaptive(double per, int cap) {
        return new Fx(Op.ADAPTIVE, null, per, cap);
    }

    private static Fx counter(double bonus, int ticks) {
        return new Fx(Op.COUNTER, null, bonus, ticks);
    }

    private static Fx undying(double heal, int cooldownTicks) {
        return new Fx(Op.UNDYING, null, heal, cooldownTicks);
    }

    private static Fx aura(double radius, double ratio) {
        return new Fx(Op.AURA, null, radius, ratio);
    }

    private static Talent t(String id, String name, String q, int cost, String desc, Fx... fx) {
        return new Talent(id, name, q, cost, desc, List.of(fx));
    }

    // ------------------------------------------------------------------ 数据表

    private static final List<Talent> BASE = List.of(
            // V2：新增纯数据；效果行为待适配层填充。
            t("v2_jingli_buji", "精力不济", "负", -2, "精力上限 −20%", fld("zuida_jingli", -0.20)),
            t("v2_gu_cd_ningse", "催蛊凝涩", "负", -2, "蛊冷却每 tick 额外增加 0.10，仅对正在冷却的蛊", f(Op.GU_CD, -0.10)),
            t("v2_zhenyuan_kubai", "真元枯败", "负", -5, "真元回复 −75%（旧 −30% 的 2.5 倍）", f(Op.REGEN_ZY, -0.75)),
            t("v2_routi_canque", "肉体残缺", "负", -5, "生命上限 −50%（旧 −20% 的 2.5 倍）", f(Op.HEALTH_MULT, -0.50)),
            t("v2_shouyuan_kuwei", "寿元枯萎", "负", -5, "寿元 −250（旧 −100 的 2.5 倍，确认前显示致死风险）", fldAdd("shouyuan", -250)),
            t("v2_qiyun_duanjue", "气运断绝", "负", -5, "基础气运上限 −37.5%（旧 −15% 的 2.5 倍）", fld("qiyun_shangxian", -0.375)),
            t("v2_xinzhi_yousun", "心智有损", "负", -5, "修炼速度 −40%、突破瓶颈 +50%（规格锁定的复合代价）", f(Op.XIULIAN, -0.40), f(Op.BREAKTHROUGH, -0.50)),
            t("v2_aptitude_shackled", "空窍桎梏", "负", -8, "本世锁定确认时原有资质；念头容量 −100%，不允许后续机缘或十绝体突破锁定", f(Op.V2_APTITUDE_LOCK, 1), fld("niantou_rongliang", -1.0)),
            t("v2_luck_forfeit", "天弃之命", "负", -8, "本世气运与气运上限持续为 0；气运增益不能抵消，约四倍量级并附不可补回代价", f(Op.V2_LUCK_ZERO, 1)),
            t("v2_lifespan_scar", "命烛残尽", "负", -8, "确认时寿元 −400，且本世寿元恢复与增加无效；寿元不足不得确认", fldAdd("shouyuan", -400), f(Op.V2_LIFESPAN_SCAR, 1)),
            t("v2_mind_xiulian_1", "修炼速度 I", "玄", 2, "+20%", f(Op.XIULIAN, 0.20)),
            t("v2_mind_xiulian_2", "修炼速度 II", "地", 3, "+40%", f(Op.XIULIAN, 0.40)),
            t("v2_mind_xiulian_3", "修炼速度 III", "天", 5, "+60%", f(Op.XIULIAN, 0.60)),
            t("v2_mind_capacity_1", "念头容量 I", "玄", 2, "+20%", fld("niantou_rongliang", 0.20)),
            t("v2_mind_capacity_2", "念头容量 II", "地", 3, "+30%", fld("niantou_rongliang", 0.30)),
            t("v2_mind_capacity_3", "念头容量 III", "天", 5, "+50%", fld("niantou_rongliang", 0.50)),
            t("v2_mind_recovery_1", "念头恢复 I", "玄", 2, "+20%", f(Op.REGEN_NT, 0.20)),
            t("v2_mind_recovery_2", "念头恢复 II", "地", 3, "+40%", f(Op.REGEN_NT, 0.40)),
            t("v2_mind_liangu_1", "炼蛊进度 I", "玄", 2, "+15%", f(Op.LIANGU, 0.15)),
            t("v2_mind_liangu_2", "炼蛊进度 II", "地", 3, "+30%", f(Op.LIANGU, 0.30)),
            t("v2_mind_cooldown_1", "蛊冷却 I", "玄", 2, "每 tick −0.15", f(Op.GU_CD, 0.15)),
            t("v2_mind_cooldown_2", "蛊冷却 II", "地", 3, "每 tick −0.25", f(Op.GU_CD, 0.25)),

            // 新增 15 条：字段拼写来自 _ref/player-variables-fields.txt；不合并 dahen_/daohen_。
            // 黄/玄/地/天成本分别为 1/2/3/5；数值为本次设计，不冒充知识库规定值。
            t("yudao_qinhe", "宇道亲和", "黄", 1, "宇道道痕 +200；熟练度效率 +20%，不直接增伤", dao("daohen_yudao", 200)),
            t("qidao_qinhe", "气道亲和", "黄", 1, "气道道痕 +200；熟练度效率 +20%，不直接增伤", dao("dahen_qidao", 200)),
            t("lvdao_qinhe", "律道亲和", "黄", 1, "律道道痕 +200；熟练度效率 +20%，不提供全流派增伤", dao("daohen_lvdao", 200)),
            t("toudao_qinhe", "偷道亲和", "黄", 1, "偷道道痕 +200；熟练度效率 +20%，不直接增伤", dao("daohen_toudao", 200)),
            t("nudao_qinhe", "奴道亲和", "黄", 1, "奴道道痕 +200；熟练度效率 +20%，不直接增伤", dao("dahen_nudao", 200)),
            t("huadao_qinhe", "化道亲和", "黄", 1, "化道字段道痕 +200；与变化道字段独立，不直接增伤", dao("daohen_huadao", 200)),
            t("andao_qinhe", "暗道亲和", "黄", 1, "暗道道痕 +200；熟练度效率 +20%，与影道独立", dao("daohen_andao", 200)),
            t("shidao_qinhe", "食道亲和", "玄", 2, "食道道痕 +500；熟练度效率 +50%，不直接增伤", dao("daohen_shidao", 500)),
            t("xindao_qinhe", "心道亲和", "玄", 2, "心道字段道痕 +500；不直接增伤", dao("daohen_xindao", 500)),
            t("yundao_qinhe", "运道亲和", "玄", 2, "运道字段道痕 +500；不直接修改气运或其上限", dao("daohen_yundao", 500)),
            t("yundao2_jinjie", "云运支系进阶", "玄", 2, "yundao2 道痕 +500；支系名称待核，不等同气运", dao("daohen_yundao2", 500)),
            t("jindao2_jinjie", "金道支系进阶", "玄", 2, "jindao2 道痕 +500；与 jindao 字段独立，支系名称待核", dao("daohen_jindao2", 500)),
            t("rendao_dacheng", "人道大成", "地", 3, "人道道痕 +800；熟练度效率 +80%，不直接等同人气", dao("dahen_rendao", 800)),
            t("tiandao_daocang", "天道道藏", "天", 5, "天道字段道痕 +1200；不提供全局增伤", dao("dahen_tiandao", 1200)),
            // 总道痕是汇总字段：仅用 FIELD_ADD，不伪造 liupai_zong，也不宣称其为第45个流派。
            t("zonghen_chubei", "总痕储备", "天", 5, "总道痕汇总字段 +300；不增加各流派道痕或伤害", fldAdd("daohen_zong", 300)),

            // ---- 黄（1 点）：20 条流派亲和 + 5 条入门（已筛除夜视/跳跃/饱和/摔落/水下呼吸等鸡肋条目） ----
            t("jiandao_qinhe", "剑道亲和", "黄", 1, "剑道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_jiandao", 200)),
            t("leidao_qinhe", "雷道亲和", "黄", 1, "雷道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_leidao", 200)),
            t("yandao_qinhe", "炎道亲和", "黄", 1, "炎道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_yandao", 200)),
            t("bingxuedao_qinhe", "冰雪道亲和", "黄", 1, "冰雪道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_bingxuedao", 200)),
            t("fengdao_qinhe", "风道亲和", "黄", 1, "风道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_fengdao", 200)),
            t("yingdao_qinhe", "影道亲和", "黄", 1, "影道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_yingdao", 200)),
            t("yuedao_qinhe", "月道亲和", "黄", 1, "月道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_yuedao", 200)),
            t("shuidao_qinhe", "水道亲和", "黄", 1, "水道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_shuidao", 200)),
            t("tudao_qinhe", "土道亲和", "黄", 1, "土道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_tudao", 200)),
            t("mudao_qinhe", "木道亲和", "黄", 1, "木道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_mudao", 200)),
            t("jindao_qinhe", "金道亲和", "黄", 1, "金道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_jindao", 200)),
            t("gudao_qinhe", "骨道亲和", "黄", 1, "骨道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_gudao", 200)),
            t("lidao_qinhe", "力道亲和", "黄", 1, "力道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_lidao", 200)),
            t("hundao_qinhe", "魂道亲和", "黄", 1, "魂道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_hundao", 200)),
            t("xuedao_qinhe", "血道亲和", "黄", 1, "血道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_xuedao", 200)),
            t("xudao_qinhe", "虚道亲和", "黄", 1, "虚道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_xudao", 200)),
            t("zhidao_qinhe", "智道亲和", "黄", 1, "智道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("dahen_zhidao", 200)),
            t("zhoudao_qinhe", "宙道亲和", "黄", 1, "宙道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("dahen_zhoudao", 200)),
            t("mengdao_qinhe", "梦道亲和", "黄", 1, "梦道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_mengdao", 200)),
            t("dudao_qinhe", "毒道亲和", "黄", 1, "毒道道痕 +200；该流派熟练度效率 +20%，不直接增伤", dao("daohen_dudao", 200)),
            t("tiepi_rougu", "铁皮肉骨", "黄", 1, "非护甲减伤 +4%", f(Op.REDUCTION, 0.04)),
            t("bufen_zhiwu", "不焚之物", "黄", 1, "常驻防火", mob("fire_resistance", 0)),
            t("shenfa_qingjie", "身法轻捷", "黄", 1, "移动速度 +5%", f(Op.SPEED_MULT, 0.05)),
            t("zhaoqi_yinlu", "朝气饮露", "黄", 1, "每 5 秒回复 1% 生命", f(Op.REGEN_HP_5S, 0.01)),
            t("niantou_zisheng", "念头自生", "黄", 1, "念头回复 +20%", f(Op.REGEN_NT, 0.20)),

            // ---- 玄（2 点）：10 条流派亲和 + 15 条进阶（已筛除跳跃强度/急迫/缓降/海豚恩惠等鸡肋条目） ----
            t("bingdao_qinhe", "兵道亲和", "玄", 2, "兵道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_bingdao", 500)),
            t("daodao_qinhe", "刀道亲和", "玄", 2, "刀道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_daodao", 500)),
            t("guangdao_qinhe", "光道亲和", "玄", 2, "光道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_guangdao", 500)),
            t("xingdao_qinhe", "星道亲和", "玄", 2, "星道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("dahen_xingdao", 500)),
            t("dandao_qinhe", "丹道亲和", "玄", 2, "丹道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_dandao", 500)),
            t("liandao_qinhe", "炼道亲和", "玄", 2, "炼道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("dahen_liandao", 500)),
            t("yindao_qinhe", "音道亲和", "玄", 2, "音道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_yindao", 500)),
            t("huandao_qinhe", "幻道亲和", "玄", 2, "幻道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_huandao", 500)),
            t("bianhuadao_qinhe", "变化道亲和", "玄", 2, "变化道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("daohen_bianhuadao", 500)),
            t("zhendao_qinhe", "阵道亲和", "玄", 2, "阵道道痕 +500；该流派熟练度效率 +50%，不直接增伤", dao("dahen_zhendao", 500)),
            t("wuying_bi", "无影壁", "玄", 2, "受击 6% 概率反弹 12% 伤害", reflect(0.06, 0.12)),
            t("ruwei_dingshen", "入微定神", "玄", 2, "非护甲减伤 +8%", f(Op.REDUCTION, 0.08)),
            t("shanbu_suifeng", "闪步随风", "玄", 2, "闪避率 +6%", f(Op.DODGE, 0.06)),
            t("xueqi_rulin", "血气如林", "玄", 2, "生命上限 +20%", f(Op.HEALTH_MULT, 0.20)),
            t("lijin_ruquan", "力进如泉", "玄", 2, "攻击伤害 +8%", f(Op.ATTACK_MULT, 0.08)),
            t("quanru_jifeng", "拳如疾风", "玄", 2, "攻击速度 +10%", f(Op.ATTACK_SPEED_MULT, 0.10)),
            t("songyue_jianli", "耸岳坚立", "玄", 2, "击退抗性 +0.4", f(Op.KNOCKBACK_FLAT, 0.4)),
            t("jingli_shengheng", "精力生生", "玄", 2, "精力上限 +30%", fld("zuida_jingli", 0.30)),
            t("chuntian_yishou", "春田益寿", "玄", 2, "寿元 +80", fldAdd("shouyuan", 80)),
            t("qiyun_weiying", "气运微荫", "玄", 2, "基础气运上限 +12%（人气加成另计；零上限保持零）",
                    fld("qiyun_shangxian", 0.12)),
            t("qiyun_yingmen", "气运盈门", "玄", 2, "基础气运上限 +18%（人气加成另计；零上限保持零）", fld("qiyun_shangxian", 0.18)),
            t("hunpo_lianxi", "魂魄炼息", "玄", 2, "魂魄上限 +15%（容量加成，不等于伤害 +15%）", fld("zuida_hunpo", 0.15)),
            t("hunzai_kuanhou", "魂载宽厚", "玄", 2, "魂魄承载极限 +15%", fld("hunpochengzaijixian", 0.15)),
            t("niantou_kuozhan", "念头扩展", "玄", 2, "念头容量 +15%", fld("niantou_rongliang", 0.15)),
            t("zhenyuan_huiliu", "真元回流", "玄", 2, "真元回复 +35%", f(Op.REGEN_ZY, 0.35)),

            // ---- 地（3 点）：21 条 ----
            t("tupo_tongtian", "突破通天", "地", 3, "突破瓶颈 −30%", f(Op.BREAKTHROUGH, 0.30)),
            t("dazuo_wangwo", "打坐忘我", "地", 3, "打坐时修炼进度 +40%", f(Op.XIULIAN, 0.40)),
            t("liangu_rushen", "炼蛊如神", "地", 3, "炼蛊进度 +40%", f(Op.LIANGU, 0.40)),
            t("gudao_dup", "蛊道独步", "地", 3, "额外蛊冷却缩短（每 tick −0.25；熟练度降耗由本体另计）", f(Op.GU_CD, 0.25)),
            t("shazhao_rufeng", "杀招如风", "地", 3, "额外杀招冷却缩短（每 tick −0.25；不等同真元降耗）", f(Op.SHAZHAO_CD, 0.25)),
            t("zhenyuan_ruquan", "真元如泉", "地", 3, "真元回复 +80%", f(Op.REGEN_ZY, 0.80)),
            t("niantou_ruyuan", "念头如渊", "地", 3, "念头回复 +60%", f(Op.REGEN_NT, 0.60)),
            t("xieshi_ziyu", "血食自愈", "地", 3, "每 5 秒回复 3% 生命", f(Op.REGEN_HP_5S, 0.03)),
            t("shenfa_ruyan", "身法如燕", "地", 3, "闪避率 +12%", f(Op.DODGE, 0.12)),
            t("baoji_rulie", "暴击如裂", "地", 3, "暴击率 +15%", f(Op.CRIT_CHANCE, 0.15)),
            t("baoshang_rufeng", "暴伤如锋", "地", 3, "暴击伤害 +80%", f(Op.CRIT_DAMAGE, 0.80)),
            t("xixue_rumo", "吸血入魔", "地", 3, "攻击吸血 +6%", f(Op.LEECH, 0.06)),
            t("fanshang_ruyu", "反伤如玉", "地", 3, "受击 10% 概率反弹 20% 伤害", reflect(0.10, 0.20)),
            t("jianjia_ruyan", "坚甲如岩", "地", 3, "非护甲减伤 +15%", f(Op.REDUCTION, 0.15)),
            t("quansheng_ruhong", "全盛如洪", "地", 3, "满血时出伤 +18%", f(Op.OUT_FULL, 0.18)),
            t("canshen_rulang", "残身如狼", "地", 3, "残血（<30%）时出伤 +25%", f(Op.OUT_LOW, 0.25)),
            t("wanjun_lili", "万钧之力", "地", 3, "常驻力量 II", mob("strength", 1)),
            t("shengji_rumu", "生息如沐", "地", 3, "常驻生命恢复 II", mob("regeneration", 1)),
            t("jiandao_dacheng", "剑道大成", "地", 3, "剑道道痕 +800；该流派熟练度效率 +80%，不直接增伤", dao("daohen_jiandao", 800)),
            t("leidao_dacheng", "雷道大成", "地", 3, "雷道道痕 +800；该流派熟练度效率 +80%，不直接增伤", dao("daohen_leidao", 800)),
            t("lidao_dacheng", "力道大成", "地", 3, "力道道痕 +800；该流派熟练度效率 +80%，不直接增伤", dao("daohen_lidao", 800)),

            // ---- 天（5 点）：16 条（已筛除幸运 III 鸡肋条目） ----
            t("wuxing_daocang", "五行道藏", "天", 5, "44 项非汇总道痕各 +300；对应熟练度效率各 +30%，不直接增伤", f(Op.DAOHEN_ALL, 300)),
            t("qiqiao_linglong", "七窍玲珑", "天", 5, "念头容量 +50%、念头之道 +50%",
                    fld("niantou_rongliang", 0.50), fld("niantou_zhida", 0.50)),
            t("hunpo_rugui", "魂魄如鬼", "天", 5, "魂魄上限 +50%、承载极限 +50%（魂道伤害仍按本体分档）",
                    fld("zuida_hunpo", 0.50), fld("hunpochengzaijixian", 0.50)),
            t("jingli_ruhai", "精力如海", "天", 5, "精力上限 +80%", fld("zuida_jingli", 0.80)),
            t("shouyuan_ruhe", "寿元如鹤", "天", 5, "寿元 +200", fldAdd("shouyuan", 200)),
            t("qiyun_ruhong", "气运如虹", "天", 5, "基础气运上限 +40%（人气加成另计；零上限保持零）",
                    fld("qiyun_shangxian", 0.40)),
            t("zhenyuan_bujie", "真元不竭", "天", 5, "真元回复 +150%", f(Op.REGEN_ZY, 1.50)),
            t("niantou_bujie", "念头不竭", "天", 5, "念头回复 +120%", f(Op.REGEN_NT, 1.20)),
            t("huixue_rusheng", "回血如生", "天", 5, "每 5 秒回复 5% 生命", f(Op.REGEN_HP_5S, 0.05)),
            t("baoji_rushen", "暴击如神", "天", 5, "暴击率 +25%、暴击伤害 +120%",
                    f(Op.CRIT_CHANCE, 0.25), f(Op.CRIT_DAMAGE, 1.20)),
            t("xixue_rushi", "吸血如饕", "天", 5, "攻击吸血 +10%", f(Op.LEECH, 0.10)),
            t("fanshang_rushan", "反伤如山", "天", 5, "受击 20% 概率反弹 35% 伤害", reflect(0.20, 0.35)),
            t("jianjia_rushen", "坚甲如神", "天", 5, "非护甲减伤 +25%", f(Op.REDUCTION, 0.25)),
            t("xiulian_rushen", "修炼如神", "天", 5, "修炼速度 +60%、突破瓶颈 −40%",
                    f(Op.XIULIAN, 0.60), f(Op.BREAKTHROUGH, 0.40)),
            t("liangu_bujie", "炼蛊不竭", "天", 5, "炼蛊进度 +60%、蛊冷却缩短（每 tick −0.3）",
                    f(Op.LIANGU, 0.60), f(Op.GU_CD, 0.30)),
            t("jixing_ruying", "疾行如影", "天", 5, "常驻迅捷 III", mob("speed", 2)),

            // ---- 整合包附属联动（天 / 神）：引用附属注册的真实 buff ----
            // 这些 id 都经 javap 核对过「确实注册」且类别为 BENEFICIAL，
            // 常驻不会把 debuff 挂成永久（如兼修的 frost/frozen 是 HARMFUL，未选用）。
            t("bingxue_daoti", "冰雪道体", "天", 5, "常驻「冰雪道增幅」（兼修附属）",
                    mob("guzhenren_addon:ice_path_boost", 0)),
            t("hundao_ronghe", "魂道融彻", "天", 5, "常驻「魂道增幅」（魂道附属）",
                    mob("hundao_addon:hundao_zengfu", 0)),
            t("qiyun_yizhi", "一掷乾坤", "天", 5, "常驻「一掷乾坤」、基础气运上限 +30%（零上限保持零）",
                    mob("yundao:yizhi_qiankun", 0), fld("qiyun_shangxian", 0.30)),

            // ---- 神（12 点）：8 条 ----
            t("shen_qiyun_zhizi", "神·气运之子", "神", 12, "基础气运上限 +80%（人气加成另计；零上限保持零）",
                    fld("qiyun_shangxian", 0.80)),
            t("shen_hunxi_zhuzai", "神·魂兮主宰", "神", 12, "魂魄上限 +100%、承载极限 +100%、念头容量 +100%",
                    fld("zuida_hunpo", 1.0), fld("hunpochengzaijixian", 1.0), fld("niantou_rongliang", 1.0)),
            t("shen_busi_bumie", "神·不死不灭", "神", 12, "每 5 秒回复 12% 生命、非护甲减伤 +30%",
                    f(Op.REGEN_HP_5S, 0.12), f(Op.REDUCTION, 0.30)),
            t("shen_zhanshen_linshi", "神·战神临世", "神", 12, "攻击伤害 +60%、攻击速度 +25%",
                    f(Op.ATTACK_MULT, 0.60), f(Op.ATTACK_SPEED_MULT, 0.25)),
            t("shen_jingang_buhuai", "神·金刚不坏", "神", 12, "击退抗性 +1、非护甲减伤 +35%",
                    f(Op.KNOCKBACK_FLAT, 1.0), f(Op.REDUCTION, 0.35)),
            t("shen_xiulian_wuji", "神·修炼无极", "神", 12, "修炼速度 +150%、突破瓶颈 −60%",
                    f(Op.XIULIAN, 1.50), f(Op.BREAKTHROUGH, 0.60)),
            t("shen_liangu_tianshu", "神·炼蛊天术", "神", 12, "炼蛊进度 +90%、蛊冷却缩短（每 tick −0.5）",
                    f(Op.LIANGU, 0.90), f(Op.GU_CD, 0.50)),
            t("shen_wandao_guizong", "神·万道归宗", "神", 12, "44 项非汇总道痕各 +1500；对应熟练度效率各 +150%，不直接增伤", f(Op.DAOHEN_ALL, 1500)),
            t("shen_liupai_jianji", "神·流派兼济", "神", 12,
                    "常驻「冰雪道增幅」+「魂道增幅」+「一掷乾坤」，三者可叠加",
                    mob("guzhenren_addon:ice_path_boost", 0),
                    mob("hundao_addon:hundao_zengfu", 0),
                    mob("yundao:yizhi_qiankun", 0)),

            // ---- 极（16 点）：2 条 ----
            t("ji_wanshi_bumie", "极·万世不灭", "极", 16, "生命上限 +150%、每 5 秒回复 40% 生命、非护甲减伤 +40%",
                    f(Op.HEALTH_MULT, 1.50), f(Op.REGEN_HP_5S, 0.40), f(Op.REDUCTION, 0.40)),
            t("ji_zhongdao_zhizun", "极·众道至尊", "极", 16, "44 项非汇总道痕各 +3000（效率各 +300%，不直接增伤）、修炼速度 +200%、突破瓶颈 −80%",
                    f(Op.DAOHEN_ALL, 3000), f(Op.XIULIAN, 2.00), f(Op.BREAKTHROUGH, 0.80)),

            // ---- 负（−2 点）：10 条 ----
            t("zhenyuan_kujie", "真元枯竭", "负", -2, "真元回复 −30%", f(Op.REGEN_ZY, -0.30)),
            t("niantou_ganhe", "念头干涸", "负", -2, "念头回复 −30%", f(Op.REGEN_NT, -0.30)),
            t("routi_chanruo", "肉体孱弱", "负", -2, "生命上限 −20%", f(Op.HEALTH_MULT, -0.20)),
            t("quanshi_piruan", "拳势疲软", "负", -2, "攻击伤害 −10%", f(Op.ATTACK_MULT, -0.10)),
            t("shenfa_chidun", "身法迟钝", "负", -2, "移动速度 −8%", f(Op.SPEED_MULT, -0.08)),
            t("shouyuan_duancu", "寿元短促", "负", -2, "寿元 −100", fldAdd("shouyuan", -100)),
            t("qiyun_xibo", "气运稀薄", "负", -2, "基础气运上限 −15%（零上限保持零）", fld("qiyun_shangxian", -0.15)),
            t("hunli_shuairuo", "魂力衰弱", "负", -2, "魂魄上限 −25%、承载极限 −25%",
                    fld("zuida_hunpo", -0.25), fld("hunpochengzaijixian", -0.25)),
            t("nianfu_xiayai", "念府狭隘", "负", -2, "念头容量 −25%", fld("niantou_rongliang", -0.25)),
            t("xiuxing_chizhi", "修行迟滞", "负", -2, "修炼速度 −30%、突破瓶颈 +40%",
                    f(Op.XIULIAN, -0.30), f(Op.BREAKTHROUGH, -0.40))
    );

    /**
     * 恶意联动词条（14 条；原 20 条里与既有词条重复的 6 条已剔除）。
     *
     * <p><b>剔重口径</b>：效果的"原始语"已被既有词条占用就算重复 ——
     * SPEED_MULT/ATTACK_SPEED_MULT（身法轻捷、拳如疾风）、HEALTH_MULT（血气如林）、
     * REDUCTION（铁皮肉骨、入微定神、坚甲如岩/如神…）、REGEN_HP_5S（朝气饮露、血食自愈…）、
     * DODGE（闪步随风、身法如燕）、MOB_EFFECT fire_resistance（不焚之物）。
     * 因此删掉：疾风身法 / 铁骨铜皮 / 生生不息 / 焚身之火 / 护体罡气 / 遁形迁跃。
     * 留下的都是本体词条里<b>没有</b>的效果：出伤附带、摔落减免、按类型分流减伤、适应、反击、免死、光环。
     *
     * <p><b>必须装「莱特兰-恶意」(l2hostility) 才可见、可选、可生效</b>：数值照恶意 3.0.18 的
     * 词条手感重做，但<b>不调用恶意的任何类</b> —— 完全由本 mod 自己的原语落地，
     * 卸载恶意后这组直接消失（已购买的会按 byIdIncludingUnavailable 保留，不白扣点）。
     *
     * <p>强度口径：<b>全量</b>（1.2.32 起从「折半」回调为恶意原数值），
     * 因为这些词条要花 1–16 点，而恶意自身对怪物是按等级放大后才到这个量级。
     * 成本沿用既有梯度：神 12 / 天 5 / 地 3 / 玄 2 / 黄 1。
     */
    private static final List<Talent> MALICE = List.of(
            // ---- 黄（1 点）----
            t("malice_moonwalk", "踏虚而行", "黄", 1, "摔落伤害 −60%（恶意·月步）",
                    f(Op.FALL_REDUCE, 0.60)),
            t("malice_slowness", "流沙缚足", "黄", 1, "攻击命中使目标缓慢 II，5 秒（恶意·流沙）",
                    onHit("minecraft:slowness", 100, 1)),

            // ---- 玄（2 点）----
            t("malice_poison", "毒蚀之能", "玄", 2, "攻击命中使目标中毒 II，5 秒（恶意·剧毒）",
                    onHit("minecraft:poison", 100, 1)),
            t("malice_freezing", "寒流侵体", "玄", 2, "攻击命中使目标缓慢 II，5 秒（恶意·寒流）",
                    onHit("minecraft:slowness", 100, 1)),
            t("malice_weakness", "卸力之能", "玄", 2, "攻击命中使目标虚弱 II，5 秒（恶意·虚弱）",
                    onHit("minecraft:weakness", 100, 1)),
            t("malice_blindness", "夺明之术", "玄", 2, "攻击命中使目标致盲 3 秒（恶意·致盲）",
                    onHit("minecraft:blindness", 60, 0)),

            // ---- 地（3 点）----
            t("malice_reflect", "镜影反照", "地", 3, "受击时 15% 概率把 30% 伤害以魔法形式反弹（恶意·反射）",
                    reflect(0.15, 0.30)),
            t("malice_counter", "反戈一击", "地", 3, "被击中后 3 秒内攻击伤害 +15%（恶意·反击）",
                    counter(0.15, 60)),
            t("malice_wither", "枯荣之力", "地", 3, "攻击命中使目标凋零 II，5 秒（恶意·凋零）",
                    onHit("minecraft:wither", 100, 1)),

            // ---- 天（5 点）----
            t("malice_dementor", "摄魂夺魄", "天", 5, "受到的「吃护甲」类伤害（近战/弹射物/爆炸）−15%（恶意·摄魂）",
                    f(Op.REDUCTION_PHYS, 0.15)),
            t("malice_dispell", "破法之瞳", "天", 5, "受到的「穿护甲」类伤害（魔法/药水/虚空）−15%（恶意·破魔）",
                    f(Op.REDUCTION_MAGIC, 0.15)),
            t("malice_adaptive", "随境应变", "天", 5, "每次受同类伤害再减伤 2%，同类最多叠 10 层（恶意·适应）",
                    adaptive(0.02, 10)),
            t("malice_killer_aura", "杀意凛然", "天", 5, "每 3 秒对 4 格内敌对生物造成 5% 攻击力的魔法伤害（恶意·杀戮光环）",
                    aura(4.0, 0.05)),

            // ---- 神（12 点）----
            t("malice_undying", "不死之身", "神", 12, "受到致命伤害时免死并回复 50% 生命，冷却 300 秒（恶意·不死）",
                    undying(0.50, 6000))
    );

    private static final List<Talent> ALL = concat(BASE, MALICE);

    private static List<Talent> concat(List<Talent> a, List<Talent> b) {
        List<Talent> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /** 恶意组是否可用（l2hostility 已安装）。首次确定后缓存；不确定时宁可不隐藏。 */
    private static int maliceState = 0;

    public static boolean maliceLoaded() {
        if (maliceState == 0) {
            try {
                maliceState = net.neoforged.fml.ModList.get().isLoaded("l2hostility") ? 1 : 2;
            } catch (Throwable t) {
                return true;
            }
        }
        return maliceState == 1;
    }

    private static final Map<String, Talent> BY_ID = new LinkedHashMap<>();

    static {
        for (Talent talent : ALL) {
            BY_ID.put(talent.id(), talent);
        }
    }

    private TianfuTalents() {
    }

    public static List<Talent> all() {
        return ALL;
    }

    public static Talent byId(String id) {
        return BY_ID.get(id);
    }

    /**
     * 折算成 {@link TianfuPool.Spec}，并入词条池（购买 / 界面分组全部走既有通道）。
     *
     * <p><b>附属联动过滤</b>：本表里引用附属 buff 的条目（{@code guzhenren_addon:} /
     * {@code hundao_addon:} / {@code yundao:}），若该附属未安装则 {@link #effectOf}
     * 解析为空。此时把整条从池子里摘掉 —— 否则玩家会花点数买到一条零效果的天赋。
     * 玩家已购买但附属后来被移除的情况，{@link #maintainEffects} 会自动跳过（不崩）。
     */
    public static List<TianfuPool.Spec> specs() {
        List<TianfuPool.Spec> out = new ArrayList<>(BASE.size());
        for (Talent talent : BASE) {
            if (!available(talent)) {
                continue;
            }
            out.add(specOf(talent));
        }
        return out;
    }

    /**
     * 恶意联动分页的条目（独立于词条页）。
     *
     * <p>未安装 l2hostility 时返回空表 —— 界面整页为空、也无法购买；
     * 已购买的旧条目仍由 {@link #byIdIncludingUnavailable} 认出来，撤销照常。
     */
    public static List<TianfuPool.Spec> maliceSpecs() {
        if (!maliceLoaded()) {
            return List.of();
        }
        List<TianfuPool.Spec> out = new ArrayList<>(MALICE.size());
        for (Talent talent : MALICE) {
            out.add(specOf(talent));
        }
        return out;
    }

    private static TianfuPool.Spec specOf(Talent talent) {
        return new TianfuPool.Spec(talent.id(), talent.name(), TianfuPool.Kind.ENTRY,
                talent.quality(), talent.cost(), 0, talent.desc());
    }

    /**
     * 按 id 查词条，<b>不</b>做附属过滤。
     *
     * <p>给「已购条目是否有效 / 可否撤销」这类判定用：玩家买了附属联动天赋后
     * 又把附属卸载时，{@link TianfuPool#byId} 查不到它（池子里已摘掉），
     * 但 purchased 里还在、点数也已扣 —— 若一并当作「不存在」，
     * 玩家就会变成点数白扣且无法撤销。故这里从全表查。
     */
    public static TianfuPool.Spec byIdIncludingUnavailable(String id) {
        for (Talent talent : ALL) {
            if (talent.id().equals(id)) {
                return specOf(talent);
            }
        }
        return null;
    }

    /** 该词条在当前环境是否可兑现（所需附属已安装）；未知 id 视为可用（交给别处兜底）。 */
    public static boolean isAvailable(String id) {
        Talent talent = BY_ID.get(id);
        return talent == null || available(talent);
    }

    /**
     * 词条是否「当前环境可兑现」：所需附属已安装。
     *
     * <p>刻意<b>不</b>在这里调用 {@link #effectOf} 做二次校验：本方法会在
     * {@link TianfuPool#ENTRIES} 类加载期被调到，而那时注册表可能尚未填充，
     * 查不到就会把附属天赋永久隐藏。效果 id 的正确性在开发期用 javap 核对
     * （{@code ice_path_boost} / {@code hundao_zengfu} / {@code yizhi_qiankun}
     * 均已确认为已注册的 BENEFICIAL 效果），运行期再由 {@link #maintainEffects}
     * 解析一次 —— 真解析不到只会「不生效」，不会崩，也不会隐藏。
     */
    private static boolean available(Talent talent) {
        for (Fx fx : talent.fx()) {
            if (fx.op() == Op.MOB_EFFECT && !modLoadedFor(fx.arg())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 该效果所属的命名空间是否已安装。
     *
     * <p>用「mod 是否加载」而不是「注册表里查不到就隐藏」：{@link TianfuPool#ENTRIES}
     * 是 {@code static final}，类加载时就会调到 {@link #specs()}，而那一刻注册表可能
     * 还没填充完 —— 只靠注册表判断会把附属天赋永久过滤掉。
     * mod 列表在类加载时一定已就绪，且与原版/本体命名空间无关（那两种一律视为可用）。
     */
    private static boolean modLoadedFor(String name) {
        if (name == null || name.indexOf(':') < 0) {
            return true;   // 原版效果、或裸名字（本体 guzhenren）→ 交给 effectOf 判断
        }
        String ns = name.substring(0, name.indexOf(':'));
        if ("minecraft".equals(ns) || "guzhenren".equals(ns)) {
            return true;
        }
        try {
            return net.neoforged.fml.ModList.get().isLoaded(ns);
        } catch (Throwable t) {
            return true;   // 加载期异常时宁可显示，避免误隐藏
        }
    }

    // ------------------------------------------------------------------ 引擎：属性类

    /** 汇总已购词条的属性类原语（供 {@link TianfuAttributes} 幂等套用）。 */
    public static Attrs attrs(Set<String> ids) {
        double health = 1.0;
        double attack = 1.0;
        double speed = 1.0;
        double attackSpeed = 1.0;
        double jump = 0.0;
        double knockback = 0.0;
        double fall = 0.0;
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                switch (fx.op()) {
                    case HEALTH_MULT -> health *= 1.0 + fx.a();
                    case ATTACK_MULT -> attack *= 1.0 + fx.a();
                    case SPEED_MULT -> speed *= 1.0 + fx.a();
                    case ATTACK_SPEED_MULT -> attackSpeed *= 1.0 + fx.a();
                    case JUMP_FLAT -> jump += fx.a();
                    case KNOCKBACK_FLAT -> knockback += fx.a();
                    case FALL_FLAT -> fall += fx.a();
                    default -> {
                    }
                }
            }
        }
        return new Attrs(health, attack, speed, attackSpeed, jump, knockback, fall);
    }

    // ------------------------------------------------------------------ 引擎：一次性本体字段

    /** 购买时一次性写入本体 {@code PlayerVariables}（上限类 / 道痕类）。 */
    public static void applyOnce(ServerPlayer player, PlayerVariables v, Set<String> ids) {
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                switch (fx.op()) {
                    case FIELD_MULT -> multiply(v, fx.arg(), 1.0 + fx.a());
                    case FIELD_ADD -> add(v, fx.arg(), fx.a());
                    case DAOHEN -> addPaired(v, fx.arg(), fx.a());
                    case DAOHEN_ALL -> allDaohen(v, fx.a());
                    default -> {
                    }
                }
            }
        }
    }

    /** 逐个道痕字段累加（跳过 {@code daohen_zong} 总道痕，避免与本体统计双计）。 */
    private static void allDaohen(PlayerVariables v, double amount) {
        for (Field field : daohenFields()) {
            addPaired(v, field.getName(), amount);
        }
    }

    /**
     * 道痕「成对写」：{@code daohen_*}/{@code dahen_*} 加值，对应流派权重加 1/10
     * （本体十绝体即 daohen 2000 / liupai 200）。
     *
     * <p>本体骨道权重字段名首字母大写（{@code Liupai_gudao}），故两个写法都试。
     */
    private static void addPaired(PlayerVariables v, String daohenName, double amount) {
        add(v, daohenName, amount);
        int cut = daohenName.indexOf('_');
        if (cut < 0) {
            return;
        }
        String suffix = daohenName.substring(cut + 1);
        double share = amount / 10.0;
        if (!tryAdd(v, "liupai_" + suffix, share)) {
            tryAdd(v, "Liupai_" + suffix, share);
        }
    }

    private static Field[] daohenCache;

    private static Field[] daohenFields() {
        Field[] cached = daohenCache;
        if (cached == null) {
            List<Field> out = new ArrayList<>();
            for (Field field : PlayerVariables.class.getFields()) {
                String name = field.getName();
                if ((name.startsWith("daohen_") || name.startsWith("dahen_"))
                        && !name.equals("daohen_zong") && field.getType() == double.class) {
                    out.add(field);
                }
            }
            daohenCache = cached = out.toArray(new Field[0]);
        }
        return cached;
    }

    // ------------------------------------------------------------------ 引擎：周期类（tick）

    /** 真元回复加成（叠加到 {@link TianfuEffects} 的每 tick 回复比例）。 */
    public static double regenZy(Set<String> ids) {
        return sum(ids, Op.REGEN_ZY);
    }

    /** 念头回复加成。 */
    public static double regenNt(Set<String> ids) {
        return sum(ids, Op.REGEN_NT);
    }

    /** 每 5 秒生命回复比例（0.05 = 5%）。 */
    public static double regenHp5s(Set<String> ids) {
        return sum(ids, Op.REGEN_HP_5S);
    }

    /** 打坐修炼进度加成。 */
    public static double xiulian(Set<String> ids) {
        return sum(ids, Op.XIULIAN);
    }

    /** 突破瓶颈减免（按本体既有口径折半计入修炼进度）。 */
    public static double breakthrough(Set<String> ids) {
        return sum(ids, Op.BREAKTHROUGH);
    }

    /** 炼蛊进度加成。 */
    public static double liangu(Set<String> ids) {
        return sum(ids, Op.LIANGU);
    }

    /** 每 tick 蛊冷却削减量。 */
    public static double guCd(Set<String> ids) {
        return sum(ids, Op.GU_CD);
    }

    /** 每 tick 杀招冷却削减量。 */
    public static double shazhaoCd(Set<String> ids) {
        return sum(ids, Op.SHAZHAO_CD);
    }

    // ------------------------------------------------------------------ 引擎：战斗乘区

    public static double critChance(Set<String> ids) {
        return sum(ids, Op.CRIT_CHANCE);
    }

    public static double critDamage(Set<String> ids) {
        return sum(ids, Op.CRIT_DAMAGE);
    }

    public static double dodge(Set<String> ids) {
        return sum(ids, Op.DODGE);
    }

    public static double leech(Set<String> ids) {
        return sum(ids, Op.LEECH);
    }

    public static double reduction(Set<String> ids) {
        return sum(ids, Op.REDUCTION);
    }

    /** 恶意·摄魂：吃护甲伤害的减伤。 */
    public static double physicalReduction(Set<String> ids) {
        return sum(ids, Op.REDUCTION_PHYS);
    }

    /** 恶意·破魔：穿护甲伤害的减伤。 */
    public static double magicReduction(Set<String> ids) {
        return sum(ids, Op.REDUCTION_MAGIC);
    }

    /** 恶意·月步：摔落伤害减免。 */
    public static double fallReduce(Set<String> ids) {
        return sum(ids, Op.FALL_REDUCE);
    }

    /** 恶意·适应：每次受同类伤害的减伤（无则 0）。 */
    public static double adaptivePer(Set<String> ids) {
        return first(ids, Op.ADAPTIVE, 0);
    }

    /** 恶意·适应：同类伤害最多叠几层（无则 0）。 */
    public static int adaptiveCap(Set<String> ids) {
        return (int) Math.round(first(ids, Op.ADAPTIVE, 1));
    }

    /** 恶意·反击：被击中后的出伤加成（无则 0）。 */
    public static double counterBonus(Set<String> ids) {
        return first(ids, Op.COUNTER, 0);
    }

    /** 恶意·反击：加成持续时间（tick）。 */
    public static int counterTicks(Set<String> ids) {
        return (int) Math.round(first(ids, Op.COUNTER, 1));
    }

    /** 恶意·不死：{回复比例, 冷却 tick}；无则 null。 */
    public static double[] undying(Set<String> ids) {
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() == Op.UNDYING) {
                    return new double[] { fx.a(), fx.b() };
                }
            }
        }
        return null;
    }

    /** 恶意·杀戮光环：{半径, 攻击力比例}；无则 null。 */
    public static double[] aura(Set<String> ids) {
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() == Op.AURA) {
                    return new double[] { fx.a(), fx.b() };
                }
            }
        }
        return null;
    }

    /** 恶意·出伤附带效果（毒/凋零/缓慢/虚弱/致盲）。 */
    public static List<Fx> onHitEffects(Set<String> ids) {
        List<Fx> out = new ArrayList<>();
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() == Op.ON_HIT_EFFECT) {
                    out.add(fx);
                }
            }
        }
        return out;
    }

    /** 取某原语的第一个值（同 op 取最大，避免叠加失控）。 */
    private static double first(Set<String> ids, Op op, int which) {
        double best = 0.0;
        boolean found = false;
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() != op) {
                    continue;
                }
                double raw = which == 0 ? fx.a() : fx.b();
                if (!found || Math.abs(raw) > Math.abs(best)) {
                    best = raw;
                    found = true;
                }
            }
        }
        return found ? best : 0.0;
    }

    /** 满血出伤加成。 */
    public static double outFull(Set<String> ids) {
        return sum(ids, Op.OUT_FULL);
    }

    /** 残血出伤加成。 */
    public static double outLow(Set<String> ids) {
        return sum(ids, Op.OUT_LOW);
    }

    /**
     * 反伤候选：取「概率 × 比例」最强者（避免多条词条的概率与比例各自求和导致数值爆炸）。
     *
     * @return {概率, 比例}；无可用条目时返回 null
     */
    public static double[] bestReflect(Set<String> ids) {
        double chance = 0.0;
        double ratio = 0.0;
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() == Op.REFLECT && fx.a() * fx.b() > chance * ratio) {
                    chance = fx.a();
                    ratio = fx.b();
                }
            }
        }
        return chance > 0.0 ? new double[] { chance, ratio } : null;
    }

    private static double sum(Set<String> ids, Op op) {
        double total = 0.0;
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() == op) {
                    total += fx.a();
                }
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ 引擎：常驻药水效果

    /** 常驻效果维护：缺失或即将到期时补一次无限时长（隐藏粒子）。 */
    public static void maintainEffects(ServerPlayer player, Set<String> ids) {
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() != Op.MOB_EFFECT) {
                    continue;
                }
                Holder<MobEffect> effect = effectOf(fx.arg());
                if (effect == null) {
                    continue;
                }
                MobEffectInstance existing = player.getEffect(effect);
                // 无限时长的 duration 是 -1，直接写 < 600 会恒真 → 每 tick 重复 addEffect。
                // 判定基准取「上次实际授予的等级」而非 fx.a()：授予时可能因玩家原有更高等级
                // 而抬高，用 fx.a() 比会永远判为不足 → 每 tick 重挂 + 每 tick 写一次数据。
                int grantedAmp = (int) TianfuData.getDouble(player, AMP_KEY + fx.arg(), fx.a());
                boolean inPlace = existing != null
                        && existing.getDuration() == -1
                        && existing.getAmplifier() == grantedAmp;
                if (!inPlace) {
                    // 玩家身上若已有更高等级的限时版本（自己吃蛊 / 喝药），
                    // 保留其等级再转常驻，避免「买天赋反而把自己 buff 降级」。
                    int amp = Math.max(grantedAmp,
                            existing == null ? 0 : existing.getAmplifier());
                    player.addEffect(new MobEffectInstance(effect, -1, amp, true, false));
                    // 记下「本模组实际授予的等级」，撤销时据此精确比对：
                    // 既不误删玩家自己 /effect 给的无限 buff，也不会因等级抬高而残留。
                    if (amp != grantedAmp) {
                        TianfuData.putDouble(player, AMP_KEY + fx.arg(), amp);
                    }
                }
            }
        }
    }

    /**
     * 撤销词条时回收本模组授予的常驻药水。
     *
     * <p>只移除「无限时长<b>且等级与本表一致</b>」的那份：
     * <ul>
     *   <li>限时药水（玩家自己吃蛊 / 喝药）不动；</li>
     *   <li>{@code /effect give @s speed infinite} 这类无限时长但等级不同的也不动；</li>
     *   <li>其它仍生效词条需要保留的同名效果不动（先收集 keep 集合）。</li>
     * </ul>
     */
    public static void revokeEffects(ServerPlayer player, Set<String> remainingIds) {
        Set<String> keep = new java.util.HashSet<>();
        for (Talent talent : owned(remainingIds)) {
            for (Fx fx : talent.fx()) {
                if (fx.op() == Op.MOB_EFFECT && fx.arg() != null) {
                    keep.add(fx.arg());
                }
            }
        }
        // 需要检查的候选：全部词条表里出现过的 MOB_EFFECT 名
        for (Talent talent : ALL) {
            for (Fx fx : talent.fx()) {
                if (fx.op() != Op.MOB_EFFECT || fx.arg() == null || keep.contains(fx.arg())) {
                    continue;
                }
                Holder<MobEffect> effect = effectOf(fx.arg());
                if (effect == null) {
                    continue;
                }
                MobEffectInstance existing = player.getEffect(effect);
                // 比对「授予时记录下来的等级」，而不是词条表里的 fx.a()：
                // 若玩家原本带着更高等级的限时 buff，本模组会抬等级转常驻，
                // 用 fx.a() 比对就会删不掉，导致撤销天赋后 buff 永久残留。
                double granted = TianfuData.getDouble(player, AMP_KEY + fx.arg(), fx.a());
                if (existing != null && existing.getDuration() == -1
                        && existing.getAmplifier() == (int) granted) {
                    player.removeEffect(effect);
                }
            }
        }
    }

    /**
     * 效果注册名 → 效果句柄。
     *
     * <p>先查原版 13 种常用效果（走静态字段，避免每次注册表查找）；
     * 未命中则回退到「按 {@link net.minecraft.resources.ResourceLocation} 在注册表里查」，
     * 这样可以引用<b>本体与整合包附属注册的 buff</b>（如 {@code jiandaoshanghaizengfu}
     * 「剑道伤害增幅」、{@code hundao_zengfu}「魂道增幅」、{@code zhoudaobuff}「宙侵」）。
     *
     * <p>注意：附属的效果 id 都挂在各自命名空间下（{@code guzhenren_addon:}、
     * {@code hundao_addon:}），故本方法接受 {@code namespace:path} 完整写法；
     * 也接受裸名字并默认按 {@code guzhenren} 命名空间解析（兼容本体 buff）。
     *
     * <p>解析失败返回 {@code null}，调用方一律跳过 —— 名字写错只会「不生效」，不会崩服。
     */
    /** 供战斗层解析「出伤附带」的效果（失败返回 null，不崩）。 */
    public static Holder<MobEffect> effectHolder(String name) {
        return effectOf(name);
    }

    private static Holder<MobEffect> effectOf(String name) {
        Holder<MobEffect> vanilla = switch (name) {
            case "resistance" -> MobEffects.DAMAGE_RESISTANCE;
            case "night_vision" -> MobEffects.NIGHT_VISION;
            case "fire_resistance" -> MobEffects.FIRE_RESISTANCE;
            case "water_breathing" -> MobEffects.WATER_BREATHING;
            case "speed" -> MobEffects.MOVEMENT_SPEED;
            case "jump_boost" -> MobEffects.JUMP;
            case "strength" -> MobEffects.DAMAGE_BOOST;
            case "regeneration" -> MobEffects.REGENERATION;
            case "dolphins_grace" -> MobEffects.DOLPHINS_GRACE;
            case "slow_falling" -> MobEffects.SLOW_FALLING;
            case "luck" -> MobEffects.LUCK;
            case "saturation" -> MobEffects.SATURATION;
            case "haste" -> MobEffects.DIG_SPEED;
            default -> null;
        };
        if (vanilla != null) {
            return vanilla;
        }
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            net.minecraft.resources.ResourceLocation id = name.indexOf(':') >= 0
                    ? net.minecraft.resources.ResourceLocation.parse(name)
                    // 裸名字按本体命名空间解析：本体的流派 buff 都在 guzhenren 下
                    : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("guzhenren", name);
            // 必须取注册表里的「真实 holder」：Holder.direct 会造出身份不同的新 holder，
            // 那样 player.getEffect(holder) 查不到已有实例，撤销时也删不掉。
            return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getHolder(id)
                    .map(h -> (Holder<MobEffect>) h)
                    .orElse(null);
        } catch (Exception e) {
            // 非法 id（含空格 / 大写 / 拼写错）一律当作不存在
            return null;
        }
    }

    // ------------------------------------------------------------------ 工具

    private static List<Talent> owned(Set<String> ids) {
        List<Talent> out = new ArrayList<>();
        for (String id : ids) {
            Talent talent = BY_ID.get(id);
            if (talent != null) {
                out.add(talent);
            }
        }
        return out;
    }

    /** 反射读写本体字段（字段名已实测存在；单字段失败静默跳过，不影响其余条目）。 */
    private static void add(PlayerVariables v, String field, double delta) {
        tryAdd(v, field, delta);
    }

    /** @return 是否写入成功（字段不存在返回 false，供「大小写两种写法」回退用） */
    private static boolean tryAdd(PlayerVariables v, String field, double delta) {
        if (delta == 0.0) {
            return false;
        }
        try {
            Field f = PlayerVariables.class.getField(field);
            double next = f.getDouble(v) + delta;
            // 气运上限兜底非负：本体每秒 `if (cap < qiyun) qiyun = cap`，
            // 上限为负会把气运硬压成负数（气运稀薄 −15 叠加低上限机缘时必然发生）。
            if ("qiyun_shangxian".equals(field) && next < 0.0) {
                next = 0.0;
            }
            f.setDouble(v, next);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void multiply(PlayerVariables v, String field, double factor) {
        if (factor == 1.0) {
            return;
        }
        try {
            Field f = PlayerVariables.class.getField(field);
            f.setDouble(v, f.getDouble(v) * factor);
        } catch (ReflectiveOperationException ignored) {
            // 同上
        }
    }

    // ------------------------------------------------------------------ 撤销反向回滚（与 applyOnce 精确对称）

    /** 与 {@link #applyOnce} 反向：把本模组当时加/乘的字段精确回滚。 */
    public static void revokeOnce(ServerPlayer player, PlayerVariables v, Set<String> ids) {
        for (Talent talent : owned(ids)) {
            for (Fx fx : talent.fx()) {
                switch (fx.op()) {
                    case FIELD_MULT -> divide(v, fx.arg(), 1.0 + fx.a());
                    case FIELD_ADD -> subtract(v, fx.arg(), fx.a());
                    case DAOHEN -> subtractPaired(v, fx.arg(), fx.a());
                    case DAOHEN_ALL -> allDaohen(v, -fx.a());
                    default -> {
                    }
                }
            }
        }
        // 常驻药水回收：本次撤销的 ids 已被移出 purchased（调用方先落库再回滚），
        // 故「仍需保留」= 当前 purchased 里所有词条提供的 MOB_EFFECT 名。
        revokeEffects(player, TianfuApply.sanitize(TianfuData.purchased(player)));
    }

    private static void divide(PlayerVariables v, String field, double divisor) {
        if (divisor == 1.0) {
            return;
        }
        try {
            Field f = PlayerVariables.class.getField(field);
            double cur = f.getDouble(v);
            if (Math.abs(cur) < 1e-6) {
                return; // 已经是 0，不做除以零
            }
            f.setDouble(v, cur / divisor);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void subtract(PlayerVariables v, String field, double amount) {
        tryAdd(v, field, -amount);
    }

    /** addPaired 的反向：daohen 减 amount，liupai 减 amount/10（失败静默跳过）。 */
    private static void subtractPaired(PlayerVariables v, String daohenName, double amount) {
        add(v, daohenName, -amount);
        int cut = daohenName.indexOf('_');
        if (cut < 0) {
            return;
        }
        String suffix = daohenName.substring(cut + 1);
        double share = -amount / 10.0;
        if (!tryAdd(v, "liupai_" + suffix, share)) {
            tryAdd(v, "Liupai_" + suffix, share);
        }
    }
}
