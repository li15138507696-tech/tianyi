package net.guzhenren.tianyi.fact;

import java.util.ArrayList;
import java.util.List;

/**
 * 情报核心（{@link WorldFact} / {@link FactLedger} / {@link IntelNet}）的离线行为测试。
 *
 * <p>跑法：{@code python _pack/run_tests.py}。因为 {@code fact} 包刻意不 import
 * 任何 Minecraft 类型，这些断言是<b>真的在跑逻辑</b>，不是扫源码文字。
 *
 * <p>覆盖：无来源不知道、传播 1→2→3 跳后停住、传播延迟未到不生效、
 * 重复 observe 幂等、过期不再当依据且 purge 真删、不同行为人得到不同 id 互不覆盖、
 * exportRows/importRows 往返、身份切换不抹除证据、不回灌上游、持有的是事实 id 而非人名、
 * 容量上限收敛、行序列化往返。
 */
public final class IntelCoreTest {

    private static int pass;
    private static int fail;

    private static final String A = "11111111-1111-1111-1111-111111111111";
    private static final String B = "22222222-2222-2222-2222-222222222222";
    private static final String W1 = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String W2 = "aaaaaaaa-0000-0000-0000-000000000002";
    private static final String W3 = "aaaaaaaa-0000-0000-0000-000000000003";
    private static final String W4 = "aaaaaaaa-0000-0000-0000-000000000004";
    private static final String W5 = "aaaaaaaa-0000-0000-0000-000000000005";
    private static final String DIM = "minecraft:overworld";
    private static final long POS = 1234L;

    public static void main(String[] args) {
        testNoSourceNoKnowledge();
        testHopChainStops();
        testTravelDelay();
        testObserveIdempotent();
        testExpiryAndPurge();
        testDistinctActorsDistinctIds();
        testExportImportRoundTrip();
        testIdentitySwitchKeepsEvidence();
        testFactsByIdNotByActorName();
        testNoBounceToUpstream();
        testExpiredFactNotSpread();
        testConfidenceDecayWithDistance();
        testCapacityCap();
        testRowCodec();

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

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < 1.0E-6;
    }

    private static WorldFact harvest(String actor, long pos, long tick) {
        return WorldFact.of(WorldFact.Kind.HARVEST, actor, "", DIM, pos, tick, 1.0, "采集蛊材");
    }

    // ---------------------------------------------------------------- 1. 无来源

    private static void testNoSourceNoKnowledge() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = harvest(A, POS, 1000L);

        check("事实入库", led.observe(f));
        check("无目击者的人不知道", !net.knows(B, f.id));
        check("无目击者 known 为空", net.known(B, 1000L).isEmpty());
        check("行为人自己也不自动知道（事实≠情报）", !net.knows(A, f.id));
        check("不存在的 id 查不到", net.know(B, "00000000-0000-0000-0000-000000000000") == null);
        check("没有任何持有者", net.holderCount() == 0 && net.count() == 0);
    }

    // ---------------------------------------------------------------- 2. 跳数

    private static void testHopChainStops() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = harvest(A, POS, 1000L);
        led.observe(f);

        IntelNet.SimpleGraph g = new IntelNet.SimpleGraph()
                .link(W1, W2, 0L, 0.9)
                .link(W2, W3, 0L, 0.9)
                .link(W3, W4, 0L, 0.9)
                .link(W4, W5, 0L, 0.9);

        check("目击者直授成功", net.teach(W1, f, 1.0, 1000L));
        IntelNet.Intel first = net.know(W1, f.id);
        check("直授 hops=0 且来自 witness", first != null && first.hops == 0
                && IntelNet.WITNESS.equals(first.from));

        check("第 1 轮只推 1 跳", net.spread(g, led, 1000L) == 1);
        check("W2 学到 hops=1", net.know(W2, f.id) != null && net.know(W2, f.id).hops == 1);
        check("一轮不级联：W3 还不知", !net.knows(W3, f.id));
        // 直授时 conf 被夹到事实自身的 baseConfidence（这里是 1.0：目击者亲眼所见），
        // 之后每传一跳乘一次边衰减 0.9 → 0.90 / 0.81 / 0.729。
        check("首跳可信度 = 1.0×0.9", approx(net.know(W2, f.id).confidence, 0.90));

        check("第 2 轮推 1 跳", net.spread(g, led, 1000L) == 1);
        check("W3 学到 hops=2", net.know(W3, f.id) != null && net.know(W3, f.id).hops == 2);
        check("W3 可信度继续衰减（×0.9）", approx(net.know(W3, f.id).confidence, 0.81));

        check("第 3 轮推 1 跳", net.spread(g, led, 1000L) == 1);
        check("W4 学到 hops=3（上限）", net.know(W4, f.id) != null
                && net.know(W4, f.id).hops == IntelNet.MAX_HOPS);

        check("跳数耗尽后不再扩散", net.spread(g, led, 1000L) == 0);
        check("第 5 人始终不知道", !net.knows(W5, f.id));
        check("持有者数 = 4", net.holderCount() == 4);
    }

    // ---------------------------------------------------------------- 3. 延迟

    private static void testTravelDelay() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = harvest(A, POS, 1000L);
        led.observe(f);
        IntelNet.SimpleGraph g = new IntelNet.SimpleGraph().link(W1, W2, 200L, 0.9);
        net.teach(W1, f, 1.0, 1000L);

        check("行期未到不传播", net.spread(g, led, 1100L) == 0);
        check("行期未到时目标不知", !net.knows(W2, f.id));
        check("行期恰好到达才传播", net.spread(g, led, 1200L) == 1);
        check("到达后带学到时刻", net.know(W2, f.id).learnedAt == 1200L);

        // 边本身过期：路断了就不传
        IntelNet net2 = new IntelNet(led);
        net2.teach(W1, f, 1.0, 1000L);
        IntelNet.SimpleGraph dead = new IntelNet.SimpleGraph().link(W1, W2, 0L, 0.9, 1100L);
        check("过期的边不走", net2.spread(dead, led, 1200L) == 0);
        check("未到期的边能走", net2.spread(dead, led, 1100L) == 1);
    }

    // ---------------------------------------------------------------- 4. 幂等

    private static void testObserveIdempotent() {
        FactLedger led = new FactLedger();
        WorldFact f1 = WorldFact.of(WorldFact.Kind.HARVEST, A, "", DIM, POS, 1000L, 1.0, "first");
        WorldFact f2 = WorldFact.of(WorldFact.Kind.HARVEST, A, "", DIM, POS, 1100L, 1.0, "second");
        WorldFact f3 = WorldFact.of(WorldFact.Kind.HARVEST, A, "", DIM, POS,
                1000L + WorldFact.TICK_BUCKET, 1.0, "later");

        check("同桶同行为 → 同 id", f1.id.equals(f2.id));
        check("跨桶 → 不同 id", !f1.id.equals(f3.id));
        check("首次 observe 返回 true", led.observe(f1));
        check("重复 observe 返回 false", !led.observe(f2));
        check("只存一条", led.size() == 1);
        check("先到者不被覆盖", "first".equals(led.get(f1.id).detail));
        check("重复写入不改 live 条数", led.live(1200L).size() == 1);

        check("跨桶后是第二条事实", led.observe(f3) && led.size() == 2);
        check("排序按 atTick 升序", led.live(2000L).get(0).atTick <= led.live(2000L).get(1).atTick);
    }

    // ---------------------------------------------------------------- 5. 过期

    private static void testExpiryAndPurge() {
        FactLedger led = new FactLedger();
        WorldFact f = new WorldFact(WorldFact.Kind.SEIZE, A, "", DIM, POS, 0L, 100L, 1.0, "");
        led.observe(f);

        check("到期前 live 有 1 条", led.live(50L).size() == 1);
        check("半程可信度减半", approx(f.effectiveConfidence(50L), 0.5));
        check("末刻仍算未过期", !f.expired(100L));
        check("过期判定用严格大于", f.expired(101L));
        check("末刻可信度归零", f.effectiveConfidence(100L) == 0.0);
        check("过期后 live 为空", led.live(101L).isEmpty());
        check("过期但未清理，账本还在", led.size() == 1 && led.all().size() == 1);
        check("purge 返回删除数 1", led.purge(101L) == 1);
        check("purge 真删", led.size() == 0 && led.isEmpty());
        check("重复 purge 返回 0", led.purge(101L) == 0);

        // 传播侧：过期事实不能再被当作依据
        FactLedger led2 = new FactLedger();
        IntelNet net = new IntelNet(led2);
        WorldFact s = new WorldFact(WorldFact.Kind.HARVEST, A, "", DIM, POS, 0L, 100L, 1.0, "");
        led2.observe(s);
        net.teach(W1, s, 1.0, 0L);
        check("过期前掌握", net.known(W1, 50L).size() == 1);
        check("过期后 known 过滤", net.known(W1, 500L).isEmpty());
    }

    // ---------------------------------------------------------------- 6. 同类型不同行为人

    private static void testDistinctActorsDistinctIds() {
        FactLedger led = new FactLedger();
        WorldFact a1 = harvest(A, POS, 1000L);
        WorldFact b1 = harvest(B, POS, 1000L);
        WorldFact otherPos = harvest(A, POS + 1, 1000L);
        WorldFact otherKind = WorldFact.of(WorldFact.Kind.SEIZE, A, "", DIM, POS, 1000L, 1.0, "");

        check("不同行为人 → 不同 id", !a1.id.equals(b1.id));
        check("不同位置 → 不同 id", !a1.id.equals(otherPos.id));
        check("不同种类 → 不同 id", !a1.id.equals(otherKind.id));
        check("id 是 UUID 形态（128 位，不是 32 位 hashCode）",
                a1.id.length() == 36 && WorldFact.fromRow(a1.toRow()) != null);

        check("两条都能入库", led.observe(a1) && led.observe(b1));
        check("互不覆盖", led.size() == 2 && led.get(a1.id).actor.equals(A)
                && led.get(b1.id).actor.equals(B));
        check("live 也是两条", led.live(1000L).size() == 2);
        check("同一行为重复观测不增加", !led.observe(harvest(A, POS, 1050L)) && led.size() == 2);
    }

    // ---------------------------------------------------------------- 7. 持久化往返

    private static void testExportImportRoundTrip() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact fa = harvest(A, POS, 1000L);
        WorldFact fb = WorldFact.of(WorldFact.Kind.ROBBERY, B, A, DIM, POS, 1000L, 0.8, "劫货");
        led.observe(fa);
        led.observe(fb);

        net.teach(W1, fa, 0.9, 1000L);
        net.teach(W2, fb, 0.7, 1000L);
        IntelNet.SimpleGraph g = new IntelNet.SimpleGraph().link(W1, W2, 0L, 0.9);
        net.spread(g, led, 1000L);

        List<String> rows = net.exportRows();
        check("导出行数 = 条目数", rows.size() == net.count() && rows.size() == 3);

        IntelNet back = new IntelNet(led);
        back.importRows(rows);
        check("往返后条目数一致", back.count() == net.count());
        check("往返后持有者一致", back.holderCount() == net.holderCount());
        check("往返后行内容一致", sorted(back.exportRows()).equals(sorted(rows)));
        check("往返后 W1 记录一致", back.snapshotOf(W1).equals(net.snapshotOf(W1)));
        check("往返后 W2 记录一致", back.snapshotOf(W2).equals(net.snapshotOf(W2)));
        check("往返后掌握关系一致", back.knows(W2, fa.id) && back.know(W2, fa.id).hops == 1);

        back.importRows(List.of("垃圾行", "", "x|y"));
        check("坏行不炸且不产生脏数据", back.holderCount() == 0 && back.count() == 0);
        back.importRows(null);
        check("null 输入安全", back.count() == 0);

        // 账本侧往返
        List<WorldFact> snap = led.snapshot();
        FactLedger restored = new FactLedger();
        restored.restore(snap);
        check("账本快照往返一致", restored.size() == led.size()
                && restored.get(fb.id) != null && restored.get(fb.id).detail.equals("劫货"));
        restored.clear();
        check("clear 后为空", restored.isEmpty());
    }

    private static List<String> sorted(List<String> in) {
        List<String> copy = new ArrayList<>(in);
        copy.sort(String::compareTo);
        return copy;
    }

    // ---------------------------------------------------------------- 8. 身份切换

    private static void testIdentitySwitchKeepsEvidence() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = WorldFact.of(WorldFact.Kind.KILL_NPC, A, B, DIM, POS, 1000L, 0.9, "杀人");

        led.observe(f);
        net.teach(W1, f, 1.0, 1000L);
        net.spread(new IntelNet.SimpleGraph().link(W1, W2, 0L, 0.9), led, 1000L);
        int countBefore = net.count();

        // 「切换身份」= 用另一个 holderId 去查（身份是玩家侧概念，世界证据不跟着变）
        check("新身份不继承旧证据", !net.knows(W3, f.id));
        check("原身份证据仍在", net.knows(W1, f.id));
        check("旁观者证据也仍在", net.knows(W2, f.id));
        check("条目未被抹除", net.count() == countBefore);
        check("事实本身未被删", led.get(f.id) != null && led.size() == 1);
        check("原情报仍带来源", net.know(W1, f.id).from.equals(IntelNet.WITNESS)
                && net.know(W2, f.id).from.equals(W1));

        // 切回原身份：证据还是原来那条（不是新建的）
        check("切回后仍是同一跳数", net.know(W1, f.id).hops == 0);
    }

    // ---------------------------------------------------------------- 9. 只认事实 id

    private static void testFactsByIdNotByActorName() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact fa = harvest(A, POS, 1000L);
        WorldFact fb = harvest(B, POS, 1000L);
        led.observe(fa);
        led.observe(fb);

        net.teach(W1, fa, 1.0, 1000L);
        net.teach(W1, fb, 1.0, 1000L);
        check("同一持有者两条事实各自成立", net.known(W1, 1000L).size() == 2);
        check("两个 id 不同", !fa.id.equals(fb.id));

        net.spread(new IntelNet.SimpleGraph().link(W1, W2, 0L, 0.9), led, 1000L);
        check("两条事实各自传播（不按人名合并）", net.known(W2, 1000L).size() == 2);
        check("W2 同时知道 A 与 B 的事", net.knows(W2, fa.id) && net.knows(W2, fb.id));

        // 只教一条时，另一条不跟着走
        IntelNet other = new IntelNet(led);
        other.teach(W1, fb, 1.0, 1000L);
        other.spread(new IntelNet.SimpleGraph().link(W1, W2, 0L, 0.9), led, 1000L);
        check("只教了 B 的事，A 的事不会顺带传播", other.knows(W2, fb.id)
                && !other.knows(W2, fa.id));
    }

    // ---------------------------------------------------------------- 10. 不回灌

    private static void testNoBounceToUpstream() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = harvest(A, POS, 1000L);
        led.observe(f);
        IntelNet.SimpleGraph g = new IntelNet.SimpleGraph()
                .link(W1, W2, 0L, 0.9)
                .link(W2, W1, 0L, 0.9);
        net.teach(W1, f, 1.0, 1000L);

        check("第一轮 W2 学到", net.spread(g, led, 1000L) == 1);
        double w1Before = net.know(W1, f.id).confidence;
        check("第二轮不再变化（不回灌上游）", net.spread(g, led, 1000L) == 0);
        check("上游可信度没被抬高", approx(net.know(W1, f.id).confidence, w1Before));
        check("上游仍是亲见", net.know(W1, f.id).hops == 0
                && IntelNet.WITNESS.equals(net.know(W1, f.id).from));
    }

    // ---------------------------------------------------------------- 11. 过期不传播

    private static void testExpiredFactNotSpread() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = new WorldFact(WorldFact.Kind.HARVEST, A, "", DIM, POS, 0L, 100L, 1.0, "");
        led.observe(f);
        net.teach(W1, f, 1.0, 0L);
        IntelNet.SimpleGraph g = new IntelNet.SimpleGraph().link(W1, W2, 0L, 0.9);

        check("过期后传播 0 条", net.spread(g, led, 500L) == 0);
        check("过期后目标不知", !net.knows(W2, f.id));
        check("prune 清掉过期持有记录", net.prune(led, 500L) == 1);
        check("prune 后 holder 清空", net.holderCount() == 0);
    }

    // ---------------------------------------------------------------- 12. 距离衰减

    private static void testConfidenceDecayWithDistance() {
        FactLedger led = new FactLedger();
        IntelNet net = new IntelNet(led);
        WorldFact f = harvest(A, POS, 1000L);
        led.observe(f);

        net.teach(W1, f, 1.0, 1000L);
        net.teach(W3, f, 1.0, 1000L);
        IntelNet.SimpleGraph g = new IntelNet.SimpleGraph()
                .link(W1, W2, 0L, 0.9)       // 近：几乎不衰减
                .link(W3, W4, 5000L, 0.9);   // 远：行期很长
        net.spread(g, led, 2000L);

        double near = net.know(W2, f.id).confidence;
        check("近距离传递已发生", net.knows(W2, f.id));
        check("远距离（行期未到）还没传", !net.knows(W4, f.id));
        net.spread(g, led, 2000L + 5000L);
        double far = net.know(W4, f.id).confidence;
        check("行期到了才传，且衰减更强", net.knows(W4, f.id) && far < near);
        check("越传越不可信（不超过上游）", far <= 0.9 + 1.0E-9 && near <= 0.9 + 1.0E-9);
        check("距离衰减系数单调", IntelNet.distanceDecay(0L) > IntelNet.distanceDecay(2400L)
                && IntelNet.distanceDecay(2400L) > IntelNet.distanceDecay(4800L));
    }

    // ---------------------------------------------------------------- 13. 容量

    private static void testCapacityCap() {
        FactLedger led = new FactLedger();
        int n = FactLedger.MAX_FACTS + 50;
        for (int i = 0; i < n; i++) {
            led.observe(harvest(A, i, i));
        }
        check("超限后收敛到上限", led.size() == FactLedger.MAX_FACTS);
        check("最旧的被淘汰", led.get(harvest(A, 0, 0).id) == null);
        check("最新的还在", led.get(harvest(A, n - 1, n - 1).id) != null);
        check("上限下仍可查询", led.live(n).size() == FactLedger.MAX_FACTS);
    }

    // ---------------------------------------------------------------- 14. 行编解码

    private static void testRowCodec() {
        WorldFact f = new WorldFact(WorldFact.Kind.ALCHEMY, A, B, DIM, POS, 4242L, 600L, 0.75,
                "炼成·月刃");
        WorldFact back = WorldFact.fromRow(f.toRow());
        check("行往返非空", back != null);
        check("行往返 id 一致", back != null && back.id.equals(f.id));
        check("行往返字段一致", back != null && back.kind == f.kind && back.actor.equals(f.actor)
                && back.target.equals(f.target) && back.dim.equals(f.dim) && back.pos == f.pos
                && back.atTick == f.atTick && back.ttl == f.ttl
                && approx(back.baseConfidence, f.baseConfidence)
                && back.detail.equals(f.detail));
        WorldFact noPos = WorldFact.of(WorldFact.Kind.SIGHTING, A, "", DIM,
                WorldFact.NO_POS, 1L, 1.0, "");
        check("无名址用 NO_POS 且可判定", !noPos.positionKnown() && noPos.pos == WorldFact.NO_POS);
        check("坏行返回 null", WorldFact.fromRow("a" + (char) 1 + "b") == null
                && WorldFact.fromRow(null) == null);
        check("同参不同对象 sameAs", WorldFact.fromRow(f.toRow()).sameAs(f));
    }
}
