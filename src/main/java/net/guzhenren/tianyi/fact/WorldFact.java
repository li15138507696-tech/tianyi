package net.guzhenren.tianyi.fact;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 一条<b>带来源</b>的真实世界事件。
 *
 * <h2>它解决什么</h2>
 * 天意与 NPC 势力都不许「后台读世界状态」：谁做了什么，必须先是<b>事实</b>
 * （有人看见、有人上报），才谈得上反应。所以「采集蛊材 / 抢占产地 / 杀人 / 抢夺 /
 * 商贸 / 炼成 / 目击 / 逆天 / 兽群」这些世界动作统一被压成一条不可变的
 * {@code WorldFact}，再由 {@link FactLedger} 记账、由 {@link IntelNet} 有限传播。
 *
 * <h2>三条硬约束</h2>
 * <ul>
 *   <li><b>身份用完整 UUID。</b>{@link #actor} / {@link #target} 是完整 UUID 字符串，
 *       维度是完整 {@code namespace:path} 键。禁止用字符串哈希冒充身份。</li>
 *   <li><b>去重靠派生 id，不靠 {@code String.hashCode()}。</b>{@link #key} 把
 *       「种类 + 行为人 + 目标 + 维度 + 位置 + <b>时间桶</b>」规范化后喂给
 *       {@link UUID#nameUUIDFromBytes}，得到 128 位稳定指纹：同一行为的连续多个 tick
 *       落在同一桶里 → 同一条事实；不同行为人 / 不同位置 / 不同种类 → 必然不同 id。
 *       哈希只有 32 位，碰撞会把两个不同的人合成一个，所以不用。</li>
 *   <li><b>情报会腐烂。</b>{@link #effectiveConfidence} 随时间线性衰减到 0，
 *       超过 {@link #ttl} 后 {@link #expired} 为真——旧事实不能再当依据。</li>
 * </ul>
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测（{@code _pack/run_tests.py}）。
 * 位置一律是 {@code BlockPos.asLong()} 的打包值，无位置时用 {@link #NO_POS}。
 */
public final class WorldFact {

    // ---------------------------------------------------------------- 种类

    /** 事实种类。刻意保持少量、语义明确，避免变成「万能属性表」。 */
    public enum Kind {
        /** 采集蛊材（tag 命中的物品被获得）。 */
        HARVEST,
        /** 抢占产地（资源点/矿脉易主）。 */
        SEIZE,
        /** 杀死 NPC。 */
        KILL_NPC,
        /** 杀死玩家。 */
        KILL_PLAYER,
        /** 抢夺资源（背包、商队、仓库被劫）。 */
        ROBBERY,
        /** 商贸成交。 */
        TRADE,
        /** 炼成蛊虫。 */
        ALCHEMY,
        /** 目击位置（只说明「谁在哪」）。 */
        SIGHTING,
        /** 对抗天意（逆天行为被<b>真的看见</b>）。 */
        DEFIANCE,
        /** 兽群活动（迁徙、捕食、巢穴）。 */
        HERD
    }

    /** 无名址事实的 {@link #pos} 取值（{@code BlockPos.asLong()} 永不产生该值）。 */
    public static final long NO_POS = Long.MIN_VALUE;

    /** 默认有效期：一个 MC 日。 */
    public static final long DEFAULT_TTL = 24000L;

    /** 去重时间桶：一个桶内的同一行为只算一条事实（默认 60 秒）。 */
    public static final long TICK_BUCKET = 1200L;

    /** 行序列化分隔符：U+0001，正常文本里不会出现。 */
    static final char SEP = (char) 1;

    // ---------------------------------------------------------------- 字段

    /** 事实唯一标识，由 {@link #key} 派生。 */
    public final String id;
    /** 种类。 */
    public final Kind kind;
    /** 行为人身份（完整 UUID 字符串）。 */
    public final String actor;
    /** 目标身份 / 被作用主体 UUID；无目标时为空串。 */
    public final String target;
    /** 维度全名，如 {@code "minecraft:overworld"}。 */
    public final String dim;
    /** {@code BlockPos.asLong()} 打包值；无名址时 {@link #NO_POS}。 */
    public final long pos;
    /** 发生时刻（{@code level.getGameTime()}）。 */
    public final long atTick;
    /** 有效期（tick 数）。 */
    public final long ttl;
    /** 原始可信度 0..1。 */
    public final double baseConfidence;
    /** 人类可读摘要（只给服务端日志看，不参与任何判定）。 */
    public final String detail;

    // ---------------------------------------------------------------- 构造

    public WorldFact(Kind kind, String actor, String target, String dim, long pos,
                     long atTick, long ttl, double baseConfidence, String detail) {
        this.kind = kind == null ? Kind.SIGHTING : kind;
        this.actor = identity(actor);
        this.target = identity(target);
        this.dim = identity(dim);
        this.pos = pos;
        this.atTick = atTick;
        this.ttl = ttl;
        this.baseConfidence = clamp01(baseConfidence);
        this.detail = detail == null ? "" : detail;
        this.id = key(this.kind, this.actor, this.target, this.dim, this.pos, this.atTick);
    }

    /** 便捷构造：有效期用 {@link #DEFAULT_TTL}。 */
    public static WorldFact of(Kind kind, String actor, String target, String dim, long pos,
                               long atTick, double confidence, String detail) {
        return new WorldFact(kind, actor, target, dim, pos, atTick, DEFAULT_TTL, confidence, detail);
    }

    /** 便捷构造：无目标、无位置。 */
    public static WorldFact of(Kind kind, String actor, String dim, long atTick,
                               double confidence, String detail) {
        return of(kind, actor, "", dim, NO_POS, atTick, confidence, detail);
    }

    // ---------------------------------------------------------------- 去重键

    /**
     * 去重键：同一「种类 + 行为人 + 目标 + 维度 + 位置 + 时间桶」得到同一 id。
     *
     * <p>时间必须桶化：一次采集会在若干相邻 tick 内反复被观测到，若把 {@code atTick}
     * 原样入键，同一次行为会炸出十几条事实，传播与追溯全被污染。桶宽
     * {@link #TICK_BUCKET} 内视为同一次行为；跨桶就是两次行为，各自留痕。
     */
    public static String key(Kind kind, String actor, String target, String dim, long pos, long atTick) {
        String canonical = (kind == null ? Kind.SIGHTING : kind).name()
                + SEP + identity(actor)
                + SEP + identity(target)
                + SEP + identity(dim)
                + SEP + pos
                + SEP + bucket(atTick);
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 时间桶编号（向下取整，负 tick 也不出错）。 */
    public static long bucket(long atTick) {
        return Math.floorDiv(atTick, TICK_BUCKET);
    }

    // ---------------------------------------------------------------- 时相

    /** 失效时刻（不含）：{@code atTick + ttl}。 */
    public long expiresAt() {
        return atTick + ttl;
    }

    /** 是否已过期（严格晚于 {@link #expiresAt()} 才算过期）。 */
    public boolean expired(long now) {
        return now > expiresAt();
    }

    /** 从发生起经过的 tick 数（不为负）。 */
    public long age(long now) {
        return Math.max(0L, now - atTick);
    }

    /**
     * 随时间线性衰减后的有效可信度：{@code baseConfidence * (1 - age / ttl)}，夹在 0..1。
     *
     * <p>不超过 {@link #baseConfidence}，不小于 0：情报只会变旧变模糊，不会因为「等了很久」
     * 反而更可信。
     */
    public double effectiveConfidence(long now) {
        long age = now - atTick;
        if (age <= 0L) {
            return clamp01(baseConfidence);
        }
        if (ttl <= 0L || age >= ttl) {
            return 0.0;
        }
        double fade = 1.0 - ((double) age / (double) ttl);
        return clamp01(baseConfidence * fade);
    }

    /** 是否带位置。 */
    public boolean positionKnown() {
        return pos != NO_POS;
    }

    // ---------------------------------------------------------------- 比较 / 序列化

    /** 是否同一条事实（按 id 判定，不看别的字段）。 */
    public boolean sameAs(WorldFact o) {
        return o != null && id.equals(o.id);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldFact && ((WorldFact) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "WorldFact{" + kind + " " + shortId(actor) + "->" + shortId(target)
                + " @" + (positionKnown() ? String.valueOf(pos) : "-")
                + " " + dim + " t=" + atTick + " ttl=" + ttl
                + " conf=" + fmt(baseConfidence) + "}";
    }

    /**
     * 行序列化（{@link FactLedger#snapshot()} 交给 MC 侧 SavedData 用的最小形式）。
     *
     * <p>字段顺序：kind, actor, target, dim, pos, atTick, ttl, baseConfidence, detail。
     */
    public String toRow() {
        return kind.name() + SEP + actor + SEP + target + SEP + dim + SEP + pos
                + SEP + atTick + SEP + ttl + SEP + baseConfidence + SEP + safe(detail);
    }

    /** {@link #toRow()} 的逆操作；坏行返回 {@code null}（不抛异常穿进 tick）。 */
    public static WorldFact fromRow(String row) {
        String[] p = fields(row);
        if (p.length < 9) {
            return null;
        }
        try {
            return new WorldFact(Kind.valueOf(p[0]), p[1], p[2], p[3],
                    Long.parseLong(p[4]), Long.parseLong(p[5]), Long.parseLong(p[6]),
                    Double.parseDouble(p[7]), p[8]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 规范化身份：null → 空串，去首尾空白并小写（UUID / 注册键都稳定）。 */
    public static String identity(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static String[] fields(String row) {
        if (row == null || row.isEmpty()) {
            return new String[0];
        }
        List<String> out = new ArrayList<>(10);
        int start = 0;
        for (int i = 0; i < row.length(); i++) {
            if (row.charAt(i) == SEP) {
                out.add(row.substring(start, i));
                start = i + 1;
            }
        }
        out.add(row.substring(start));
        return out.toArray(new String[0]);
    }

    private static String safe(String s) {
        return s.replace(SEP, ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "-";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
