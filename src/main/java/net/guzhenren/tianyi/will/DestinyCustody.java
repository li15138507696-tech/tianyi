package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 宿命蛊的<b>保管身份与账本</b>（纯逻辑，不 import 任何 Minecraft 类型）。
 *
 * <h2>为什么不靠物品 UUID</h2>
 * 物品 UUID、NBT 副本、创造模式取物都能被复制，<b>UUID 不构成唯一性证明</b>。
 * 唯一性只能由服务端的世界账本给出。账本分开记这几件事，任何一件都不能冒充另一件：
 * <ul>
 *   <li>{@code worldId} —— 世界身份（存档级）；</li>
 *   <li>{@code logicalId} —— 宿命蛊的逻辑身份（全世界恒定，复制品沿用同一逻辑身份）；</li>
 *   <li>{@code generation} —— 实体化代次（每次合法实体化 +1）；</li>
 *   <li>{@code custodyRevision} —— 保管记录版本（每次合法转移/丢失 +1）；</li>
 *   <li>{@code exists} —— 实物此刻是否在账本上；</li>
 *   <li>{@code holder} —— 可验证的保管位置/承载者（可为空）；</li>
 *   <li>{@code lastTxnId} —— 最近一次合法转移事务 id（用于幂等）。</li>
 * </ul>
 *
 * <h2>三条硬纪律</h2>
 * <ol>
 *   <li><b>同一世界不得产生第二个有效正品</b>：{@link #issue} 只在 {@code !exists}
 *       且无未决事务时允许实体化；重放同一事务 id 返回原凭证（幂等），别的 id 一律拒绝。</li>
 *   <li><b>载体丢失不改阶段</b>：{@link #onCarrierLost} 只把 {@code exists} 置 false 并记
 *       「待核验」，绝不碰 {@link Destiny.DestinyGu} 的阶段。火烧、岩浆、虚空、掉落清理
 *       都走这条路。</li>
 *   <li><b>复制不自动认正</b>：{@link #markDuplicate} 进入隔离并保留证据，
 *       <b>不会</b>把副本认定为真，也<b>不会</b>断言正品仍在；解除隔离需要具名核验人。</li>
 * </ol>
 *
 * <p>持久化：{@link #snapshot()} / {@link #restore(Map)} 用纯字符串 map 往返，
 * MC 层只需把它塞进 CompoundTag，本类不认识 NBT。
 */
public final class DestinyCustody {

    /** 账本状态。丢失载体是 {@link #UNVERIFIED}，不是阶段。 */
    public enum State {
        /** 从未实体化，或已确认不在。 */
        ABSENT,
        /** 在账本上且位置可验证。 */
        PRESENT,
        /** 载体丢失/待核验（存在性未知）。 */
        UNVERIFIED,
        /** 出现复制证据，账本被隔离，等待具名核验。 */
        QUARANTINED
    }

    /** 拒绝原因。{@link #NONE} 只出现在成功结果里。 */
    public enum Reject {
        NONE,
        /** 事务 id 缺失或空白。 */
        BAD_TXN,
        /** 已经有有效正品，不能再实体化第二个。 */
        ALREADY_EXISTS,
        /** 有未决事务，先结束它。 */
        PENDING_TXN,
        /** 账本上不存在实物。 */
        ABSENT,
        /** 保管版本不符（过期请求 / 并发转移）。 */
        STALE_REVISION,
        /** 来源与账本记录不符。 */
        WRONG_SOURCE,
        /** 目标非法（空、或与来源相同）。 */
        BAD_TARGET,
        /** 账本隔离中，任何写操作都拒绝。 */
        QUARANTINED,
        /** 世界身份不符（把别的存档的账本塞进来了）。 */
        WORLD_MISMATCH
    }

    /**
     * 可验证的保管位置/承载者。
     *
     * @param kind {@code player} / {@code entity} / {@code block} / {@code unknown}
     * @param id   承载者标识（玩家 UUID、实体 UUID、方块键）
     * @param dim  维度键（方块承载时有意义，其余可为空串）
     * @param pos  方块位置打包（{@code BlockPos.asLong()}），非方块为 0
     */
    public record Holder(String kind, String id, String dim, long pos) {
        public static Holder of(String kind, String id) {
            return new Holder(kind == null ? "unknown" : kind, id == null ? "" : id, "", 0L);
        }

        public static Holder player(String uuid) {
            return of("player", uuid);
        }

        public static Holder entity(String uuid) {
            return of("entity", uuid);
        }

        public static Holder block(String dim, long packedPos) {
            return new Holder("block", "block@" + packedPos, dim == null ? "" : dim, packedPos);
        }

        public static Holder unknown(String id) {
            return of("unknown", id);
        }

        public boolean present() {
            return id != null && !id.isBlank();
        }

        public boolean sameAs(Holder other) {
            return other != null && kind.equals(other.kind) && id.equals(other.id)
                    && dim.equals(other.dim) && pos == other.pos;
        }
    }

    /** {@link #validate} 的结论。 */
    public record Check(boolean valid, String reason) {
    }

    /** 写操作结果。{@code ok=false} 时 {@link #reject} 说明原因。 */
    public record Result(boolean ok, Reject reject, boolean replayed, String detail, String token) {
        public static Result done(String token, boolean replayed, String detail) {
            return new Result(true, Reject.NONE, replayed, detail, token);
        }

        public static Result fail(Reject reject, String detail) {
            return new Result(false, reject, false, detail, "");
        }
    }

    /** 复制证据：序列号（物品 UUID/NBT 摘要/创造模式取物记录）＋说明＋时刻。 */
    public record Duplicate(String serial, String note, long at) {
    }

    private final String worldId;
    private final String logicalId;
    private int generation;
    private long custodyRevision;
    private boolean exists;
    private State state = State.ABSENT;
    private Holder holder;
    private String lastTxnId = "";
    private String pendingTxnId = "";
    private boolean pendingVerification;
    private String quarantineReason = "";
    private String lastReason = "";
    private final Map<String, Duplicate> duplicates = new LinkedHashMap<>();
    private final List<String> evidence = new ArrayList<>();

    private DestinyCustody(String worldId, String logicalId) {
        this.worldId = worldId == null ? "" : worldId;
        this.logicalId = logicalId == null ? "" : logicalId;
    }

    /**
     * 新账本。{@code logicalId} 全世界恒定，复制品沿用同一逻辑身份，
     * 因此「同一逻辑身份出现两枚」一定会在账本里对不上。
     */
    public static DestinyCustody create(String worldId, String logicalId) {
        return new DestinyCustody(worldId, logicalId);
    }

    // ---------------------------------------------------------------- 读

    public String worldId() {
        return worldId;
    }

    public String logicalId() {
        return logicalId;
    }

    public int generation() {
        return generation;
    }

    public long custodyRevision() {
        return custodyRevision;
    }

    public boolean exists() {
        return exists;
    }

    public State state() {
        return state;
    }

    /** 当前可验证的保管位置；不存在/丢失时为 {@code null}。 */
    public Holder holder() {
        return holder;
    }

    public String lastTxnId() {
        return lastTxnId;
    }

    public String pendingTxnId() {
        return pendingTxnId;
    }

    /** 载体丢失后为 true，等待核验；<b>与阶段无关</b>。 */
    public boolean pendingVerification() {
        return pendingVerification;
    }

    public boolean quarantined() {
        return state == State.QUARANTINED;
    }

    public String quarantineReason() {
        return quarantineReason;
    }

    public String lastReason() {
        return lastReason;
    }

    public List<Duplicate> duplicates() {
        return new ArrayList<>(duplicates.values());
    }

    public List<String> evidence() {
        return new ArrayList<>(evidence);
    }

    /** 当前有效正品的凭证串（世界＋逻辑身份＋代次＋保管版本）。 */
    public String token() {
        return worldId + ':' + logicalId + ':' + generation + ':' + custodyRevision;
    }

    // ---------------------------------------------------------------- 写：实体化

    /**
     * 实体化一次。仅在 {@code !exists} 且无未决事务时允许。
     *
     * <p>同一世界<b>不得</b>产生第二个有效正品：已经有正品时，只有「同一事务 id 的重放」
     * 会返回原凭证，其他一律 {@link Reject#ALREADY_EXISTS}。
     */
    public Result issue(Holder to, String txnId, long now) {
        if (state == State.QUARANTINED) {
            return Result.fail(Reject.QUARANTINED, "账本隔离中，不能实体化：" + quarantineReason);
        }
        if (txnId == null || txnId.isBlank()) {
            return Result.fail(Reject.BAD_TXN, "实体化必须有事务 id");
        }
        if (exists) {
            if (txnId.equals(lastTxnId)) {
                return Result.done(token(), true, "重放同一实体化事务：凭证不变");
            }
            return Result.fail(Reject.ALREADY_EXISTS,
                    "同一世界已有有效正品（代次=" + generation + "），不得产生第二个");
        }
        if (!pendingTxnId.isBlank() && !pendingTxnId.equals(txnId)) {
            return Result.fail(Reject.PENDING_TXN, "有未决事务 " + pendingTxnId + "，先结束它");
        }
        generation++;
        custodyRevision++;
        exists = true;
        state = State.PRESENT;
        holder = to;
        lastTxnId = txnId;
        pendingTxnId = "";
        pendingVerification = false;
        lastReason = "issue:" + txnId;
        evidence.add("issue@" + now + " txn=" + txnId + " holder=" + describe(to));
        return Result.done(token(), false, "实体化成功，代次=" + generation);
    }

    // ---------------------------------------------------------------- 写：转移

    /** 转移到另一个承载者（只给 id，承载类型记为 unknown）。 */
    public Result transfer(String fromId, String toId, long expectedRevision, String txnId) {
        return transfer(fromId, Holder.unknown(toId), expectedRevision, txnId);
    }

    /**
     * 合法转移：服务端核对来源、目标、当前保管版本与事务 id。
     *
     * <p>版本不符（{@link Reject#STALE_REVISION}）与重复 txnId 一律拒绝；
     * 重放<b>已成功</b>的同一 txnId 返回幂等成功且不改账。
     */
    public Result transfer(String fromId, Holder to, long expectedRevision, String txnId) {
        if (txnId == null || txnId.isBlank()) {
            return Result.fail(Reject.BAD_TXN, "转移必须有事务 id");
        }
        if (txnId.equals(lastTxnId)) {
            return Result.done(token(), true, "重放同一转移事务：账本不变");
        }
        if (state == State.QUARANTINED) {
            return Result.fail(Reject.QUARANTINED, "账本隔离中，转移被拒绝：" + quarantineReason);
        }
        if (!exists) {
            return Result.fail(Reject.ABSENT, "账本上没有实物（载体丢失待核验）");
        }
        if (expectedRevision != custodyRevision) {
            return Result.fail(Reject.STALE_REVISION,
                    "保管版本不符：期望 " + expectedRevision + "，当前 " + custodyRevision);
        }
        if (holder == null || !holder.id().equals(fromId == null ? "" : fromId)) {
            return Result.fail(Reject.WRONG_SOURCE,
                    "来源与账本不符：账本=" + describe(holder) + " 请求=" + fromId);
        }
        if (to == null || !to.present()) {
            return Result.fail(Reject.BAD_TARGET, "目标非法（空）");
        }
        if (to.id().equals(holder.id())) {
            return Result.fail(Reject.BAD_TARGET, "目标与来源相同");
        }
        holder = to;
        custodyRevision++;
        lastTxnId = txnId;
        state = State.PRESENT;
        pendingVerification = false;
        lastReason = "transfer:" + txnId;
        return Result.done(token(), false, "转移成功，保管版本=" + custodyRevision);
    }

    // ---------------------------------------------------------------- 写：载体丢失

    /** 载体丢失（火烧/岩浆/虚空/掉落清理/实体消失）。 */
    public void onCarrierLost(String reason) {
        onCarrierLost(reason, 0L);
    }

    /**
     * 载体丢失：只把 {@code exists} 置 false 并记「待核验」。
     *
     * <p><b>绝不改阶段</b>——本类根本没有阶段字段，调用方也不得借此推进
     * {@link Destiny.DestinyGu}。丢失是可恢复的保管状态，不是本体被毁。
     */
    public void onCarrierLost(String reason, long now) {
        String why = reason == null || reason.isBlank() ? "unknown" : reason;
        exists = false;
        state = State.UNVERIFIED;
        pendingVerification = true;
        holder = null;
        custodyRevision++;
        lastReason = "carrier-lost:" + why;
        evidence.add("carrier-lost@" + now + " " + why);
    }

    // ---------------------------------------------------------------- 写：未决事务

    /** 开始一个未决事务（例如跨刻的分发流程）；未结束前禁止实体化。 */
    public boolean beginPendingTxn(String txnId) {
        if (txnId == null || txnId.isBlank() || !pendingTxnId.isBlank()) {
            return false;
        }
        pendingTxnId = txnId;
        return true;
    }

    /** 结束未决事务。 */
    public boolean endPendingTxn(String txnId) {
        if (txnId == null || !txnId.equals(pendingTxnId)) {
            return false;
        }
        pendingTxnId = "";
        return true;
    }

    // ---------------------------------------------------------------- 写：复制

    /**
     * 标记一枚复制品（重放/创造模式取物/同 UUID 第二枚）。
     *
     * <p>进入隔离并保留证据；<b>不自动认定副本为真</b>，也不改动正品的代次与位置。
     * 隔离期间所有写操作被拒绝，解除需要具名核验人 {@link #liftQuarantine}。
     */
    public Result markDuplicate(String serial, String note, long now) {
        String key = serial == null || serial.isBlank() ? "unnamed" : serial;
        duplicates.put(key, new Duplicate(key, note == null ? "" : note, now));
        state = State.QUARANTINED;
        quarantineReason = "duplicate:" + key;
        lastReason = "duplicate:" + key;
        evidence.add("duplicate@" + now + " serial=" + key + " note=" + note);
        return Result.fail(Reject.QUARANTINED,
                "发现复制证据，账本隔离；副本不认定为真，正品需具名核验");
    }

    /** 解除隔离。必须给出具名核验人；不改变 {@code exists}，也不认任何副本为真。 */
    public boolean liftQuarantine(String reviewer, String note) {
        if (reviewer == null || reviewer.isBlank()) {
            return false;
        }
        state = exists ? State.PRESENT : State.UNVERIFIED;
        quarantineReason = "";
        lastReason = "quarantine-lifted:" + reviewer;
        evidence.add("lift@" + reviewer + " " + (note == null ? "" : note));
        return true;
    }

    // ---------------------------------------------------------------- 校验

    /**
     * 判断「这枚实物是否就是当前有效正品」——供切片 2 的物品校验调用。
     *
     * @param vesselToken    物品上携带的凭证（{@link #token()}）
     * @param generation     物品记录的实体化代次
     * @param custodyRevision 物品记录的保管版本
     */
    public Check validate(String vesselToken, int generation, long custodyRevision) {
        if (vesselToken == null || vesselToken.isBlank()) {
            return new Check(false, "物品没有凭证");
        }
        if (!exists) {
            return new Check(false, "账本上没有实物（载体丢失待核验）");
        }
        if (state == State.QUARANTINED) {
            return new Check(false, "账本隔离中：" + quarantineReason);
        }
        if (state != State.PRESENT) {
            return new Check(false, "账本状态不是「在位」：" + state);
        }
        if (generation != this.generation) {
            return new Check(false, "实体化代次不符：物品=" + generation + " 账本=" + this.generation);
        }
        if (custodyRevision != this.custodyRevision) {
            return new Check(false, "保管版本不符：物品=" + custodyRevision
                    + " 账本=" + this.custodyRevision + "（可能已转移或丢失）");
        }
        if (!vesselToken.equals(token())) {
            return new Check(false, "凭证与当前正品不符（复制品/重放）");
        }
        return new Check(true, "与当前有效正品一致");
    }

    // ---------------------------------------------------------------- 持久化

    /** 纯字符串快照，MC 层写进 CompoundTag 即可。 */
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("worldId", worldId);
        m.put("logicalId", logicalId);
        m.put("generation", Integer.toString(generation));
        m.put("custodyRevision", Long.toString(custodyRevision));
        m.put("exists", Boolean.toString(exists));
        m.put("state", state.name());
        m.put("holderKind", holder == null ? "" : holder.kind());
        m.put("holderId", holder == null ? "" : holder.id());
        m.put("holderDim", holder == null ? "" : holder.dim());
        m.put("holderPos", holder == null ? "0" : Long.toString(holder.pos()));
        m.put("lastTxnId", lastTxnId);
        m.put("pendingTxnId", pendingTxnId);
        m.put("pendingVerification", Boolean.toString(pendingVerification));
        m.put("quarantineReason", quarantineReason);
        m.put("lastReason", lastReason);
        StringBuilder dup = new StringBuilder();
        for (Duplicate d : duplicates.values()) {
            if (dup.length() > 0) {
                dup.append('\n');
            }
            dup.append(d.serial()).append('|').append(d.at()).append('|').append(d.note());
        }
        m.put("duplicates", dup.toString());
        m.put("evidence", String.join("\n", evidence));
        return m;
    }

    /** 从 {@link #snapshot()} 恢复；坏值一律按保守默认处理，不抛异常。 */
    public static DestinyCustody restore(Map<String, String> data) {
        Map<String, String> m = data == null ? java.util.Collections.emptyMap() : data;
        DestinyCustody c = new DestinyCustody(m.get("worldId"), m.get("logicalId"));
        c.generation = parseInt(m.get("generation"), 0);
        c.custodyRevision = parseLong(m.get("custodyRevision"), 0L);
        c.exists = Boolean.parseBoolean(m.getOrDefault("exists", "false"));
        c.state = parseState(m.get("state"), c.exists ? State.PRESENT : State.ABSENT);
        String kind = m.getOrDefault("holderKind", "");
        if (!kind.isBlank()) {
            c.holder = new Holder(kind, m.getOrDefault("holderId", ""),
                    m.getOrDefault("holderDim", ""), parseLong(m.get("holderPos"), 0L));
        }
        c.lastTxnId = m.getOrDefault("lastTxnId", "");
        c.pendingTxnId = m.getOrDefault("pendingTxnId", "");
        c.pendingVerification = Boolean.parseBoolean(m.getOrDefault("pendingVerification", "false"));
        c.quarantineReason = m.getOrDefault("quarantineReason", "");
        c.lastReason = m.getOrDefault("lastReason", "");
        String dup = m.getOrDefault("duplicates", "");
        for (String line : dup.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 3);
            String serial = parts[0];
            long at = parts.length > 1 ? parseLong(parts[1], 0L) : 0L;
            String note = parts.length > 2 ? parts[2] : "";
            c.duplicates.put(serial, new Duplicate(serial, note, at));
        }
        String ev = m.getOrDefault("evidence", "");
        for (String line : ev.split("\n")) {
            if (!line.isBlank()) {
                c.evidence.add(line);
            }
        }
        return c;
    }

    private static int parseInt(String v, int fallback) {
        try {
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String v, long fallback) {
        try {
            return v == null || v.isBlank() ? fallback : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static State parseState(String v, State fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return State.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String describe(Holder h) {
        if (h == null) {
            return "none";
        }
        return h.kind() + ':' + h.id() + (h.dim().isBlank() ? "" : "@" + h.dim());
    }
}
