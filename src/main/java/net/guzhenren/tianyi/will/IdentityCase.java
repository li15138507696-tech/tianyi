package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 身份证据链：多份证据<b>互相印证或互相矛盾</b>，据此给出「这人到底是谁」的判定。
 *
 * <h2>三条不可动摇的规矩</h2>
 * <ol>
 *   <li><b>证据记在当时的那个身份上。</b>玩家改名、换皮、换势力，都不会改写已经写下的
 *       证据 —— 每条证据都绑在「事发那一刻的完整 UUID」上。</li>
 *   <li><b>单条弱证据不构成识破。</b>一份目击、一次拾取都只是线索；
 *       要 {@link #IDENTIFY_AT} 以上的可信度、且至少 {@link #MIN_KINDS} 类<b>不同</b>证据
 *       才能判为识破。</li>
 *   <li><b>矛盾不会被抹掉。</b>指向另一种身份的证词会<b>压低</b>结论可信度，
 *       但原始记录一律保留 —— 所以「谁在撒谎」是可追溯的。</li>
 * </ol>
 *
 * <h2>为什么不能只数条数</h2>
 * 十条同一种证据（比如十个人都重复同一句传闻）不等于十个独立证据。
 * 计分时同一 {@link Kind} 只取<b>权重最高的那条</b>参与主分，其余按
 * {@link #DUPLICATE_FACTOR} 折价，避免「刷同一句流言就能定罪」。
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class IdentityCase {
    /** 证据种类。种类必须不同才算「独立印证」。 */
    public enum Kind {
        /** 目击（有人在现场看到了）。 */
        WITNESS,
        /** 持有物（身上/仓里搜出了什么）。 */
        POSSESSION,
        /** 证词（别人转述）。 */
        TESTIMONY,
        /** 炼蛊/手艺留下的痕迹。 */
        CRAFT_TRACE,
        /** 关系线索（跟谁来往）。 */
        RELATION
    }

    /** 判定结果。 */
    public enum Verdict {
        /** 证据不足。 */
        INSUFFICIENT,
        /** 有嫌疑，但还定不了。 */
        SUSPECT,
        /** 已识破。 */
        IDENTIFIED
    }

    /** 识破所需的最低可信度。 */
    public static final double IDENTIFY_AT = 6.0;
    /** 起疑所需的最低可信度。 */
    public static final double SUSPECT_AT = 2.0;
    /** 至少需要几类不同证据才能识破。 */
    public static final int MIN_KINDS = 3;
    /** 同种证据的重复项折价。 */
    public static final double DUPLICATE_FACTOR = 0.25;

    /**
     * 一条证据。不可变。
     *
     * @param subjectUuid 事发当时的完整 UUID（改名不改它）
     * @param identityTag 这条证据指向「哪个身份」（空串表示不指向特定身份）
     */
    public record Evidence(String id, String subjectUuid, Kind kind, double weight,
                           double reliability, String identityTag, String dim, long pos,
                           long at, String note) {
        public Evidence {
            Knowledge.requireIdentity(subjectUuid);
            if (kind == null) {
                throw new IllegalArgumentException("证据必须有种类");
            }
            weight = Math.max(0.0, weight);
            reliability = Knowledge.clamp(reliability, 0.0, 1.0);
        }

        /** 单条证据的有效分。 */
        public double score() {
            return weight * reliability;
        }
    }

    private final String subjectUuid;
    private final List<Evidence> evidence = new ArrayList<>();
    /** 去重键：同一种类 + 同一时间桶 + 同一位置，视为同一条。 */
    private final Set<String> seen = new LinkedHashSet<>();

    public IdentityCase(String subjectUuid) {
        this.subjectUuid = Knowledge.requireIdentity(subjectUuid);
    }

    public String subjectUuid() {
        return subjectUuid;
    }

    public int size() {
        return evidence.size();
    }

    public List<Evidence> all() {
        return new ArrayList<>(evidence);
    }

    /**
     * 收一条证据。
     *
     * @param bucketTicks 时间桶（同一桶内的同种同地证据视为重复）
     * @return {@code false} 表示是重复证据，没有收下
     */
    public boolean add(Evidence e, long bucketTicks) {
        if (e == null || !e.subjectUuid().equals(subjectUuid)) {
            return false; // 只收记在本人 UUID 上的证据
        }
        long bucket = bucketTicks <= 0 ? e.at() : e.at() / bucketTicks;
        String key = e.kind() + "|" + bucket + "|" + e.dim() + "|" + e.pos();
        if (!seen.add(key)) {
            return false;
        }
        evidence.add(e);
        return true;
    }

    /** 有证据指向的身份标签（按分数从高到低）。 */
    public List<String> identityTags() {
        Map<String, Double> byTag = new LinkedHashMap<>();
        for (Evidence e : evidence) {
            if (e.identityTag().isEmpty()) {
                continue;
            }
            byTag.merge(e.identityTag(), e.score(), Double::sum);
        }
        List<Map.Entry<String, Double>> list = new ArrayList<>(byTag.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (var en : list) {
            out.add(en.getKey());
        }
        return out;
    }

    /** 指向最多的那个身份（没有则空串）。 */
    public String leadingIdentity() {
        List<String> tags = identityTags();
        return tags.isEmpty() ? "" : tags.get(0);
    }

    /**
     * 可信度：不同种类各取最高一条计入主分，同种重复按折价计入。
     *
     * <p>矛盾证据（指向与主流身份不同的标签）会按比例扣分，但不会让记录消失。
     */
    public double confidence() {
        String lead = leadingIdentity();
        Map<Kind, Double> best = new LinkedHashMap<>();
        double dupSum = 0.0;
        for (Evidence e : evidence) {
            if (!lead.isEmpty() && !e.identityTag().isEmpty() && !e.identityTag().equals(lead)) {
                continue; // 矛盾项放到 contradicted() 里处理
            }
            double s = e.score();
            Double cur = best.get(e.kind());
            if (cur == null) {
                best.put(e.kind(), s);
            } else if (s > cur) {
                dupSum += cur * DUPLICATE_FACTOR;
                best.put(e.kind(), s);
            } else {
                dupSum += s * DUPLICATE_FACTOR;
            }
        }
        double main = 0.0;
        for (double v : best.values()) {
            main += v;
        }
        double contradiction = contradictedScore();
        return Math.max(0.0, main + dupSum - contradiction * 0.5);
    }

    /** 指向「非主流身份」的证据分之和。 */
    public double contradictedScore() {
        String lead = leadingIdentity();
        if (lead.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Evidence e : evidence) {
            if (!e.identityTag().isEmpty() && !e.identityTag().equals(lead)) {
                sum += e.score();
            }
        }
        return sum;
    }

    /** 参与计分的证据种类数。 */
    public int kindCount() {
        Set<Kind> kinds = new LinkedHashSet<>();
        for (Evidence e : evidence) {
            kinds.add(e.kind());
        }
        return kinds.size();
    }

    /** 判定。 */
    public Verdict verdict() {
        double c = confidence();
        if (c >= IDENTIFY_AT && kindCount() >= MIN_KINDS) {
            return Verdict.IDENTIFIED;
        }
        if (c >= SUSPECT_AT) {
            return Verdict.SUSPECT;
        }
        return Verdict.INSUFFICIENT;
    }

    /** 一句话结论（给日志/命令用，不是给玩家看的面板）。 */
    public String summary() {
        return String.format(Locale.ROOT,
                "身份案{%s 判定=%s 可信=%.2f 证据=%d 类=%d 指向=%s 矛盾=%.2f}",
                subjectUuid.substring(0, 8), verdict(), confidence(), evidence.size(),
                kindCount(), leadingIdentity().isEmpty() ? "-" : leadingIdentity(),
                contradictedScore());
    }

    /** 全部身份案。 */
    public static final class Ledger {
        private final Map<String, IdentityCase> bySubject = new LinkedHashMap<>();

        public IdentityCase of(String uuid) {
            String key = Knowledge.requireIdentity(uuid);
            return bySubject.computeIfAbsent(key, IdentityCase::new);
        }

        public IdentityCase find(String uuid) {
            try {
                return bySubject.get(Knowledge.requireIdentity(uuid));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public int size() {
            return bySubject.size();
        }

        /** 已识破的身份数。 */
        public int identifiedCount() {
            int n = 0;
            for (IdentityCase c : bySubject.values()) {
                if (c.verdict() == Verdict.IDENTIFIED) {
                    n++;
                }
            }
            return n;
        }

        public List<IdentityCase> all() {
            return new ArrayList<>(bySubject.values());
        }

        /** 从存档恢复一条证据（不走时间桶去重，去重键已存在时自然被拒）。 */
        public void restore(Evidence e) {
            if (e == null) {
                return;
            }
            of(e.subjectUuid()).add(e, 0L);
        }
    }
}
