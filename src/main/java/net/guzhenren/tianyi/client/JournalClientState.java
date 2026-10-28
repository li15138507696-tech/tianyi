package net.guzhenren.tianyi.client;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianyi.net.JournalSyncPayload;
import net.guzhenren.tianyi.net.EncounterSyncPayload;

/**
 * 客户端侧的唯一数据源：<b>只持有服务端发过来的那份</b>。
 *
 * <p>这里没有任何"世界总账"，也没有隐藏逻辑 —— 服务端没发的，客户端就没有。
 * 界面只做展示与交互，不做任何权限判断。
 */
public final class JournalClientState {
    private JournalClientState() {
    }

    private static volatile boolean hasData = false;
    private static volatile boolean titleUnlocked = false;
    private static volatile String title = "见闻录";
    private static volatile long serverNow = 0L;
    private static volatile List<JournalSyncPayload.Row> rows = new ArrayList<>();
    private static volatile EncounterSyncPayload encounter = waitingEncounter();
    private static volatile long encounterReceivedAt;

    private static EncounterSyncPayload waitingEncounter() {
        return new EncounterSyncPayload("", "WAITING", "", Long.MIN_VALUE, 0,
                "", "", 32, 32, 128, "尚无当前机缘。继续在天地间行走，留意新的异象。");
    }

    public static void acceptEncounter(EncounterSyncPayload payload) {
        if (payload == null) return;
        encounter = payload;
        encounterReceivedAt = System.currentTimeMillis();
    }

    public static EncounterSyncPayload encounter() { return encounter; }

    public static int encounterSecondsLeft() {
        return (int) Math.max(0L, (long) encounter.secondsLeft()
                - Math.max(0L, System.currentTimeMillis() - encounterReceivedAt) / 1000L);
    }

    /** 收到一次同步。 */
    public static void accept(JournalSyncPayload p) {
        if (p == null) {
            return;
        }
        titleUnlocked = p.titleUnlocked();
        title = p.title() == null || p.title().isBlank() ? "见闻录" : p.title();
        serverNow = p.now();
        rows = p.rows() == null ? new ArrayList<>() : new ArrayList<>(p.rows());
        hasData = true;
    }

    /** 退出登录/断开时清掉，避免串到下一个角色。 */
    public static void clear() {
        hasData = false;
        titleUnlocked = false;
        title = "见闻录";
        serverNow = 0L;
        rows = new ArrayList<>();
        encounter = waitingEncounter();
        encounterReceivedAt = 0;
    }

    public static boolean hasData() {
        return hasData;
    }

    public static boolean titleUnlocked() {
        return titleUnlocked;
    }

    public static String title() {
        return title;
    }

    public static long now() {
        return serverNow;
    }

    public static List<JournalSyncPayload.Row> rows() {
        return rows;
    }

    /** 按大类过滤（0 观象 / 1 藏识 / 2 行藏；察局用 caseId 过滤）。 */
    public static List<JournalSyncPayload.Row> byKind(int kind) {
        List<JournalSyncPayload.Row> out = new ArrayList<>();
        for (JournalSyncPayload.Row r : rows) {
            if (r.kind() == kind) {
                out.add(r);
            }
        }
        out.sort((a, b) -> Long.compare(b.observedAt(), a.observedAt()));
        return out;
    }

    /** 察局：所有已归入疑局的条目，按疑局分组（保持首次出现顺序）。 */
    public static List<JournalSyncPayload.Row> cases() {
        List<JournalSyncPayload.Row> out = new ArrayList<>();
        for (JournalSyncPayload.Row r : rows) {
            if (!r.caseId().isEmpty()) {
                out.add(r);
            }
        }
        out.sort((a, b) -> Long.compare(a.observedAt(), b.observedAt()));
        return out;
    }
}
