package net.guzhenren.tianyi.encounter;

import java.util.HashMap;
import java.util.Map;

public final class EncounterRunTest {
    private static final String OWNER = "a0000000-0000-0000-0000-000000000001";
    private static final String EVENT = "b0000000-0000-0000-0000-000000000002";
    private static final String ENEMY = "c0000000-0000-0000-0000-000000000003";
    public static void main(String[] args) {
        transitionsAndRewards();
        timersAndRange();
        persistenceAndPenalty();
        corruptionRejected();
        System.out.println("EncounterRunTest: PASS");
    }

    private static void transitionsAndRewards() {
        EncounterRun r = new EncounterRun(OWNER);
        check(r.baseReward == 32 && r.stake == 32 && r.bonus == 128, "approved reward defaults");
        check(!r.due() && !r.investigate() && !r.startBattle(ENEMY, 400), "waiting guards");
        r.elapse(1200);
        check(r.due(), "initial countdown expires");
        open(r);
        check(r.phase == EncounterRun.Phase.SEEK && !r.baseGranted && !r.stakePaid, "open does not pay");
        check(!r.open(EVENT, "minecraft:overworld", 1, "minecraft:zombie", "怪物", "", 32, 32, 128), "cannot replace active event");
        check(r.investigate() && !r.investigate(), "investigate pays once");
        check(!r.startBattle("", 400), "empty enemy rejected");
        check(r.startBattle(ENEMY, 400), "battle starts after investigate");
        check(!r.startBattle(ENEMY, 400) && r.stakePaid && r.remainingTicks == 400, "battle cannot debit twice");
        check(r.win(50) && !r.win(50) && !r.lose("重复", 50), "single winning settlement");
        check(r.terminal() && r.settled && !r.penaltyPending && r.message.equals("胜利"), "win state");
        check(!r.finish(50), "cannot finish settled battle");
        r.elapse(50);
        open(r);
        check(!r.baseGranted && !r.stakePaid && !r.settled && r.enemyUuid.isEmpty() && r.message.isEmpty(), "new event clears state");
        check(r.finish(20) && !r.finish(20) && !r.penaltyPending, "skip gives no penalty");
        r.elapse(20);
        open(r);
        r.investigate();
        check(r.finish(20), "ready event can be finished");
    }

    private static void timersAndRange() {
        EncounterRun r = ready();
        int paused = r.remainingTicks;
        r.elapse(Integer.MAX_VALUE);
        check(r.remainingTicks == paused, "ready never expires");
        r.startBattle(ENEMY, 400);
        r.outside(true, 35);
        check(r.outsideExpired(35) && !r.outsideExpired(36), "grace boundary");
        r.outside(false, 1);
        check(r.outsideTicks == 0, "returning inside resets grace");
        r.outside(true, Integer.MAX_VALUE);
        check(r.outsideTicks == 100000, "outside time saturates");
        r.outside(true, -10);
        check(r.outsideTicks == 100000, "negative time ignored");
        r.elapse(-1);
        check(r.remainingTicks == 400, "negative countdown ignored");
        Map<String, String> before = r.snapshot();
        check(r.snapshot().equals(before), "offline absence of elapse pauses countdown");
        r.elapse(Integer.MAX_VALUE);
        check(r.remainingTicks == 0 && r.phase == EncounterRun.Phase.FIGHT, "world service decides timeout loss");
        EncounterRun seek = new EncounterRun(OWNER);
        open(seek);
        seek.elapse(Integer.MAX_VALUE);
        check(seek.phase == EncounterRun.Phase.SEEK && seek.remainingTicks == 0, "seek does not count down");
        seek.outside(true, 100);
        check(seek.outsideTicks == 0 && !seek.outsideExpired(0), "range only applies in battle");
    }

    private static void persistenceAndPenalty() {
        EncounterRun r = ready();
        r.startBattle(ENEMY, 400);
        r.outside(true, 12);
        EncounterRun restored = EncounterRun.restore(r.snapshot());
        check(restored.snapshot().equals(r.snapshot()), "all fight fields roundtrip");
        check(restored.lose("离开范围", 50), "loss settlement");
        check(!restored.lose("再次", 50) && !restored.win(50), "single losing settlement");
        restored.elapse(50);
        EncounterRun pending = EncounterRun.restore(restored.snapshot());
        check(!pending.due(), "pending penalty blocks next event");
        check(!pending.open(EVENT, "minecraft:overworld", 1, "minecraft:zombie", "怪物", "", 32, 32, 128), "pending penalty cannot be erased by open");
        check(pending.consumePenalty() && !pending.consumePenalty() && pending.due(), "penalty consumed exactly once");
        check(!EncounterRun.restore(pending.snapshot()).consumePenalty(), "consumed penalty survives reload");
        check(EncounterRun.restore(new EncounterRun(OWNER).snapshot()).phase == EncounterRun.Phase.WAITING, "empty waiting snapshot roundtrip");
    }

    private static void corruptionRejected() {
        Map<String, String> valid = ready().snapshot();
        invalid(valid, "owner", "invalid");
        invalid(valid, "id", "invalid");
        invalid(valid, "enemyUuid", "invalid");
        invalid(valid, "enemyId", "bad id");
        invalid(valid, "phase", "UNKNOWN");
        invalid(valid, "stake", "-1");
        invalid(valid, "remainingTicks", "oops");
        invalid(valid, "settled", "perhaps");
        invalid(valid, "stake", "2147483647");
        invalid(valid, "bonus", "2147483648");
        invalid(valid, "remainingTicks", "1728001");
        invalid(valid, "enemyName", "x".repeat(129));
        invalid(valid, "warning", "x".repeat(513));
        invalid(valid, "message", "x".repeat(1025));
        Map<String, String> missing = new HashMap<>(valid);
        missing.remove("baseGranted"); reject(missing);
        Map<String, String> fight = ready().snapshot();
        fight.put("phase", "FIGHT");
        fight.put("stakePaid", "true");
        reject(fight);
    }

    private static EncounterRun ready() { EncounterRun r = new EncounterRun(OWNER); open(r); r.investigate(); return r; }
    private static void open(EncounterRun r) {
        check(r.open(EVENT, "minecraft:overworld", 42L, "minecraft:zombie", "僵尸", "小心", 32, 32, 128), "open event");
        check(r.position == 42L && r.baseReward == 32 && r.stake == 32 && r.bonus == 128, "event configuration");
    }
    private static void invalid(Map<String, String> valid, String key, String value) {
        Map<String, String> bad = new HashMap<>(valid); bad.put(key, value); reject(bad);
    }
    private static void reject(Map<String, String> data) {
        try { EncounterRun.restore(data); throw new AssertionError("corruption accepted: " + data); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
