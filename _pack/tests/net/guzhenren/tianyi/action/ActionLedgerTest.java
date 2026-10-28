package net.guzhenren.tianyi.action;

import java.util.List;
import java.util.Map;

/**
 * 布局步骤与结算的离线行为测试。
 *
 * <p>跑法：{@code python _pack/run_tests.py}。{@code action} 包的接口与账本刻意不 import
 * 任何 Minecraft 类型，所以这些断言是在<b>真的跑状态机</b>。
 *
 * <p>覆盖：去重拦住重复追杀、五种终态的迁移、被阻断后的冷却与复发、
 * 重试耗尽后放弃、DEFERRED 不占用重试次数、快照往返后重启不重复、
 * 去重键持久化、清理只删已结算的旧项。
 */
public final class ActionLedgerTest {

    private static int pass;
    private static int fail;

    private static final String TARGET = "33333333-3333-3333-3333-333333333333";
    private static final String EXEC = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String DIM = "minecraft:overworld";

    public static void main(String[] args) {
        testAdmitOnce();
        testDuplicateBlocked();
        testTerminalAllowsNewPlan();
        testOkSettlesDone();
        testBlockedCooldownThenRequeue();
        testFailCooldownShorter();
        testAttemptsExhaustedAbandons();
        testDeniedAbandons();
        testDeferredKeepsPending();
        testSnapshotRestoreRoundTrip();
        testDedupeSurvivesRestart();
        testPurgeRemovesOldSettled();
        testActResultSemantics();
        testDedupeKeyBucketing();

        System.out.println();
        System.out.printf("结果：%d 项通过，%d 项失败%n", pass, fail);
        if (fail > 0) {
            throw new IllegalStateException(fail + " 项失败");
        }
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

    private static ActionPlan plan(int actionNo, long now) {
        return new ActionPlan("npc.pursue" + actionNo, EXEC, TARGET, DIM, 100L,
                now, now, 0.8);
    }

    // ---------------------------------------------------------------- 用例

    static void testAdmitOnce() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(1, 1000L);
        String key = ActionPlan.dedupeKey(p.actionId(), TARGET, 1000L, 1200L);
        check("首次 admit 收下", led.admit(p, key, 1000L));
        check("账本 size=1", led.size() == 1);
        check("按去重键能取回", led.byDedupe(key) == p);
    }

    static void testDuplicateBlocked() {
        ActionLedger led = new ActionLedger();
        String key = ActionPlan.dedupeKey("npc.pursue1", TARGET, 1000L, 1200L);
        led.admit(plan(1, 1000L), key, 1000L);
        ActionPlan second = plan(1, 1000L);
        check("同键第二条被拒（不重复生成追杀）", !led.admit(second, key, 1000L));
        check("账本仍只有 1 条", led.size() == 1);
    }

    static void testTerminalAllowsNewPlan() {
        ActionLedger led = new ActionLedger();
        String key = ActionPlan.dedupeKey("npc.pursue1", TARGET, 1000L, 1200L);
        ActionPlan first = plan(1, 1000L);
        led.admit(first, key, 1000L);
        first.start(1000L);
        led.settle(first.id(), ActResult.ok("npc.pursue1", 0.1, "抓到了", 1000L, EXEC), 1000L);
        check("第一条已终结", first.state() == ActionPlan.State.DONE);
        // 同一时间桶的键仍然存在，但旧步骤已终结 → 允许新步骤
        check("终结后同键可再收", led.admit(plan(1, 1000L), key, 1000L));
    }

    static void testOkSettlesDone() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(2, 500L);
        led.admit(p, "k2", 500L);
        p.start(500L);
        check("start 后 attempts=1", p.attempts() == 1);
        led.settle(p.id(), ActResult.ok("npc.pursue2", 0.2, "货物已截", 600L, EXEC), 600L);
        check("OK → DONE", p.state() == ActionPlan.State.DONE);
        check("失败计数仍为 0", p.failures() == 0);
        check("不再到期可跑", !p.open(700L));
    }

    static void testBlockedCooldownThenRequeue() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(3, 0L);
        led.admit(p, "k3", 0L);
        p.start(0L);
        led.settle(p.id(), ActResult.blocked("npc.pursue3", "载体被清，观测断开", 0L, EXEC), 0L);
        check("BLOCKED → 状态 blocked", p.state() == ActionPlan.State.BLOCKED);
        check("失败计数 +1", p.failures() == 1);
        check("冷却期未到不可跑", !p.open(100L));
        p.requeue(100L);
        check("冷却未到 requeue 无效", p.state() == ActionPlan.State.BLOCKED);
        long after = ActionPlan.BLOCK_COOLDOWN + 10L;
        check("冷却到期后可跑", p.open(after));
        p.requeue(after);
        check("requeue 回到 PENDING", p.state() == ActionPlan.State.PENDING);
    }

    static void testFailCooldownShorter() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(4, 0L);
        led.admit(p, "k4", 0L);
        p.start(0L);
        led.settle(p.id(), ActResult.failed("npc.pursue4", 0.1, "本体返回失败", 0L, EXEC), 0L);
        check("FAILED → 状态 failed", p.state() == ActionPlan.State.FAILED);
        check("失败冷却比被阻断短",
                ActionPlan.FAIL_COOLDOWN < ActionPlan.BLOCK_COOLDOWN);
    }

    static void testAttemptsExhaustedAbandons() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(5, 0L);
        led.admit(p, "k5", 0L);
        long now = 0L;
        for (int i = 0; i < ActionPlan.MAX_ATTEMPTS; i++) {
            p.start(now);
            led.settle(p.id(), ActResult.failed("npc.pursue5", 0.1, "又失败", now, EXEC), now);
            now += ActionPlan.BLOCK_COOLDOWN * 4L;
        }
        check("重试耗尽 → ABANDONED", p.state() == ActionPlan.State.ABANDONED);
        check("尝试次数到上限", p.attempts() == ActionPlan.MAX_ATTEMPTS);
        check("放弃后永不到期", !p.open(now + 1_000_000L));
    }

    static void testDeniedAbandons() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(6, 0L);
        led.admit(p, "k6", 0L);
        p.start(0L);
        led.settle(p.id(), ActResult.denied("npc.pursue6", "势力已灭", 0L, EXEC), 0L);
        check("DENIED → ABANDONED（条件不成立就别硬来）", p.state() == ActionPlan.State.ABANDONED);
    }

    static void testDeferredKeepsPending() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(7, 0L);
        led.admit(p, "k7", 0L);
        p.start(0L);
        led.settle(p.id(), ActResult.deferred("npc.pursue7", "时机未到", 0L, EXEC), 0L);
        check("DEFERRED → 回到 PENDING", p.state() == ActionPlan.State.PENDING);
        check("DEFERRED 不算失败", p.failures() == 0);
        check("DEFERRED 后立刻不可跑（要等冷却）", !p.open(1L));
        check("冷却过后可跑", p.open(ActionPlan.FAIL_COOLDOWN + 10L));
    }

    static void testSnapshotRestoreRoundTrip() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(8, 0L);
        led.admit(p, "k8", 0L);
        p.start(0L);
        led.settle(p.id(), ActResult.blocked("npc.pursue8", "被截", 0L, EXEC), 0L);

        List<Map<String, String>> snap = led.snapshot();
        check("快照有 1 行", snap.size() == 1);

        ActionLedger restored = new ActionLedger();
        restored.restore(snap);
        check("恢复后 size=1", restored.size() == 1);
        ActionPlan r = restored.get(p.id());
        check("恢复后 id 一致", r != null && r.id().equals(p.id()));
        check("恢复后状态一致", r != null && r.state() == ActionPlan.State.BLOCKED);
        check("恢复后失败计数一致", r != null && r.failures() == 1);
        check("恢复后冷却一致", r != null && r.cooldownUntil() == p.cooldownUntil());
    }

    static void testDedupeSurvivesRestart() {
        ActionLedger led = new ActionLedger();
        String key = ActionPlan.dedupeKey("npc.pursue9", TARGET, 1000L, 1200L);
        led.admit(plan(9, 1000L), key, 1000L);

        ActionLedger reloaded = new ActionLedger();
        reloaded.restore(led.snapshot());
        check("重启后去重键能拦重复", !reloaded.admit(plan(9, 1000L), key, 1000L));
        check("重启后账本仍 1 条", reloaded.size() == 1);
    }

    static void testPurgeRemovesOldSettled() {
        ActionLedger led = new ActionLedger();
        ActionPlan p = plan(10, 0L);
        led.admit(p, "k10", 0L);
        p.start(0L);
        led.settle(p.id(), ActResult.ok("npc.pursue10", 0.1, "完成", 0L, EXEC), 0L);
        check("未到保留期不删", led.purge(1000L, 0L) == 0);
        check("过了保留期删掉",
                led.purge(ActionLedger.KEEP_SETTLED + 1000L, 0L) == 1);
        check("删完 size=0", led.size() == 0);
    }

    static void testActResultSemantics() {
        check("OK 才算改动", ActResult.ok("a", 0.1, "", 0L, "").succeeded());
        check("OK 不计罚", !ActResult.ok("a", 0.1, "", 0L, "").penalise());
        check("BLOCKED 算阻断", ActResult.blocked("a", "", 0L, "").status()
                == ActResult.Status.BLOCKED);
        check("BLOCKED 计罚", ActResult.blocked("a", "", 0L, "").penalise());
        check("FAILED 计罚", ActResult.failed("a", 0.1, "", 0L, "").penalise());
        check("DENIED 不计罚也不改动",
                !ActResult.denied("a", "", 0L, "").penalise()
                        && !ActResult.denied("a", "", 0L, "").status().changed());
        check("负消耗被夹到 0", ActResult.ok("a", -1.0, "", 0L, "").cost() == 0.0);
        check("null 原因被兜住", ActResult.ok("a", 0.0, null, 0L, null).reason().isEmpty());
    }

    static void testDedupeKeyBucketing() {
        String k1 = ActionPlan.dedupeKey("npc.pursue", TARGET, 1200L, 1200L);
        String k2 = ActionPlan.dedupeKey("npc.pursue", TARGET, 2399L, 1200L);
        String k3 = ActionPlan.dedupeKey("npc.pursue", TARGET, 2400L, 1200L);
        check("同桶不同刻同键（一次行为只算一次）", k1.equals(k2));
        check("跨桶换键（隔一分钟是新事）", !k1.equals(k3));
        check("不同目标不同键",
                !ActionPlan.dedupeKey("npc.pursue", "x", 0L, 1200L)
                        .equals(ActionPlan.dedupeKey("npc.pursue", "y", 0L, 1200L)));
    }
}
