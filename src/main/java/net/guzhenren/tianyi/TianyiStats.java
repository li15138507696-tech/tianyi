package net.guzhenren.tianyi;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 系统面板的「属性」组装层 —— 把本体的只读状态整理成一份可直接显示的标签。
 *
 * <h2>字段来源（全部实测，不是猜）</h2>
 * 用 {@code javap} 导出 {@code GuzhenrenModVariables$PlayerVariables} 的 319 个字段后挑选：
 * <pre>
 *   三资源   zhenyuan/zuida_zhenyuan · jingli/zuida_jingli · niantou/niantou_rongliang
 *   修为     zhuanshu（转数） · jieduan（阶段） · gushi_xiulian_jindu（修炼进度）
 *   生存     shouyuan（寿元） · yali（十绝体压力） · daode（道德） · renqi（人气）
 *   资质     tizhi（资质） · kongqiao（空窍） · qiyun/qiyun_shangxian（气运）
 *   战       gongjili（攻击） · fangyuli（防御） · 生命值用 MC 自己的 getHealth/getMaxHealth
 *   魂       hunpo/zuida_hunpo · hunpochengzaijixian（承载上限）
 *   经济     yuanshishuliang（元石） · GongXianZhi（贡献）
 *   流派     liupai_… 与 daohen_… 共 44 组，这里只取道痕前 3 高与总数
 * </pre>
 *
 * <h2>为什么不画「修炼进度条」</h2>
 * 本体没有暴露「突破所需进度」的阈值（{@code gushi_xiulian_jindu} 只是一个累计值，
 * {@code gushi_xiulian_dangqian} 的语义未在知识库里定义），所以只显示数字。
 * <b>宁可少画一条条，也不编一个假比例。</b>
 */
public final class TianyiStats {
    /** 只挑常见的 44 个道痕字段里，用于「前 3 高」的那些（键名 + 中文名）。 */
    private static final String[][] DAOHEN = {
            {"daohen_lidao", "力道"}, {"daohen_jiandao", "剑道"}, {"daohen_hundao", "魂道"},
            {"daohen_xuedao", "血道"}, {"daohen_yuedao", "月道"}, {"daohen_yudao", "宇道"},
            {"dahen_zhoudao", "宙道"}, {"dahen_qidao", "气道"}, {"dahen_liandao", "炼道"},
            {"daohen_shuidao", "水道"}, {"daohen_lvdao", "律道"}, {"daohen_toudao", "偷道"},
            {"dahen_zhidao", "智道"}, {"daohen_jindao", "金道"}, {"daohen_mudao", "木道"},
            {"daohen_yandao", "炎道"}, {"daohen_tudao", "土道"}, {"daohen_leidao", "雷道"},
            {"daohen_bianhuadao", "变化道"}, {"daohen_guangdao", "光道"}, {"daohen_fengdao", "风道"},
            {"daohen_bingdao", "骨道"}, {"daohen_bingxuedao", "冰雪道"}, {"dahen_nudao", "奴道"},
            {"dahen_xingdao", "星道"}, {"daohen_dudao", "毒道"}, {"daohen_dandao", "丹道"},
    };

    private TianyiStats() {
    }

    /** 组装属性标签（合并进面板状态）。 */
    public static CompoundTag build(ServerPlayer player) {
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        CompoundTag t = new CompoundTag();

        // 三资源（知识库 1.1）
        t.putDouble("zy", v.zhenyuan);
        t.putDouble("zyMax", v.zuida_zhenyuan);
        t.putDouble("jl", v.jingli);
        t.putDouble("jlMax", v.zuida_jingli);
        t.putDouble("nt", v.niantou);
        t.putDouble("ntMax", v.niantou_rongliang);

        // 修为与生存
        t.putInt("zhuan", (int) Math.round(v.zhuanshu));
        t.putInt("jieduan", (int) Math.round(v.jieduan));
        t.putDouble("xiulian", v.gushi_xiulian_jindu);
        t.putDouble("shouyuan", v.shouyuan);
        t.putDouble("yali", v.yali);
        t.putDouble("daode", v.daode);
        t.putDouble("renqi", v.renqi);

        // 资质 / 气运 / 空窍
        t.putDouble("tizhi", v.tizhi);
        t.putDouble("kongqiao", v.kongqiao);
        t.putDouble("qiyun", v.qiyun);
        t.putDouble("qiyunCap", v.qiyun_shangxian);

        // 战斗与魂魄
        t.putDouble("hp", player.getHealth());
        t.putDouble("hpMax", player.getMaxHealth());
        t.putDouble("atk", v.gongjili);
        t.putDouble("def", v.fangyuli);
        t.putDouble("hunpo", v.hunpo);
        t.putDouble("hunpoMax", v.zuida_hunpo);
        t.putDouble("hunpoCap", v.hunpochengzaijixian);

        // 经济
        t.putDouble("yuanshi", v.yuanshishuliang);
        t.putDouble("gongxian", v.GongXianZhi);

        // 道痕：只取前 3 高 + 总数（44 个全列会挤爆面板）
        double[] top = {0, 0, 0};
        String[] topName = {"", "", ""};
        int count = 0;
        double sum = 0;
        for (String[] pair : DAOHEN) {
            double val = read(v, pair[0]);
            if (val <= 0) {
                continue;
            }
            count++;
            sum += val;
            for (int i = 0; i < 3; i++) {
                if (val > top[i]) {
                    for (int j = 2; j > i; j--) {
                        top[j] = top[j - 1];
                        topName[j] = topName[j - 1];
                    }
                    top[i] = val;
                    topName[i] = pair[1];
                    break;
                }
            }
        }
        t.putInt("daohenCount", count);
        t.putDouble("daohenSum", sum);
        for (int i = 0; i < 3; i++) {
            t.putString("daohenName" + i, topName[i]);
            t.putDouble("daohenVal" + i, top[i]);
        }
        return t;
    }

    /** 用反射读一个 {@code PlayerVariables} 字段（字段名多、版本间可能增删，避免硬编码 getter）。 */
    private static double read(PlayerVariables v, String field) {
        try {
            var f = PlayerVariables.class.getField(field);
            Object o = f.get(v);
            return o instanceof Number n ? n.doubleValue() : 0.0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0;
        }
    }

    /**
     * 气运等级名（知识库 15.4）。
     *
     * <p>正区间有明确分界：1~20 时来运转 / 20~40 天随人愿 / 40~60 时运亨通 /
     * 60~80 吉星高照 / 80~100 鸿运齐天；0 为「无」。
     * 负区间的<b>具体分界知识库没给</b>，所以只给一个「凶」字，不编档位。
     */
    public static String qiyunName(double q) {
        if (q <= 0) {
            return "无";
        }
        if (q < 20) {
            return "时来运转";
        }
        if (q < 40) {
            return "天随人愿";
        }
        if (q < 60) {
            return "时运亨通";
        }
        if (q < 80) {
            return "吉星高照";
        }
        return "鸿运齐天";
    }
}
