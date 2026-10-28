package net.guzhenren.tianyi.will;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 蛊兽名录的离线行为测试。
 *
 * <p>锁的是「名录本身是否自洽」：id 唯一且全是 {@code guzhenren:} 命名空间、
 * 阶数落在 1–5、每阶都有货、流派分类正确、查询接口与数据一致。
 *
 * <p><b>名录里的 id 是否真实存在于本体 jar</b> 不在这里验 —— 那需要读 jar，
 * 由 {@code _pack/audit_bestiary.py} 对本体 lang 表逐条核对。
 */
public final class BeastCatalogTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testShape();
        testTiers();
        testDao();
        testQueries();
        testTierNames();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：本套件只验名录自洽；id 是否真实存在由 audit_bestiary.py 对本体 jar 核验。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void testShape() {
        check("名录有内容", BeastCatalog.size() >= 70);
        Set<String> ids = new HashSet<>();
        boolean dup = false;
        boolean namespaced = true;
        boolean hasName = true;
        for (var e : BeastCatalog.all()) {
            if (!ids.add(e.id())) {
                dup = true;
            }
            if (!e.id().startsWith("guzhenren:")) {
                namespaced = false;
            }
            if (e.name().isEmpty()) {
                hasName = false;
            }
        }
        check("id 不重复", !dup);
        check("id 都是 guzhenren 命名空间", namespaced);
        check("每条都有中文名", hasName);
        check("MAX_TIER 是 5", BeastCatalog.MAX_TIER == 5);
    }

    private static void testTiers() {
        int sum = 0;
        for (int t = 1; t <= BeastCatalog.MAX_TIER; t++) {
            int n = BeastCatalog.byTier(t).size();
            sum += n;
            check(t + " 阶有货（" + n + " 只）", n > 0);
        }
        check("各阶数量之和等于总数", sum == BeastCatalog.size());

        boolean legal = true;
        for (var e : BeastCatalog.all()) {
            if (e.tier() < 1 || e.tier() > 5) {
                legal = false;
            }
        }
        check("所有阶数都在 1–5", legal);

        // 兽皇档必须是 5 阶
        check("魂兽·兽皇是 5 阶",
                BeastCatalog.tierOf("guzhenren:hunshou") == 5);
        check("不灭玄武是 5 阶",
                BeastCatalog.tierOf("guzhenren:bumiexuanwu") == 5);
        // 特殊种是 1 阶
        check("木魅树精是 1 阶",
                BeastCatalog.tierOf("guzhenren:mumeishujing_1") == 1);
        check("豹是 1 阶", BeastCatalog.tierOf("guzhenren:bao") == 1);
    }

    private static void testDao() {
        // 抽样核对：知识库 6.2 表格里几个标志性对应关系
        check("豪电狼属雷道", BeastCatalog.daoOf("guzhenren:hao_dian_lang")
                == BeastCatalog.Dao.LEI);
        check("虎属金道", BeastCatalog.daoOf("guzhenren:hu") == BeastCatalog.Dao.JIN);
        check("碧水羚属水道", BeastCatalog.daoOf("guzhenren:bishuiling")
                == BeastCatalog.Dao.SHUI);
        check("刻纹蛇属宙道", BeastCatalog.daoOf("guzhenren:ke_wen_she")
                == BeastCatalog.Dao.ZHOU);
        check("魂兽属魂道", BeastCatalog.daoOf("guzhenren:hunshou")
                == BeastCatalog.Dao.HUN);
        check("冰鳞鲨属冰雪道", BeastCatalog.daoOf("guzhenren:bing_lin_sha")
                == BeastCatalog.Dao.BING);

        // 每个流派都至少有一只
        for (BeastCatalog.Dao d : BeastCatalog.Dao.values()) {
            if (d == BeastCatalog.Dao.NONE) {
                continue;
            }
            check("流派「" + d.cn + "」有货", !BeastCatalog.byDao(d).isEmpty());
        }

        // 名录的四档（2–5 阶）里，雷道每档各一只 —— 特殊种「电狼」也是雷道，所以总数会多 1
        int leiRanked = 0;
        for (int t = 2; t <= 5; t++) {
            leiRanked += BeastCatalog.byTierAndDao(t, BeastCatalog.Dao.LEI).size();
        }
        check("雷道四档齐全（百兽/千兽/万兽/兽皇各一）", leiRanked == 4);
        check("雷道另有特殊种电狼",
                BeastCatalog.byDao(BeastCatalog.Dao.LEI).size() == 5);
        // 冰雪道在名录里没有兽皇一档
        check("冰雪道没有兽皇档",
                BeastCatalog.byTierAndDao(5, BeastCatalog.Dao.BING).isEmpty());
        check("冰雪道有 2/3/4 三档",
                !BeastCatalog.byTierAndDao(2, BeastCatalog.Dao.BING).isEmpty()
                        && !BeastCatalog.byTierAndDao(3, BeastCatalog.Dao.BING).isEmpty()
                        && !BeastCatalog.byTierAndDao(4, BeastCatalog.Dao.BING).isEmpty());
    }

    private static void testQueries() {
        check("查不到返回 0 阶", BeastCatalog.tierOf("guzhenren:no_such_beast") == 0);
        check("查不到返回无流派",
                BeastCatalog.daoOf("guzhenren:no_such_beast") == BeastCatalog.Dao.NONE);
        check("查不到名字为空", BeastCatalog.nameOf("guzhenren:no_such_beast").isEmpty());
        check("名字查得到", "豪电狼".equals(BeastCatalog.nameOf("guzhenren:hao_dian_lang")));

        List<BeastCatalog.Entry> le4 = BeastCatalog.byTierAndDao(4, BeastCatalog.Dao.LEI);
        check("4 阶雷道只有雷冠头狼", le4.size() == 1
                && "guzhenren:lei_guan_tou_lang".equals(le4.get(0).id()));
        check("不存在的组合返回空",
                BeastCatalog.byTierAndDao(1, BeastCatalog.Dao.TOU).isEmpty());

        check("摘要可读", !BeastCatalog.summary().isEmpty()
                && BeastCatalog.summary().contains("只"));
    }

    private static void testTierNames() {
        check("1 阶叫普通蛊兽", "普通蛊兽".equals(BeastCatalog.tierName(1)));
        check("2 阶叫百兽", "百兽".equals(BeastCatalog.tierName(2)));
        check("3 阶叫千兽", "千兽".equals(BeastCatalog.tierName(3)));
        check("4 阶叫万兽", "万兽".equals(BeastCatalog.tierName(4)));
        check("5 阶叫兽皇", "兽皇".equals(BeastCatalog.tierName(5)));
        check("越界返回兜底", "蛊兽".equals(BeastCatalog.tierName(9)));
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name);
        }
    }

    private BeastCatalogTest() {
    }
}
