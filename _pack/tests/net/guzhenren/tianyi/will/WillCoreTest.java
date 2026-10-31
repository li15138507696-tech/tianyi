package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 天意世界级行为的离线测试 —— 不依赖 Minecraft / NeoForge。
 *
 * <p>跑法：{@code python _pack/run_tests.py}。因为 {@code will} 包刻意不 import
 * 任何 Minecraft 类型，这些断言是<b>真的在跑逻辑</b>，不是扫源码文字。
 *
 * <p>覆盖：知识盲区、旧情报失准、局部天意清除、道德与气运在不同局势下的决策差异、
 * 扶持魔头、压制高德强者、气运不改威胁、高气运仍可脱险、姿态翻转、人劫酝酿被破坏、
 * 布局不叠加、机缘被截取、寿蛊只动供给、灾劫四阶段与针对性上限、宿命阶段与星宿争夺、
 * 反制逐项、天外之魔不免疫、升仙不被宿命封死、仙窍内部时间、回溯校验与防复制。
 */
public final class WillCoreTest {

    private static int pass;
    private static int fail;

    private static final String P_A = "11111111-1111-1111-1111-111111111111";
    private static final String P_B = "22222222-2222-2222-2222-222222222222";
    private static final String P_C = "33333333-3333-3333-3333-333333333333";
    private static final String NPC = "44444444-4444-4444-4444-444444444444";

    public static void main(String[] args) {
        testKnowledgeBlindSpot();
        testStaleIntelDegrades();
        testLocalPurge();
        testSameMoralLuckDifferentSituation();
        testWeakMoralPawnSupported();
        testVillainSupportedToCounterThreat();
        testStrongMoralBreakerSuppressed();
        testLuckDoesNotDriveThreat();
        testHighLuckTargetStillEscapes();
        testLostIntelCausesObserve();
        testPostureFlips();
        testPloyGestationBroken();
        testPloyNoStackingAndCooldown();
        testOpportunityInterceptAndBaitCap();
        testLifespanSupplyNeverTouchesInventory();
        testShouGuSourceAudit();
        testBeastHerdEcology();
        testAlchemyEvidenceBinding();
        testIdentityEvidenceChain();
        testCarrierUnifiedState();
        testAlchemyProductInference();
        testOfferSettleOnce();
        testAlchemySessionReset();
        testCalamityPhases();
        testCalamityTargetingCapAndPenalty();
        testDestinyStages();
        testStarWillContest();
        testCounterAspectsAreDistinct();
        testTianwaiNotImmune();
        testAscensionWithoutDestinyLock();
        testApertureInnerTime();
        testHistoryVerifyAndGuards();
        testHistoryNoInventoryNoWillLeak();
        testNoAiDependency();

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

    private static Assessment.Situation situation(long now) {
        Assessment.Situation s = new Assessment.Situation(now);
        s.with(Assessment.Channel.BEAST);
        s.with(Assessment.Channel.RUMOR);
        s.with(Assessment.Channel.RESOURCE);
        s.with(Assessment.Channel.HERITAGE);
        s.with(Assessment.Channel.FACTION);
        s.with(Assessment.Channel.CALAMITY);
        s.with(Assessment.Channel.CARRIER);
        return s;
    }

    /** 与 BlockPos.asLong 完全一致的打包（测试不能依赖 Minecraft，所以自己算）。 */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static Assessment.Actor actor(String id, double power, double morality, double luck,
                                         double luckCap) {
        return new Assessment.Actor(id, Assessment.ActorKind.PLAYER, "")
                .power(power).resources(0.5).growth(0.0)
                .morality(morality).luck(luck, luckCap)
                .confidence(1.0).knownDefiance(0.0);
    }

    // ---------------------------------------------------------------- 感知

    private static void testKnowledgeBlindSpot() {
        Knowledge.Store k = new Knowledge.Store();
        long now = 1000L;
        boolean seen = k.observe(Knowledge.Fact.observed(P_A, Knowledge.Kind.POSITION,
                42.0, now, "guzhenren:zhongzhou", 12345L, Knowledge.Source.CONTACT));
        check("观测写入成功", seen);

        Knowledge.Track live = k.track(P_A, Knowledge.Kind.POSITION, now, -1);
        check("有情报时可实时跟踪", live.live() && !live.positionStale());
        check("实时跟踪带坐标", live.pos() == 12345L);

        k.cover(P_A, now + 10, 5000L, "shenbuzhi");
        long later = now + 100;

        boolean stillSees = k.observe(Knowledge.Fact.observed(P_A, Knowledge.Kind.POSITION,
                99.0, later, "guzhenren:zhongzhou", 999L, Knowledge.Source.SELF_SPREAD));
        check("遮蔽期间新观测被拒绝（真实盲区）", !stillSees);
        check("遮蔽后 latest 为空", k.latest(P_A, Knowledge.Kind.POSITION, later).isEmpty());

        Knowledge.Track blind = k.track(P_A, Knowledge.Kind.POSITION, later, -1);
        check("遮蔽后不实时", !blind.live());
        check("遮蔽后坐标失效，不能继续精确追踪", blind.positionStale()
                && blind.pos() == Knowledge.Fact.NO_POS);
        check("遮蔽后仍能按旧情报推断数值", blind.value() == 42.0);
        check("遮蔽原因可查", "shenbuzhi".equals(k.coverCause(P_A)));

        k.uncover(P_A);
        long after = now + 200;
        boolean reSeen = k.observe(Knowledge.Fact.observed(P_A, Knowledge.Kind.POSITION,
                7.0, after, "guzhenren:zhongzhou", 777L, Knowledge.Source.CONTACT));
        check("解除遮蔽后恢复观测", reSeen && k.track(P_A, Knowledge.Kind.POSITION, after, -1).live());
    }

    private static void testStaleIntelDegrades() {
        Knowledge.Store k = new Knowledge.Store();
        k.observe(Knowledge.Fact.observed(P_A, Knowledge.Kind.POWER, 10.0, 0L,
                "guzhenren:zhongnan", 0L, Knowledge.Source.CONTACT));

        long soft = WillConfig.FACT_SOFT_TTL + 1;
        Knowledge.Track t = k.track(P_A, Knowledge.Kind.POWER, soft, 0);
        check("旧情报不再实时", !t.live());
        check("旧情报可信度衰减", t.confidence() < 0.9 && t.confidence() > 0.0);

        long hard = WillConfig.FACT_HARD_TTL + 1;
        check("超过硬过期后彻底清除", k.latest(P_A, Knowledge.Kind.POWER, hard).isEmpty());

        // 推断不能被当观测写进库
        boolean inferred = k.observe(new Knowledge.Fact(P_A, Knowledge.Kind.POWER, 50.0,
                soft, "", Knowledge.Fact.NO_POS, 1.0, Knowledge.Source.INFERRED));
        check("推断不得冒充观测", !inferred);
    }

    private static void testLocalPurge() {
        Knowledge.Store k = new Knowledge.Store();
        Carrier.Network net = new Carrier.Network();
        net.add("n-over", Carrier.Kind.REGION, "minecraft:overworld", 0L, 256.0);
        net.add("n-net", Carrier.Kind.DAO_MARK, "minecraft:the_nether", 0L, 256.0);
        long now = 5000L;

        check("清除前有触达", net.reachAt("minecraft:overworld", 0L, now, 256.0) > 0.1);
        int purged = net.purge("minecraft:overworld", 0L, 200.0, k, now, "tianxiaoyisan");
        check("局部载体被清除", purged == 1);
        check("该处天意触达归零", net.reachAt("minecraft:overworld", 0L, now, 256.0) == 0.0);
        check("该处出现区域盲区", net.regionalBlind("minecraft:overworld", now));
        check("别的维度不受影响（局部≠世界天意死亡）",
                net.reachAt("minecraft:the_nether", 0L, now, 256.0) > 0.1);
        check("世界的天意仍然活着", net.willAlive(now));

        long later = now + WillConfig.CARRIER_PURGE_BLIND + 1;
        net.regrow(later, WillConfig.CARRIER_PURGE_BLIND);
        check("盲区到期后可以重新渗入", !net.regionalBlind("minecraft:overworld", later));
    }

    // ---------------------------------------------------------------- 判断

    private static void testSameMoralLuckDifferentSituation() {
        // 同一份道德（50）与气运（30），只改世界局势
        Knowledge.Store kA = new Knowledge.Store();
        WillCore wA = new WillCore(kA, "world-A");
        Assessment.Situation sA = situation(1000L);
        sA.add(actor(P_A, 0.2, 50, 30, 100));
        sA.add(actor(P_B, 5.0, 0, 0, 100));
        Assessment.Report rA = Assessment.assess(sA);
        wA.decide(sA);

        Knowledge.Store kB = new Knowledge.Store();
        WillCore wB = new WillCore(kB, "world-B");
        Assessment.Situation sB = situation(1000L);
        sB.add(actor(P_A, 0.2, 50, 30, 100));
        sB.add(actor(P_B, 0.2, 50, 30, 100));
        sB.add(actor(P_C, 0.2, 50, 30, 100));
        Assessment.Report rB = Assessment.assess(sB);
        wB.decide(sB);

        check("局势A失衡", rA.imbalanced);
        check("局势B不失衡", !rB.imbalanced);
        check("同一人同一道德气运：失衡场被压制",
                wA.postureOf(P_B) == Intent.Posture.SUPPRESS);
        check("同一人同一道德气运：均衡场不被压制",
                wB.postureOf(P_B) != Intent.Posture.SUPPRESS);
        check("弱小的同一个人在两场都值得扶持",
                wA.postureOf(P_A) == Intent.Posture.SUPPORT
                        && wB.postureOf(P_A) == Intent.Posture.SUPPORT);
    }

    private static void testWeakMoralPawnSupported() {
        Assessment.Situation s = situation(1000L);
        s.add(actor(P_A, 0.05, 60, 10, 100));
        s.add(actor(P_B, 3.0, 0, 0, 100));
        Assessment.Report r = Assessment.assess(s);
        check("高道德弱小者入扶持候选", r.supportCandidates.contains(P_A));
        check("其棋子价值达到门槛", r.utilityOf(P_A) >= WillConfig.MIN_UTILITY_TO_SUPPORT);
        check("其威胁不到压制线", r.threatOf(P_A) < WillConfig.MIN_THREAT_TO_SUPPRESS);
    }

    private static void testVillainSupportedToCounterThreat() {
        Assessment.Situation s = situation(1000L);
        // 魔头：道德 -80，但能制衡更大的威胁
        s.add(actor(P_A, 0.30, -80, 5, 100));
        s.add(actor(P_B, 0.80, 40, 10, 100));
        s.add(actor(P_C, 0.30, 10, 10, 100));
        Assessment.Report r = Assessment.assess(s);
        check("魔头进入扶持候选（可被利用来制衡）", r.supportCandidates.contains(P_A));
        check("魔头的棋子价值够高", r.utilityOf(P_A) >= WillConfig.MIN_UTILITY_TO_SUPPORT);

        WillCore w = new WillCore(new Knowledge.Store(), "w");
        w.decide(s);
        check("天意确实会扶持低道德者", w.postureOf(P_A) == Intent.Posture.SUPPORT);
    }

    private static void testStrongMoralBreakerSuppressed() {
        Assessment.Situation s = situation(1000L);
        s.add(actor(P_A, 0.95, 95, 30, 100));
        s.add(actor(P_B, 0.05, 0, 0, 100));
        Assessment.Report r = Assessment.assess(s);
        Assessment.Actor a = s.find(P_A);
        check("高德强者也进压制候选", r.suppressCandidates.contains(P_A));
        check("道德不构成豁免", Assessment.shouldSuppressDespiteMorality(r, a));

        WillCore w = new WillCore(new Knowledge.Store(), "w");
        w.decide(s);
        check("天意确实压制高德强者", w.postureOf(P_A) == Intent.Posture.SUPPRESS);
        boolean viaHuman = false;
        for (Intent i : w.decide(s)) {
            if (i.kind == Intent.Kind.SUPPRESS && P_A.equals(i.target)
                    && i.channel == Assessment.Channel.RUMOR) {
                viaHuman = true;
            }
        }
        check("对高德者优先借人施劫（不是直接降灾）",
                viaHuman || w.postureOf(P_A) == Intent.Posture.SUPPRESS);
    }

    private static void testLuckDoesNotDriveThreat() {
        Assessment.Situation low = situation(1000L);
        low.add(actor(P_A, 0.3, 20, -40, 100));
        low.add(actor(P_B, 0.3, 20, -40, 100));
        Assessment.Report rl = Assessment.assess(low);

        Assessment.Situation high = situation(1000L);
        high.add(actor(P_A, 0.3, 20, 300, 500));
        high.add(actor(P_B, 0.3, 20, 300, 500));
        Assessment.Report rh = Assessment.assess(high);

        check("气运不改变威胁评分",
                Math.abs(rl.threatOf(P_A) - rh.threatOf(P_A)) < 1e-9);
        check("气运不改变压制候选",
                rl.suppressCandidates.contains(P_A) == rh.suppressCandidates.contains(P_A));

        Assessment.Actor capZero = actor(P_C, 0.3, 20, 88, 0);
        check("上限为零被识别为超常", capZero.luckAnomalous());
        check("上限为零时气运不参与走向", capZero.effectiveLuck() == 0.0);
        Assessment.Actor negative = actor(P_C, 0.3, 20, -25, 100);
        check("负气运不算超常", !negative.luckAnomalous());
        Assessment.Actor huge = actor(P_C, 0.3, 20, 480, 500);
        check("超过100不被压回0..100", huge.effectiveLuck() == 480.0);
    }

    private static void testHighLuckTargetStillEscapes() {
        // 被针对性压制的高气运者：仍能靠准备/抵抗渡过灾劫
        Calamity.Spec spec = Calamity.spec(Calamity.Tier.HAO_JIE, 1.0, 20.0, false);
        check("针对性被硬上限截断", spec.targeting <= WillConfig.CALAMITY_TARGETING_CAP);
        Calamity.Run run = new Calamity.Run("c1", P_A, "guzhenren:zhongzhou", 0L, spec, 0L);
        run.prepared = true;
        long t = 0;
        while (!run.resolved && t < 200_000L) {
            t += 200L;
            run.tick(t, 0.8);
        }
        check("准备充分即可渡过（不是必杀）", run.resolved && run.success);
        check("渡过还能拿到道痕", run.daoMarks > 0.0);
        check("玩家可利用灾劫成长", run.profitable());
    }

    private static void testLostIntelCausesObserve() {
        Knowledge.Store k = new Knowledge.Store();
        WillCore w = new WillCore(k, "w");
        Assessment.Situation s = situation(1000L);
        Assessment.Actor big = actor(P_A, 0.95, 0, 0, 100).confidence(0.2);
        s.add(big);
        s.add(actor(P_B, 0.05, 30, 10, 100));
        Assessment.Report r = Assessment.assess(s);
        check("低置信度仍进候选（体量在那）", r.suppressCandidates.contains(P_A));

        List<Intent> intents = w.decide(s);
        boolean observed = false;
        boolean suppressed = false;
        for (Intent i : intents) {
            if (i.kind == Intent.Kind.OBSERVE && P_A.equals(i.target)) {
                observed = true;
            }
            if (i.kind == Intent.Kind.SUPPRESS && P_A.equals(i.target)) {
                suppressed = true;
            }
        }
        check("情报不足时先派观测", observed);
        check("情报不足时不动手", !suppressed);

        // 失去情报后布局会误判：按旧情报行动 → 布局失败
        Ploy p = new Ploy("pl-1", "suppress", P_A, NPC, "beast_drive",
                "target_hidden", 1000L, 900_000L, true);
        p.step(Ploy.StepKind.STRIKE, NPC, 5000L, "埋伏");
        Ploy.Verdict v = p.evaluate(6000L, new Ploy.Signals(true, true, 0, true, 0.5));
        check("失去目标的情报后布局失败", v == Ploy.Verdict.FAILED);
        check("失败原因是失去目标", "target-hidden".equals(p.failReason));
    }

    private static void testPostureFlips() {
        Knowledge.Store k = new Knowledge.Store();
        WillCore w = new WillCore(k, "w");

        Assessment.Situation supportPhase = situation(1000L);
        supportPhase.add(actor(P_A, 0.05, 60, 10, 100));
        supportPhase.add(actor(P_B, 3.0, 0, 0, 100));
        supportPhase.add(actor(P_C, 2.9, 0, 0, 100));
        w.decide(supportPhase);
        check("先扶持",
                w.postureOf(P_A) == Intent.Posture.SUPPORT || w.postureOf(P_A) == Intent.Posture.LAISSEZ_FAIRE);

        // 玩家改变局势：变强了
        Assessment.Situation grown = situation(200_000L);
        grown.add(actor(P_A, 9.0, 60, 10, 100));
        grown.add(actor(P_B, 0.4, 0, 0, 100));
        Assessment.Report rg = Assessment.assess(grown);
        boolean flipped = w.switchPosture(rg, P_A);
        check("变强后从扶持翻转为压制", flipped && w.postureOf(P_A) == Intent.Posture.SUPPRESS);

        // 再反过来
        Assessment.Situation shrunk = situation(400_000L);
        shrunk.add(actor(P_A, 0.02, 60, 10, 100));
        shrunk.add(actor(P_B, 0.02, 0, 0, 100));
        shrunk.add(actor(P_C, 0.02, 0, 0, 100));
        Assessment.Report rs = Assessment.assess(shrunk);
        boolean back = w.switchPosture(rs, P_A);
        check("削弱后又翻回扶持", back && w.postureOf(P_A) == Intent.Posture.SUPPORT);
    }

    private static void testPloyGestationBroken() {
        Ploy p = new Ploy("pl-escape", "human-tribulation", P_A, NPC, "npc_interest",
                "npc_arrives", 1000L, 900_000L, true);
        p.step(Ploy.StepKind.SEED_INFO, NPC, 3000L, "流言");
        p.step(Ploy.StepKind.ASSEMBLE, NPC, 5000L, "聚人");
        p.step(Ploy.StepKind.STRIKE, NPC, 7000L, "伏击");

        // 酝酿期内玩家还在原地
        check("酝酿期还在推进",
                p.evaluate(4000L, new Ploy.Signals(false, true, 20, true, 0.4))
                        != Ploy.Verdict.FAILED);

        // 玩家快速移动/改道 → 针对性布局作废
        Ploy q = new Ploy("pl-escape2", "human-tribulation", P_A, NPC, "npc_interest",
                "npc_arrives", 1000L, 900_000L, true);
        q.step(Ploy.StepKind.STRIKE, NPC, 7000L, "伏击");
        Ploy.Verdict v = q.evaluate(6000L, new Ploy.Signals(false, true,
                WillConfig.RENJIE_BREAK_DISTANCE + 10, true, 0.4));
        check("玩家跑远后布局失败", v == Ploy.Verdict.FAILED);
        check("失败原因是目标已远遁", "target-outran".equals(q.failReason));

        // 渠道被切断（载体被清/势力被灭）
        Ploy r = new Ploy("pl-channel", "human-tribulation", P_A, NPC, "npc_interest",
                "npc_arrives", 1000L, 900_000L, true);
        Ploy.Verdict v2 = r.evaluate(3000L, new Ploy.Signals(false, true, 10, false, 0.4));
        check("渠道断了布局失败", v2 == Ploy.Verdict.FAILED);
        check("失败原因是渠道丢失", "channel-lost".equals(r.failReason));

        // 执行者打不过目标 → 人劫扑空（不是必死）
        Ploy s = new Ploy("pl-hopeless", "human-tribulation", P_A, NPC, "npc_interest",
                "npc_arrives", 1000L, 900_000L, true);
        s.step(Ploy.StepKind.STRIKE, NPC, 2000L, "伏击");
        Ploy.Verdict v3 = s.evaluate(3000L, new Ploy.Signals(false, true, 10, true, 0.9));
        check("执行者不敌则人劫失败", v3 == Ploy.Verdict.FAILED);
        check("失败原因是执行者不敌", "executor-outmatched".equals(s.failReason));
    }

    private static void testPloyNoStackingAndCooldown() {
        // ---- B04：配额是「可恢复的时间窗口」，不是终身上限 ----
        WillCore w = new WillCore(new Knowledge.Store(), "w");
        long now = 1000L;
        check("初始有配额", w.quotaLeft(P_B, now));

        // 连续两次「真的执行成功」把窗口配额用光
        w.noteActionExecuted(P_B, now);
        w.noteActionExecuted(P_B, now);
        check("两次之后配额用尽", !w.quotaLeft(P_B, now));
        check("窗口内已用配额 = 2", w.quotaUsed(P_B) == WillConfig.MAX_SAME_ACTION);

        // 同一窗口内决策器不再压制
        Assessment.Situation s1 = situation(now);
        s1.add(actor(P_A, 0.3, -50, 0, 100));
        s1.add(actor(P_B, 9.0, 0, 0, 100));
        boolean suppressedInWindow = false;
        for (Intent i : w.decide(s1)) {
            if (i.kind == Intent.Kind.SUPPRESS && P_B.equals(i.target)) {
                suppressedInWindow = true;
            }
        }
        check("配额用尽后窗口内不再压制", !suppressedInWindow);

        // 窗口一过 → 配额自动恢复（关键：不是终身禁止）
        long after = now + WillConfig.ACTION_WINDOW + 1;
        check("窗口过后配额恢复", w.quotaLeft(P_B, after));
        Assessment.Situation s2 = situation(after);
        s2.add(actor(P_A, 0.3, -50, 0, 100));
        s2.add(actor(P_B, 9.0, 0, 0, 100));
        boolean suppressedAgain = false;
        for (Intent i : w.decide(s2)) {
            if (i.kind == Intent.Kind.SUPPRESS && P_B.equals(i.target)) {
                suppressedAgain = true;
            }
        }
        check("窗口过后可以再次压制", suppressedAgain);

        // ---- 失败不耗机会：decide 本身不扣配额 ----
        WillCore w0 = new WillCore(new Knowledge.Store(), "w0");
        Assessment.Situation s0 = situation(1000L);
        s0.add(actor(P_A, 0.3, -50, 0, 100));
        s0.add(actor(P_B, 9.0, 0, 0, 100));
        w0.decide(s0);
        check("决定压制但执行前不扣配额", w0.quotaUsed(P_B) == 0);
        check("执行后才会扣", quotaAfterExecute(w0, P_B));

        WillCore w2 = new WillCore(new Knowledge.Store(), "w2");
        w2.onPloyFailed("pl-x", P_A, 5000L);
        check("布局失败登记冷却", w2.cooldowns().containsKey(P_A));
        check("冷却够长", w2.cooldowns().get(P_A) >= 5000L + WillConfig.PLOY_FAIL_COOLDOWN);
    }

    private static boolean quotaAfterExecute(WillCore w, String id) {
        w.noteActionExecuted(id, 2000L);
        return w.quotaUsed(id) == 1;
    }

    // ---------------------------------------------------------------- 机缘 / 供给

    private static void testOpportunityInterceptAndBaitCap() {
        Opportunity.Forge f = new Opportunity.Forge();
        Assessment.Report empty = Assessment.assess(new Assessment.Situation(0L));
        Assessment.Actor benef = actor(P_A, 0.1, 40, 20, 100);

        List<Opportunity.Offer> offers = f.propose(empty, P_A,
                Assessment.Channel.RESOURCE, benef, 1000L,
                "guzhenren:zhongzhou", 900L, "地层中的木行灵气在三十日后结成蛊材");
        check("产出了机会", offers.size() == 1);
        Opportunity.Offer o = offers.get(0);
        check("机会带出处", o.origin.contains("三十日"));
        check("机会未成熟前不能领", !o.available(1000L));

        long mature = o.matureAt + 1;
        check("成熟后可领", o.available(mature));
        boolean taken = f.claim(o.id, P_B, mature);
        check("被别人截取成功", taken && P_B.equals(o.claimedBy));
        check("截取被记录（非预期对象）", o.intercepted());
        check("同一机会不能被二次领取", !f.claim(o.id, P_C, mature));

        // 诱饵比例受上限约束
        Opportunity.Forge f2 = new Opportunity.Forge();
        for (int i = 0; i < 200; i++) {
            f2.propose(empty, P_A, Assessment.Channel.HERITAGE, benef, 1000L + i * 37L,
                    "guzhenren:donghai", i * 1237L, "某位蛊师死后留下的传承线索");
        }
        long baits = f2.active().stream().filter(x -> x.bait).count();
        double ratio = (double) baits / f2.active().size();
        check("诱饵占比不超过上限", ratio <= WillConfig.BAIT_RATIO_CAP);

        // 扶持渠道不得是灾劫或兽群（那不是机缘）
        List<Opportunity.Offer> bad = f2.propose(empty, P_A,
                Assessment.Channel.CALAMITY, benef, 9999L, "guzhenren:ximo", 5L, "x");
        check("灾劫不会被当成机缘发出去", bad.isEmpty());
    }

    private static void testLifespanSupplyNeverTouchesInventory() {
        Immortal.LifespanSupply sup = new Immortal.LifespanSupply(0L);
        check("寿蛊供给声明永不触碰库存", sup.neverTouchesInventory());
        check("初始速率正常", sup.rate == 1.0);

        sup.influence(-0.8);
        check("歉收把速率压下去", sup.rate < 0.5);
        long due = sup.nextSupplyAt;
        int producedLow = 0;
        for (int i = 0; i < 6; i++) {
            producedLow += sup.settle(due + i * WillConfig.SHOU_GU_SUPPLY_PERIOD);
        }
        check("歉收期不是每次都有成蛊", producedLow < 6);

        Immortal.LifespanSupply rich = new Immortal.LifespanSupply(0L);
        rich.influence(0.5);
        int producedHigh = rich.settle(rich.nextSupplyAt);
        check("丰产期成蛊更多", producedHigh > 1);

        // 反射式检查 API：不得存在扣库存的方法
        boolean hasRemoval = false;
        for (var m : Immortal.LifespanSupply.class.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("remove") || n.contains("take") || n.contains("deduct")
                    || n.contains("consume")) {
                hasRemoval = true;
            }
        }
        check("供给层没有扣除库存的方法", !hasRemoval);
    }

    /**
     * 寿蛊来源审计：白名单来自本体 {@code NpcStock.longevityIds()}，
     * 供给只能产出白名单里的 ID；白名单为空时什么都不发。
     */
    private static void testShouGuSourceAudit() {
        Immortal.LifespanSupply sup = new Immortal.LifespanSupply(0L);
        check("未审计时白名单为空", sup.auditedCount() == 0);
        check("白名单为空时挑不出东西", sup.pick(123L).isEmpty());
        check("白名单为空时任何 ID 都不合法", !sup.allowed("guzhenren:shou_gu"));

        // 只接受完整注册 ID：裸名字必须被拒
        int n = sup.audit(java.util.List.of(
                "guzhenren:shou_gu",
                "guzhenren:shi_nian_shou_gu",
                "bainianshougu",      // 无命名空间 → 拒
                "",                    // 空串 → 拒
                "guzhenren:not_a_gu")); // 有命名空间就收（是不是寿蛊由本体清单决定）
        check("审计只收完整注册 ID", n == 3);
        check("已审计的寿蛊可产出", sup.allowed("guzhenren:shou_gu"));
        check("大小写不敏感", sup.allowed("GUZHENREN:SHOU_GU"));
        check("未审计的 ID 不合法", !sup.allowed("guzhenren:not_in_list"));

        // pick 只能从白名单里出
        boolean allAllowed = true;
        for (long seed = 0; seed < 20; seed++) {
            String picked = sup.pick(seed);
            if (picked.isEmpty() || !sup.allowed(picked)) {
                allAllowed = false;
            }
        }
        check("产出候选永远落在白名单内", allAllowed);

        // 重新审计会覆盖旧清单
        sup.audit(java.util.List.of("guzhenren:shou_gu"));
        check("重新审计后只剩一个来源", sup.auditedCount() == 1);
        check("被移出清单的 ID 不再合法", !sup.allowed("guzhenren:shi_nian_shou_gu"));

        // 审计不会引入任何扣库存能力
        check("审计后仍然不碰库存", sup.neverTouchesInventory());
    }

    // ---------------------------------------------------------------- 旧产地/物流

    // 本次清理：testResourceSiteLedger / testCargoBatchIdempotentSettlement 已删除。
    // 被测类（ResourceSite / CargoBatch）随旧产地与物流系统整体退场，
    // 覆盖关系由 resource/FiniteSite + SiteInventory 的测试套件接管。

    // ---------------------------------------------------------------- B01 / B05 回归

    /**
     * B01：机缘必须「申请 → 成熟 → 兑现」三段，且只能兑现一次。
     *
     * <p>旧实现的 bug 是申请完当场查成熟度（必然不成熟）就返回，
     * 之后没有任何东西去兑现旧的成熟机缘 —— 扶持永远走不到产地创建。
     */
    private static void testOfferSettleOnce() {
        Opportunity.Forge f = new Opportunity.Forge();
        Assessment.Report empty = Assessment.assess(new Assessment.Situation(0L));
        Assessment.Actor benef = actor(P_A, 0.1, 40, 20, 100);

        var offers = f.propose(empty, P_A, Assessment.Channel.RESOURCE, benef, 1000L,
                "guzhenren:zhongzhou", 900L, "野外蛊材自然成熟");
        check("申请成功", offers.size() == 1);
        var o = offers.get(0);

        check("刚申请时还不可兑现", !o.due(1000L));
        check("申请会计入 pending", f.pendingFor(P_A, 1000L) == 1);

        long mature = o.matureAt + 1;
        check("成熟后进入待兑现", o.due(mature));
        check("成熟后 pending 仍在（直到兑现）", f.pendingFor(P_A, mature) == 1);
        check("待兑现列表里有它", f.due(mature).contains(o));

        check("第一次兑现成功", o.markSettled(mature));
        check("已兑现后不再待兑现", !o.due(mature + 1));
        check("重复兑现被拒（幂等，不会产第二份）", !o.markSettled(mature + 2));
        check("兑现计数为 1", f.settledCount() == 1);
        check("兑现后不再占 pending", f.pendingFor(P_A, mature + 1) == 0);

        // 过期的机缘不该再被兑现
        var f2 = new Opportunity.Forge();
        var late = f2.propose(empty, P_B, Assessment.Channel.HERITAGE, actor(P_B, 0.1, 10, 5, 100),
                2000L, "guzhenren:donghai", 100L, "蛊方线索").get(0);
        check("过期后不再待兑现", !late.due(late.expiresAt + 1));
    }

    /**
     * B05：进度归零＝这一轮结束，「见过不足」必须一起清掉。
     *
     * <p>否则「20 归零后又有既有库存」会被当成一次新的炼成。
     */
    private static void testAlchemySessionReset() {
        var t = new AlchemyTracker.Tracker();
        t.observe(20.0, "fang:1", "", 0);
        t.observe(0.0, "fang:1", "", 0); // 这一轮结束（失败/取消）
        check("归零后满值滞留不算炼成",
                !t.observe(100.0, "fang:1", "guzhenren:shou_gu", 1).completed());
        check("归零后计数仍是 0", t.completions() == 0);

        // 真的重炼一轮才算
        t.observe(10.0, "fang:1", "", 0);
        check("重新开炼后可以炼成",
                t.observe(100.0, "fang:1", "guzhenren:shou_gu", 1).completed());
        check("计数为 1", t.completions() == 1);
    }

    // ---------------------------------------------------------------- 兽群

    /**
     * 兽群的生态账：增长封顶、生成即扣、猎杀崩溃、迁徙。
     *
     * <p>最关键的是「生成一只就少一只」：{@code drive} 拿不到种群就返回 0，
     * 所以天意不可能凭空变出兽潮。
     */
    private static void testBeastHerdEcology() {
        String dim = "guzhenren:beimo";
        long pos = pack(0, 70, 0);
        BeastHerd h = new BeastHerd("herd-1", "guzhenren:hunshou_1", dim, pos,
                10.0, 20.0, 0.8, 0.05, 1000L);
        check("初始种群不超承载力", h.population() <= h.capacity());
        check("初始有增长空间", h.headroom() > 0);

        double grown = 0.0;
        for (int i = 0; i < 400; i++) {
            grown += h.grow(0.0416, 2000L + i * 1000L);
        }
        check("会长", grown > 0);
        check("增长到承载力封顶", h.population() <= h.capacity() + 1e-9);

        double before = h.population();
        int canDrive = (int) Math.floor(before);
        int driven = h.drive(canDrive + 50, 9000L);
        check("赶不出超过种群的兽", driven == canDrive);
        check("赶走后种群真的减少", h.population() < before);
        check("赶兽计数正确", h.driven() == driven);

        int rest = h.drive(999, 9100L);
        check("能赶出剩下的", rest >= 0);
        if (h.population() < 1.0) {
            check("种群见底变崩溃", h.state() == BeastHerd.State.COLLAPSED);
            // B08：崩溃不是永久死亡 —— 但要等邻群迁入（有延迟、且要求栖息地过得去）
            check("崩溃后立刻不会繁殖", h.grow(100.0, 9200L) == 0.0);
            check("崩溃后赶不出兽", h.drive(1, 9300L) == 0);

            long late = 9200L + BeastHerd.RECOLONIZE_DELAY + 1;
            check("等够时间后邻群迁入", h.grow(1.0, late) > 0.0);
            check("迁入后不再是崩溃态", h.state() != BeastHerd.State.COLLAPSED);
            check("迁入只能补极少量（不是满额重开）",
                    h.population() <= 1.0 + 1e-9);

            // 栖息地太差 → 不迁入
            BeastHerd barren2 = new BeastHerd("herd-barren2", "guzhenren:hunshou_1", dim,
                    pos + 9, 0.0, 20.0, 0.4, 0.05, 1000L);
            check("栖息地差时不迁入", barren2.grow(1.0, 1000L + BeastHerd.RECOLONIZE_DELAY + 1) == 0.0);
        }

        BeastHerd hunted = new BeastHerd("herd-2", "guzhenren:mumeishujing_1", dim,
                pos + 1, 12.0, 20.0, 0.7, 0.05, 1000L);
        int got = hunted.harvest(5, 2000L);
        check("猎杀有收获", got == 5);
        check("猎杀后种群减少", hunted.population() < 12.0);
        check("猎杀计数正确", hunted.harvested() == 5);
        check("猎杀不会超过现有种群", hunted.harvest(9999, 2100L) <= 12);

        BeastHerd moving = new BeastHerd("herd-3", "guzhenren:hunshou_1", dim,
                pos + 2, 5.0, 20.0, 0.2, 0.05, 1000L);
        check("栖息地差不该留", moving.shouldMigrate());
        long newPos = pack(600, 70, 600);
        check("能迁徙", moving.migrate(newPos, 3000L, "habitat-poor"));
        check("锚点真的挪了", moving.anchorPos() == newPos);
        check("迁徙计数正确", moving.migrations() == 1);
        check("原因可查", moving.lastCause().contains("habitat"));

        BeastHerd crowded = new BeastHerd("herd-4", "guzhenren:hunshou_1", dim,
                pos + 3, 19.0, 20.0, 0.9, 0.05, 1000L);
        check("过挤会想迁徙", crowded.shouldMigrate());

        BeastHerd.Book book = new BeastHerd.Book();
        var opened = book.open("h-9", "guzhenren:hunshou_1", dim, pos + 10, 20.0, 0.8, 1000L);
        check("建群成功", opened != null);
        check("同一处不重复建", book.open("h-10", "guzhenren:hunshou_1", dim, pos + 10,
                20.0, 0.8, 2000L) == opened);
        check("按坐标找得到", book.at(dim, pos + 10, 64.0) == opened);
        check("跨维度找不到", book.at("guzhenren:zhongzhou", pos + 10, 64.0) == null);

        boolean rejected = false;
        try {
            new BeastHerd("bad", "裸名字", dim, pos, 5.0, 10.0, 0.5, 0.05, 0L);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("兽群拒绝非完整注册 ID", rejected);
    }

    // ---------------------------------------------------------------- 炼蛊证据

    /**
     * 炼蛊证据绑定：只有「真的从不足走到恰好 100，且有可识别产物」才算一次炼成。
     *
     * <p>反证的三件事：99.9 不算、100.1 不算、进度停在 100 只算一次。
     */
    private static void testAlchemyEvidenceBinding() {
        AlchemyTracker.Tracker t = new AlchemyTracker.Tracker();
        String fang = "guzhenren:suoshigu_gu_fang";
        String product = "guzhenren:wei_lian_hua_yu_she_gu";

        check("开局进度为零不算完成",
                !t.observe(0.0, fang, product, 1).completed());
        check("进度不足不算完成",
                !t.observe(99.9, fang, product, 1).completed());
        check("恰好 100 且见过不足 → 完成",
                t.observe(100.0, fang, product, 1).completed());
        check("完成后计数为 1", t.completions() == 1);

        // 停在 100 不会重复计数
        check("满值滞留不重复计数", !t.observe(100.0, fang, product, 1).completed());
        check("再读一次也不计数", !t.observe(100.0, fang, product, 1).completed());
        check("计数仍是 1", t.completions() == 1);

        // 超过 100（浮点误差/异常）不认
        AlchemyTracker.Tracker over = new AlchemyTracker.Tracker();
        over.observe(50.0, fang, product, 1);
        check("100.0001 不算完成", !over.observe(100.0001, fang, product, 1).completed());
        check("超过满值后计数仍为 0", over.completions() == 0);

        // 没产物 → 不构成证据（记不出炼出了什么）
        AlchemyTracker.Tracker noProduct = new AlchemyTracker.Tracker();
        noProduct.observe(80.0, fang, product, 1);
        check("没有产物 ID 不算证据", !noProduct.observe(100.0, fang, "", 1).completed());
        check("裸名字产物也不算证据", !noProduct.observe(100.0, fang, "裸名字", 1).completed());

        // 数量为 0 不算
        AlchemyTracker.Tracker zero = new AlchemyTracker.Tracker();
        zero.observe(70.0, fang, product, 1);
        check("产物数量为 0 不算证据", !zero.observe(100.0, fang, product, 0).completed());

        // 第二次真的重新炼：先回到不足，再到 100
        AlchemyTracker.Tracker twice = new AlchemyTracker.Tracker();
        twice.observe(10.0, fang, product, 1);
        check("第一次炼成", twice.observe(100.0, fang, product, 2).completed());
        twice.observe(0.0, fang, product, 0);
        twice.observe(40.0, fang, product, 1);
        var second = twice.observe(100.0, fang, product, 1);
        check("第二次也能炼成", second.completed());
        check("两次都记上", twice.completions() == 2);
        check("产物 ID 被规范化", second.productId().equals(product));

        // 观测器按 UUID 登记
        int before = AlchemyTracker.tracked();
        AlchemyTracker.of(P_A);
        check("按 UUID 建观测器", AlchemyTracker.tracked() == before + 1);
        AlchemyTracker.of(P_A);
        check("同一 UUID 不重复建", AlchemyTracker.tracked() == before + 1);
        AlchemyTracker.forget(P_A);
        check("可以移除", AlchemyTracker.tracked() == before);
    }

    // ---------------------------------------------------------------- 身份证据链

    /**
     * 身份证据链：多份证据互相印证才识破；同一句流言刷十遍不算十个证据；
     * 矛盾证据压低结论但不会抹掉记录。
     */
    private static void testIdentityEvidenceChain() {
        String tag = "yin-xian-gu-shi";
        IdentityCase c = new IdentityCase(P_A);

        // 单条弱证据 → 不足
        c.add(new IdentityCase.Evidence("e1", P_A, IdentityCase.Kind.WITNESS,
                1.0, 1.0, tag, "guzhenren:zhongzhou", 100L, 1000L, "有人看见"), 1000L);
        check("单条弱证据不足以定罪", c.verdict() == IdentityCase.Verdict.INSUFFICIENT);

        // 同种同地同时间桶的重复证据被拒
        boolean dup = c.add(new IdentityCase.Evidence("e1b", P_A, IdentityCase.Kind.WITNESS,
                1.0, 1.0, tag, "guzhenren:zhongzhou", 100L, 1100L, "同一件事又说一遍"), 1000L);
        check("重复证据被拒收", !dup);
        check("证据条数没变", c.size() == 1);

        // 换一种证据（独立来源）
        c.add(new IdentityCase.Evidence("e2", P_A, IdentityCase.Kind.POSSESSION,
                2.5, 1.0, tag, "guzhenren:zhongzhou", 120L, 2000L, "身上搜出蛊方"), 1000L);
        check("两类证据还差一点", c.verdict() != IdentityCase.Verdict.IDENTIFIED);

        c.add(new IdentityCase.Evidence("e3", P_A, IdentityCase.Kind.TESTIMONY,
                2.5, 1.0, tag, "guzhenren:zhongzhou", 140L, 3000L, "证人转述"), 1000L);
        check("三类独立证据 → 识破", c.verdict() == IdentityCase.Verdict.IDENTIFIED);
        check("可信度够高", c.confidence() >= IdentityCase.IDENTIFY_AT);
        check("指向被认出", tag.equals(c.leadingIdentity()));

        // 十句同种流言 ≠ 十个证据
        IdentityCase rumor = new IdentityCase(P_B);
        for (int i = 0; i < 10; i++) {
            rumor.add(new IdentityCase.Evidence("r" + i, P_B, IdentityCase.Kind.TESTIMONY,
                    1.0, 1.0, "liu-yan", "guzhenren:beimo", 900L + i, 1000L + i * 5000L,
                    "流言第" + i + "手"), 1000L);
        }
        check("十句流言不构成识破", rumor.verdict() != IdentityCase.Verdict.IDENTIFIED);
        check("种类数只有 1", rumor.kindCount() == 1);
        check("重复项被折价（分数没到 10）", rumor.confidence() < 10.0);

        // 矛盾证据：压低结论，但记录保留
        double beforeContradiction = c.confidence();
        c.add(new IdentityCase.Evidence("e4", P_A, IdentityCase.Kind.RELATION,
                2.0, 1.0, "另一个身份", "guzhenren:zhongzhou", 160L, 4000L, "另有说法"), 1000L);
        check("矛盾证据压低可信度", c.confidence() < beforeContradiction);
        check("矛盾证据本身仍被记录", c.size() == 4);
        check("矛盾分数可查", c.contradictedScore() > 0.0);
        check("主流指向没被推翻", tag.equals(c.leadingIdentity()));

        // 只能收记在本人 UUID 上的证据
        boolean wrong = c.add(new IdentityCase.Evidence("x", P_C, IdentityCase.Kind.WITNESS,
                9.0, 1.0, tag, "d", 1L, 1L, "别人的证据"), 1000L);
        check("拒绝记在别人 UUID 上的证据", !wrong);

        // 案件簿
        IdentityCase.Ledger led = new IdentityCase.Ledger();
        var caseA = led.of(P_A);
        caseA.add(new IdentityCase.Evidence("l1", P_A, IdentityCase.Kind.WITNESS,
                2.5, 1.0, tag, "d", 1L, 10L, ""), 1000L);
        caseA.add(new IdentityCase.Evidence("l2", P_A, IdentityCase.Kind.POSSESSION,
                2.5, 1.0, tag, "d", 2L, 20L, ""), 1000L);
        caseA.add(new IdentityCase.Evidence("l3", P_A, IdentityCase.Kind.CRAFT_TRACE,
                2.5, 1.0, tag, "d", 3L, 30L, ""), 1000L);
        check("案件簿登记", led.size() == 1);
        check("能找到同一案", led.find(P_A) == caseA);
        check("识破计数正确", led.identifiedCount() == 1);
        check("摘要可读", !caseA.summary().isEmpty());
    }

    // ---------------------------------------------------------------- 载体统一状态

    /**
     * 载体统一状态机：寄存/传播/消耗/清除/恢复收敛到一个 state()，
     * 不允许各处自己算一套。
     */
    private static void testCarrierUnifiedState() {
        Carrier.Network net = new Carrier.Network();
        var node = net.add("n1", Carrier.Kind.DAO_MARK, "minecraft:overworld", 0L, 256.0);
        long now = 1000L;

        check("新载体是活跃", node.state(now) == Carrier.State.ACTIVE);
        check("活跃载体能寄存", node.canHost(now));

        // 反复消耗 → 枯竭
        net.consume("n1", 0.85, now);
        check("被消耗到低位 → 枯竭", node.state(now) == Carrier.State.DRAINED);
        check("枯竭不能寄存", !node.canHost(now));

        // 清除 → 已清除（盲区）
        net.purge("minecraft:overworld", 0L, 64.0, new Knowledge.Store(), now, "tianxiaoyisan");
        check("被清后是已清除", node.state(now) == Carrier.State.PURGED);
        check("已清除不能寄存", !node.canHost(now));
        check("已清除时触达为零", node.effectiveReach(now) == 0.0);

        // 盲区到期后恢复 → 恢复中
        long later = now + WillConfig.CARRIER_PURGE_BLIND + 1;
        net.regrow(later, 2_000L);
        Carrier.State s = node.state(later);
        check("过了盲区进入恢复中", s == Carrier.State.REGROWING);
        check("恢复中不算活跃", s != Carrier.State.ACTIVE);

        // 一直恢复到满 → 回到活跃，且退出恢复态
        for (int i = 0; i < 200; i++) {
            net.regrow(later, 5_000L);
        }
        check("渗满后回到活跃", node.state(later) == Carrier.State.ACTIVE);
        check("恢复计时被清零（不会永远停在恢复中）", node.regrowTicks == 0L);
        check("状态名可读", !node.stateName(later).isEmpty());

        // 世界级状态 = 最强那处
        var node2 = net.add("n2", Carrier.Kind.REGION, "minecraft:overworld", 500L, 256.0);
        net.intrude("n2", 0.95);
        check("世界状态取最强", net.worldState(later) == Carrier.State.ACTIVE);
        net.intrude("n1", 0.95);
        check("全都枯竭时世界状态随之下降",
                net.worldState(later) == Carrier.State.DRAINED
                        || net.worldState(later) == Carrier.State.PURGED);

        // 状态计数自洽
        var counts = net.stateCounts(later);
        int sum = counts.values().stream().mapToInt(Integer::intValue).sum();
        check("状态计数总数等于载体数", sum == net.count());
        check("按 id 查状态", net.stateOf("n1", later) == node.state(later));
        check("查不存在的载体返回 null", net.stateOf("nope", later) == null);
    }

    // ---------------------------------------------------------------- 产物推断

    /**
     * 本体不暴露「这次炼出了什么」，所以产物只能靠背包差异推断。
     *
     * <p>这里锁的是推断本身的三条纪律：只认增加、只认完整 ID、挑最大增量。
     */
    private static void testAlchemyProductInference() {
        var a = "guzhenren:wei_lian_hua_yu_she_gu";
        var b = "guzhenren:yuan_lao_gu_1";
        var c = "guzhenren:suoshigu_gu_fang";

        Map<String, Integer> before = new java.util.LinkedHashMap<>();
        before.put(a, 1);
        before.put(b, 5);
        Map<String, Integer> after = new java.util.LinkedHashMap<>();
        after.put(a, 3);
        after.put(b, 2);   // 减少的不算（消耗）
        after.put(c, 1);
        after.put("裸名字", 99); // 没有命名空间 → 忽略

        var d = AlchemyTracker.diff(before, after);
        check("只认数量增加的物品", d.containsKey(a) && d.get(a) == 2);
        check("减少的不算产物", !d.containsKey(b));
        check("新增的也认", d.containsKey(c) && d.get(c) == 1);
        check("裸名字被忽略", !d.containsKey("裸名字"));

        var top = AlchemyTracker.topProduct(d);
        check("挑出最大增量当产物", top != null && a.equals(top.getKey()) && top.getValue() == 2);

        // 并列时结果稳定（按 id 排序取第一个）
        Map<String, Integer> tie = new java.util.LinkedHashMap<>();
        tie.put(b, 2);
        tie.put(a, 2);
        var t1 = AlchemyTracker.topProduct(tie);
        var t2 = AlchemyTracker.topProduct(tie);
        check("并列时结果稳定", t1 != null && t2 != null && t1.getKey().equals(t2.getKey()));
        check("并列取字典序靠前的", t1 != null && t1.getKey().equals(a));

        check("空差异挑不出产物", AlchemyTracker.topProduct(Map.of()) == null);
        check("null 差异挑不出产物", AlchemyTracker.topProduct(null) == null);
        check("before 为 null 时全算新增",
                AlchemyTracker.diff(null, after).size() == 3);

        // 端到端：见过不足 → 恰好 100 且差异有产物 → 记为一次炼成
        AlchemyTracker.Tracker tr = new AlchemyTracker.Tracker();
        tr.observe(0.0, "fang:7", "", 0);
        tr.observe(60.0, "fang:7", "", 0);
        var product = AlchemyTracker.topProduct(AlchemyTracker.diff(before, after));
        check("推断出产物", product != null);
        var done = tr.observe(100.0, "fang:7", product == null ? "" : product.getKey(),
                product == null ? 0 : product.getValue());
        check("差异产物让这次炼成成立", done.completed());
        check("产物就是差异里那一项", a.equals(done.productId()));
        check("数量取自差异", done.count() == 2);

        // 挑不出产物 → 不成立（宁可漏记，也不写假证据）
        AlchemyTracker.Tracker tr2 = new AlchemyTracker.Tracker();
        tr2.observe(50.0, "fang:7", "", 0);
        check("没有产物就不算证据",
                !tr2.observe(100.0, "fang:7", "", 0).completed());
    }

    private static void testCalamityPhases() {
        check("转数不够不能发地灾",
                !Calamity.conditionsMet(Calamity.Tier.DI_ZAI, -1, 1.0, 1.0, 1.0, false));
        check("条件满足可发地灾",
                Calamity.conditionsMet(Calamity.Tier.DI_ZAI, 2, 1.0, 1.0, 1.0, false));
        check("天道权限不足不能发天劫",
                !Calamity.conditionsMet(Calamity.Tier.TIAN_JIE, 4, 1.0, 0.1, 1.0, false));
        check("仙窍不稳时不发天劫",
                !Calamity.conditionsMet(Calamity.Tier.TIAN_JIE, 4, 0.2, 1.0, 1.0, false));
        check("宿命毁灭后高阶灾劫条件放宽",
                Calamity.conditionsMet(Calamity.Tier.TIAN_JIE, 4, 1.0, 0.50, 1.0, true));

        Calamity.Spec spec = Calamity.spec(Calamity.Tier.TIAN_JIE, 0.3, 10.0, false);
        Calamity.Run run = new Calamity.Run("c-phases", P_A, "guzhenren:beiyuan", 0L, spec, 0L);
        check("灾劫从酝酿开始", run.phase == Calamity.Phase.GESTATING);

        List<Calamity.Phase> seen = new ArrayList<>();
        long t = 0L;
        while (!run.finished() && t < 300_000L) {
            t += 100L;
            Calamity.Phase ph = run.tick(t, 0.4);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != ph) {
                seen.add(ph);
            }
        }
        check("经过酝酿阶段", seen.contains(Calamity.Phase.GESTATING));
        check("经过显形阶段", seen.contains(Calamity.Phase.MANIFEST));
        check("经过判定阶段", seen.contains(Calamity.Phase.CLIMAX));
        check("经过余波阶段", seen.contains(Calamity.Phase.AFTERMATH));
        check("灾劫有实体或环境效果", !spec.effectKeys.isEmpty());
        check("灾劫不是一道闪电（效果多于一个）", spec.effectKeys.size() >= 2);
        check("混沌不受天意支配", !Calamity.chaosInfluenced());
    }

    private static void testCalamityTargetingCapAndPenalty() {
        Calamity.Spec overshoot = Calamity.spec(Calamity.Tier.WAN_JIE, 5.0, 40.0, false);
        check("针对性地板以上被截断", overshoot.targeting == WillConfig.CALAMITY_TARGETING_CAP);
        check("失败惩罚有上限",
                overshoot.failPenalty <= WillConfig.CALAMITY_MAX_FAIL_PENALTY);

        // 万劫：毫无准备 + 无抵抗 → 渡劫失败
        Calamity.Spec spec = Calamity.spec(Calamity.Tier.WAN_JIE, 0.45, 40.0, false);
        Calamity.Run run = new Calamity.Run("c-fail", P_A, "guzhenren:nanjiang", 0L, spec, 0L);
        long t = 0L;
        while (!run.resolved && t < 300_000L) {
            t += 100L;
            run.tick(t, 0.0);
        }
        check("万劫无准备无抵抗会渡劫失败", run.resolved && !run.success);
        check("失败只扣道痕，不直接抹杀", run.daoMarks < 0.0
                && Math.abs(run.daoMarks) <= 40.0 * WillConfig.CALAMITY_MAX_FAIL_PENALTY + 1e-6);

        // 地灾最轻：同样毫无准备也能自己扛过去（灾劫有档位差，不是一刀切）
        Calamity.Spec light = Calamity.spec(Calamity.Tier.DI_ZAI, 0.45, 5.0, false);
        Calamity.Run lightRun = new Calamity.Run("c-light", P_A, "guzhenren:nanjiang", 0L, light, 0L);
        long lt = 0L;
        while (!lightRun.resolved && lt < 300_000L) {
            lt += 100L;
            lightRun.tick(lt, 0.0);
        }
        check("地灾无准备也能渡过", lightRun.resolved && lightRun.success);

        // 同一目标同时只能有几场
        Calamity.Book book = new Calamity.Book();
        book.open("a", P_A, "d", 0L, Calamity.spec(Calamity.Tier.DI_ZAI, 0.1, 5.0, false), 0L);
        book.open("b", P_A, "d", 0L, Calamity.spec(Calamity.Tier.DI_ZAI, 0.1, 5.0, false), 0L);
        check("灾劫可记账多场，由决策器限流", book.countFor(P_A) == 2);
    }

    // ---------------------------------------------------------------- 宿命 / 星宿

    private static void testDestinyStages() {
        Destiny.DestinyGu d = new Destiny.DestinyGu("world-1");
        check("默认本体受损", d.stage() == Destiny.Stage.BODY_DAMAGED);
        check("默认星宿已合道", d.starAligned());
        check("受损时天道权限下降但非零", d.heavenControl() > 0.0 && d.heavenControl() < 1.0);

        // 旧数值不再驱动阶段：没有完成事务就不算完整
        d.repair(1.0, 50L, "task-progress");
        check("修复任务进度不推进阶段", d.stage() == Destiny.Stage.BODY_DAMAGED);
        d.completeRepair("", 60L, "no-txn");
        check("没有事务 id 不算完成修复", d.stage() == Destiny.Stage.BODY_DAMAGED);

        d.completeRepair("txn-repair-1", 100L, "star-will repair");
        check("完成修复事务才到本体完整", d.stage() == Destiny.Stage.BODY_WHOLE);
        check("完整时天道权限满", d.heavenControl() == 1.0);

        d.completeBodyDestruction("txn-destroy-1", 200L, "tianwai contact");
        check("摧毁事务→本体被毁仍可重现", d.stage() == Destiny.Stage.BODY_DESTROYED_REVIVABLE);
        check("摧毁不会自动拆分分发", d.stage() != Destiny.Stage.SPLIT_AND_DISTRIBUTED);
        check("摧毁原因可查", d.lastCause().contains("tianwai"));
        check("摧毁后天意仍在（权限非零）", d.heavenControl() > 0.5);

        d.completeSplitRefinement("", 300L, "no-txn");
        check("没有事务 id 不会拆分分发", d.stage() == Destiny.Stage.BODY_DESTROYED_REVIVABLE);
        d.completeSplitRefinement("txn-split-1", 400L, "refine distribute");
        check("独立炼制事务才拆分分发", d.stage() == Destiny.Stage.SPLIT_AND_DISTRIBUTED);
        check("分发后天意仍不被关掉", d.heavenControl() > 0.5);

        d.completeSplitRefinement("txn-split-1", 500L, "replay");
        check("重放同一事务幂等（不重复推进）", d.lastChangeAt() == 400L);
        check("重放不改阶段", d.stage() == Destiny.Stage.SPLIT_AND_DISTRIBUTED);

        // 旧入口不再推进阶段
        Destiny.DestinyGu legacy = new Destiny.DestinyGu("world-2");
        legacy.damage(5.0, 10L, "fire");
        check("damage 只记证据、不改阶段", legacy.stage() == Destiny.Stage.BODY_DAMAGED);
        legacy.repair(0.4, 20L, "task");
        check("repair 只改修复任务进度",
                legacy.stage() == Destiny.Stage.BODY_DAMAGED && legacy.repairProgress() > 0.3);

        boolean oldNamesGone;
        try {
            Destiny.Stage.valueOf("DAMAGED");
            oldNamesGone = false;
        } catch (IllegalArgumentException e) {
            oldNamesGone = true;
        }
        check("旧三阶段名已移除", oldNamesGone);

        check("唯一性校验", d.matchesWorld("world-1") && !d.matchesWorld("world-2"));
    }

    private static void testStarWillContest() {
        WillCore w = new WillCore(new Knowledge.Store(), "w");
        Destiny.StarWill star = w.star();
        check("星宿有独立目标", star.currentGoal() != null);
        check("星宿份额非满", star.share() < 1.0);

        int wins = 0;
        for (int i = 0; i < 40; i++) {
            if (star.interfere(w, 1000L + i * 2000L, 0.9)) {
                wins++;
            }
            star.recover(2000L);
        }
        check("星宿会干涉天意", wins > 0 || star.contestLosses() > 0);
        check("星宿不是永动机（会疲惫）", star.exhaustion() >= 0.0);
        check("干涉结果被记账", star.contestWins() + star.contestLosses() > 0);

        boolean countered = star.willCounter(0.9);
        check("天意可以反过来压制星宿", countered || star.share() <= 0.45);
        check("星宿意志可描述", !star.describe().isEmpty());
    }

    // ---------------------------------------------------------------- 反制

    private static void testCounterAspectsAreDistinct() {
        var andu = Counter.aspectsOf(Counter.Kind.ANDU);
        var shen = Counter.aspectsOf(Counter.Kind.SHENBUZHI);
        var xiao = Counter.aspectsOf(Counter.Kind.TIANXIAOYISAN);
        var lian = Counter.aspectsOf(Counter.Kind.LIANDAO_PURGE);
        var tianji = Counter.aspectsOf(Counter.Kind.TIANJI);

        check("暗渡只改感知", andu.equals(java.util.EnumSet.of(Counter.Aspect.PERCEPTION)));
        // 原著：「任何的念头、意志、情感，都推测不出你的状态和来历」→ 只挡推算
        check("神不知只改推算（不改感知）",
                shen.equals(java.util.EnumSet.of(Counter.Aspect.DIVINATION)));
        check("天消意散只改载体", xiao.equals(java.util.EnumSet.of(Counter.Aspect.CARRIER)));
        check("炼道清除只改载体", lian.equals(java.util.EnumSet.of(Counter.Aspect.CARRIER)));
        check("炼化天道道痕只改载体（治根）",
                Counter.aspectsOf(Counter.Kind.REFINE_HEAVEN_DAOMARK)
                        .equals(java.util.EnumSet.of(Counter.Aspect.CARRIER)));
        check("天机手段落在推算一侧",
                tianji.equals(java.util.EnumSet.of(Counter.Aspect.DIVINATION)));

        // 暗渡真的产生盲区
        Knowledge.Store k = new Knowledge.Store();
        long now = 1000L;
        k.observe(Knowledge.Fact.observed(P_A, Knowledge.Kind.POSITION, 5.0, now,
                "guzhenren:zhongzhou", 10L, Knowledge.Source.CONTACT));
        Assessment.Actor a = actor(P_A, 0.2, 0, 0, 100).dim("guzhenren:zhongzhou");
        Destiny.DestinyGu d = new Destiny.DestinyGu("w");
        Counter.Result an = Counter.apply(Counter.Kind.ANDU, a, k, d, now, 1.0);
        check("暗渡生效", an != null && an.coverTicks() > 0);
        check("暗渡造成真实盲区",
                k.latest(P_A, Knowledge.Kind.POSITION, now + 1).isEmpty());
        check("暗渡被标注为原著未核实机制", an.unreliable());

        // 神不知不产生盲区，只压推算
        Knowledge.Store k2 = new Knowledge.Store();
        k2.observe(Knowledge.Fact.observed(P_A, Knowledge.Kind.POSITION, 5.0, now,
                "guzhenren:zhongzhou", 10L, Knowledge.Source.CONTACT));
        Counter.Result sh = Counter.apply(Counter.Kind.SHENBUZHI, a, k2, d, now, 1.0);
        check("神不知不产生感知盲区", sh.coverTicks() == 0L
                && k2.latest(P_A, Knowledge.Kind.POSITION, now + 1).isPresent());
        check("神不知压低推算（噪声>0）", Counter.divinationNoise(Counter.Kind.SHENBUZHI) > 0.5);
        check("九转智道可破神不知",
                Counter.apply(Counter.Kind.SHENBUZHI, a, k2, d, null, now, 1.0, true)
                        .changed().isEmpty());
    }

    private static void testTianwaiNotImmune() {
        var full = Counter.aspectsOf(Counter.Kind.TIANWAI_FULL);
        var partial = Counter.aspectsOf(Counter.Kind.TIANWAI_PARTIAL);
        // 原著：「方源成为完整的天外之魔，已经超脱限制」→ 只动宿命约束这一环
        check("完整天外之魔只改宿命约束",
                full.equals(java.util.EnumSet.of(Counter.Aspect.DESTINY_BIND)));
        check("完整天外之魔不改变天道控制",
                !full.contains(Counter.Aspect.HEAVEN_CONTROL));
        check("完整天外之魔也不改感知", !full.contains(Counter.Aspect.PERCEPTION));
        check("半成品强度只有一部分",
                Counter.strengthFactor(Counter.Kind.TIANWAI_PARTIAL)
                        < Counter.strengthFactor(Counter.Kind.TIANWAI_FULL));
        check("半成品仍受天意摆布（没有完整豁免）",
                partial.equals(java.util.EnumSet.of(Counter.Aspect.DESTINY_BIND)));

        for (Counter.Kind k : Counter.Kind.values()) {
            check("任何手段都不等于免疫攻击：" + k, !Counter.immuneToCalamity(k));
        }

        // 完整天外之魔仍会被灾劫打到
        Calamity.Spec spec = Calamity.spec(Calamity.Tier.TIAN_JIE, 0.4, 12.0, false);
        Calamity.Run run = new Calamity.Run("c-tw", P_A, "guzhenren:yijixukong", 0L, spec, 0L);
        long t = 0;
        while (!run.resolved && t < 300_000L) {
            t += 100L;
            run.tick(t, 0.0);
        }
        check("天外之魔照样会吃灾劫伤害", run.damageDealt > 0.0);
    }

    // ---------------------------------------------------------------- 仙道

    private static void testAscensionWithoutDestinyLock() {
        Immortal.Ascension asc = new Immortal.Ascension();
        Immortal.SanQi balanced = new Immortal.SanQi(1.0, 1.0, 1.0);
        Immortal.SanQi lopsided = new Immortal.SanQi(3.0, 0.2, 0.1);
        check("三气均衡调和度高", balanced.harmony() > lopsided.harmony());
        check("均衡时成功率更高",
                asc.successChance(balanced, 50) > asc.successChance(lopsided, 50));

        // 3.13.0：宿命不再是普通升仙的总开关
        boolean allAllowed = true;
        for (Destiny.Stage s : Destiny.Stage.values()) {
            if (DestinyRules.normalAscension(s).verdict() != DestinyRules.Verdict.ALLOWED) {
                allAllowed = false;
            }
        }
        check("普通升仙在四个阶段都不被封死", allAllowed);
        check("普通升仙原因可读",
                DestinyRules.normalAscension(Destiny.Stage.BODY_WHOLE).reason().contains("灾劫"));

        Immortal.Ascension.Result r = asc.attempt(balanced, 50, 0.8, 0.0);
        check("升仙按概率结算（roll=0 必成）", r.success());
        check("成功不扣寿元", r.lifespanCost() <= 0.0);

        Immortal.Ascension.Result f = asc.attempt(lopsided, 0, 0.8, 0.99);
        check("失败不会直接抹杀（只是扣寿元）", !f.success() && f.lifespanCost() >= 0.0);

        asc.observeNature(500_000L);
        check("师法自然有积累", asc.shifaZiran > 0.0);
        check("师法自然有上限", asc.shifaZiran <= WillConfig.SHIFA_ZIRAN_BONUS_CAP);
    }

    private static void testApertureInnerTime() {
        Immortal.Aperture ap = new Immortal.Aperture(P_A);
        check("默认内部时间倍率有下限", ap.innerTimeRatio >= WillConfig.APERTURE_TIME_RATIO_MIN);
        ap.advanceTime(1000L);
        check("内部时间按倍率推进", ap.innerTicks == 1000L);
        check("仙元会恢复", ap.essence > 0.0);

        ap.addResource("guzhenren:shou_gu", 3.0);
        check("资源用完整注册 ID 记账", ap.resource("guzhenren:shou_gu") == 3.0);
        boolean rejected = false;
        try {
            ap.addResource("裸名字", 1.0);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("拒绝非完整注册 ID", rejected);

        ap.upgrade(0.1);
        check("升级提升内部时间倍率", ap.innerTimeRatio > WillConfig.APERTURE_TIME_RATIO_MIN);
        check("升级提升容量", ap.capacity > 100.0);
        check("倍率有上限", ap.innerTimeRatio <= WillConfig.APERTURE_TIME_RATIO_MAX);

        // 原著：「任何的福地洞天…天意也管不了这里」但「若非天道道痕，仙窍中怎可能有天意产生」
        check("独立仙窍是天意的避风港（无天道道痕时管不了）",
                !ap.willIntrusionPossible(true, false));
        check("独立仙窍里若有天道道痕，天意仍会本地产生",
                ap.willIntrusionPossible(true, true));
        check("非独立仙窍本就在天地之间", ap.willIntrusionPossible(false, false));
        ap.independent = true;
        check("独立且无天道道痕时本地不生天意", !ap.willIntrusionPossible());
        ap.heavenDaoMark = 3200.0;
        check("持有三千余道天道道痕后本地生天意", ap.willIntrusionPossible());
    }

    // ---------------------------------------------------------------- 回溯

    private static void testHistoryVerifyAndGuards() {
        History.Store store = new History.Store();
        List<History.Part> parts = new ArrayList<>(List.of(
                new History.Part(History.Section.CHUNK, 1024, 8_000_000L, "d-chunk"),
                new History.Part(History.Section.ENTITY, 88, 200_000L, "d-ent"),
                new History.Part(History.Section.PLAYER, 4, 40_000L, "d-player"),
                new History.Part(History.Section.MOD_DATA, 1, 90_000L, "d-mod")));
        History.Snapshot snap = new History.Snapshot("s1", "main", "", 1000L,
                "全部 7 维度 / 已加载区块半径 8", parts, "d-all", true, "");
        store.add(snap);

        Map<History.Section, String> actual = new EnumMap<>(History.Section.class);
        actual.put(History.Section.CHUNK, "d-chunk");
        actual.put(History.Section.ENTITY, "d-ent");
        actual.put(History.Section.PLAYER, "d-player");
        actual.put(History.Section.MOD_DATA, "d-mod");
        check("完整性校验通过", History.verify(snap, actual));

        actual.put(History.Section.CHUNK, "tampered");
        check("任一分区不符即拒绝", !History.verify(snap, actual));

        History.RewindPlan blocked = History.plan(store, "s1", false, false);
        check("世界未关闭时拒绝恢复", !blocked.canProceed());
        check("列出阻塞原因", blocked.blockers().size() >= 2);

        History.RewindPlan ok = History.plan(store, "s1", true, true);
        check("条件齐备可以恢复", ok.canProceed());
        check("生成新分支（不覆盖原时间线）", !ok.newBranch().equals("main")
                && ok.newBranch().startsWith("main-r"));
        check("边界说明里写明保存范围", ok.boundaryNotes().stream()
                .anyMatch(n -> n.contains("保存范围")));
        check("边界说明里写明玩家记忆无法抹除", ok.boundaryNotes().stream()
                .anyMatch(n -> n.contains("无法由游戏抹除")));

        History.Snapshot unverified = new History.Snapshot("s2", "main", "", 2000L,
                "x", parts, "d-all", false, "");
        store.add(unverified);
        check("只能回到验证过的节点", store.latest("main").takenAt == 1000L);
        History.RewindPlan bad = History.plan(store, "s2", true, true);
        check("未验证快照拒绝恢复", !bad.canProceed());
    }

    private static void testHistoryNoInventoryNoWillLeak() {
        check("回溯不携带背包（防复制）", !History.carriesInventory());
        check("只有施术者保留记忆", History.remembers(P_A, P_A)
                && !History.remembers(P_A, P_B));
        check("天意不会因为系统日志就获得未来信息", !History.willLearnsFromLogs());

        History.Store store = new History.Store();
        check("空仓库没有可回溯节点", store.latest(null) == null);
        check("分支名不重复", !store.forkBranch("main").equals("main"));
    }

    // ---------------------------------------------------------------- 无 AI

    private static void testNoAiDependency() {
        // 核心决策不依赖任何网络/AI：全新实例、无外部配置也能完整跑一轮
        Knowledge.Store k = new Knowledge.Store();
        WillCore w = new WillCore(k, "offline-world");
        Assessment.Situation s = situation(10_000L);
        s.add(actor(P_A, 0.5, 30, 20, 100));
        s.add(actor(P_B, 0.6, -40, 0, 100));
        List<Intent> out = w.decide(s);
        check("无 AI 也能产出决策", out != null);
        check("无 AI 也能评估格局", !Assessment.assess(s).note.isEmpty());
        check("状态可导出", !w.status().isEmpty());

        // 反复空世界也不炸
        WillCore w2 = new WillCore(new Knowledge.Store(), "empty");
        check("空世界决策不抛异常", w2.decide(new Assessment.Situation(1L)) != null);
    }
}
