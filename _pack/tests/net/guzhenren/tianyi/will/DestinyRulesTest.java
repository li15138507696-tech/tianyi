package net.guzhenren.tianyi.will;

/**
 * 宿命规则查询的离线行为测试（纯 javac + java，不依赖 Minecraft）。
 *
 * <p>锁两条相反方向的纪律：<b>未知不默认允许特殊能力</b>，
 * 也<b>不默认封死普通玩法</b>。四态必须都能出现，且原因可读。
 */
public final class DestinyRulesTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testNormalPlayNeverLocked();
        testVerdictFourStates();
        testTianwaiNeedsEvidence();
        testDestroyBodyNeedsEverything();
        testRefineDistributeNotImplemented();
        testChunqiuCicadaIsolated();
        testUnknownMeans();
        testAnswerHelpers();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：规则只回答「能不能做/需要什么」；实际执行与实机效果未验证。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void testNormalPlayNeverLocked() {
        boolean ascendAllowed = true;
        for (Destiny.Stage s : Destiny.Stage.values()) {
            if (DestinyRules.normalAscension(s).verdict() != DestinyRules.Verdict.ALLOWED) {
                ascendAllowed = false;
            }
        }
        check("普通升仙在四个阶段都允许（不被宿命整体封死）", ascendAllowed);
        check("普通升仙即使阶段未知也允许",
                DestinyRules.normalAscension(null).verdict() == DestinyRules.Verdict.ALLOWED);
        check("普通升仙不因本体完整而被封",
                DestinyRules.normalAscension(Destiny.Stage.BODY_WHOLE).verdict()
                        == DestinyRules.Verdict.ALLOWED);
        check("MC 默认重生独立不受限",
                DestinyRules.vanillaRespawn().verdict() == DestinyRules.Verdict.ALLOWED);
        check("默认重生原因说明独立",
                DestinyRules.vanillaRespawn().reason().contains("独立"));
    }

    private static void testVerdictFourStates() {
        boolean allowed = DestinyRules.evaluate(DestinyRules.Query.of(
                DestinyRules.Act.ASCEND_NORMAL)).verdict() == DestinyRules.Verdict.ALLOWED;
        boolean limited = DestinyRules.tianwai(DestinyRules.Qualification.TIANWAI_PARTIAL,
                DestinyRules.Evidence.WEAK).verdict() == DestinyRules.Verdict.LIMITED;
        boolean insufficient = DestinyRules.tianwai(DestinyRules.Qualification.NONE,
                DestinyRules.Evidence.NONE).verdict() == DestinyRules.Verdict.INSUFFICIENT;
        boolean notImplemented = DestinyRules.mingBai().verdict()
                == DestinyRules.Verdict.NOT_IMPLEMENTED;
        check("四态都能出现：允许", allowed);
        check("四态都能出现：受限", limited);
        check("四态都能出现：条件不足", insufficient);
        check("四态都能出现：未实现", notImplemented);
        check("四态文案可读",
                !DestinyRules.verdictText(DestinyRules.Verdict.ALLOWED).isBlank()
                        && !DestinyRules.verdictText(DestinyRules.Verdict.NOT_IMPLEMENTED).isBlank());
    }

    private static void testTianwaiNeedsEvidence() {
        check("无证据的天外之魔不通过",
                DestinyRules.tianwai(DestinyRules.Qualification.NONE,
                        DestinyRules.Evidence.NONE).verdict()
                        == DestinyRules.Verdict.INSUFFICIENT);
        check("自称完整但无证据也不通过",
                DestinyRules.tianwai(DestinyRules.Qualification.TIANWAI_FULL,
                        DestinyRules.Evidence.NONE).verdict()
                        == DestinyRules.Verdict.INSUFFICIENT);
        check("完整＋可验证证据才允许",
                DestinyRules.tianwai(DestinyRules.Qualification.TIANWAI_FULL,
                        DestinyRules.Evidence.VERIFIED).verdict()
                        == DestinyRules.Verdict.ALLOWED);
        check("半成品受限且不被当成完整",
                DestinyRules.tianwai(DestinyRules.Qualification.TIANWAI_PARTIAL,
                        DestinyRules.Evidence.VERIFIED).verdict()
                        == DestinyRules.Verdict.LIMITED);
        check("天外资格不冒充灾劫免疫",
                !DestinyRules.tianwai(DestinyRules.Qualification.TIANWAI_FULL,
                        DestinyRules.Evidence.VERIFIED).reason().isBlank());
    }

    private static void testDestroyBodyNeedsEverything() {
        DestinyRules.Query base = DestinyRules.Query.of(DestinyRules.Act.DESTROY_BODY)
                .withStage(Destiny.Stage.BODY_WHOLE);
        check("缺资格不能摧毁本体", DestinyRules.destroyBody(base).verdict()
                == DestinyRules.Verdict.INSUFFICIENT);

        DestinyRules.Query full = base.withQualification(DestinyRules.Qualification.TIANWAI_FULL);
        check("缺证据不能摧毁本体", DestinyRules.destroyBody(full).verdict()
                == DestinyRules.Verdict.INSUFFICIENT);

        DestinyRules.Query evidenced = full.withEvidence(DestinyRules.Evidence.VERIFIED);
        check("缺接触不能摧毁本体", DestinyRules.destroyBody(evidenced).verdict()
                == DestinyRules.Verdict.INSUFFICIENT);

        DestinyRules.Query all = evidenced.withContact(DestinyRules.Contact.PHYSICAL);
        check("资格＋证据＋接触齐备才允许发起摧毁事务",
                DestinyRules.destroyBody(all).verdict() == DestinyRules.Verdict.ALLOWED);
        check("允许也只是「可发起事务」，不直接改阶段",
                DestinyRules.destroyBody(all).reason().contains("事务"));

        check("本体已毁时摧毁事务不再适用",
                DestinyRules.destroyBody(all.withStage(Destiny.Stage.BODY_DESTROYED_REVIVABLE))
                        .verdict() == DestinyRules.Verdict.LIMITED);
        check("已分发时摧毁事务不再适用",
                DestinyRules.destroyBody(all.withStage(Destiny.Stage.SPLIT_AND_DISTRIBUTED))
                        .verdict() == DestinyRules.Verdict.LIMITED);
        check("半成品天外之魔不能摧毁本体",
                DestinyRules.destroyBody(evidenced.withContact(DestinyRules.Contact.PHYSICAL)
                        .withQualification(DestinyRules.Qualification.TIANWAI_PARTIAL))
                        .verdict() == DestinyRules.Verdict.INSUFFICIENT);
    }

    private static void testRefineDistributeNotImplemented() {
        check("后续炼制分发未实现",
                DestinyRules.refineDistribute(Destiny.Stage.BODY_DESTROYED_REVIVABLE).verdict()
                        == DestinyRules.Verdict.NOT_IMPLEMENTED);
        check("摧毁阶段不会自动得到分发",
                DestinyRules.refineDistribute(Destiny.Stage.BODY_DESTROYED_REVIVABLE)
                        .reason().contains("不会自动"));
        check("未实现结论不得被判为允许",
                !DestinyRules.refineDistribute(Destiny.Stage.BODY_DESTROYED_REVIVABLE).allowed());
        check("命败未实现", DestinyRules.mingBai().verdict()
                == DestinyRules.Verdict.NOT_IMPLEMENTED);
        check("命败未实现不冒充允许", !DestinyRules.mingBai().allowed());
    }

    private static void testChunqiuCicadaIsolated() {
        check("无证据时不认定春秋蝉可用",
                DestinyRules.chunqiuCicada(DestinyRules.Evidence.NONE).verdict()
                        == DestinyRules.Verdict.INSUFFICIENT);
        check("有证据时春秋蝉受限而非全局封禁",
                DestinyRules.chunqiuCicada(DestinyRules.Evidence.VERIFIED).verdict()
                        == DestinyRules.Verdict.LIMITED);
        boolean zhouOpen = true;
        for (Destiny.Stage s : Destiny.Stage.values()) {
            if (DestinyRules.zhouDaoPermanentlyBlocked(s)) {
                zhouOpen = false;
            }
        }
        check("宙道不被春秋蝉永久禁用", zhouOpen);
        check("宙道未知阶段也不封禁", !DestinyRules.zhouDaoPermanentlyBlocked(null));
    }

    private static void testUnknownMeans() {
        check("未知手段不默认允许",
                DestinyRules.evaluate(null).verdict() == DestinyRules.Verdict.INSUFFICIENT);
        check("空查询不默认允许",
                DestinyRules.evaluate(new DestinyRules.Query(null, null, null, null, null))
                        .verdict() == DestinyRules.Verdict.INSUFFICIENT);
        check("特殊复活无证据→条件不足",
                DestinyRules.evaluate(DestinyRules.Query.of(DestinyRules.Act.RESURRECT_SPECIAL))
                        .verdict() == DestinyRules.Verdict.INSUFFICIENT);
        check("特殊复活有证据→未实现",
                DestinyRules.evaluate(DestinyRules.Query.of(DestinyRules.Act.RESURRECT_SPECIAL)
                        .withEvidence(DestinyRules.Evidence.VERIFIED)).verdict()
                        == DestinyRules.Verdict.NOT_IMPLEMENTED);
        check("未给出条件时按最保守默认",
                DestinyRules.Query.of(DestinyRules.Act.TIANWAI_QUALIFY).qualification()
                        == DestinyRules.Qualification.NONE);
    }

    private static void testAnswerHelpers() {
        DestinyRules.Answer allowed = DestinyRules.normalAscension(Destiny.Stage.BODY_DAMAGED);
        DestinyRules.Answer limited = DestinyRules.chunqiuCicada(DestinyRules.Evidence.VERIFIED);
        DestinyRules.Answer blocked = DestinyRules.mingBai();
        check("允许的结论 allowed()=true", allowed.allowed() && !allowed.blocked());
        check("受限的结论 allowed()=true 但非全允许", limited.allowed() && !limited.blocked());
        check("未实现的结论 blocked()=true", blocked.blocked() && !blocked.allowed());
        check("描述可读", !DestinyRules.describe(allowed).isBlank()
                && DestinyRules.describe(null).contains("无结论"));
    }

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
