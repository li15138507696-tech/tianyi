package net.guzhenren.daotu;

import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 旧存档迁移：1.0.0「出身」/ 1.1.0「十六路阶位」→ 1.3.0「肉鸽抽选」。
 *
 * <p>v2 取消了 16 路阶位，但老玩家不该白玩。折算口径：
 * <ul>
 *   <li>读过「出身」的（1.0.0 存档）：按出身强弱给若干<b>起始抽选次数</b>；</li>
 *   <li>有 16 路阶位的（1.1.0 / 1.2.x 存档）：取<b>最高阶</b>折算成抽选次数；</li>
 *   <li>两者都没有：新号，从 0 抽开始。</li>
 * </ul>
 *
 * <p><b>为什么折算成抽选次数而不是直接给属性</b>：抽选次数是本局唯一的成长刻度，
 * 折算成次数后玩家可以自己抽、自己选，行为与正常游玩完全一致，
 * 不会出现「老存档一登录就带着一堆看不懂的属性」。
 *
 * <p><b>幂等</b>：靠 {@code migrated} 标记，重复登录不会二次折算。
 *
 * <p><b>不追溯扣道痕</b>：v1 的阶位奖励发过的道痕就留在玩家身上，
 * 只有 v2 之后抽出来的道痕才记进 {@code run/ledger} 并在转世时扣回。
 */
public final class DaotuMigrate {

    /** 旧出身 id → 折算的起始抽选次数。 */
    private static final Map<String, Integer> ORIGIN_TO_DRAWS = Map.ofEntries(
            Map.entry("xiongjia_kuangtu", 4),
            Map.entry("zuitu", 4),
            Map.entry("tianwai_yigu", 4),
            Map.entry("baijia_liehu", 8),
            Map.entry("shangdui_yigu", 8),
            Map.entry("guyue_pangzhi", 8),
            Map.entry("dushi_fang", 8),
            Map.entry("guyue_dixi", 8),
            Map.entry("baijia_shaozhu", 8),
            Map.entry("lianxuan_xuetu", 8),
            Map.entry("jianmen_zhujian", 8),
            Map.entry("bingxue_waimen", 8),
            Map.entry("sanxiu", 8),
            Map.entry("hundao_yishi", 12),
            Map.entry("daoyou", 12),
            Map.entry("qiankun_sanyi", 12));

    /** v1 阶位 → 折算抽选次数（最高阶是 5）。 */
    private static final int[] RANK_TO_DRAWS = {0, 4, 8, 12, 16, 20};

    private DaotuMigrate() {
    }

    /**
     * 若检测到旧存档，折算成起始抽选次数。
     *
     * @return true 表示本次做了迁移
     */
    public static boolean migrateIfNeeded(ServerPlayer player) {
        if (DaotuData.migrated(player)) {
            return false;
        }

        int draws = 0;
        String note;

        String oldOrigin = DaotuData.origin(player);
        int maxRank = highestLegacyRank(player);

        if (maxRank > 0) {
            // 1.1.0+ 存档：按最高阶折算（比出身更准，优先）
            draws = RANK_TO_DRAWS[Math.min(maxRank, RANK_TO_DRAWS.length - 1)];
            note = "旧存档十六路最高 §e第 " + maxRank + " 阶";
        } else if (!oldOrigin.isEmpty()) {
            // 1.0.0 存档：按出身折算
            draws = ORIGIN_TO_DRAWS.getOrDefault(oldOrigin, 4);
            note = "旧存档出身 §e" + oldOrigin;
        } else {
            // 新号
            DaotuData.markMigrated(player);
            return false;
        }

        DaotuData.clearOrigin(player);
        DaotuData.markMigrated(player);

        // 补发流程自己逐次递增 draws；不能在补发前预加一次。
        DaotuDraw.replayForMigration(player, draws);

        DaotuGrowthApply.refreshTitles(player);
        DaotuAttributes.sync(player);

        player.displayClientMessage(Component.literal(
                "§6[道途] " + note + " §6已折算为 §e" + draws
                        + " §6次抽选，属性已按新规则重算。按 §eM §6查看与重选。"), false);
        return true;
    }

    /** 读旧存档里 16 路的最大阶位（v2 起不再写入，只用于迁移）。 */
    private static int highestLegacyRank(ServerPlayer player) {
        var tag = DaotuData.root(player, false);
        if (tag == null || !tag.contains("rank", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return 0;
        }
        var rankTag = tag.getCompound("rank");
        int max = 0;
        for (String key : rankTag.getAllKeys()) {
            max = Math.max(max, rankTag.getInt(key));
        }
        return Math.min(max, 5);
    }
}
