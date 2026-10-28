package net.guzhenren.tianfu;

import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;

/**
 * 凡体的「流派道痕」写入。
 *
 * <p>统一模板：该流派 {@code daohen_*} / {@code dahen_*} +1000（＝十绝体 2000 的一半）。
 * 飞行道与情魅道在本体 PlayerVariables 里没有 {@code daohen_*} 字段，
 * 按其 1/10 比例改写 {@code liupai_*} +100（本体对十绝体正是 daohen 2000 / liupai 200）。
 * 杀神（tizhi=54）不给开局道痕：本体 46 个 {@code liupai_*} 中没有 shadao，道痕靠击杀累积（见 {@link TianfuSlay}）。
 */
public final class TianfuDaohen {
    public static final double DAOHEN = 1000.0;
    public static final double LIUPAI_EQUIVALENT = 100.0;

    private TianfuDaohen() {
    }

    /**
     * 道痕 → 加成换算：每 {@link #DAOHEN 1000} 道痕对应 {@code perThousand} 的加成
     * （如 {@code perThousand = 0.12} 时，1000 道痕 = +12%，2000 道痕 = +24%）。
     *
     * <p>战斗层的伤害加成 / 减伤全部走这个口径，强度随玩家实际道痕增长。
     */
    public static double scale(double daohen, double perThousand) {
        return daohen / DAOHEN * perThousand;
    }

    /**
     * 该 {@code tizhi} 是否属于「带流派道痕的体质」（用户确认的宙蚀生效范围）。
     *
     * <p>凡体侧与 {@link #write} 的 case 一一对应（写道的才返回 true）；
     * 十绝体侧对齐 {@code TianfuNative.applySijue} 的逐号写入：
     * <ul>
     *   <li>1 太日阳莽体 → 余道；2 古月阴荒体 → 宙道；3 北冥冰魄体 → 魂道/冰雪道</li>
     *   <li>4 森海轮回体 → 木道；5 炎煌雷泽体 → 炎道/雷道；6 万金妙华体 → 金道</li>
     *   <li>7 大力真武体 → 力道；8 逍遥智心体 → 智道；9 厚土元央体 → 土道</li>
     *   <li>10 宇宙大衍体 → 律道</li>
     * </ul>
     * 54 杀神不给开局道痕（靠击杀累积），故不参与。
     */
    public static boolean hasDaochen(int tizhi) {
        return switch (tizhi) {
            // 凡体 17–53：凡属「流派体质」的都在此列（与 write 的 case 同步）。
            // 45 御空踏云体只写 liupai（本体无 daohen_feixingdao 字段），仍是飞行道体质，故计入。
            // 53 血煞蜕变体两项皆无、54 杀神刻意不给开局道痕，均不计。
            case 17, 18, 19, 21, 22, 24, 25, 26, 28, 30, 31, 33, 37, 39, 40, 42, 43, 45, 48 -> true;
            // 十绝体 1–10
            case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 -> true;
            default -> false;
        };
    }

    public static void write(PlayerVariables v, String bodyId) {
        // 与本体十绝体同款「成对写」：daohen +1000 / liupai +100（本体为 2000 / 200）。
        // 只写 daohen 会导致本体的流派/道痕显示与判定拿不到该流派，故必须成对。
        switch (bodyId) {
            case "bingdao" -> { v.daohen_bingdao += DAOHEN; v.liupai_bingdao += LIUPAI_EQUIVALENT; }
            case "bingxuedao" -> { v.daohen_bingxuedao += DAOHEN; v.liupai_bingxuedao += LIUPAI_EQUIVALENT; }
            case "fengdao" -> { v.daohen_fengdao += DAOHEN; v.liupai_fengdao += LIUPAI_EQUIVALENT; }
            case "gudao" -> { v.daohen_gudao += DAOHEN; v.Liupai_gudao += LIUPAI_EQUIVALENT; }  // 本体字段名首字母大写
            case "hundao" -> { v.daohen_hundao += DAOHEN; v.liupai_hundao += LIUPAI_EQUIVALENT; }
            case "jiandao" -> { v.daohen_jiandao += DAOHEN; v.liupai_jiandao += LIUPAI_EQUIVALENT; }
            case "jindao" -> { v.daohen_jindao += DAOHEN; v.liupai_jindao += LIUPAI_EQUIVALENT; }
            case "lidao" -> { v.daohen_lidao += DAOHEN; v.liupai_lidao += LIUPAI_EQUIVALENT; }
            case "mudao" -> { v.daohen_mudao += DAOHEN; v.liupai_mudao += LIUPAI_EQUIVALENT; }
            case "qidao" -> { v.dahen_qidao += DAOHEN; v.liupai_qidao += LIUPAI_EQUIVALENT; }
            case "xudao" -> { v.daohen_xudao += DAOHEN; v.liupai_xudao += LIUPAI_EQUIVALENT; }
            case "yingdao" -> { v.daohen_yingdao += DAOHEN; v.liupai_yingdao += LIUPAI_EQUIVALENT; }
            case "yuedao" -> { v.daohen_yuedao += DAOHEN; v.liupai_yuedao += LIUPAI_EQUIVALENT; }
            case "zhidao" -> { v.dahen_zhidao += DAOHEN; v.liupai_zhidao += LIUPAI_EQUIVALENT; }
            case "zhoudao" -> { v.dahen_zhoudao += DAOHEN; v.liupai_zhoudao += LIUPAI_EQUIVALENT; }
            case "feixingdao" -> v.liupai_feixingdao += LIUPAI_EQUIVALENT;  // 本体无 daohen_feixingdao 字段
            case "mengdao" -> { v.daohen_mengdao += DAOHEN; v.liupai_mengdao += LIUPAI_EQUIVALENT; }
            case "xiuluo" -> { v.daohen_xuedao += DAOHEN; v.liupai_xuedao += LIUPAI_EQUIVALENT; }  // 修罗战体：血道
            case "tudao" -> { v.daohen_tudao += DAOHEN; v.liupai_tudao += LIUPAI_EQUIVALENT; }
            default -> {
                // 词条与不写道痕的体质（血煞蜕变体/杀神）不触发。
            }
        }
    }

    /** write 的反向回滚（撤销凡体时调用，成对减 DAOHEN/LIUPAI_EQUIVALENT）。 */
    public static void revoke(PlayerVariables v, String bodyId) {
        switch (bodyId) {
            case "bingdao" -> { v.daohen_bingdao -= DAOHEN; v.liupai_bingdao -= LIUPAI_EQUIVALENT; }
            case "bingxuedao" -> { v.daohen_bingxuedao -= DAOHEN; v.liupai_bingxuedao -= LIUPAI_EQUIVALENT; }
            case "fengdao" -> { v.daohen_fengdao -= DAOHEN; v.liupai_fengdao -= LIUPAI_EQUIVALENT; }
            case "gudao" -> { v.daohen_gudao -= DAOHEN; v.Liupai_gudao -= LIUPAI_EQUIVALENT; }
            case "hundao" -> { v.daohen_hundao -= DAOHEN; v.liupai_hundao -= LIUPAI_EQUIVALENT; }
            case "jiandao" -> { v.daohen_jiandao -= DAOHEN; v.liupai_jiandao -= LIUPAI_EQUIVALENT; }
            case "jindao" -> { v.daohen_jindao -= DAOHEN; v.liupai_jindao -= LIUPAI_EQUIVALENT; }
            case "lidao" -> { v.daohen_lidao -= DAOHEN; v.liupai_lidao -= LIUPAI_EQUIVALENT; }
            case "mudao" -> { v.daohen_mudao -= DAOHEN; v.liupai_mudao -= LIUPAI_EQUIVALENT; }
            case "qidao" -> { v.dahen_qidao -= DAOHEN; v.liupai_qidao -= LIUPAI_EQUIVALENT; }
            case "xudao" -> { v.daohen_xudao -= DAOHEN; v.liupai_xudao -= LIUPAI_EQUIVALENT; }
            case "yingdao" -> { v.daohen_yingdao -= DAOHEN; v.liupai_yingdao -= LIUPAI_EQUIVALENT; }
            case "yuedao" -> { v.daohen_yuedao -= DAOHEN; v.liupai_yuedao -= LIUPAI_EQUIVALENT; }
            case "zhidao" -> { v.dahen_zhidao -= DAOHEN; v.liupai_zhidao -= LIUPAI_EQUIVALENT; }
            case "zhoudao" -> { v.dahen_zhoudao -= DAOHEN; v.liupai_zhoudao -= LIUPAI_EQUIVALENT; }
            case "feixingdao" -> v.liupai_feixingdao -= LIUPAI_EQUIVALENT;
            case "mengdao" -> { v.daohen_mengdao -= DAOHEN; v.liupai_mengdao -= LIUPAI_EQUIVALENT; }
            case "xiuluo" -> { v.daohen_xuedao -= DAOHEN; v.liupai_xuedao -= LIUPAI_EQUIVALENT; }
            case "tudao" -> { v.daohen_tudao -= DAOHEN; v.liupai_tudao -= LIUPAI_EQUIVALENT; }
            default -> {
            }
        }
    }
}
