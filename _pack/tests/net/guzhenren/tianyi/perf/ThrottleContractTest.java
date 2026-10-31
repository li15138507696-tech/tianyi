package net.guzhenren.tianyi.perf;

import java.util.List;

import net.guzhenren.tianyi.action.ActionLedger;
import net.guzhenren.tianyi.action.ActionPlan;
import net.guzhenren.tianyi.action.ActResult;
import net.guzhenren.tianyi.journal.PlayerJournal;
import net.guzhenren.tianyi.resource.SiteBudget;
import net.guzhenren.tianyi.will.WillConfig;
import net.guzhenren.tianyi.will.WillForce;

/**
 * 3.14.1 性能改动的<b>节流契约</b>回归测试。
 *
 * <h2>为什么要专门锁这些数字</h2>
 * 这一版做的是「每 tick 的全量遍历改成按周期/按需」。最容易发生的回归不是写错逻辑，
 * 而是后人为了「更跟手」把周期改小、把每轮预算放宽——那会直接把服务端 tick 打爆，
 * 而且不会有任何编译错误。所以把「多久一次、一次多少」写成断言：
 * 只允许等于或更保守，不允许悄悄变快。
 *
 * <h2>覆盖</h2>
 * <ul>
 *   <li>有限资源点：200 刻一轮、每轮 4 候选 / 1 落地、全局 64、8×8 区块 1 点；</li>
 *   <li>天意现象：四个窗口常量，以及「窗口能被巡查周期整除」这条幂等前提；</li>
 *   <li>决策/布局：决策周期 200、单轮 16 条、每 tick 最多 4 条布局；</li>
 *   <li>账本与见闻的容量上限；</li>
 *   <li>布局账本空表时 {@code due()} 的快速返回（3.14.1 新增的空转优化）。</li>
 * </ul>
 *
 * <h2>不覆盖</h2>
 * 真实 tick 耗时、{@code getBlockState}/{@code getBlockEntity} 的真实成本、网络包大小
 * —— 那些只能在游戏里用 spark/tick 分析或实机观测，见 {@code docs/3.14.1-性能与缺陷.md}。
 *
 * <p>跑法：{@code python _pack/run_tests.py}（本套件已登记进 SUITES）。
 */
public final class ThrottleContractTest {

    private static int pass;
    private static int fail;

    private static final String TARGET = "33333333-3333-3333-3333-333333333333";
    private static final String EXEC = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String DIM = "minecraft:overworld";

    public static void main(String[] args) {
        testSiteCadence();
        testForceWindows();
        testWindowDividesPeriod();
        testDecisionCadence();
        testLedgerBounds();
        testEmptyLedgerDueFastPath();
        testJournalBounds();

        System.out.println();
        System.out.printf("结果：%d 项通过，%d 项失败%n", pass, fail);
        System.out.println("边界：只锁纯逻辑里的节流常量与空表快速返回；"
                + "每 tick 真实耗时、方块/方块实体读取成本未在此验证（未实机）。");
        if (fail > 0) {
            throw new IllegalStateException(fail + " 项失败");
        }
    }

    // ---------------------------------------------------------------- 有限资源点

    /** 200 刻一轮；0 刻与负数都不跑，避免开服瞬间扎堆。 */
    static void testSiteCadence() {
        check("资源点：每 200 刻一轮", SiteBudget.CHECK_INTERVAL_TICKS == 200);
        check("资源点：200 刻到期", SiteBudget.due(200));
        check("资源点：199 刻不到期", !SiteBudget.due(199));
        check("资源点：201 刻不到期", !SiteBudget.due(201));
        check("资源点：0 刻不到期", !SiteBudget.due(0));
        check("资源点：负数不到期", !SiteBudget.due(-200));
        check("资源点：每轮最多 4 个候选", SiteBudget.CANDIDATES_PER_CHECK == 4);
        check("资源点：每轮最多落地 1 个", SiteBudget.PLACEMENTS_PER_CHECK == 1);
        check("资源点：全局上限 64", SiteBudget.GLOBAL_LIMIT == 64);
        check("资源点：8×8 区块 1 点", SiteBudget.REGION_CHUNKS == 8);
        check("资源点：生长 72000 刻", SiteBudget.GROWTH_TICKS == 72_000L);
        check("资源点：亲眼半径 24", SiteBudget.WITNESS_RADIUS == 24);
    }

    // ---------------------------------------------------------------- 天意现象

    /** 四个窗口是「一个窗口最多一次」的物理基础，只能等于或更长。 */
    static void testForceWindows() {
        check("窗口：天象 ≥ 6000 刻", WillForce.OMEN_WINDOW >= 6_000L);
        check("窗口：雷击 ≥ 6000 刻", WillForce.BOLT_WINDOW >= 6_000L);
        check("窗口：产地枯损 ≥ 24000 刻", WillForce.SPOIL_WINDOW >= 24_000L);
        check("窗口：兽潮 ≥ 12000 刻", WillForce.WAVE_WINDOW >= 12_000L);
        check("窗口：灾劫挑选 ≥ 1200 刻", WillForce.CALAMITY_WINDOW >= 1_200L);
    }

    /**
     * {@link WillForce#fires} 的幂等前提：巡查周期必须整除每一个窗口。
     *
     * <p>不整除时「起点 + 巡查区间」会跨过窗口边界，导致某个窗口被整段跳过
     * 或被命中两次（见 {@code fires} 的 span 注释）。这条断言就是那个坑的守门员。
     */
    static void testWindowDividesPeriod() {
        long[] windows = {WillForce.OMEN_WINDOW, WillForce.BOLT_WINDOW,
                WillForce.SPOIL_WINDOW, WillForce.WAVE_WINDOW};
        String[] names = {"天象", "雷击", "枯损", "兽潮"};
        for (int intensity = WillForce.OFF; intensity <= WillForce.MAX; intensity++) {
            WillForce f = WillForce.of(intensity);
            int period = Math.max(1, f.phenomenaPeriod());
            for (int i = 0; i < windows.length; i++) {
                check("窗口整除：档位 " + intensity + " 的「" + names[i] + "」窗口 "
                                + windows[i] + " 能被巡查周期 " + period + " 整除",
                        windows[i] % period == 0);
            }
        }
    }

    // ---------------------------------------------------------------- 决策与布局

    static void testDecisionCadence() {
        check("决策：默认周期 200 刻", WillConfig.DECISION_PERIOD == 200);
        check("决策：单轮最多 16 条", WillConfig.DECISION_BATCH == 16);
        check("布局：每 tick 最多 4 条", WillConfig.ACTIVE_JOBS_PER_TICK == 4);
        check("布局：档位 0/1 的决策周期不加速",
                WillForce.of(WillForce.OFF).decisionPeriod() == WillConfig.DECISION_PERIOD
                        && WillForce.of(WillForce.GENTLE).decisionPeriod() == WillConfig.DECISION_PERIOD);
        check("布局：档位 0 现象周期不短于 400", WillForce.of(WillForce.OFF).phenomenaPeriod() >= 400);
        check("布局：档位 1 现象周期不短于 400", WillForce.of(WillForce.GENTLE).phenomenaPeriod() >= 400);
        check("布局：活跃布局上限 6", WillConfig.MAX_ACTIVE_PLOYS == 6);
    }

    // ---------------------------------------------------------------- 账本容量

    static void testLedgerBounds() {
        check("布局账本：上限 256 条", ActionLedger.MAX_PLANS == 256);
        check("布局账本：已结算保留 6 游戏小时", ActionLedger.KEEP_SETTLED == 24_000L * 6L);
    }

    /**
     * 空账本的 {@code due(now)} 必须立刻返回空表。
     *
     * <p>3.14.1 之前它每次都 new 一个 ArrayList 再排序；现在空表直接返回 {@code List.of()}。
     * 这个用例同时锁住「返回的表不能为空 / 不能塞进不该跑的计划」这两件事。
     */
    static void testEmptyLedgerDueFastPath() {
        ActionLedger empty = new ActionLedger();
        List<ActionPlan> none = empty.due(1_000L);
        check("空账本：due 返回空表", none != null && none.isEmpty());

        ActionLedger led = new ActionLedger();
        ActionPlan p = new ActionPlan("npc.pursue1", EXEC, TARGET, DIM, 100L, 1_000L, 1_000L, 0.8);
        check("有账本：admit 收下", led.admit(p, "k1", 1_000L));
        List<ActionPlan> due = led.due(1_000L);
        check("有账本：due 返回这一条", due.size() == 1 && due.get(0) == p);

        p.start(1_000L);
        led.settle(p.id(), ActResult.ok("npc.pursue1", 0.2, "已结算", 1_100L, EXEC), 1_100L);
        check("结算后：due 不再返回它", led.due(9_999_999L).isEmpty());
    }

    // ---------------------------------------------------------------- 见闻录容量

    /** 见闻录按玩家限 120 条，超了从最早开始裁；不会无界增长。 */
    static void testJournalBounds() {
        check("见闻录：上限 120 条", PlayerJournal.MAX_ENTRIES == 120);
        PlayerJournal j = new PlayerJournal();
        for (int i = 0; i < PlayerJournal.MAX_ENTRIES + 25; i++) {
            j.add(entry(i));
        }
        check("见闻录：超上限被裁剪到 120", j.size() == PlayerJournal.MAX_ENTRIES);
        check("见闻录：最新的那条还在", j.find("id-" + (PlayerJournal.MAX_ENTRIES + 24)) != null);
        check("见闻录：最早的那条已被裁掉", j.find("id-0") == null);
    }

    private static PlayerJournal.Entry entry(int i) {
        return new PlayerJournal.Entry("id-" + i, "key-" + i,
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "标题" + i, "正文", DIM, 0L, i, "", "", "");
    }

    // ---------------------------------------------------------------- 工具

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }
}
