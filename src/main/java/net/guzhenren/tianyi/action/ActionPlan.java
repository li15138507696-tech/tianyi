package net.guzhenren.tianyi.action;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 天意的一条布局步骤 —— <b>可持久化、可失败、可复发</b>。
 *
 * <h2>为什么不能只存一个「好感度」</h2>
 * 需求第八条要求：布局要有步骤、执行者、冷却、失败记录，并且<b>天意可以失败</b>。
 * 一个数字做不到这些 —— 玩家截了一批货，天意必须记得「这步没成、为什么没成、
 * 多久之后才敢再试」。所以每步都是一个带状态的对象。
 *
 * <p>纯逻辑，不 import Minecraft。时间一律 {@code long}，身份一律完整 UUID / 势力 key 字符串。
 */
public final class ActionPlan {
    /** 步骤状态。 */
    public enum State {
        PENDING, RUNNING, /** 渠道被切断 —— 玩家破局了 */ BLOCKED,
        DONE, FAILED, ABANDONED
    }

    /** 同一 (actionId,targetId) 的重试上限；超了就 ABANDONED，不再无限骚扰。 */
    public static final int MAX_ATTEMPTS = 4;
    /** 失败后的基础冷却（刻）。2 分钟。 */
    public static final long FAIL_COOLDOWN = 2_400L;
    /** 被阻断后的冷却更长：玩家既然切断了渠道，天意要重新铺路。 */
    public static final long BLOCK_COOLDOWN = 9_600L;

    /** 稳定 id。反序列化时会被存档里的值覆盖（保证重放不产生新步骤）。 */
    private String id;
    private final String actionId;
    private final String executorId;
    private final String targetId;
    private final String dim;
    private final long pos;
    private final long createdAt;
    private final double confidence;

    private long dueAt;
    private State state = State.PENDING;
    private int attempts;
    private int failures;
    private String lastReason = "";
    /** 去重键（持久化，重启后仍能拦住重复步骤）。 */
    private String dedupeKey = "";
    private long cooldownUntil;
    private long settledAt;

    public ActionPlan(String actionId, String executorId, String targetId, String dim, long pos,
                      long createdAt, long dueAt, double confidence) {
        this.id = UUID.randomUUID().toString();
        this.actionId = actionId == null ? "?" : actionId;
        this.executorId = executorId == null ? "" : executorId;
        this.targetId = targetId == null ? "" : targetId;
        this.dim = dim == null ? "" : dim;
        this.pos = pos;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
        this.confidence = clamp(confidence, 0.0, 1.0);
    }

    // ------------------------------------------------------------ 去重键

    /**
     * 去重键：同一动作 + 同一目标 + 同一时间桶，视为同一件事。
     *
     * <p>这是「同一事件不会重复触发追杀」的实现点。时间桶默认 1200 刻（1 分钟），
     * 避免一次事件被同一 tick 的多次扫描各生成一条。
     */
    public static String dedupeKey(String actionId, String targetId, long tick, long bucket) {
        long b = bucket <= 0 ? 1200L : bucket;
        return (actionId == null ? "?" : actionId) + '|' + (targetId == null ? "" : targetId)
                + '|' + (tick / b);
    }

    /** 稳定 id（同一 dedupeKey 派生同一个 UUID），便于幂等落盘。 */
    public static String stableId(String dedupeKey) {
        return UUID.nameUUIDFromBytes(("tianyi.plan|" + dedupeKey).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    // ------------------------------------------------------------ 状态迁移

    /** 到点没有？ */
    public boolean due(long now) {
        return now >= dueAt && now >= cooldownUntil;
    }

    /**
     * 是否还值得跑。
     *
     * <p>{@code BLOCKED} / {@code FAILED} 不是终结 —— 冷却一过就重新可跑，
     * 这正是「天意失败后会换个方式再来」的实现点。只有 {@code DONE} /
     * {@code ABANDONED} 才算真正关门。
     */
    public boolean open(long now) {
        if (attempts >= MAX_ATTEMPTS) {
            return false;
        }
        if (now < dueAt || now < cooldownUntil) {
            return false;
        }
        return switch (state) {
            case PENDING, RUNNING, BLOCKED, FAILED -> true;
            case DONE, ABANDONED -> false;
        };
    }

    public void start(long now) {
        state = State.RUNNING;
        attempts++;
        lastReason = "";
    }

    /** 结算。所有终态都从这里进，保证 attempts/failures/cooldown 一致。 */
    public void settle(ActResult r, long now) {
        settledAt = now;
        lastReason = r.reason();
        switch (r.status()) {
            case OK -> state = State.DONE;
            case DENIED -> {
                state = State.ABANDONED;
                lastReason = "条件不成立：" + r.reason();
            }
            case DEFERRED -> {
                state = State.PENDING;
                dueAt = now + FAIL_COOLDOWN;
            }
            case BLOCKED -> {
                failures++;
                state = State.BLOCKED;
                cooldownUntil = now + BLOCK_COOLDOWN;
                // 被阻断不是终结：等冷却过后天意可以换个方式再试一次
                if (attempts < MAX_ATTEMPTS) {
                    dueAt = cooldownUntil;
                }
            }
            case FAILED -> {
                failures++;
                state = State.FAILED;
                cooldownUntil = now + FAIL_COOLDOWN;
                if (attempts < MAX_ATTEMPTS) {
                    dueAt = cooldownUntil;
                }
            }
        }
        if (attempts >= MAX_ATTEMPTS && state != State.DONE && state != State.ABANDONED) {
            state = State.ABANDONED;
            lastReason = "重试耗尽：" + lastReason;
        }
    }

    /** 被阻断后，冷却结束重新排队。 */
    public void requeue(long now) {
        if (state == State.BLOCKED || state == State.FAILED) {
            if (attempts < MAX_ATTEMPTS && now >= cooldownUntil) {
                state = State.PENDING;
                dueAt = now;
            } else if (attempts >= MAX_ATTEMPTS) {
                state = State.ABANDONED;
            }
        }
    }

    public void abandon(String why) {
        state = State.ABANDONED;
        lastReason = why == null ? "" : why;
    }

    /**
     * 绑定去重键并落进存档。
     *
     * <p>必须持久化：否则服务端重启后 {@link ActionLedger} 只能用 id 去重，
     * 同一件事会被重新收进来 —— 正好踩中「重启后不能重复生成追杀」。
     */
    public void bindDedupe(String key) {
        this.dedupeKey = key == null ? "" : key;
    }

    public String dedupeKey() {
        return dedupeKey;
    }

    // ------------------------------------------------------------ 读

    public String id() {
        return id;
    }

    public String actionId() {
        return actionId;
    }

    public String executorId() {
        return executorId;
    }

    public String targetId() {
        return targetId;
    }

    public String dim() {
        return dim;
    }

    public long pos() {
        return pos;
    }

    public long createdAt() {
        return createdAt;
    }

    public long dueAt() {
        return dueAt;
    }

    public double confidence() {
        return confidence;
    }

    public State state() {
        return state;
    }

    public int attempts() {
        return attempts;
    }

    public int failures() {
        return failures;
    }

    public String lastReason() {
        return lastReason;
    }

    public long cooldownUntil() {
        return cooldownUntil;
    }

    public long settledAt() {
        return settledAt;
    }

    /** 天意现在对这步还有多少把握（失败与被阻断都会削）。 */
    public double effectiveConfidence(long now) {
        double c = confidence * (1.0 - 0.25 * failures);
        if (state == State.BLOCKED) {
            c *= 0.5;
        }
        if (now < cooldownUntil) {
            c *= 0.5;
        }
        return clamp(c, 0.0, 1.0);
    }

    // ------------------------------------------------------------ 持久化

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("actionId", actionId);
        m.put("executor", executorId);
        m.put("target", targetId);
        m.put("dim", dim);
        m.put("pos", Long.toString(pos));
        m.put("created", Long.toString(createdAt));
        m.put("due", Long.toString(dueAt));
        m.put("conf", Double.toString(confidence));
        m.put("state", state.name());
        m.put("attempts", Integer.toString(attempts));
        m.put("failures", Integer.toString(failures));
        m.put("reason", lastReason);
        m.put("cooldown", Long.toString(cooldownUntil));
        m.put("settled", Long.toString(settledAt));
        m.put("dedupe", dedupeKey);
        return m;
    }

    public static ActionPlan fromMap(Map<String, String> m) {
        ActionPlan p = new ActionPlan(m.getOrDefault("actionId", "?"),
                m.getOrDefault("executor", ""), m.getOrDefault("target", ""),
                m.getOrDefault("dim", ""), parseLong(m.get("pos"), Long.MIN_VALUE),
                parseLong(m.get("created"), 0L), parseLong(m.get("due"), 0L),
                parseDouble(m.get("conf"), 0.0));
        // id 用存档里那个，保证幂等（重放同一份数据不会长出新步骤）
        if (m.get("id") != null && !m.get("id").isBlank()) {
            p.overrideId(m.get("id"));
        }
        p.state = parseEnum(m.get("state"));
        p.attempts = (int) parseLong(m.get("attempts"), 0L);
        p.failures = (int) parseLong(m.get("failures"), 0L);
        p.lastReason = m.getOrDefault("reason", "");
        p.cooldownUntil = parseLong(m.get("cooldown"), 0L);
        p.settledAt = parseLong(m.get("settled"), 0L);
        p.dedupeKey = m.getOrDefault("dedupe", "");
        return p;
    }

    /**
     * 仅反序列化用：把 id 还原成存档里的值。
     *
     * <p>不暴露到公开 API —— 外部改 id 会破坏去重。
     */
    private void overrideId(String v) {
        if (v != null && !v.isBlank()) {
            this.id = v;
        }
    }

    public List<String> describe(long now) {
        List<String> out = new ArrayList<>();
        out.add(actionId + "→" + (targetId.isEmpty() ? "-" : targetId)
                + " [" + state + "] 尝试 " + attempts + "/" + MAX_ATTEMPTS
                + " 失败 " + failures
                + " 把握 " + String.format(Locale.ROOT, "%.2f", effectiveConfidence(now))
                + (lastReason.isEmpty() ? "" : " 因：" + lastReason));
        return out;
    }

    // ------------------------------------------------------------ utils

    private static State parseEnum(String v) {
        if (v == null) {
            return State.PENDING;
        }
        try {
            return State.valueOf(v);
        } catch (IllegalArgumentException e) {
            return State.PENDING;
        }
    }

    private static long parseLong(String v, long dflt) {
        if (v == null) {
            return dflt;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static double parseDouble(String v, double dflt) {
        if (v == null) {
            return dflt;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public String toString() {
        return "ActionPlan{" + actionId + " " + state + " attempts=" + attempts + "}";
    }
}
