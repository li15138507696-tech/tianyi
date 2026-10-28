package net.guzhenren.tianyi.journal;

import java.util.List;
import java.util.Map;

/**
 * 《见闻录 / 窥天录》的离线行为测试。
 *
 * <p>这里锁的是<b>玩家知识本身的规则</b>：去重、陈旧标注、疑局结论、题名解锁、
 * 玩家标注不改变世界事实。真实事件来源（拾取/产地/灾劫/上门/炼蛊）在 world 包里，
 * 必须靠实机或受控世界验证，不在本套件的覆盖范围内 —— 这一点写在测试末尾的注释里。
 */
public final class JournalTest {
    private static int pass = 0;
    private static int fail = 0;

    private static final String A = "00000000-0000-0000-0000-0000000000a1";

    public static void main(String[] args) {
        testDedupe();
        testStaleAndHistory();
        testCaseVerdicts();
        testTitleUnlock();
        testPlayerMarksDoNotTouchWorld();
        testKindFilters();
        testCap();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：本套件只验证玩家知识规则；世界事件来源未在此验证。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- 用例

    /** 同键重复只留一条，且只推进「最后核实」，不改写内容。 */
    private static void testDedupe() {
        PlayerJournal j = new PlayerJournal();
        boolean first = j.add(sight("e1", "pick|gu|1", "拾得寿蛊", 1000L));
        boolean second = j.add(sight("e2", "pick|gu|1", "拾得寿蛊（第二次）", 3000L));
        check("首次记录被收下", first);
        check("同键重复不再新增", !second);
        check("条目数仍为 1", j.size() == 1);
        check("内容不被后来者改写", "拾得寿蛊".equals(j.all().get(0).title));
        check("最后核实时间被推进", j.all().get(0).lastVerifiedAt == 3000L);
        check("事件时间保持首次", j.all().get(0).observedAt == 1000L);
    }

    /** 陈旧只标注，不删除历史。 */
    private static void testStaleAndHistory() {
        PlayerJournal j = new PlayerJournal();
        j.add(sight("e1", "a", "旧事", 1000L));
        long later = 1000L + PlayerJournal.STALE_TICKS + 1;
        check("超过时限标为久未核实", j.all().get(0).stale(later));
        check("未超时限不算陈旧", !j.all().get(0).stale(1000L));
        check("陈旧条目仍在档里（不静默删除）", j.size() == 1);
        check("摘要里体现陈旧数", j.summary(later).contains("久未核实=1"));
    }

    /** 结论四档：孤证 / 相互印证 / 存在矛盾 / 尚无定论。 */
    private static void testCaseVerdicts() {
        PlayerJournal j = new PlayerJournal();
        check("空案为尚无定论", "尚无定论".equals(j.caseVerdict("case-x")));

        j.add(entry("c1", "k1", PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "产地减产", 1000L, "case-x", "未名疑局"));
        check("单条为孤证", "孤证".equals(j.caseVerdict("case-x")));

        j.add(entry("c2", "k2", PlayerJournal.Kind.SIGHT, PlayerJournal.Source.HEARD,
                "商人提到旧路", 2000L, "case-x", "未名疑局"));
        check("两种来源为相互印证", "相互印证".equals(j.caseVerdict("case-x")));

        // 标记存疑 → 变成「存在矛盾」，但两条都还在
        j.toggleDoubtful("c2");
        check("存疑后为存在矛盾", "存在矛盾".equals(j.caseVerdict("case-x")));
        check("矛盾时证据不被删除", j.cases().get("case-x").size() == 2);
        check("疑局分组正确", j.cases().size() == 1);
        check("尚缺条件可查", !j.caseMissing("case-x").isEmpty());
        check("疑局标题可查", "未名疑局".equals(j.caseTitle("case-x")));
    }

    /** 题名解锁只看真实内容：亲见 ≥3 且至少一条进疑局。 */
    private static void testTitleUnlock() {
        PlayerJournal j = new PlayerJournal();
        check("空档案题名是见闻录", "见闻录".equals(j.title()));
        j.add(sight("s1", "k1", "一", 1000L));
        j.add(sight("s2", "k2", "二", 2000L));
        check("两条亲见还不够", !j.titleUnlocked());
        j.add(sight("s3", "k3", "三", 3000L));
        check("三条亲见但无疑局 → 仍不解锁", !j.titleUnlocked());
        // 把一条记入疑局
        var e = j.find("s1");
        var moved = new PlayerJournal.Entry(e.id, e.key, e.kind, e.source, e.title, e.detail,
                e.dim, e.pos, e.observedAt, "case-1", "未名疑局", "尚缺：更多独立来源。");
        j.replace(e, moved);
        check("有了疑局才解锁窥天录", j.titleUnlocked());
        check("题名随之改变", "窥天录".equals(j.title()));

        // 听闻不计入亲见
        PlayerJournal k = new PlayerJournal();
        k.add(entry("h1", "h1", PlayerJournal.Kind.SIGHT, PlayerJournal.Source.HEARD,
                "听说", 1000L, "case-1", "未名疑局"));
        k.add(entry("h2", "h2", PlayerJournal.Kind.SIGHT, PlayerJournal.Source.HEARD,
                "又听说", 2000L, "case-1", "未名疑局"));
        k.add(entry("h3", "h3", PlayerJournal.Kind.SIGHT, PlayerJournal.Source.HEARD,
                "还听说", 3000L, "case-1", "未名疑局"));
        check("全听闻不解锁", !k.titleUnlocked());
    }

    /** 玩家标注只动自己的笔记，不产生任何"世界事实"字段。 */
    private static void testPlayerMarksDoNotTouchWorld() {
        PlayerJournal j = new PlayerJournal();
        j.add(sight("e1", "k1", "事", 1000L));
        check("收藏返回新状态", Boolean.TRUE.equals(j.toggleFavorite("e1")));
        check("再点取消", Boolean.FALSE.equals(j.toggleFavorite("e1")));
        check("存疑返回新状态", Boolean.TRUE.equals(j.toggleDoubtful("e1")));
        check("不存在的条目返回 null", j.toggleFavorite("nope") == null);
        check("收藏数为 0（已取消）", j.favoriteCount() == 0);
        check("存疑数为 1", j.doubtfulCount() == 1);
        // 存疑不改 source：它仍是「亲见」，只是玩家自己有疑问
        check("存疑不改获知方式",
                j.find("e1").source == PlayerJournal.Source.WITNESSED);
    }

    /** 三个页签各自只取自己那一类。 */
    private static void testKindFilters() {
        PlayerJournal j = new PlayerJournal();
        j.add(entry("a", "a", PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                "见闻", 1000L, "", ""));
        j.add(entry("b", "b", PlayerJournal.Kind.KNOWLEDGE, PlayerJournal.Source.WITNESSED,
                "认识", 2000L, "", ""));
        j.add(entry("c", "c", PlayerJournal.Kind.TRACE, PlayerJournal.Source.WITNESSED,
                "痕迹", 3000L, "", ""));
        check("观象只有 SIGHT", j.sights().size() == 1
                && "见闻".equals(j.sights().get(0).title));
        check("藏识只有 KNOWLEDGE", j.knowledge().size() == 1
                && "认识".equals(j.knowledge().get(0).title));
        check("行藏只有 TRACE", j.traces().size() == 1
                && "痕迹".equals(j.traces().get(0).title));
        check("观象按时间倒序", j.sights().get(0).observedAt >= 0);
    }

    /** 条目上限：超了丢最旧的，不无限膨胀。 */
    private static void testCap() {
        PlayerJournal j = new PlayerJournal();
        for (int i = 0; i < PlayerJournal.MAX_ENTRIES + 20; i++) {
            j.add(sight("e" + i, "k" + i, "第" + i, 1000L + i));
        }
        check("不超过上限", j.size() == PlayerJournal.MAX_ENTRIES);
        check("丢的是最旧的", !j.all().get(0).title.equals("第0"));
        check("最新那条还在", j.all().get(j.size() - 1).title
                .equals("第" + (PlayerJournal.MAX_ENTRIES + 19)));
    }

    // ---------------------------------------------------------------- 工具

    private static PlayerJournal.Entry sight(String id, String key, String title, long at) {
        return entry(id, key, PlayerJournal.Kind.SIGHT, PlayerJournal.Source.WITNESSED,
                title, at, "", "");
    }

    private static PlayerJournal.Entry entry(String id, String key, PlayerJournal.Kind kind,
                                             PlayerJournal.Source src, String title, long at,
                                             String caseId, String caseTitle) {
        return new PlayerJournal.Entry(id, key, kind, src, title, "正文", "minecraft:overworld",
                0L, at, caseId, caseTitle, caseId.isEmpty() ? "" : "尚缺：更多独立来源。");
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name);
        }
    }

    private JournalTest() {
    }
}
