package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianyi.journal.PlayerJournal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 见闻录的存取与投喂：<b>每个玩家一份</b>，只装他自己有依据知道的事。
 *
 * <h2>为什么必须服务端筛选</h2>
 * 面板只能拿到 {@link #load(ServerPlayer)} 出来的这份东西。
 * 世界总账（天意的知识库、情报网）<b>不下发</b>，
 * 客户端也没有任何"隐藏"逻辑 —— 拿不到就是拿不到。
 *
 * <h2>投喂来源（只喂真实的、玩家确实能感知的事）</h2>
 * <ul>
 *   <li>亲手拾得蛊材 → 观象·亲见</li>
 *   <li>自己名下产地出货 / 储量下降 → 观象·亲见</li>
 *   <li>天象异变（诱饵被识破的雷）→ 观象·亲见</li>
 *   <li>有人上门（人劫）→ 观象·亲见</li>
 *   <li>自己渡过的灾劫 → 观象·亲见</li>
 *   <li>炼成一次（有确切产物）→ 藏识·亲见</li>
 *   <li>打过某类妖兽 → 藏识·亲见（兽类特征）</li>
 *   <li>被人看见的当众行为 → 行藏·亲见</li>
 * </ul>
 *
 * <p>绝不投喂：天意的布局、威胁评分、别人私下知道的事、以及"天意已经看不见你"这类后台结论。
 */
public final class JournalStore {
    private JournalStore() {
    }

    /** 玩家 PersistentData 里的子标签。 */
    public static final String ROOT = "journal";

    // ---------------------------------------------------------------- 存取

    /** 取这个玩家的见闻录（没有就新建）。 */
    public static PlayerJournal load(ServerPlayer player) {
        CompoundTag root = root(player, true);
        PlayerJournal j = new PlayerJournal();
        for (Tag t : root.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                var e = new PlayerJournal.Entry(
                        c.getString("id"), c.getString("key"),
                        PlayerJournal.Kind.valueOf(c.getString("kind")),
                        PlayerJournal.Source.valueOf(c.getString("src")),
                        c.getString("title"), c.getString("detail"),
                        c.getString("dim"), c.getLong("pos"), c.getLong("at"),
                        c.getString("case"), c.getString("caseTitle"), c.getString("caseMiss"));
                e.lastVerifiedAt = c.getLong("verified");
                e.favorite = c.getBoolean("fav");
                e.doubtful = c.getBoolean("doubt");
                j.add(e);
            } catch (IllegalArgumentException ignored) {
                // 跨版本/损坏条目跳过，不阻断整份档案
            }
        }
        return j;
    }

    /** 写回。 */
    public static void save(ServerPlayer player, PlayerJournal j) {
        CompoundTag root = root(player, true);
        ListTag list = new ListTag();
        for (PlayerJournal.Entry e : j.all()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.id);
            c.putString("key", e.key);
            c.putString("kind", e.kind.name());
            c.putString("src", e.source.name());
            c.putString("title", e.title);
            c.putString("detail", e.detail);
            c.putString("dim", e.dim);
            c.putLong("pos", e.pos);
            c.putLong("at", e.observedAt);
            c.putString("case", e.caseId);
            c.putString("caseTitle", e.caseTitle);
            c.putString("caseMiss", e.caseMissing);
            c.putLong("verified", e.lastVerifiedAt);
            c.putBoolean("fav", e.favorite);
            c.putBoolean("doubt", e.doubtful);
            list.add(c);
        }
        root.put("entries", list);
    }

    private static CompoundTag root(ServerPlayer player, boolean create) {
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains("tianyi", Tag.TAG_COMPOUND)) {
            if (!create) {
                return new CompoundTag();
            }
            pd.put("tianyi", new CompoundTag());
        }
        CompoundTag tianyi = pd.getCompound("tianyi");
        if (!tianyi.contains(ROOT, Tag.TAG_COMPOUND)) {
            tianyi.put(ROOT, new CompoundTag());
        }
        return tianyi.getCompound(ROOT);
    }

    // ---------------------------------------------------------------- 投喂

    /**
     * 通用投喂：把一条<b>已经按本人见闻造好</b>的条目写进这个玩家自己的见闻录。
     *
     * <p>只做存取，不做判断：去重靠 {@link PlayerJournal.Entry#key}，
     * 事实校验由调用方（例如 {@code world/SiteEvidence}）在写之前完成。
     * 同一个 key 重复投喂只推进「最后核实」，不会新增第二条。
     *
     * @return 真正新增时 {@code true}（调用方据此决定是否回一次同步）
     */
    public static boolean note(ServerPlayer player, PlayerJournal.Entry entry) {
        if (player == null || entry == null) {
            return false;
        }
        PlayerJournal j = load(player);
        if (!j.add(entry)) {
            return false;
        }
        save(player, j);
        return true;
    }

    /** 观象·亲见：亲手拾得蛊材。 */
    public static boolean notePickup(ServerPlayer player, String itemId, int count,
                                     String dim, long pos, long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "sight-pick-" + now + "-" + (pos & 0xFFFF),
                "pick|" + itemId + "|" + (now / 6000L),           // 同一物品同一时间段只记一次
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "拾得" + shortName(itemId) + " ×" + count,
                "在野外拾得此物。来路未见异常，也未见得干净。",
                dim, pos, now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /** 观象·亲见：自己名下的产地出货。 */
    public static boolean noteSiteYield(ServerPlayer player, String siteId, String itemId,
                                        int count, long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "sight-site-" + now + "-" + siteId.hashCode(),
                "site|" + siteId + "|" + (now / 12_000L),
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "产地出货：" + shortName(itemId) + " ×" + count,
                "此处产地又出了一批。若连日减少，未必是地力，也可能是有人先来过了。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /** 观象·亲见：天象异变。 */
    public static boolean noteOmen(ServerPlayer player, String what, long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "sight-omen-" + now, "omen|" + what + "|" + (now / 6000L),
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "天象：" + what,
                "晴空骤变，来得没有道理。见者只记下时辰，不解释缘由。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /** 观象·亲见：有人上门打听。 */
    public static boolean noteVisitor(ServerPlayer player, int count, long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "sight-visit-" + now, "visit|" + (now / 6000L),
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "有人上门（" + count + " 人）",
                "来者确曾问起你的去向，目的未明。他们没有久留。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /** 观象·亲见：自己渡过的灾劫。 */
    public static boolean noteCalamity(ServerPlayer player, String tierCn, boolean passed,
                                       long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "sight-cal-" + now, "cal|" + tierCn + "|" + (now / 12_000L),
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "灾劫：" + tierCn + (passed ? "（渡过）" : "（受创）"),
                passed ? "扛下来了。身上道痕有增。" : "没扛住。损了些东西。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /**
     * 藏识·亲见：一次炼制尝试。
     *
     * <p><b>注意</b>：本体不暴露「这次炼出了什么」，背包差分也不能单独证明炼制成功
     * （同期拾取、交易、取出都会污染差分）。所以这里<b>只记尝试</b>，
     * 结果一律写成「结果未核实」并附上疑似产物，供玩家自己去验证。
     */
    public static boolean noteAlchemyAttempt(ServerPlayer player, String productHint,
                                             int count, long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "know-craft-" + now, "craft|" + (now / 12_000L),
                PlayerJournal.Kind.KNOWLEDGE, PlayerJournal.Source.WITNESSED,
                "炼制尝试：结果未核实",
                "这一炉确实开过、也确实走完了。"
                        + (productHint == null || productHint.isEmpty()
                        ? "但产物无从确认。"
                        : "背包里多出 " + shortName(productHint) + " ×" + count
                        + "，是否为这一炉所出，尚无旁证。")
                        + "若同期另有拾取或交易，此推断更不可靠。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /**
     * 观象·亲见：天意压下来的世界现象（3.14）。
     *
     * <p>只记<b>玩家自己看得见的那一面</b>：天变了、产地枯了、兽群挪过来了。
     * 不写「天意对你出手了」「威胁值 0.7」这类后台判定 —— 那些只有服务端日志里才有。
     *
     * @param tag  去重用的现象种类（{@code omen} / {@code spoil} / {@code wave}）
     * @param what 玩家看到的那句话
     */
    public static boolean notePressure(ServerPlayer player, String tag, String what, long now) {
        if (player == null || tag == null || tag.isEmpty()) {
            return false;
        }
        String safe = what == null || what.isBlank() ? "有些不对劲" : what;
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "sight-force-" + now + "-" + tag,
                // 幂等键：同一现象同一种类在同一窗口内只记一条
                "force|" + tag + "|" + (now / 6_000L),
                PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                safe,
                "连着几天都是这样，就不是巧合了。见者只记下时辰，不解释缘由。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /** 藏识·亲见：打过某类妖兽。 */
    public static boolean noteBeast(ServerPlayer player, String beastId, String tierName,
                                    long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "know-beast-" + beastId, "beast|" + beastId,
                PlayerJournal.Kind.KNOWLEDGE, PlayerJournal.Source.WITNESSED,
                "兽类：" + tierName,
                "亲历过一次。其性、其力，已大致有数。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    /** 行藏·亲见：一次有目击者的当众行为。 */
    public static boolean noteTrace(ServerPlayer player, String what, long now) {
        PlayerJournal j = load(player);
        boolean added = j.add(new PlayerJournal.Entry(
                "trace-" + now + "-" + Math.abs(what.hashCode() % 1000),
                "trace|" + what + "|" + (now / 12_000L),
                PlayerJournal.Kind.TRACE, PlayerJournal.Source.WITNESSED,
                what,
                "当时附近有人。看到了多少不好说，但确有人看见。",
                player.level().dimension().location().toString(),
                player.blockPosition().asLong(), now, "", "", ""));
        if (added) {
            save(player, j);
        }
        return added;
    }

    // ---------------------------------------------------------------- 玩家操作

    /** 玩家操作类型。 */
    public enum Action {
        FAVORITE, DOUBTFUL, TOGGLE_CASE
    }

    /**
     * 处理一次面板操作。
     *
     * <p>服务端重新校验：条目必须<b>属于这个玩家</b>，否则什么都不做。
     *
     * @return 是否真的改了东西（改了就回一次同步）
     */
    public static boolean handle(ServerPlayer player, Action action, String entryId, long now) {
        PlayerJournal j = load(player);
        switch (action) {
            case FAVORITE -> {
                if (j.toggleFavorite(entryId) == null) {
                    return false;
                }
            }
            case DOUBTFUL -> {
                if (j.toggleDoubtful(entryId) == null) {
                    return false;
                }
            }
            case TOGGLE_CASE -> {
                var e = j.find(entryId);
                if (e == null) {
                    return false;
                }
                // 首期：把这条记进/移出「未名疑局」。只标注，不触发任何世界效果。
                var replacement = new PlayerJournal.Entry(e.id, e.key, e.kind, e.source,
                        e.title, e.detail, e.dim, e.pos, e.observedAt,
                        e.inCase() ? "" : "case-1",
                        e.inCase() ? "" : "未名疑局",
                        e.inCase() ? "" : "尚缺：更多独立来源。");
                replacement.lastVerifiedAt = e.lastVerifiedAt;
                replacement.favorite = e.favorite;
                replacement.doubtful = e.doubtful;
                if (!j.replace(e, replacement)) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        save(player, j);
        return true;
    }

    /** 物品 id → 短名（去掉命名空间）。 */
    private static String shortName(String id) {
        if (id == null) {
            return "某物";
        }
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}
