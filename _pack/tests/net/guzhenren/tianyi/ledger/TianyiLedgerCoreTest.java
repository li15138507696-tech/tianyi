package net.guzhenren.tianyi.ledger;

import java.util.HashMap;
import java.util.Map;

/**
 * 天机账本的离线行为测试（不依赖 Minecraft，可用 javac + java 直接跑）。
 *
 * <p>覆盖用户点名的边界：重复付款、退款、重复领奖、日上限、跨日归零、
 * 迁移幂等、处罚不污染累计。运行方式见 {@code _pack/run_ledger_test.py}。
 */
public final class TianyiLedgerCoreTest {

    /** 内存 Store：等价于玩家的持久数据。 */
    static final class Mem implements TianyiLedgerCore.Store {
        final Map<String, Integer> ints = new HashMap<>();
        final Map<String, Boolean> bools = new HashMap<>();

        public int getInt(String key, int def) {
            return ints.getOrDefault(key, def);
        }

        public void putInt(String key, int value) {
            ints.put(key, value);
        }

        public boolean getBool(String key, boolean def) {
            return bools.getOrDefault(key, def);
        }

        public void putBool(String key, boolean value) {
            bools.put(key, value);
        }
    }

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        testGrantBasics();
        testDailyCap();
        testNextDayResets();
        testSpendOnlyTouchesBalance();
        testRefundDoesNotInflateLifetime();
        testBuyRefundLoopCannotFarmTier();
        testPenalizeClampsAndIsIsolated();
        testSettleOnceIsIdempotent();
        testMigrateIsIdempotent();
        testConsistentGuard();

        System.out.println();
        System.out.printf("结果：%d 项通过，%d 项失败%n", pass, fail);
        if (fail > 0) {
            throw new IllegalStateException(fail + " 项测试失败");
        }
    }

    // ---------------------------------------------------------------- 用例

    private static void testGrantBasics() {
        Mem s = new Mem();
        int got = TianyiLedgerCore.grant(s, 30, 100, 80);
        check("收入入账 30", got == 30);
        check("余额 = 30", TianyiLedgerCore.points(s) == 30);
        check("累计 = 30", TianyiLedgerCore.lifetime(s) == 30);
        check("当日已赚 = 30", TianyiLedgerCore.earnedToday(s, 100) == 30);
        check("负数收入被忽略", TianyiLedgerCore.grant(s, -50, 100, 80) == 0);
        check("忽略负数后累计不变", TianyiLedgerCore.lifetime(s) == 30);
    }

    private static void testDailyCap() {
        Mem s = new Mem();
        TianyiLedgerCore.grant(s, 60, 5, 80);
        int got = TianyiLedgerCore.grant(s, 60, 5, 80);
        check("日上限截断：第二次只给 20", got == 20);
        check("日上限后余额 = 80", TianyiLedgerCore.points(s) == 80);
        check("日上限后累计 = 80", TianyiLedgerCore.lifetime(s) == 80);
        check("第三次给 0", TianyiLedgerCore.grant(s, 60, 5, 80) == 0);
    }

    private static void testNextDayResets() {
        Mem s = new Mem();
        TianyiLedgerCore.grant(s, 80, 5, 80);
        int got = TianyiLedgerCore.grant(s, 80, 6, 80);
        check("跨日重置后又能赚满 80", got == 80);
        check("跨日累计 = 160", TianyiLedgerCore.lifetime(s) == 160);
        check("新一天已赚 = 80", TianyiLedgerCore.earnedToday(s, 6) == 80);
    }

    private static void testSpendOnlyTouchesBalance() {
        Mem s = new Mem();
        TianyiLedgerCore.grant(s, 50, 1, 100);
        check("支出成功", TianyiLedgerCore.spend(s, 20));
        check("余额 = 30", TianyiLedgerCore.points(s) == 30);
        check("支出不动累计", TianyiLedgerCore.lifetime(s) == 50);
        check("支出不动当日已赚", TianyiLedgerCore.earnedToday(s, 1) == 50);
        check("余额不足时失败", !TianyiLedgerCore.spend(s, 999));
        check("失败后余额不变", TianyiLedgerCore.points(s) == 30);
    }

    private static void testRefundDoesNotInflateLifetime() {
        Mem s = new Mem();
        TianyiLedgerCore.grant(s, 100, 1, 100);
        TianyiLedgerCore.spend(s, 100);
        TianyiLedgerCore.refund(s, 100);
        check("退款后余额回满", TianyiLedgerCore.points(s) == 100);
        check("退款不增加累计", TianyiLedgerCore.lifetime(s) == 100);
        check("退款不增加当日已赚", TianyiLedgerCore.earnedToday(s, 1) == 100);
        check("退款单独记账 = 100", TianyiLedgerCore.refundedTotal(s) == 100);
    }

    private static void testBuyRefundLoopCannotFarmTier() {
        Mem s = new Mem();
        for (int i = 0; i < 20; i++) {
            TianyiLedgerCore.grant(s, 10, 1, 1000);
            TianyiLedgerCore.spend(s, 10);
            TianyiLedgerCore.refund(s, 10);
        }
        check("买卖退款 20 轮后累计仍为 200", TianyiLedgerCore.lifetime(s) == 200);
        check("买卖退款 20 轮后余额 200", TianyiLedgerCore.points(s) == 200);
    }

    private static void testPenalizeClampsAndIsIsolated() {
        Mem s = new Mem();
        TianyiLedgerCore.grant(s, 12, 1, 100);
        int taken = TianyiLedgerCore.penalize(s, 50);
        check("处罚只扣到 0（实际扣 12）", taken == 12);
        check("处罚后余额 0", TianyiLedgerCore.points(s) == 0);
        check("处罚不动累计", TianyiLedgerCore.lifetime(s) == 12);
        check("处罚单独记账 12", TianyiLedgerCore.penaltyTotal(s) == 12);
    }

    private static void testSettleOnceIsIdempotent() {
        Mem s = new Mem();
        check("首次结算成功", TianyiLedgerCore.settleOnce(s, "life.3"));
        check("重复结算失败", !TianyiLedgerCore.settleOnce(s, "life.3"));
        check("已结算标记可查", TianyiLedgerCore.settled(s, "life.3"));
        check("其它动作不受影响", TianyiLedgerCore.settleOnce(s, "life.4"));
    }

    private static void testMigrateIsIdempotent() {
        Mem s = new Mem();
        // 旧档：只有旧键的数值被传进来
        check("首次迁移执行", TianyiLedgerCore.migrate(s, 340, 1200));
        check("迁移后余额 340", TianyiLedgerCore.points(s) == 340);
        check("迁移后累计 1200", TianyiLedgerCore.lifetime(s) == 1200);
        check("schema = 2", TianyiLedgerCore.schema(s) == TianyiLedgerCore.SCHEMA);
        check("二次迁移不执行", !TianyiLedgerCore.migrate(s, 340, 1200));

        // 已有更高累计时，迁移不得压低
        Mem t = new Mem();
        TianyiLedgerCore.migrate(t, 10, 50);
        t.putInt(TianyiLedgerCore.K_SCHEMA, 0); // 模拟旧结构再次出现
        TianyiLedgerCore.migrate(t, 10, 20);
        check("迁移不会压低累计（50 保持不变）", TianyiLedgerCore.lifetime(t) == 50);
    }

    private static void testConsistentGuard() {
        Mem s = new Mem();
        check("空账本一致", TianyiLedgerCore.consistent(s));
        TianyiLedgerCore.grant(s, 10, 1, 100);
        s.putInt(TianyiLedgerCore.K_POINTS, -5);
        check("负余额被判为不一致", !TianyiLedgerCore.consistent(s));
    }

    // ---------------------------------------------------------------- 断言

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
