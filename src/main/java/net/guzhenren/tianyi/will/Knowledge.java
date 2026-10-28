package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 天意的感知与知识边界。
 *
 * <h2>核心纪律（对应原著的「天意不是全知」）</h2>
 * <ul>
 *   <li><b>后台能读到，不等于天意知道。</b>所有进入 {@link Store} 的情报都必须带
 *       {@link Source}：天意只能通过自身分布（{@link Source#SELF_SPREAD}）、接触
 *       （{@link Source#CONTACT}）、载体（{@link Source#CARRIER}）、已知事件
 *       （{@link Source#EVENT}）获得信息；{@link Source#INFERRED} 只能由旧情报推出。</li>
 *   <li><b>遮蔽即盲区。</b>{@link Store#cover} 之后 {@link Store#latest} 对该主体一律
 *       返回空；天意只剩 {@link Store#estimate} 的模糊推断，且<b>拿不到实时坐标</b>。</li>
 *   <li><b>旧情报会腐烂。</b>超过 {@link WillConfig#FACT_SOFT_TTL} 后可信度按时间线性
 *       衰减，超过 {@link WillConfig#FACT_HARD_TTL} 直接清除——所以「失去目标」之后
 *       的追踪必然失准。</li>
 *   <li><b>身份用完整标识。</b>玩家/NPC 一律是完整 UUID 字符串，维度是完整
 *       {@code namespace:path} 键，注册项是完整注册 ID。禁止用字符串哈希冒充身份，
 *       否则两个不同实体可能被天意误当成同一个。</li>
 * </ul>
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class Knowledge {
    private Knowledge() {
    }

    /** 情报来源。前四种是天意「真的看到」，最后一种是推断。 */
    public enum Source {
        /** 天意自身分布：加载中的区块被天意覆盖到。 */
        SELF_SPREAD(1.00),
        /** 直接接触：实体与天意载体/天意造物接触。 */
        CONTACT(0.90),
        /** 载体传递：经仙元石、宿命蛊等载体回报。 */
        CARRIER(0.80),
        /** 已知事件：死亡、渡劫、升仙等世界事件被记录。 */
        EVENT(0.75),
        /** 传闻：NPC 口耳相传，可信度低、会变形。 */
        RUMOR(0.35),
        /** 推断：只能由旧情报 + 常识推出，<b>不是观测</b>。 */
        INFERRED(0.20);

        public final double baseConfidence;

        Source(double baseConfidence) {
            this.baseConfidence = baseConfidence;
        }

        public boolean observed() {
            return this != INFERRED;
        }
    }

    /** 情报种类。刻意保持少量、语义明确，避免变成「万能属性表」。 */
    public enum Kind {
        /** 综合实力（转数/战力/道痕合成）。 */
        POWER,
        /** 资源控制（元石、蛊材、库存）。 */
        RESOURCE,
        /** 道德值（原生本体字段，社会信任用）。 */
        MORALITY,
        /** 气运（原生本体字段，机缘走向用）。 */
        LUCK,
        /** 位置（实时坐标）。 */
        POSITION,
        /** 逆天行为（天意<b>实际知道</b>的那些）。 */
        DEFIANCE,
        /** 所属势力。 */
        FACTION,
        /** 仙窍状态。 */
        APERTURE,
        /** 宿命/星宿相关状态。 */
        DESTINY,
        /** 炼成一次（有确切产物 ID 与真实尝试才算）。 */
        CRAFT
    }

    /** 一条情报。不可变。 */
    public static final class Fact {
        public final String subject;
        public final Kind kind;
        public final double value;
        public final long observedAt;
        public final String dim;
        /** 打包的 BlockPos；{@link #NO_POS} 表示与位置无关。 */
        public final long pos;
        public final double confidence;
        public final Source source;

        public static final long NO_POS = Long.MIN_VALUE;

        public Fact(String subject, Kind kind, double value, long observedAt,
                    String dim, long pos, double confidence, Source source) {
            this.subject = requireIdentity(subject);
            this.kind = kind;
            this.value = value;
            this.observedAt = observedAt;
            this.dim = dim;
            this.pos = pos;
            this.confidence = clamp(confidence, 0.0, 1.0) * source.baseConfidence;
            this.source = source;
        }

        /** 便捷构造：观测类情报。 */
        public static Fact observed(String subject, Kind kind, double value, long now,
                                    String dim, long pos, Source source) {
            return new Fact(subject, kind, value, now, dim, pos, 1.0, source);
        }

        /** 便捷构造：无位置情报。 */
        public static Fact of(String subject, Kind kind, double value, long now, Source source) {
            return new Fact(subject, kind, value, now, "", NO_POS, 1.0, source);
        }

        public long age(long now) {
            return Math.max(0L, now - observedAt);
        }

        /** 时间衰减后的有效可信度（0..1）。 */
        public double effectiveConfidence(long now) {
            long age = age(now);
            if (age >= WillConfig.FACT_HARD_TTL) {
                return 0.0;
            }
            if (age <= WillConfig.FACT_SOFT_TTL) {
                return confidence;
            }
            double span = (double) (WillConfig.FACT_HARD_TTL - WillConfig.FACT_SOFT_TTL);
            double fade = 1.0 - ((age - WillConfig.FACT_SOFT_TTL) / span);
            return confidence * clamp(fade, 0.0, 1.0);
        }

        @Override
        public String toString() {
            return "Fact{" + subject + " " + kind + "=" + fmt(value) + " src=" + source
                    + " conf=" + fmt(confidence) + " ageOk}";
        }
    }

    /** 天意对某个主体的「实时可跟踪」状态。 */
    public record Track(boolean live, double value, double confidence, String dim,
                        long pos, boolean positionStale, String note) {
    }

    /**
     * 世界级知识库。按主体索引；有界；支持遮蔽与衰减。
     *
     * <p>不是线程安全的：只在服务端主线程（或离线测试线程）上使用。
     */
    public static final class Store {
        private final Map<String, List<Fact>> bySubject = new LinkedHashMap<>();
        private final Map<String, Long> coveredUntil = new HashMap<>();
        private final Map<String, String> coverCause = new HashMap<>();

        // ------------------------------------------------------------ 观测入口

        /**
         * 记录一条观测情报。
         *
         * @return {@code false} 表示天意此刻对该主体处于盲区，这条情报<b>根本没被看到</b>。
         */
        public boolean observe(Fact fact) {
            if (fact == null) {
                return false;
            }
            if (!fact.source.observed()) {
                // 推断不能当观测写入：这是「天意不能自欺」的硬边界
                return false;
            }
            if (isCovered(fact.subject, fact.observedAt)) {
                return false;
            }
            List<Fact> list = bySubject.computeIfAbsent(fact.subject, k -> new ArrayList<>());
            list.add(fact);
            // 有界：超上限丢最旧
            while (list.size() > WillConfig.FACTS_PER_SUBJECT) {
                list.remove(0);
            }
            return true;
        }

        /** 已过期条目清理（决策器每轮调用一次即可）。 */
        public void purge(long now) {
            bySubject.values().removeIf(list -> {
                list.removeIf(f -> f.age(now) >= WillConfig.FACT_HARD_TTL);
                return list.isEmpty();
            });
            coveredUntil.entrySet().removeIf(e -> e.getValue() <= now - WillConfig.FACT_HARD_TTL);
        }

        // ------------------------------------------------------------ 遮蔽 / 盲区

        /**
         * 遮蔽一个主体：从现在起 {@code ticks} 刻内，天意看不到它的任何新情报。
         *
         * <p>对应原著「神不知 / 暗渡 / 天消意散」这类手段的效果落点之一：
         * 改变的是<b>感知</b>，不是给一个「免疫天意」的祝福。
         */
        public void cover(String subject, long now, long ticks, String cause) {
            long until = now + Math.max(WillConfig.BLIND_MIN_TICKS, ticks);
            coveredUntil.merge(requireIdentity(subject), until, Math::max);
            coverCause.put(subject, cause == null ? "unknown" : cause);
        }

        /** 解除遮蔽（例：反制的时效结束、或天意重新摸到载体）。 */
        public void uncover(String subject) {
            coveredUntil.remove(subject);
            coverCause.remove(subject);
        }

        public boolean isCovered(String subject, long now) {
            Long until = coveredUntil.get(subject);
            return until != null && until > now;
        }

        public String coverCause(String subject) {
            return coverCause.get(subject);
        }

        // ------------------------------------------------------------ 读取

        /** 最近一条同类情报；处于盲区时返回空 —— 这就是「真实盲区」。 */
        public Optional<Fact> latest(String subject, Kind kind, long now) {
            if (isCovered(subject, now)) {
                return Optional.empty();
            }
            List<Fact> list = bySubject.get(subject);
            if (list == null) {
                return Optional.empty();
            }
            Fact best = null;
            for (Fact f : list) {
                if (f.kind != kind) {
                    continue;
                }
                if (f.effectiveConfidence(now) <= 0.0) {
                    continue;
                }
                if (best == null || f.observedAt > best.observedAt) {
                    best = f;
                }
            }
            return Optional.ofNullable(best);
        }

        /**
         * 天意对该主体的最佳估计。
         *
         * <p>盲区或情报过期时返回的是<b>按旧情报衰减的推断值</b>，并且
         * {@link Track#live()} 为 {@code false}、{@link Track#positionStale()} 为
         * {@code true} ——调用方据此必须放弃精确追踪。
         */
        public Track track(String subject, Kind kind, long now, double fallback) {
            boolean covered = isCovered(subject, now);
            Optional<Fact> last = rawLatest(subject, kind, now);
            if (last.isEmpty()) {
                return new Track(false, fallback, 0.0, "", Fact.NO_POS, true,
                        covered ? "covered-no-intel" : "no-intel");
            }
            Fact f = last.get();
            double eff = f.effectiveConfidence(now);
            boolean live = !covered && eff > 0.0 && f.age(now) <= WillConfig.FACT_SOFT_TTL;
            if (live) {
                return new Track(true, f.value, eff, f.dim, f.pos, false, "live");
            }
            // 失去目标：只能用旧情报推断，且不能给实时坐标
            double decayed = f.value; // 数值本身不随时间"腐烂"，腐烂的是可信度
            return new Track(false, decayed, eff, f.dim, Fact.NO_POS, true,
                    covered ? "covered-infer" : "stale-infer");
        }

        /** 忽略遮蔽的原始查询，仅给内部推断用。 */
        private Optional<Fact> rawLatest(String subject, Kind kind, long now) {
            List<Fact> list = bySubject.get(subject);
            if (list == null) {
                return Optional.empty();
            }
            Fact best = null;
            for (Fact f : list) {
                if (f.kind != kind) {
                    continue;
                }
                if (f.effectiveConfidence(now) <= 0.0) {
                    continue;
                }
                if (best == null || f.observedAt > best.observedAt) {
                    best = f;
                }
            }
            return Optional.ofNullable(best);
        }

        /** 天意是否<b>实际知道</b>该主体有逆天行为（不是后台判定）。 */
        public boolean knowsDefiance(String subject, long now, long window) {
            if (isCovered(subject, now)) {
                return false;
            }
            List<Fact> list = bySubject.get(subject);
            if (list == null) {
                return false;
            }
            for (Fact f : list) {
                if (f.kind == Kind.DEFIANCE && f.value > 0.0
                        && f.age(now) <= window
                        && f.effectiveConfidence(now) > 0.0) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 天意<b>实际知道</b>的逆天行为强度合计（只算窗口内、置信度未归零的）。
         *
         * <p>这是决策器 threat 里那一项的输入：后台知道不算，看到才算。
         */
        public double defianceWeight(String subject, long now, long window) {
            if (isCovered(subject, now)) {
                return 0.0;
            }
            List<Fact> list = bySubject.get(subject);
            if (list == null) {
                return 0.0;
            }
            double sum = 0.0;
            for (Fact f : list) {
                if (f.kind == Kind.DEFIANCE && f.value > 0.0 && f.age(now) <= window) {
                    sum += f.value * f.effectiveConfidence(now);
                }
            }
            return sum;
        }

        /** 观测到的全部主体（用于分区调度；不含被遮蔽者）。 */
        public List<String> subjects(long now) {
            List<String> out = new ArrayList<>();
            for (String s : bySubject.keySet()) {
                if (!isCovered(s, now)) {
                    out.add(s);
                }
            }
            return out;
        }

        public int size() {
            return bySubject.size();
        }

        /** 该主体是否曾经进入过天意的视野（用于区分「陌生人」与「被遮蔽者」）。 */
        public boolean everSeen(String subject) {
            return bySubject.containsKey(subject);
        }

        // ------------------------------------------------------------ 序列化（供 MC 层持久化）

        public Map<String, List<Fact>> snapshotFacts() {
            Map<String, List<Fact>> copy = new LinkedHashMap<>();
            bySubject.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
            return copy;
        }

        public Map<String, Long> snapshotCovers() {
            return new HashMap<>(coveredUntil);
        }

        /** 从存档恢复（服务端重启 / 重连）。覆盖现有内容。 */
        public void importState(Map<String, List<Fact>> facts, Map<String, Long> covers) {
            bySubject.clear();
            coveredUntil.clear();
            if (facts != null) {
                facts.forEach((k, v) -> bySubject.put(k, new ArrayList<>(v)));
            }
            if (covers != null) {
                coveredUntil.putAll(covers);
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 校验并规范化身份：必须是完整 UUID，或 {@code 前缀:id} 形式的注册键。 */
    public static String requireIdentity(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("天意的目标必须有完整身份，收到空值");
        }
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException ignored) {
            // 非 UUID：允许 namespace:path（维度 / 注册 ID / 势力 id）
        }
        if (id.indexOf(':') < 0) {
            throw new IllegalArgumentException(
                    "身份必须是完整 UUID 或 namespace:path，禁止用裸字符串冒充：" + id);
        }
        return id.toLowerCase(Locale.ROOT);
    }

    public static boolean looksLikeUuid(String id) {
        if (id == null) {
            return false;
        }
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
