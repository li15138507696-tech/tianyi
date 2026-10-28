package net.guzhenren.tianyi.journal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 玩家自己的「见闻录」——<b>不是</b>世界账本。
 *
 * <h2>这个类解决的核心问题</h2>
 * 天意知道的（{@code Knowledge.Store}）和玩家知道的，是两件事。
 * 面板一旦直接同步世界账本，哪怕客户端再隐藏，情报也已经发出去了。
 * 所以这里只存<b>这个玩家有依据知道的</b>条目：
 * <ul>
 *   <li>每条都带 {@link Source}：亲眼所见 / 听人转述 / 自己推断；</li>
 *   <li>每条都带 {@code observedAt}（什么时候知道）与 {@code lastVerifiedAt}（最后核对）；</li>
 *   <li>过期只标注 {@code stale}，<b>不改写历史</b>；</li>
 *   <li>重复条目合并，不刷屏。</li>
 * </ul>
 *
 * <h2>四个页签的归类</h2>
 * <table border="1">
 *   <tr><th>页签</th><th>取什么</th></tr>
 *   <tr><td>观象</td><td>{@link Kind#SIGHT}：最近发生的异常</td></tr>
 *   <tr><td>察局</td><td>带 {@code caseId} 的条目，按疑局分组</td></tr>
 *   <tr><td>藏识</td><td>{@link Kind#KNOWLEDGE}：经得起验证的认识</td></tr>
 *   <tr><td>行藏</td><td>{@link Kind#TRACE}：自己留下的痕迹</td></tr>
 * </table>
 *
 * <h2>题名解锁</h2>
 * 玩家还没接触到「天意」这层的时候，标题就叫<b>见闻录</b>；
 * 只有真的攒到 {@link #UNLOCK_WITNESSED} 条亲见、且其中有一条已经进入疑局，
 * 才改叫<b>窥天录</b>。解锁依据全部来自实际内容，不是天数也不是等级。
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class PlayerJournal {
    /** 条目大类。 */
    public enum Kind {
        /** 观象：最近发生的异常。 */
        SIGHT,
        /** 藏识：已经核实、经得起追问的认识。 */
        KNOWLEDGE,
        /** 行藏：自己留下的痕迹（暴露风险）。 */
        TRACE
    }

    /** 这条是玩家怎么知道的。 */
    public enum Source {
        /** 亲眼所见。 */
        WITNESSED("亲见"),
        /** 听人转述。 */
        HEARD("听闻"),
        /** 自己推出来的。 */
        DEDUCED("推断");

        public final String cn;

        Source(String cn) {
            this.cn = cn;
        }
    }

    /** 一条见闻。不可变字段 + 少量可变的玩家标注。 */
    public static final class Entry {
        public final String id;
        /** 去重键：同一件事重复触发只留一条。 */
        public final String key;
        public final Kind kind;
        public final Source source;
        public final String title;
        public final String detail;
        public final String dim;
        public final long pos;
        public final long observedAt;
        /** 疑局 id；空串表示还没归入任何疑局。 */
        public final String caseId;
        /** 疑局标题（这条属于哪个疑局）。 */
        public final String caseTitle;
        /** 疑局里「还缺什么」。 */
        public final String caseMissing;

        public long lastVerifiedAt;
        public boolean favorite;
        public boolean doubtful;

        public Entry(String id, String key, Kind kind, Source source, String title, String detail,
                     String dim, long pos, long observedAt, String caseId, String caseTitle,
                     String caseMissing) {
            this.id = id;
            this.key = key == null ? "" : key;
            this.kind = kind == null ? Kind.SIGHT : kind;
            this.source = source == null ? Source.WITNESSED : source;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.dim = dim == null ? "" : dim;
            this.pos = pos;
            this.observedAt = observedAt;
            this.caseId = caseId == null ? "" : caseId;
            this.caseTitle = caseTitle == null ? "" : caseTitle;
            this.caseMissing = caseMissing == null ? "" : caseMissing;
            this.lastVerifiedAt = observedAt;
        }

        public boolean inCase() {
            return !caseId.isEmpty();
        }

        /** 多久没核对了（刻）。 */
        public long unverifiedFor(long now) {
            return Math.max(0L, now - lastVerifiedAt);
        }

        /** 是否「久未核实」。 */
        public boolean stale(long now) {
            return unverifiedFor(now) >= STALE_TICKS;
        }
    }

    /** 超过这个时长没核对，标注「久未核实」（2 游戏日）。 */
    public static final long STALE_TICKS = 24_000L * 2L;
    /** 记得的条目上限（防止无限膨胀）。 */
    public static final int MAX_ENTRIES = 120;
    /** 解锁「窥天录」题名需要的亲见条数。 */
    public static final int UNLOCK_WITNESSED = 3;

    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> keys = new LinkedHashSet<>();

    public int size() {
        return entries.size();
    }

    public List<Entry> all() {
        return new ArrayList<>(entries);
    }

    /**
     * 记一条见闻。
     *
     * @param key 去重键（同键只保留最早那条，并把核实时间往前推）
     * @return 真正新增时返回 {@code true}
     */
    public boolean add(Entry e) {
        if (e == null) {
            return false;
        }
        if (!e.key.isEmpty() && keys.contains(e.key)) {
            // 重复：只更新核实时间，不新增、不改写内容
            for (Entry old : entries) {
                if (old.key.equals(e.key)) {
                    old.lastVerifiedAt = Math.max(old.lastVerifiedAt, e.observedAt);
                    return false;
                }
            }
            return false;
        }
        if (!e.key.isEmpty()) {
            keys.add(e.key);
        }
        entries.add(e);
        while (entries.size() > MAX_ENTRIES) {
            Entry removed = entries.remove(0);
            keys.remove(removed.key);
        }
        return true;
    }

    public Entry find(String id) {
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 用新条目替换旧条目（保持原位置）。
     *
     * <p>条目的大部分字段是 final（标题、时间、地点不能被面板改写），
     * 所以「归入疑局」这类会改结构的标注用替换实例来实现。
     */
    public boolean replace(Entry oldE, Entry newE) {
        int idx = entries.indexOf(oldE);
        if (idx < 0 || newE == null) {
            return false;
        }
        entries.set(idx, newE);
        return true;
    }

    /** 收藏 / 取消收藏。返回新状态；找不到返回 {@code null}。 */
    public Boolean toggleFavorite(String id) {
        Entry e = find(id);
        if (e == null) {
            return null;
        }
        e.favorite = !e.favorite;
        return e.favorite;
    }

    /**
     * 标记「存疑」/ 取消。
     *
     * <p>这是<b>玩家的判断</b>，不会触发任何世界效果（不会打雷、不会算识破）。
     */
    public Boolean toggleDoubtful(String id) {
        Entry e = find(id);
        if (e == null) {
            return null;
        }
        e.doubtful = !e.doubtful;
        return e.doubtful;
    }

    /** 观象页：按时间倒序。 */
    public List<Entry> sights() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind == Kind.SIGHT) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Long.compare(b.observedAt, a.observedAt));
        return out;
    }

    /** 藏识页。 */
    public List<Entry> knowledge() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind == Kind.KNOWLEDGE) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Long.compare(b.observedAt, a.observedAt));
        return out;
    }

    /** 行藏页。 */
    public List<Entry> traces() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind == Kind.TRACE) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Long.compare(b.observedAt, a.observedAt));
        return out;
    }

    /** 察局页：按疑局 id 分组，组内保持时间顺序。 */
    public Map<String, List<Entry>> cases() {
        Map<String, List<Entry>> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (!e.inCase()) {
                continue;
            }
            out.computeIfAbsent(e.caseId, k -> new ArrayList<>()).add(e);
        }
        for (List<Entry> list : out.values()) {
            list.sort((a, b) -> Long.compare(a.observedAt, b.observedAt));
        }
        return out;
    }

    /** 某条疑局里所有条目的共同线索：还缺什么（取第一条非空的）。 */
    public String caseMissing(String caseId) {
        for (Entry e : entries) {
            if (e.caseId.equals(caseId) && !e.caseMissing.isEmpty()) {
                return e.caseMissing;
            }
        }
        return "尚缺：更多独立来源。";
    }

    public String caseTitle(String caseId) {
        for (Entry e : entries) {
            if (e.caseId.equals(caseId) && !e.caseTitle.isEmpty()) {
                return e.caseTitle;
            }
        }
        return caseId;
    }

    /**
     * 某个疑局的结论档位。
     *
     * <p>只用「孤证 / 相互印证 / 存在矛盾 / 尚无定论」四档，
     * <b>不显示概率、也不显示「天意布局已确认」这种后台答案</b>。
     */
    public String caseVerdict(String caseId) {
        List<Entry> list = cases().get(caseId);
        if (list == null || list.isEmpty()) {
            return "尚无定论";
        }
        int sources = new LinkedHashSet<>(list.stream().map(e -> e.source).toList()).size();
        boolean contradiction = list.stream().anyMatch(e -> e.doubtful);
        if (contradiction) {
            return "存在矛盾";
        }
        if (list.size() == 1) {
            return "孤证";
        }
        if (sources >= 2) {
            return "相互印证";
        }
        return "尚无定论";
    }

    /** 亲见条数。 */
    public int witnessedCount() {
        int n = 0;
        for (Entry e : entries) {
            if (e.source == Source.WITNESSED) {
                n++;
            }
        }
        return n;
    }

    /** 有没有条目进了疑局。 */
    public boolean hasCase() {
        for (Entry e : entries) {
            if (e.inCase()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 题名是否已解锁为「窥天录」。
     *
     * <p>依据全部来自实际内容：亲见 ≥ {@link #UNLOCK_WITNESSED} 且至少有一条进了疑局。
     */
    public boolean titleUnlocked() {
        return witnessedCount() >= UNLOCK_WITNESSED && hasCase();
    }

    /** 面板标题。 */
    public String title() {
        return titleUnlocked() ? "窥天录" : "见闻录";
    }

    /** 收藏数 / 存疑数（诊断用）。 */
    public int favoriteCount() {
        int n = 0;
        for (Entry e : entries) {
            if (e.favorite) {
                n++;
            }
        }
        return n;
    }

    public int doubtfulCount() {
        int n = 0;
        for (Entry e : entries) {
            if (e.doubtful) {
                n++;
            }
        }
        return n;
    }

    /** 摘要（日志/命令用）。 */
    public String summary(long now) {
        int stale = 0;
        for (Entry e : entries) {
            if (e.stale(now)) {
                stale++;
            }
        }
        return String.format(Locale.ROOT,
                "Journal{规则=%s 条目=%d 亲见=%d 疑局=%d 收藏=%d 存疑=%d 久未核实=%d}",
                title(), entries.size(), witnessedCount(), cases().size(),
                favoriteCount(), doubtfulCount(), stale);
    }
}
