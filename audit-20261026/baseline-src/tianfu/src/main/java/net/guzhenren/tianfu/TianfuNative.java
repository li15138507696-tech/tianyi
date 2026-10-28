package net.guzhenren.tianfu;

import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.procedures.TianziliuchengProcedure;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

/**
 * 本体天赋入池的原生写入：复刻 {@code UikongqiaoguanbiProcedure} 中甲乙丙丁机缘与十绝体的
 * 授予分支（同一随机区间、同一概率、同一字段），让本体 KE1（真元反推档位）与 KE2（十绝体
 * 运行时效果）原生生效，附属不自建一套效果体系。
 */
public final class TianfuNative {
    private TianfuNative() {
    }

    /**
     * 机缘（甲乙丙丁）：按档位写真元上限 + 气运 + 命格 roll，与本体开窍完全同款。
     * kongqiao 直接写 KE1 稳定值（甲 2 / 乙 1 / 丙 5 / 丁 8），避免反推口径不一致。
     */
    public static void applyJiyuan(ServerPlayer player, PlayerVariables v, String id) {
        switch (id) {
            case "jiyuan_jia" -> {
                v.kongqiao = 2.0;
                v.zuida_zhenyuan = roll(800, 990);
                v.qiyun = roll(1, 10);
                if (roll(1, 3) == 1) {
                    v.mingge = -2.0;
                    v.qiyun_shangxian = roll(35, 45);
                } else {
                    TianziliuchengProcedure.execute(player);
                    // 该分支不设上限：若原本上限低于本次气运 roll，本体下一秒会把气运削平
                    // （`if (cap < qiyun) qiyun = cap`），roll 就白摇了 → 抬到至少覆盖本次 roll。
                    v.qiyun_shangxian = Math.max(v.qiyun_shangxian, v.qiyun);
                }
            }
            case "jiyuan_yi" -> {
                v.kongqiao = 1.0;
                v.zuida_zhenyuan = roll(600, 790);
                v.qiyun_shangxian = roll(10, 20);
                rollMingge(player, 0.5);
            }
            case "jiyuan_bing" -> {
                v.kongqiao = 5.0;
                v.zuida_zhenyuan = roll(400, 590);
                v.qiyun_shangxian = roll(1, 3);
                rollMingge(player, 0.7);
            }
            case "jiyuan_ding" -> {
                v.kongqiao = 8.0;
                v.zuida_zhenyuan = roll(200, 390);
                v.qiyun_shangxian = roll(1, 10);
                rollMingge(player, 0.9);
            }
            default -> {
                return;
            }
        }
        v.markSyncDirty();
        player.displayClientMessage(Component.literal(
                "§b[天赋] 机缘改命完成：真元上限 " + (int) v.zuida_zhenyuan
                        + "，档位由本体自动同步"), false);
    }

    /** 十绝体：写 kongqiao=3 + 真元 1000 + 指定号 tizhi + 本体同款 2000/200 道痕与附带字段。 */
    public static void applySijue(ServerPlayer player, PlayerVariables v, int tizhi) {
        v.kongqiao = 3.0;
        v.zuida_zhenyuan = 1000.0;
        v.tizhi = tizhi;
        switch (tizhi) {
            case 1 -> { // 太日阳莽体
                v.daohen_yudao = 2000.0;
                v.liupai_yudao = 200.0;
            }
            case 2 -> { // 古月阴荒体
                v.dahen_zhoudao = 2000.0;
                v.liupai_zhoudao = 200.0;
                v.zhoudao_shijian_baifenbi = 3.0;
                v.zhoudao_cd_baifenbi = 0.75;
            }
            case 3 -> { // 北冥冰魄体
                v.zuida_hunpo = 500.0;
                v.hunpochengzaijixian = 50000.0;
                if (roll(0, 1) == 1) {
                    v.daohen_hundao = 2000.0;
                    v.liupai_hundao = 200.0;
                } else {
                    v.daohen_bingxuedao = 2000.0;
                    v.liupai_bingxuedao = 200.0;
                }
            }
            case 4 -> { // 森海轮回体
                addScore(player, "smz", 20);
                v.daohen_mudao = 2000.0;
                v.liupai_mudao = 200.0;
            }
            case 5 -> { // 炎煌雷泽体
                if (roll(0, 1) == 1) {
                    v.daohen_yandao = 2000.0;
                    v.liupai_yandao = 200.0;
                } else {
                    v.daohen_leidao = 2000.0;
                    v.liupai_leidao = 200.0;
                }
                setScore(player, "雷道消耗", 1);
            }
            case 6 -> { // 万金妙华体
                v.daohen_jindao = 2000.0;
                v.liupai_jindao = 200.0;
            }
            case 7 -> { // 大力真武体
                v.daohen_lidao = 2000.0;
                v.liupai_lidao = 200.0;
                v.zuida_jingli = 200.0;
                addScore(player, "gjl", 20);
            }
            case 8 -> { // 逍遥智心体
                v.dahen_zhidao = 2000.0;
                v.liupai_zhidao = 200.0;
                v.niantou_zhida = 2000.0;
                v.niantou_rongliang = 20000.0;
            }
            case 9 -> { // 厚土元央体
                v.daohen_tudao = 2000.0;
                v.liupai_tudao = 200.0;
            }
            case 10 -> { // 宇宙大衍体
                v.daohen_lvdao = 2000.0;
                v.liupai_lvdao = 200.0;
            }
            default -> {
            }
        }
        v.markSyncDirty();
        player.displayClientMessage(Component.literal(tizhi == 54
                ? "§c[天赋] 杀神加身：真元上限 1000，杀戮成长与杀怪噬道生效"
                : "§c[天赋] 十绝体加身：真元上限 1000，本体体质效果原生生效"), false);
    }

    /** 本体同款命格 roll：{@code p} 概率命格 −1，否则走天资流畅（命格 1–13 及其附带奖励）。 */
    private static void rollMingge(ServerPlayer player, double p) {
        PlayerVariables v = player.getData(net.guzhenren.network.GuzhenrenModVariables.PLAYER_VARIABLES);
        if (Math.random() < p) {
            v.mingge = -1.0;
            v.markSyncDirty();
        } else {
            TianziliuchengProcedure.execute(player);
        }
    }

    /** 本体同款随机：{@code Mth.nextInt(RandomSource.create(), min, max)}（含端点）。 */
    private static double roll(int min, int max) {
        return Mth.nextInt(RandomSource.create(), min, max);
    }

    private static void addScore(Entity entity, String name, int delta) {
        setScore(entity, name, getScore(entity, name) + delta);
    }

    private static int getScore(Entity entity, String name) {
        Scoreboard sc = entity.level().getScoreboard();
        Objective so = sc.getObjective(name);
        return so != null
                ? sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entity.getScoreboardName()), so).get()
                : 0;
    }

    private static void setScore(Entity entity, String name, int value) {
        Scoreboard sc = entity.level().getScoreboard();
        Objective so = sc.getObjective(name);
        if (so == null) {
            so = sc.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name), RenderType.INTEGER, true, null);
        }
        sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entity.getScoreboardName()), so).set(value);
    }
}
