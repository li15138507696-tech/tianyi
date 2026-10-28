package net.guzhenren.tianyi.journal;

import java.util.UUID;
import net.guzhenren.tianyi.resource.FiniteSite;

/**
 * 有限资源点投进《见闻录》的那一条：<b>只写这个玩家自己确实看见的事</b>。
 *
 * <p>本类不 import 任何 Minecraft 类型，可在 {@code _pack/run_tests.py} 里离线跑
 * （{@code _pack/tests/.../SiteNoticeTest.java}）。世界侧 {@code world/SiteEvidence}
 * 负责校验维度/距离/现场事实，然后调用这里造条目。
 *
 * <h2>写什么、不写什么</h2>
 * <ul>
 *   <li>写：走到过、亲手取过、亲眼看见看守的兽、亲见它倒下、桶/根毁了。</li>
 *   <li>不写：点的精确坐标（条目里的 pos 是<b>玩家自己</b>的位置）、后台评分、
 *       别人的采集、全图还有多少点、库存账本的任何中间量。</li>
 * </ul>
 *
 * <h2>去重键</h2>
 * <p>{@code site|站点UUID|事件|revision}。到达与目击守护不带 revision（一个点一辈子一条），
 * 采集/死亡/毁坏带 revision（同一次采集不重复刷屏，下一次采集仍是新的一条）。
 */
public final class SiteNotice {
    /** 玩家离多近才算“看见”。与 {@code SiteBudget.WITNESS_RADIUS} 同值，避免两处各写一个 24。 */
    public static final int WITNESS_RADIUS = 24;

    public enum Event {
        /** 走到跟前，看见这里有一处东西。 */
        ARRIVED,
        /** 亲手取走一份，并看见还剩多少。 */
        HARVESTED,
        /** 亲眼看见看守的兽。 */
        GUARDIAN_SEEN,
        /** 亲眼看见看守的兽倒下。 */
        GUARDIAN_SLAIN,
        /** 亲眼看见桶/根被毁，此处从此绝了。 */
        CARRIER_LOST
    }

    private SiteNotice() {}

    /** 距离平方是否落在“亲眼看见”的范围内。负数（未知距离）一律不算看见。 */
    public static boolean witnessed(double distanceSqr) {
        return distanceSqr >= 0.0 && distanceSqr <= (double) WITNESS_RADIUS * WITNESS_RADIUS;
    }

    /** 去重键。到达/目击守护与 revision 无关；采集/死亡/毁坏与 revision 有关。 */
    public static String key(UUID siteId, Event event, long revision) {
        if (siteId == null || event == null) throw new IllegalArgumentException("missing notice identity");
        String base = "site|" + siteId + "|" + tag(event);
        return switch (event) {
            case ARRIVED, GUARDIAN_SEEN -> base;
            case HARVESTED, GUARDIAN_SLAIN, CARRIER_LOST -> base + "|" + Math.max(0L, revision);
        };
    }

    private static String tag(Event event) {
        return switch (event) {
            case ARRIVED -> "arrive";
            case HARVESTED -> "take";
            case GUARDIAN_SEEN -> "guard";
            case GUARDIAN_SLAIN -> "slain";
            case CARRIER_LOST -> "lost";
        };
    }

    /**
     * 造一条见闻。
     *
     * @param count 采集份数或剩余份数（0 表示不写数字）
     * @param pos   <b>玩家自己</b>的位置（不是资源点的坐标）
     */
    public static PlayerJournal.Entry entry(Event event, UUID siteId, FiniteSite.Kind kind, long revision,
            int count, String dim, long pos, long now) {
        if (event == null || siteId == null) throw new IllegalArgumentException("missing notice identity");
        FiniteSite.Kind k = kind == null ? FiniteSite.Kind.PLANT : kind;
        String title = title(event, k, count);
        String detail = detail(event, k, count);
        return new PlayerJournal.Entry(
                "site-" + tag(event) + "-" + now + "-" + (Math.abs(siteId.hashCode()) % 10000),
                key(siteId, event, revision),
                kindOf(event), PlayerJournal.Source.WITNESSED,
                title, detail, dim, pos, now, "", "", "");
    }

    private static PlayerJournal.Kind kindOf(Event event) {
        return switch (event) {
            case HARVESTED, GUARDIAN_SLAIN -> PlayerJournal.Kind.KNOWLEDGE;
            case ARRIVED, GUARDIAN_SEEN, CARRIER_LOST -> PlayerJournal.Kind.SIGHT;
        };
    }

    private static String what(FiniteSite.Kind kind) {
        return switch (kind) {
            case PLANT -> "一丛蛊材";
            case ORE -> "一处露头的矿";
            case GUARDED -> "一只兽守着的桶";
        };
    }

    private static String title(Event event, FiniteSite.Kind kind, int count) {
        return switch (event) {
            case ARRIVED -> "野地里" + what(kind);
            case HARVESTED -> count > 0 ? "取走一份（此处还剩 " + count + "）" : "取走了最后一份";
            case GUARDIAN_SEEN -> "看守此地的兽";
            case GUARDIAN_SLAIN -> "看守此地的兽已倒下";
            case CARRIER_LOST -> "藏东西的桶/根没了";
        };
    }

    private static String detail(Event event, FiniteSite.Kind kind, int count) {
        return switch (event) {
            case ARRIVED -> "生得自然，不像有人种下的。这里的东西有限，取了就少，不会再有人补。";
            case HARVESTED -> "亲手取的。剩下的看着还有，但不会凭空再多出来；根断了，这里就彻底空了。";
            case GUARDIAN_SEEN -> "它守在这里，不追远，也不退。绕开、设套、引走都行，只是别指望它让路。";
            case GUARDIAN_SLAIN -> "亲见其倒地。此后再无人看守——桶里的东西谁都能拿。";
            case CARRIER_LOST -> "桶或根一毁，此处再不会生出东西。剩下的也没了。";
        };
    }
}
