package net.guzhenren.tianyi.resource;

/**
 * {@link SiteBudget} 的离线确定性测试：预算、上限、区域、生长计时、守护兽不补生。
 *
 * <p>这些判断是有限资源点服务的“决策层”。真正读写世界的那部分在
 * {@code world/FiniteSiteService}，它 import 了 Minecraft 类型，跑不了纯 javac，
 * 只能靠 {@code _pack/audit_sites.py} 做静态接线审计 + 进游戏实机验证。
 *
 * <p>运行：{@code python _pack/probes/run_site_service.py}
 * （或由 D 登记进 {@code _pack/run_tests.py} 的 SUITES）。
 */
public final class SiteBudgetTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testCheckCadence();
        testGlobalAndRegionLimits();
        testPerCheckBudgets();
        testGrowthAccrual();
        testGuardianNeverRespawn();
        testNeedsSlot();
        testUnits();
        testRegionKey();
        testConstantsMatchSpec();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：只验证纯决策；世界读写、真实区块/容器/实体未在此验证（未实机）。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    /** 每 200 刻一轮；0 刻（刚开服）不跑，避免开服瞬间扎堆。 */
    private static void testCheckCadence() {
        check("200 刻该检查", SiteBudget.due(200));
        check("400 刻该检查", SiteBudget.due(400));
        check("199 刻不检查", !SiteBudget.due(199));
        check("201 刻不检查", !SiteBudget.due(201));
        check("0 刻不检查", !SiteBudget.due(0));
        check("负数刻不检查", !SiteBudget.due(-200));
    }

    /** 全局 64、8×8 区块 1 点。 */
    private static void testGlobalAndRegionLimits() {
        check("0 个点还有空位", SiteBudget.hasRoom(0));
        check("63 个点还有空位", SiteBudget.hasRoom(63));
        check("64 个点已满", !SiteBudget.hasRoom(64));
        check("超过 64 已满", !SiteBudget.hasRoom(65));
        check("负数视作满（保守）", !SiteBudget.hasRoom(-1));
        check("区域没被占可以放", SiteBudget.regionAvailable(true));
        check("区域已被占不能放", !SiteBudget.regionAvailable(false));
    }

    /** 每轮最多 4 候选、落地 1 点。 */
    private static void testPerCheckBudgets() {
        check("还没看候选", SiteBudget.candidatesLeft(0));
        check("看过 3 个还能看", SiteBudget.candidatesLeft(3));
        check("看过 4 个就到顶", !SiteBudget.candidatesLeft(4));
        check("还没落地", SiteBudget.placementsLeft(0));
        check("落地 1 个就到顶", !SiteBudget.placementsLeft(1));
    }

    /** 只有已加载 + 环境合法 + 根基完好 + 有空槽位才累计时间。 */
    private static void testGrowthAccrual() {
        check("条件齐全才计时", SiteBudget.mayAccrueGrowth(true, true, true, true));
        check("未加载不计时", !SiteBudget.mayAccrueGrowth(false, true, true, true));
        check("环境不合法不计时", !SiteBudget.mayAccrueGrowth(true, false, true, true));
        check("根基断了不计时", !SiteBudget.mayAccrueGrowth(true, true, false, true));
        check("没有空槽位不计时", !SiteBudget.mayAccrueGrowth(true, true, true, false));
        check("全不满足更不计时", !SiteBudget.mayAccrueGrowth(false, false, false, false));
    }

    /** 守护兽永不补生：已绑定就是不许，不管它是活着、死了还是没加载。 */
    private static void testGuardianNeverRespawn() {
        check("还没绑定时才允许首次生成", SiteBudget.maySpawnGuardian(false));
        check("已绑定（可能活着/死了/未加载）一律不许再生成", !SiteBudget.maySpawnGuardian(true));
    }

    /** 没物化过的槽位要物化；植物挂账增长也要一个槽位。 */
    private static void testNeedsSlot() {
        check("未物化需要槽位", SiteBudget.needsSlot(false, false));
        check("已物化且无挂账不需要", !SiteBudget.needsSlot(true, false));
        check("已物化但有挂账增长需要", SiteBudget.needsSlot(true, true));
    }

    /** 初始量落在模板区间内，越界的 roll 也不越界。 */
    private static void testUnits() {
        check("roll 0 取下限", SiteBudget.units(4, 8, 0) == 4);
        check("roll 4 取上限", SiteBudget.units(4, 8, 4) == 8);
        check("roll 2 取中间", SiteBudget.units(4, 8, 2) == 6);
        check("超大 roll 取模收敛", SiteBudget.units(3, 6, 999) >= 3 && SiteBudget.units(3, 6, 999) <= 6);
        check("负 roll 也不越界", SiteBudget.units(3, 6, -7) >= 3 && SiteBudget.units(3, 6, -7) <= 6);
        boolean rejected = false;
        try {
            SiteBudget.units(5, 4, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("反区间被拒绝", rejected);
    }

    /** 生成记号与 SitePlacement 写进 regionMarker 的格式完全一致。 */
    private static void testRegionKey() {
        check("原点区域", "minecraft:overworld:0:0".equals(SiteBudget.regionKey("minecraft:overworld", 0, 0)));
        check("同一 8×8 区块同记号", SiteBudget.regionKey("minecraft:overworld", 0, 0)
                .equals(SiteBudget.regionKey("minecraft:overworld", 127, 127)));
        check("跨一个区块换记号", !SiteBudget.regionKey("minecraft:overworld", 0, 0)
                .equals(SiteBudget.regionKey("minecraft:overworld", 128, 0)));
        check("负坐标按 floorDiv 收敛", "minecraft:overworld:-1:0".equals(
                SiteBudget.regionKey("minecraft:overworld", -1, 0)));
        check("维度是记号的一部分", !SiteBudget.regionKey("minecraft:overworld", 0, 0)
                .equals(SiteBudget.regionKey("minecraft:the_nether", 0, 0)));
        boolean rejected = false;
        try {
            SiteBudget.regionKey("", 0, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("空维度被拒绝", rejected);
    }

    /** 阈值与需求一致，防止有人手滑改小。 */
    private static void testConstantsMatchSpec() {
        check("检查间隔 200 刻", SiteBudget.CHECK_INTERVAL_TICKS == 200);
        check("每轮 4 候选", SiteBudget.CANDIDATES_PER_CHECK == 4);
        check("每轮 1 落地", SiteBudget.PLACEMENTS_PER_CHECK == 1);
        check("区域 8×8 区块", SiteBudget.REGION_CHUNKS == 8);
        check("全局 64", SiteBudget.GLOBAL_LIMIT == 64);
        check("生长 72000 刻", SiteBudget.GROWTH_TICKS == 72000L);
        check("终身预算上限 4", SiteBudget.LIFETIME_GROWTH_LIMIT == 4);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }
}
