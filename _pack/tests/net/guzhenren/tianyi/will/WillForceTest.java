package net.guzhenren.tianyi.will;

import java.util.List;

/**
 * 天意强度档位的离线测试（3.14）—— 不依赖 Minecraft / NeoForge。
 *
 * <p>跑法：{@code python _pack/run_tests.py}。这里断言的是「档位真的改变行为」，
 * 不是「档位枚举存在」：
 * <ol>
 *   <li>档位 1 与 3.13 的常量逐项相等（默认档位不得悄悄改行为）；</li>
 *   <li>档位 0 是真的零干预（决策器一条意图都不产出）；</li>
 *   <li>档位 ≥ 2 时既有压制渠道、灾劫渠道真的会被选到；</li>
 *   <li>阈值/配额/周期随档位单调变化；</li>
 *   <li>窗口幂等：同一窗口只命中一次，重启不会补发；</li>
 *   <li>诱饵比例随档位上升但仍受硬上限；兽潮永远不凭空生成。</li>
 * </ol>
 */
public final class WillForceTest {

    private static int pass;
    private static int fail;

    private static final String P_A = "51111111-1111-1111-1111-111111111111";
    private static final String P_B = "52222222-2222-2222-2222-222222222222";

    public static void main(String[] args) {
        testClampAndLabels();
        testGentleMatchesLegacy();
        testOffIsSilent();
        testStrictOpensPressure();
        testThresholdsMoveWithIntensity();
        testDeterministicRollAndEventKey();
        testWindowFiresExactlyOnce();
        testCoreOffProducesNothing();
        testCalamityChannelReachable();
        testBaitRatioScalesButCapped();
        testCalamityScaleAndTargetingCap();
        testBeastWaveNeverInventsBeasts();

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

    /** 与 BlockPos.asLong 一致的打包（测试不能依赖 Minecraft）。 */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static Assessment.Situation situation(long now, WillForce force,
                                                  double powerA, double powerB) {
        Assessment.Situation s = new Assessment.Situation(now).withForce(force);
        s.with(Assessment.Channel.BEAST);
        s.with(Assessment.Channel.RESOURCE);
        s.with(Assessment.Channel.CALAMITY);
        s.add(new Assessment.Actor(P_A, Assessment.ActorKind.PLAYER, "")
                .power(powerA).resources(0.5).confidence(1.0));
        s.add(new Assessment.Actor(P_B, Assessment.ActorKind.PLAYER, "")
                .power(powerB).resources(0.5).confidence(1.0));
        return s;
    }

    // ---------------------------------------------------------------- 档位表

    private static void testClampAndLabels() {
        check("越界夹到 0", WillForce.of(-7).intensity() == 0);
        check("越界夹到 3", WillForce.of(99).intensity() == WillForce.MAX);
        check("默认实例是温和", WillForce.gentle().intensity() == WillForce.GENTLE);
        check("档位名可读", "温和".equals(WillForce.of(1).label())
                && "天倾".equals(WillForce.of(3).label())
                && "关闭".equals(WillForce.of(0).label()));
        check("档位对象可复用", WillForce.of(2) == WillForce.of(2));
    }

    private static void testGentleMatchesLegacy() {
        WillForce g = WillForce.gentle();
        check("默认档位仍会落世界现象", g.phenomenaEnabled());
        check("默认档位不放开压制", !g.pressureEnabled());
        check("默认档位不降灾劫", !g.calamityEnabled() && g.calamityChance() == 0.0);
        check("默认档位无兽潮", !g.beastWaveEnabled() && g.beastWaveChance() == 0.0);
        check("决策周期与 3.13 相同", g.decisionPeriod() == WillConfig.DECISION_PERIOD);
        check("单轮执行上限与 3.13 相同", g.decisionBatch() == WillConfig.DECISION_BATCH);
        check("失衡阈值与 3.13 相同", g.balanceShare() == WillConfig.IMBALANCE_SHARE);
        check("失衡死区与 3.13 相同", g.balanceDeadband() == WillConfig.BALANCE_DEADBAND);
        check("压制门槛与 3.13 相同", g.suppressThreshold() == WillConfig.MIN_THREAT_TO_SUPPRESS);
        check("扶持门槛与 3.13 相同", g.supportThreshold() == WillConfig.MIN_UTILITY_TO_SUPPORT);
        check("窗口配额与 3.13 相同", g.maxSameAction() == WillConfig.MAX_SAME_ACTION);
        check("单轮压制上限与 3.13 相同", g.maxSuppressPerRound() == 2);
        check("单轮动作上限与 3.13 相同", g.maxActionPerRound() == 3);
        check("意志消耗倍率为 1", g.budgetCostScale() == 1.0);
        check("布局上限与 3.13 相同", g.maxActivePloys() == WillConfig.MAX_ACTIVE_PLOYS);
        check("人劫酝酿不缩短", g.renjieGestationScale() == 1.0);
        check("无额外人劫 NPC", g.renjieExtraNpc() == 0);
        check("诱饵门限与 3.13 相同", g.baitSinThreshold() == WillConfig.BAIT_SIN_THRESHOLD);
        check("诱饵上限沿用硬上限", g.baitCap() == WillConfig.BAIT_RATIO_CAP);
        check("温和档不给灾劫加冷却", g.calamityCooldown() == 0L);
        check("温和档不给灾劫加总量上限", g.calamityMaxActive() >= 64);
        check("温和档借兽规模不变", g.beastDriveFactor() == 1.0);
    }

    private static void testOffIsSilent() {
        WillForce o = WillForce.of(WillForce.OFF);
        check("关闭档识别为关闭", o.isOff());
        check("关闭档不落任何现象", !o.phenomenaEnabled());
        check("关闭档不压制", o.maxSuppressPerRound() == 0);
        check("关闭档不执行动作", o.maxActionPerRound() == 0 && o.decisionBatch() == 0);
        check("关闭档无灾劫", o.calamityChance() == 0.0 && o.calamityMaxActive() == 0);
        check("关闭档无兽潮", !o.beastWaveEnabled() && o.beastWaveWant() == 0);
        check("关闭档不出兽", o.beastDriveMax() == 0);
        check("关闭档永不产生诱饵", o.baitSinThreshold() >= 1.0);
        check("关闭档永不命中窗口", !o.fires("omen", P_A, 0L, WillForce.OMEN_WINDOW));
    }

    private static void testStrictOpensPressure() {
        WillForce s2 = WillForce.of(WillForce.STRICT);
        WillForce s3 = WillForce.of(WillForce.TILT);
        check("严厉档放开既有压制渠道", s2.pressureEnabled());
        check("严厉档灾劫是真的", s2.calamityEnabled() && s2.calamityChance() > 0.0);
        check("天倾档允许兽潮", s3.beastWaveEnabled() && s3.beastWaveChance() > 0.0);
        check("兽潮规模有上限", s3.beastWaveWant() > 0
                && s3.beastWaveWant() <= WillConfig.BEAST_DRIVE_MAX);
        check("档位越高决策越勤", s2.decisionPeriod() < WillForce.gentle().decisionPeriod()
                && s3.decisionPeriod() < s2.decisionPeriod());
        check("档位越高执行越多", s2.decisionBatch() >= WillForce.gentle().decisionBatch()
                && s3.decisionBatch() > s2.decisionBatch());
        check("档位越高出手越省", s2.budgetCostScale() < 1.0
                && s3.budgetCostScale() < s2.budgetCostScale());
        check("档位越高人劫酝酿越短（但不为 0）", s2.renjieGestationScale() < 1.0
                && s3.renjieGestationScale() < s2.renjieGestationScale()
                && s3.renjieGestationScale() > 0.0);
        check("档位越高布局上限越高", s3.maxActivePloys() > s2.maxActivePloys()
                && s2.maxActivePloys() > WillForce.gentle().maxActivePloys());
        check("档位越高现象越勤", s3.phenomenaPeriod() < s2.phenomenaPeriod()
                && s2.phenomenaPeriod() <= WillForce.gentle().phenomenaPeriod());
    }

    private static void testThresholdsMoveWithIntensity() {
        WillForce g = WillForce.of(1);
        WillForce s2 = WillForce.of(2);
        WillForce s3 = WillForce.of(3);
        check("压制门槛随档位下降",
                g.suppressThreshold() > s2.suppressThreshold()
                        && s2.suppressThreshold() > s3.suppressThreshold());
        check("失衡阈值随档位下降",
                g.balanceShare() > s2.balanceShare() && s2.balanceShare() > s3.balanceShare());
        check("窗口配额随档位上升",
                s2.maxSameAction() > g.maxSameAction()
                        && s3.maxSameAction() > s2.maxSameAction());

        // 45% 实力份额 → 威胁 0.45：温和档摸不到 0.55 的线，严厉档能
        Assessment.Report r1 = Assessment.assess(situation(1000L, g, 45.0, 55.0));
        Assessment.Report r2 = Assessment.assess(situation(1000L, s2, 45.0, 55.0));
        check("温和档：0.45 威胁不在压制候选", !r1.suppressCandidates.contains(P_A));
        check("温和档：0.55 威胁在压制候选", r1.suppressCandidates.contains(P_B));
        check("严厉档：0.45 威胁也进压制候选", r2.suppressCandidates.contains(P_A));
        check("严厉档：压制候选仍按威胁排序", !r2.suppressCandidates.isEmpty()
                && r2.suppressCandidates.get(0).equals(P_B));

        Assessment.Report r3 = Assessment.assess(situation(1000L, s3, 34.0, 66.0));
        check("天倾档：0.34 威胁也能被压制（阈值 0.32）",
                s3.suppressThreshold() < 0.35 && r3.suppressCandidates.contains(P_A));
    }

    private static void testDeterministicRollAndEventKey() {
        double a = WillForce.roll("omen", P_A, 7L);
        double b = WillForce.roll("omen", P_A, 7L);
        check("同一输入同一结果", a == b);
        check("结果落在 0..1", a >= 0.0 && a < 1.0);
        boolean changed = false;
        for (long w = 1L; w <= 20L; w++) {
            if (WillForce.roll("omen", P_A, w) != a) {
                changed = true;
                break;
            }
        }
        check("换窗口就换结果", changed);
        check("换主体就换结果", WillForce.roll("omen", P_A, 7L) != WillForce.roll("omen", P_B, 7L));
        check("幂等键在同一窗口内稳定",
                WillForce.eventKey("spoil", P_A, 30_000L, 24_000L)
                        .equals(WillForce.eventKey("spoil", P_A, 30_100L, 24_000L)));
        check("幂等键跨窗口不同",
                !WillForce.eventKey("spoil", P_A, 24_000L, 24_000L)
                        .equals(WillForce.eventKey("spoil", P_A, 48_000L, 24_000L)));
    }

    private static void testWindowFiresExactlyOnce() {
        WillForce[] all = {WillForce.of(1), WillForce.of(2), WillForce.of(3)};
        for (WillForce f : all) {
            long w = WillForce.OMEN_WINDOW;
            int hits = 0;
            for (long t = 0L; t < w; t += f.phenomenaPeriod()) {
                if (f.fires("omen", P_A, t, w)) {
                    hits++;
                }
            }
            check("档位 " + f.intensity() + "：一个窗口恰好落一次", hits == 1);
        }
        WillForce f3 = WillForce.of(3);
        int windows = 0;
        for (long win = 0L; win < 12L; win++) {
            for (long t = win * 6_000L; t < (win + 1) * 6_000L; t += f3.phenomenaPeriod()) {
                if (f3.fires("omen", P_A, t, 6_000L)) {
                    windows++;
                }
            }
        }
        check("连续 12 个窗口每个都有且只有一次", windows == 12);
        int differ = 0;
        for (long win = 0L; win < 12L; win++) {
            if (firstFire(f3, P_A, win, 6_000L) != firstFire(f3, P_B, win, 6_000L)) {
                differ++;
            }
        }
        check("不同主体的触发时刻会不同（至少一次）", differ > 0);
    }

    /** 某个主体在某个窗口里被命中的第一刻（没有命中返回 -1）。 */
    private static long firstFire(WillForce f, String subject, long win, long windowTicks) {
        for (long t = win * windowTicks; t < (win + 1) * windowTicks; t += f.phenomenaPeriod()) {
            if (f.fires("omen", subject, t, windowTicks)) {
                return t;
            }
        }
        return -1L;
    }

    private static void testCoreOffProducesNothing() {
        WillCore off = new WillCore(new Knowledge.Store(), "minecraft:overworld");
        List<Intent> none = off.decide(situation(1_000L, WillForce.of(0), 40.0, 60.0));
        check("强度 0：一条意图都不产出", none.isEmpty());
        check("强度 0：不登记姿态", off.postures().isEmpty());
        check("强度 0：不消耗窗口配额", off.actionCounts().isEmpty());

        WillCore gentle = new WillCore(new Knowledge.Store(), "minecraft:overworld");
        List<Intent> some = gentle.decide(situation(1_000L, WillForce.of(1), 40.0, 60.0));
        check("温和档：仍有动作（不是整条链路死掉）", !some.isEmpty());
    }

    private static void testCalamityChannelReachable() {
        WillForce s2 = WillForce.of(2);
        long hit = -1L;
        for (long w = 0L; w < 4_000L; w++) {
            if (WillForce.roll("calamity-urge", P_B, w) < s2.calamityChance()) {
                hit = w;
                break;
            }
        }
        check("能在有限窗口内找到触发灾劫的时机", hit >= 0L);
        long now = hit * WillForce.CALAMITY_WINDOW + 10L;

        WillCore gentle = new WillCore(new Knowledge.Store(), "minecraft:overworld");
        List<Intent> g = gentle.decide(situation(now, WillForce.of(1), 40.0, 60.0));
        boolean gentleCalamity = false;
        for (Intent i : g) {
            if (i.channel == Assessment.Channel.CALAMITY) {
                gentleCalamity = true;
            }
        }
        check("温和档：绝不选灾劫渠道", !gentleCalamity);

        WillCore strict = new WillCore(new Knowledge.Store(), "minecraft:overworld");
        List<Intent> s = strict.decide(situation(now, s2, 40.0, 60.0));
        boolean strictCalamity = false;
        for (Intent i : s) {
            if (i.kind == Intent.Kind.SUPPRESS && i.channel == Assessment.Channel.CALAMITY) {
                strictCalamity = true;
            }
        }
        check("严厉档：灾劫渠道真的会被选到", strictCalamity);
        check("严厉档：灾劫消耗的意志更省",
                s2.calamityChance() > 0.0 && s2.budgetCostScale() < 1.0);
    }

    private static void testBaitRatioScalesButCapped() {
        WillForce g = WillForce.of(1);
        WillForce t3 = WillForce.of(3);
        Assessment.Situation sg = situation(0L, g, 40.0, 60.0);
        Assessment.Situation st = situation(0L, t3, 40.0, 60.0);
        Assessment.Report rg = Assessment.assess(sg);
        Assessment.Report rt = Assessment.assess(st);
        Opportunity.Forge fg = new Opportunity.Forge();
        Opportunity.Forge ft = new Opportunity.Forge();
        int n = 240;
        for (int i = 0; i < n; i++) {
            long now = 1_000L + i * 701L;
            long pos = pack(10 + i, 70, 20 + i);
            fg.propose(rg, P_B, Assessment.Channel.RESOURCE, sg.find(P_B), now,
                    "minecraft:overworld", pos, "test", g.baitSinThreshold());
            ft.propose(rt, P_B, Assessment.Channel.RESOURCE, st.find(P_B), now,
                    "minecraft:overworld", pos, "test", t3.baitSinThreshold());
        }
        int bg = 0;
        for (Opportunity.Offer o : fg.active()) {
            if (o.bait) {
                bg++;
            }
        }
        int bt = 0;
        for (Opportunity.Offer o : ft.active()) {
            if (o.bait) {
                bt++;
            }
        }
        double ratioG = fg.active().isEmpty() ? 0.0 : (double) bg / fg.active().size();
        double ratioT = ft.active().isEmpty() ? 0.0 : (double) bt / ft.active().size();
        // 上限是「逐次判定」（先看当前占比再决定），所以最后一次可能越过一个条目的粒度。
        // 这里按这个真实语义断言，而不是假装它是「事后夹紧」。
        double granularity = 1.0 / (n + 1);
        check("机缘确实产生了（可比较）", fg.active().size() == n && ft.active().size() == n);
        check("温和档诱饵占比不超过硬上限（含一条粒度）",
                ratioG <= WillConfig.BAIT_RATIO_CAP + granularity);
        check("天倾档诱饵占比不超过硬上限（含一条粒度）",
                ratioT <= WillConfig.BAIT_RATIO_CAP + granularity);
        check("天倾档占比明显高于温和档", ratioT > ratioG);
        check("档位越高诱饵越多", bt >= bg);
        check("天倾档确实产生了诱饵", bt > 0);
        check("诱饵没有把机缘全吃掉", ratioT < 0.5);
    }

    private static void testCalamityScaleAndTargetingCap() {
        Calamity.Spec base = Calamity.spec(Calamity.Tier.DI_ZAI, 0.30, 10.0, false);
        Calamity.Spec big = Calamity.spec(Calamity.Tier.DI_ZAI, 0.30, 10.0, false,
                WillForce.of(3).calamityScaleFactor());
        check("档位 1 的灾劫规格与旧公式一致",
                Math.abs(base.scale - Calamity.Tier.DI_ZAI.scale) < 1e-9);
        check("天倾档灾劫规模更大", big.scale > base.scale);
        check("灾劫规模仍不超过 1", big.scale <= 1.0);
        Calamity.Spec capped = Calamity.spec(Calamity.Tier.TIAN_JIE, 0.44, 10.0, false,
                WillForce.of(3).calamityScaleFactor());
        check("针对性仍夹在硬上限",
                capped.targeting <= WillConfig.CALAMITY_TARGETING_CAP + 1e-9);
        Calamity.Spec same = Calamity.spec(Calamity.Tier.TIAN_JIE, 0.44, 10.0, false);
        check("档位只改规模，不改道痕收益",
                Math.abs(capped.daoMarkReward - same.daoMarkReward) < 1e-9);
        check("档位只改规模，不改失败惩罚",
                Math.abs(capped.failPenalty - same.failPenalty) < 1e-9);
        check("档位不新增灾劫种类", Calamity.Tier.values().length == 4);
    }

    private static void testBeastWaveNeverInventsBeasts() {
        String dim = "minecraft:overworld";
        long pos = pack(0, 70, 0);
        BeastHerd herd = new BeastHerd("herd-force", "guzhenren:hunshou_1", dim, pos,
                3.0, 20.0, 0.8, 0.05, 1_000L);
        WillForce t3 = WillForce.of(3);
        int want = t3.beastWaveWant();
        check("兽潮想赶的数量超过现成种群", want > herd.population());
        int got = herd.drive(want, 2_000L);
        check("赶不出超过种群的兽（不凭空刷怪）", got == 3);
        check("种群被真的扣减", herd.population() == 0.0);
        check("种群见底后再赶为 0", herd.drive(want, 2_100L) == 0);
        check("兽群规模上限来自既有配置",
                t3.beastDriveMax() == WillConfig.BEAST_DRIVE_MAX);
    }
}
