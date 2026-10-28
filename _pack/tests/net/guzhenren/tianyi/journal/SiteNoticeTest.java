package net.guzhenren.tianyi.journal;

import java.util.UUID;
import net.guzhenren.tianyi.resource.FiniteSite;

/**
 * {@link SiteNotice} 的离线确定性测试：谁能算“亲眼看见”、key 怎么去重、条目里不写什么。
 *
 * <p>世界侧的事实校验（同维度、距离、现场实物）在 {@code world/SiteEvidence}，
 * 它 import 了 Minecraft 类型，只能静态审计 + 实机验证，不在这里。
 */
public final class SiteNoticeTest {
    private static int pass = 0;
    private static int fail = 0;

    private static final UUID SITE_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SITE_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String DIM = "minecraft:overworld";

    public static void main(String[] args) {
        testWitnessRadius();
        testKeyDedupe();
        testEntryContent();
        testJournalDedupe();
        testNoBackstageLeak();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：只验证纯条目规则；谁真的看见了、现场是否真有实物由世界侧与实机验证。");
        if (fail > 0) {
            System.exit(1);
        }
    }

    /** 24 格以内算看见；超出、负数（未知距离）都不算。 */
    private static void testWitnessRadius() {
        check("贴脸算看见", SiteNotice.witnessed(0.0));
        check("24 格边界算看见", SiteNotice.witnessed(24.0 * 24.0));
        check("24 格多一点不算", !SiteNotice.witnessed(24.0 * 24.0 + 1.0));
        check("很远不算", !SiteNotice.witnessed(1_000_000.0));
        check("未知距离（负数）不算", !SiteNotice.witnessed(-1.0));
        check("半径与 SiteBudget 一致", SiteNotice.WITNESS_RADIUS == 24);
    }

    /** 到达/目击一生一条；采集/死亡/毁坏按 revision 各记一条。 */
    private static void testKeyDedupe() {
        String arrive1 = SiteNotice.key(SITE_A, SiteNotice.Event.ARRIVED, 3);
        String arrive2 = SiteNotice.key(SITE_A, SiteNotice.Event.ARRIVED, 99);
        check("到达不看 revision（同一点一辈子一条）", arrive1.equals(arrive2));
        String guard1 = SiteNotice.key(SITE_A, SiteNotice.Event.GUARDIAN_SEEN, 1);
        String guard2 = SiteNotice.key(SITE_A, SiteNotice.Event.GUARDIAN_SEEN, 7);
        check("目击守护也不看 revision", guard1.equals(guard2));
        check("到达与目击不是同一条", !arrive1.equals(guard1));
        check("同一点不同事件不撞 key", !SiteNotice.key(SITE_A, SiteNotice.Event.HARVESTED, 1)
                .equals(SiteNotice.key(SITE_A, SiteNotice.Event.CARRIER_LOST, 1)));
        check("采集按 revision 各记一条", !SiteNotice.key(SITE_A, SiteNotice.Event.HARVESTED, 1)
                .equals(SiteNotice.key(SITE_A, SiteNotice.Event.HARVESTED, 2)));
        check("不同点不撞 key", !SiteNotice.key(SITE_A, SiteNotice.Event.HARVESTED, 1)
                .equals(SiteNotice.key(SITE_B, SiteNotice.Event.HARVESTED, 1)));
        boolean rejected = false;
        try {
            SiteNotice.key(null, SiteNotice.Event.ARRIVED, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("缺站点 id 被拒绝", rejected);
    }

    /** 条目字段：亲见来源、时间、维度、玩家自己的位置。 */
    private static void testEntryContent() {
        PlayerJournal.Entry e = SiteNotice.entry(SiteNotice.Event.HARVESTED, SITE_A,
                FiniteSite.Kind.PLANT, 4, 2, DIM, 12345L, 6789L);
        check("来源是亲见", e.source == PlayerJournal.Source.WITNESSED);
        check("采集归入藏识", e.kind == PlayerJournal.Kind.KNOWLEDGE);
        check("时间就是观察时刻", e.observedAt == 6789L);
        check("维度如实记录", DIM.equals(e.dim));
        check("位置是玩家自己的位置", e.pos == 12345L);
        check("标题非空", !e.title.isBlank());
        check("正文非空", !e.detail.isBlank());
        check("标题带剩余量", e.title.contains("2"));
        check("key 与 revision 绑定", e.key.equals(SiteNotice.key(SITE_A, SiteNotice.Event.HARVESTED, 4)));

        PlayerJournal.Entry arrived = SiteNotice.entry(SiteNotice.Event.ARRIVED, SITE_A,
                FiniteSite.Kind.GUARDED, 0, 0, DIM, 1L, 2L);
        check("到达归入观象", arrived.kind == PlayerJournal.Kind.SIGHT);
        check("三种模板的到达各有说法", !arrived.title.equals(SiteNotice.entry(SiteNotice.Event.ARRIVED,
                SITE_B, FiniteSite.Kind.ORE, 0, 0, DIM, 1L, 2L).title));
    }

    /** 真的进 PlayerJournal：同 key 只留一条，只推进核实时间。 */
    private static void testJournalDedupe() {
        PlayerJournal j = new PlayerJournal();
        PlayerJournal.Entry first = SiteNotice.entry(SiteNotice.Event.ARRIVED, SITE_A,
                FiniteSite.Kind.PLANT, 1, 0, DIM, 10L, 100L);
        PlayerJournal.Entry again = SiteNotice.entry(SiteNotice.Event.ARRIVED, SITE_A,
                FiniteSite.Kind.PLANT, 1, 0, DIM, 10L, 900L);
        check("第一次收下", j.add(first));
        check("同点同事件第二次不再新增", !j.add(again));
        check("条目数仍为 1", j.size() == 1);
        check("核实时间被推进", j.all().get(0).lastVerifiedAt == 900L);
        check("观察时刻保持首次", j.all().get(0).observedAt == 100L);
    }

    /** 不写后台评分、不写别人的事、不把站点坐标塞进正文。 */
    private static void testNoBackstageLeak() {
        for (SiteNotice.Event event : SiteNotice.Event.values()) {
            PlayerJournal.Entry e = SiteNotice.entry(event, SITE_A, FiniteSite.Kind.PLANT, 2, 3, DIM, 7L, 8L);
            String text = e.title + "|" + e.detail;
            check(event + " 不写评分", !text.contains("评分") && !text.contains("score"));
            check(event + " 不写账本术语", !text.contains("revision") && !text.contains("库存账本"));
            check(event + " 不写站点 UUID", !text.contains(SITE_A.toString()));
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }
}
