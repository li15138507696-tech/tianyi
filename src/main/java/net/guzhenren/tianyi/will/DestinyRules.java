package net.guzhenren.tianyi.will;

import java.util.Locale;

/**
 * 宿命规则查询（纯逻辑，不 import 任何 Minecraft 类型）。
 *
 * <h2>为什么要有这一层</h2>
 * 旧实现把宿命折算成 {@code fateLock()} 一个万能系数：完整就整体封死，毁灭就整体解除。
 * 那是「命运总开关」，既误封普通玩法，又让「天外之魔/摧毁本体」这类具体条件失去意义。
 * 3.13.0 改为按 <b>阶段＋具体手段＋目标条件＋证据</b> 逐项判定，四态：
 * {@link Verdict#ALLOWED} / {@link Verdict#LIMITED} / {@link Verdict#INSUFFICIENT} /
 * {@link Verdict#NOT_IMPLEMENTED}，并带可读原因。
 *
 * <h2>两条相反方向的硬纪律</h2>
 * <ul>
 *   <li><b>未知不默认允许特殊能力</b>：没有证据的天外之魔资格、特殊复活一律
 *       {@link Verdict#INSUFFICIENT}。</li>
 *   <li><b>未知也不默认封死普通玩法</b>：普通升仙、MC 默认重生在任何阶段都
 *       {@link Verdict#ALLOWED}。宿命不是普通玩法的总闸。</li>
 * </ul>
 *
 * <p>本类只回答「能不能做/需要什么」，不执行、不写档；摧毁本体与后续炼制分发必须由
 * 各自的完成事务（{@link Destiny.DestinyGu#completeBodyDestruction} /
 * {@link Destiny.DestinyGu#completeSplitRefinement}）推进阶段。
 */
public final class DestinyRules {
    private DestinyRules() {
    }

    /** 规则结论四态。 */
    public enum Verdict {
        /** 允许。 */
        ALLOWED,
        /** 允许但受限（效果打折扣、只影响特定对象、需要代价）。 */
        LIMITED,
        /** 条件不足（缺证据/资格/接触/阶段），当前不能做。 */
        INSUFFICIENT,
        /** 本项目未实现，不得假装有效。 */
        NOT_IMPLEMENTED
    }

    /** 被查询的具体手段/动作。 */
    public enum Act {
        /** 普通升仙（三气升仙），<b>不得</b>被宿命整体封死。 */
        ASCEND_NORMAL,
        /** MC 默认重生（死亡后重生点），独立于宿命。 */
        VANILLA_RESPAWN,
        /** 特殊复活手段（器物/术法级复活），按证据分别判定。 */
        RESURRECT_SPECIAL,
        /** 天外之魔资格判定。 */
        TIANWAI_QUALIFY,
        /** 命败。 */
        MING_BAI,
        /** 春秋蝉（单独建模，<b>不永久禁用宙道</b>）。 */
        CHUNQIU_CICADA,
        /** 摧毁宿命本体。 */
        DESTROY_BODY,
        /** 摧毁之后的炼制与分发众生（独立后续事务）。 */
        REFINE_DISTRIBUTE
    }

    /** 天外之魔资格。 */
    public enum Qualification {
        NONE, TIANWAI_PARTIAL, TIANWAI_FULL
    }

    /** 与宿命本体的接触条件。 */
    public enum Contact {
        NONE, OBSERVED, PHYSICAL
    }

    /** 证据强度。{@link #NONE} 表示「没有可验证证据」，不是「已证伪」。 */
    public enum Evidence {
        NONE, WEAK, VERIFIED
    }

    /** 一条规则结论。 */
    public record Answer(Act act, Verdict verdict, String reason) {
        /** 是否放行（含受限）。 */
        public boolean allowed() {
            return verdict == Verdict.ALLOWED || verdict == Verdict.LIMITED;
        }

        /** 是否被挡下（条件不足或未实现）。 */
        public boolean blocked() {
            return verdict == Verdict.INSUFFICIENT || verdict == Verdict.NOT_IMPLEMENTED;
        }
    }

    /** 查询条件。未给出的项按最保守默认（无资格/无接触/无证据）。 */
    public record Query(Act act, Destiny.Stage stage, Qualification qualification,
                        Contact contact, Evidence evidence) {
        public static Query of(Act act) {
            return new Query(act, null, Qualification.NONE, Contact.NONE, Evidence.NONE);
        }

        public Query withStage(Destiny.Stage s) {
            return new Query(act, s, qualification, contact, evidence);
        }

        public Query withQualification(Qualification q) {
            return new Query(act, stage, q == null ? Qualification.NONE : q, contact, evidence);
        }

        public Query withContact(Contact c) {
            return new Query(act, stage, qualification, c == null ? Contact.NONE : c, evidence);
        }

        public Query withEvidence(Evidence e) {
            return new Query(act, stage, qualification, contact, e == null ? Evidence.NONE : e);
        }
    }

    // ---------------------------------------------------------------- 统一入口

    /** 按条件判定。{@code null} 查询或未知手段一律不默认允许。 */
    public static Answer evaluate(Query q) {
        if (q == null || q.act() == null) {
            return new Answer(null, Verdict.INSUFFICIENT, "未知手段：不默认允许任何特殊能力");
        }
        String stageText = q.stage() == null ? "阶段未知" : q.stage().name();
        return switch (q.act()) {
            case ASCEND_NORMAL -> normalAscension(q.stage());
            case VANILLA_RESPAWN -> vanillaRespawn();
            case RESURRECT_SPECIAL -> resurrectSpecial(q.evidence(), stageText);
            case TIANWAI_QUALIFY -> tianwai(q.qualification(), q.evidence());
            case MING_BAI -> mingBai();
            case CHUNQIU_CICADA -> chunqiuCicada(q.evidence());
            case DESTROY_BODY -> destroyBody(q);
            case REFINE_DISTRIBUTE -> refineDistribute(q.stage());
        };
    }

    // ---------------------------------------------------------------- 普通玩法：永不整体封死

    /**
     * 普通升仙。<b>任何阶段都允许</b>：宿命不再是升仙的总闸，
     * 天道对长生的压力由灾劫与寿蛊各自承担。
     */
    public static Answer normalAscension(Destiny.Stage stage) {
        String s = stage == null ? "阶段未知" : stage.name();
        return new Answer(Act.ASCEND_NORMAL, Verdict.ALLOWED,
                "普通升仙不受宿命整体封锁（阶段=" + s + "）；天道压力由灾劫与寿蛊独立承担");
    }

    /** MC 默认重生：独立机制，不受宿命限制。 */
    public static Answer vanillaRespawn() {
        return new Answer(Act.VANILLA_RESPAWN, Verdict.ALLOWED,
                "MC 默认重生独立于宿命，不受阶段限制");
    }

    // ---------------------------------------------------------------- 特殊手段

    private static Answer resurrectSpecial(Evidence evidence, String stageText) {
        if (evidence != Evidence.VERIFIED) {
            return new Answer(Act.RESURRECT_SPECIAL, Verdict.INSUFFICIENT,
                    "没有可验证证据证明该复活手段成立（阶段=" + stageText + "）：未知不默认允许");
        }
        return new Answer(Act.RESURRECT_SPECIAL, Verdict.NOT_IMPLEMENTED,
                "本项目未实现特殊复活手段；默认重生请查 VANILLA_RESPAWN");
    }

    /** 天外之魔资格：完整＋证据才允许；半成品受限；无证据一律条件不足。 */
    public static Answer tianwai(Qualification qualification, Evidence evidence) {
        if (qualification == Qualification.TIANWAI_FULL && evidence == Evidence.VERIFIED) {
            return new Answer(Act.TIANWAI_QUALIFY, Verdict.ALLOWED,
                    "完整天外之魔且证据可验证：超脱宿命约束（仍不免灾劫伤害）");
        }
        if (qualification == Qualification.TIANWAI_PARTIAL && evidence != Evidence.NONE) {
            return new Answer(Act.TIANWAI_QUALIFY, Verdict.LIMITED,
                    "半成品天外之魔：仍受天意摆布，只按部分强度生效");
        }
        return new Answer(Act.TIANWAI_QUALIFY, Verdict.INSUFFICIENT,
                "无证据：未知不默认获得天外之魔资格");
    }

    /** 命败：本项目未实现。 */
    public static Answer mingBai() {
        return new Answer(Act.MING_BAI, Verdict.NOT_IMPLEMENTED,
                "命败未实现：不得假装有效，也不得用「未知」当允许");
    }

    /**
     * 春秋蝉：<b>单独建模</b>，只影响它自身的一次使用，
     * 不构成对宙道的永久禁用。
     */
    public static Answer chunqiuCicada(Evidence evidence) {
        if (evidence == Evidence.VERIFIED) {
            return new Answer(Act.CHUNQIU_CICADA, Verdict.LIMITED,
                    "只影响春秋蝉自身的一次使用，不永久禁用宙道");
        }
        return new Answer(Act.CHUNQIU_CICADA, Verdict.INSUFFICIENT,
                "无证据：不认定春秋蝉可用，也不因此封禁宙道");
    }

    /**
     * 宙道是否被永久禁用。<b>恒为 false</b>：春秋蝉的问题不能外溢成整条宙道封禁。
     */
    public static boolean zhouDaoPermanentlyBlocked(Destiny.Stage stage) {
        return false;
    }

    /**
     * 摧毁宿命本体：需要完整天外之魔资格＋实际接触＋可验证证据，
     * 且本体尚未被毁。满足条件也只是「可发起事务」，阶段仍由完成事务推进。
     */
    public static Answer destroyBody(Query q) {
        if (q == null) {
            return new Answer(Act.DESTROY_BODY, Verdict.INSUFFICIENT, "未给出条件：不默认允许");
        }
        Destiny.Stage stage = q.stage();
        if (stage != null && !stage.bodyExists()) {
            return new Answer(Act.DESTROY_BODY, Verdict.LIMITED,
                    "本体已不在（阶段=" + stage.name() + "），摧毁事务不再适用");
        }
        if (q.qualification() != Qualification.TIANWAI_FULL) {
            return new Answer(Act.DESTROY_BODY, Verdict.INSUFFICIENT,
                    "摧毁本体需要完整天外之魔资格");
        }
        if (q.evidence() != Evidence.VERIFIED) {
            return new Answer(Act.DESTROY_BODY, Verdict.INSUFFICIENT,
                    "摧毁本体需要可验证证据（无证据不默认允许）");
        }
        if (q.contact() != Contact.PHYSICAL) {
            return new Answer(Act.DESTROY_BODY, Verdict.INSUFFICIENT,
                    "摧毁本体需要实际接触本体（仅观察不够）");
        }
        return new Answer(Act.DESTROY_BODY, Verdict.ALLOWED,
                "条件齐备：可发起摧毁事务；阶段只由完成的摧毁事务推进");
    }

    /**
     * 摧毁之后的炼制与分发：<b>未实现</b>。
     * 即使本体已被毁，也不会自动得到「拆分分发」阶段。
     */
    public static Answer refineDistribute(Destiny.Stage stage) {
        String s = stage == null ? "阶段未知" : stage.name();
        return new Answer(Act.REFINE_DISTRIBUTE, Verdict.NOT_IMPLEMENTED,
                "后续炼制与分发未实现（阶段=" + s + "）：摧毁不会自动得到拆分分发");
    }

    // ---------------------------------------------------------------- 文案

    /** 可读的四态名称（命令/日志用）。 */
    public static String verdictText(Verdict v) {
        if (v == null) {
            return "未知";
        }
        return switch (v) {
            case ALLOWED -> "允许";
            case LIMITED -> "受限";
            case INSUFFICIENT -> "条件不足";
            case NOT_IMPLEMENTED -> "未实现";
        };
    }

    /** 一条结论的完整可读描述。 */
    public static String describe(Answer a) {
        if (a == null) {
            return "无结论";
        }
        return String.format(Locale.ROOT, "%s：%s（%s）",
                a.act() == null ? "未知手段" : a.act().name(), verdictText(a.verdict()), a.reason());
    }
}
