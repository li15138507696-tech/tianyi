package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 布局：天意的长期多步计划。
 *
 * <h2>一个合格布局必须有</h2>
 * <ol>
 *   <li><b>明确目标</b>（{@link #goal}）——不是「随机事件」，而是为了平衡或制约。</li>
 *   <li><b>步骤</b>（{@link Step}）——每步有执行者、到期时间、可判定成败。</li>
 *   <li><b>执行者</b>（{@link #executor}）——NPC / 势力 / 兽群 / 载体，天意不亲自下场。</li>
 *   <li><b>资源来源</b>（{@link #resourceSource}）——机会从哪来，不能凭空。</li>
 *   <li><b>终止条件</b>（{@link #terminateCondition}）——什么时候算结束。</li>
 * </ol>
 *
 * <h2>失败必须可失败</h2>
 * 玩家跑得太快、改道、遮掩身份、切断信息，都会让布局在酝酿期作废
 * （{@link #evaluate} 逐条判定）。失败之后进冷却，不再叠加同一种惩罚。
 */
public final class Ploy {
    public enum Status {
        /** 酝酿中：还没落地，玩家仍有机会破坏它。 */
        GESTATING,
        /** 执行中：已开始产生世界效果。 */
        RUNNING,
        SUCCEEDED,
        FAILED,
        ABORTED
    }

    public enum StepKind {
        /** 埋信息：让线索进入世界（流言、书信、可发现的痕迹）。 */
        SEED_INFO,
        /** 聚人：让有利益关系的 NPC/势力知道并动起来。 */
        ASSEMBLE,
        /** 出手：人劫/兽群/灾劫的具体一步。 */
        STRIKE,
        /** 收尾：兑现结果，或暴露痕迹供玩家反查。 */
        FOLLOWUP
    }

    /** 布局的一步。 */
    public static final class Step {
        public final StepKind kind;
        public final String actorId;
        public final long dueAt;
        public boolean done;
        public boolean failed;
        public String detail;

        public Step(StepKind kind, String actorId, long dueAt, String detail) {
            this.kind = kind;
            this.actorId = actorId;
            this.dueAt = dueAt;
            this.detail = detail == null ? "" : detail;
        }
    }

    /** 布局在执行时需要的外部信号（由 MC 层提供，本类不认识 Minecraft）。 */
    public record Signals(boolean targetHidden, boolean hasIntel, double targetDistance,
                          boolean channelAvailable, double targetPower) {
    }

    public final String id;
    public final String goal;
    public final String target;
    /** 执行者：真正动手的那个（NPC/势力/兽群），不是天意。 */
    public final String executor;
    /** 资源来源：这一步消耗的世界资源从哪来。 */
    public final String resourceSource;
    /** 终止条件的人类可读描述（同时用于日志核对）。 */
    public final String terminateCondition;
    public final long createdAt;
    public final long deadline;
    public final boolean targeted;
    public final List<Step> steps = new ArrayList<>();
    public Status status = Status.GESTATING;
    public int escalation;
    public String failReason = "";
    public long finishedAt = 0L;
    /** 布局建立时的锚点（玩家位置）。用来判定「他跑远了，布局作废」。 */
    public long anchorPos = Knowledge.Fact.NO_POS;
    /** 锚点所属维度（完整维度键）。 */
    public String anchorDim = "";
    /** 世界效果是否已经落下（成功只结算一次，防重复生成）。 */
    public boolean effectApplied = false;

    public Ploy(String id, String goal, String target, String executor,
                String resourceSource, String terminateCondition,
                long createdAt, long deadline, boolean targeted) {
        this.id = id;
        this.goal = goal;
        this.target = target == null ? "" : Knowledge.requireIdentity(target);
        this.executor = executor == null ? "" : executor;
        this.resourceSource = resourceSource == null ? "" : resourceSource;
        this.terminateCondition = terminateCondition == null ? "" : terminateCondition;
        this.createdAt = createdAt;
        this.deadline = deadline;
        this.targeted = targeted;
    }

    public Ploy step(StepKind kind, String actorId, long dueAt, String detail) {
        steps.add(new Step(kind, actorId, dueAt, detail));
        return this;
    }

    /** 记录锚点：布局是「冲着当时那个位置的那个人」铺的。 */
    public Ploy anchor(long pos, String dim) {
        this.anchorPos = pos;
        this.anchorDim = dim == null ? "" : dim;
        return this;
    }

    public boolean finished() {
        return status == Status.SUCCEEDED || status == Status.FAILED || status == Status.ABORTED;
    }

    /**
     * 判定当前是否失败 / 是否该转入执行。
     *
     * @return 判定结果码，便于测试断言
     */
    public Verdict evaluate(long now, Signals sig) {
        if (finished()) {
            return Verdict.ALREADY_DONE;
        }
        // 1) 目标彻底失去情报 → 布局作废（不能靠旧情报一直精确追踪）
        if (sig.targetHidden()) {
            return fail(now, "target-hidden");
        }
        if (!sig.hasIntel()) {
            return fail(now, "no-intel");
        }
        // 2) 玩家快速行动/改道 → 针对性布局失效
        if (targeted && sig.targetDistance > WillConfig.RENJIE_BREAK_DISTANCE) {
            return fail(now, "target-outran");
        }
        // 3) 渠道被切断（载体被清、势力被灭）
        if (!sig.channelAvailable()) {
            return fail(now, "channel-lost");
        }
        // 4) 超时
        if (now > deadline) {
            return fail(now, "deadline");
        }
        // 5) 推进步骤
        boolean allDone = !steps.isEmpty();
        for (Step s : steps) {
            if (s.failed) {
                return fail(now, "step-failed:" + s.kind);
            }
            if (!s.done && now >= s.dueAt) {
                // 目标实力远超执行者 → 这一步扑空（人劫不是万能）
                if (s.kind == StepKind.STRIKE && sig.targetPower > 0 && executionHopeless(sig)) {
                    s.failed = true;
                    return fail(now, "executor-outmatched");
                }
                s.done = true;
                status = Status.RUNNING;
            }
            if (!s.done) {
                allDone = false;
            }
        }
        if (allDone && status != Status.SUCCEEDED) {
            status = Status.SUCCEEDED;
            finishedAt = now;
            return Verdict.SUCCEEDED;
        }
        return status == Status.RUNNING ? Verdict.PROGRESSING : Verdict.GESTATING;
    }

    /** 执行者打不过目标：人劫会失败，而不是变成必死判定。 */
    private boolean executionHopeless(Signals sig) {
        return sig.targetPower > 0.75 && escalation <= 0;
    }

    private Verdict fail(long now, String why) {
        status = Status.FAILED;
        failReason = why;
        finishedAt = now;
        return Verdict.FAILED;
    }

    /** 判定结果码。 */
    public enum Verdict {
        GESTATING, PROGRESSING, SUCCEEDED, FAILED, ALREADY_DONE
    }

    public String describe() {
        return String.format(Locale.ROOT, "Ploy{%s %s→%s %s steps=%d/%d %s}",
                id, goal, target, status, steps.stream().filter(s -> s.done).count(),
                steps.size(), failReason);
    }

    // ================================================================ 布局簿

    /**
     * 布局簿：管理所有正在进行的布局，负责数量上限与失败冷却。
     */
    public static final class PloyBook {
        private final Map<String, Ploy> byId = new LinkedHashMap<>();

        public Ploy open(String id, String goal, String target, String executor,
                         String resourceSource, String terminateCondition,
                         long now, boolean targeted) {
            long gest = targeted ? WillConfig.RENJIE_MIN_GESTATION
                    : WillConfig.OPPORTUNITY_MIN_GESTATION;
            Ploy p = new Ploy(id, goal, target, executor, resourceSource, terminateCondition,
                    now, now + Math.min(WillConfig.PLOY_MAX_LIFETIME,
                    gest + WillConfig.PLOY_MAX_LIFETIME / 2), targeted);
            byId.put(id, p);
            return p;
        }

        public Ploy get(String id) {
            return byId.get(id);
        }

        public boolean canOpen(long now) {
            return canOpen(now, WillConfig.MAX_ACTIVE_PLOYS);
        }

        /** 3.14：同时存在的布局上限可随天意强度提高（档位 1 仍等于 {@link WillConfig#MAX_ACTIVE_PLOYS}）。 */
        public boolean canOpen(long now, int maxActive) {
            int active = 0;
            for (Ploy p : byId.values()) {
                if (!p.finished()) {
                    active++;
                }
            }
            return active < Math.max(1, maxActive);
        }

        /** 每轮推进；同时清掉太老的已完成布局。 */
        public void tick(long now, Map<String, Long> cooldowns) {
            byId.entrySet().removeIf(e -> e.getValue().finished()
                    && now - e.getValue().finishedAt > WillConfig.PLOY_MAX_LIFETIME);
            for (Ploy p : byId.values()) {
                if (!p.finished() && now > p.deadline) {
                    p.status = Status.FAILED;
                    p.failReason = "deadline";
                    p.finishedAt = now;
                    cooldowns.merge(p.target, now + WillConfig.PLOY_FAIL_COOLDOWN, Math::max);
                }
            }
        }

        public void fail(String id, long now) {
            Ploy p = byId.get(id);
            if (p != null && !p.finished()) {
                p.status = Status.FAILED;
                p.failReason = "explicit";
                p.finishedAt = now;
            }
        }

        public List<Ploy> active() {
            List<Ploy> out = new ArrayList<>();
            for (Ploy p : byId.values()) {
                if (!p.finished()) {
                    out.add(p);
                }
            }
            return out;
        }

        public List<Ploy> all() {
            return new ArrayList<>(byId.values());
        }

        public int failedCount() {
            int n = 0;
            for (Ploy p : byId.values()) {
                if (p.status == Status.FAILED) {
                    n++;
                }
            }
            return n;
        }
    }
}
