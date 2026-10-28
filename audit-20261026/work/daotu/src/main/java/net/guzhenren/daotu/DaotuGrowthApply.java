package net.guzhenren.daotu;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.world.entity.player.Player;

/**
 * 头衔落地：把本局成长刻度写成本体 {@code shenfen} 一行。
 *
 * <p><b>v1.4.0 变更</b>：势力系统删除后，这里只负责「按抽选次数给头衔」。
 * {@code shili} 不再由本模组写入（势力概念已不存在，写「无门无派」反而干扰玩家）。
 *
 * <p><b>绝不写</b>：{@code tizhi}（天命领地）、{@code zuida_zhenyuan} / {@code kongqiao}
 * （本体反推）、{@code qiyun_shangxian}（只由特质升）、{@code daohen_zong}（KE1 每秒重算）。
 *
 * <p>每个改过的字段都要 {@code markSyncDirty()}，否则客户端属性面板不刷新。
 */
public final class DaotuGrowthApply {

    private DaotuGrowthApply() {
    }

    /**
     * 刷新 {@code shenfen} 一行（抽选次数变化后调用）。
     *
     * <p>幂等：值没变就不写，避免每 tick 触发同步。
     */
    public static void refreshTitles(Player player) {
        String shenfen = drawTitle(player);
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (!shenfen.equals(v.shenfen)) {
            v.shenfen = shenfen;
            v.markSyncDirty();
        }
    }

    /**
     * 本局抽选次数 → 头衔。
     *
     * <p>门槛按「抽选次数」而非历练值，因为它就是这局的成长刻度。
     * 无上限：超过最高档一律「问道者」。
     */
    private static String drawTitle(Player player) {
        int n = DaotuRun.draws(player);
        if (n >= 100) {
            return "问道者";
        }
        if (n >= 60) {
            return "问道人";
        }
        if (n >= 30) {
            return "修行者";
        }
        if (n >= 10) {
            return "入门者";
        }
        return "求道者";
    }

    /**
     * 往本体某个道痕字段上 {@code +=}。
     *
     * @return false 表示字段名不认识（调用方应记日志）
     */
    public static boolean addDaohen(PlayerVariables v, String key, double amount) {
        switch (key) {
            // ---- 36 个 daohen_* ----
            case "daohen_andao" -> v.daohen_andao += amount;
            case "daohen_bianhuadao" -> v.daohen_bianhuadao += amount;
            case "daohen_bingdao" -> v.daohen_bingdao += amount;
            case "daohen_bingxuedao" -> v.daohen_bingxuedao += amount;
            case "daohen_dandao" -> v.daohen_dandao += amount;
            case "daohen_daodao" -> v.daohen_daodao += amount;
            case "daohen_dudao" -> v.daohen_dudao += amount;
            case "daohen_fengdao" -> v.daohen_fengdao += amount;
            case "daohen_guangdao" -> v.daohen_guangdao += amount;
            case "daohen_gudao" -> v.daohen_gudao += amount;
            case "daohen_huadao" -> v.daohen_huadao += amount;
            case "daohen_huandao" -> v.daohen_huandao += amount;
            case "daohen_hundao" -> v.daohen_hundao += amount;
            case "daohen_jiandao" -> v.daohen_jiandao += amount;
            case "daohen_jindao" -> v.daohen_jindao += amount;
            case "daohen_jindao2" -> v.daohen_jindao2 += amount;
            case "daohen_leidao" -> v.daohen_leidao += amount;
            case "daohen_lidao" -> v.daohen_lidao += amount;
            case "daohen_lvdao" -> v.daohen_lvdao += amount;
            case "daohen_mengdao" -> v.daohen_mengdao += amount;
            case "daohen_mudao" -> v.daohen_mudao += amount;
            case "daohen_shidao" -> v.daohen_shidao += amount;
            case "daohen_shuidao" -> v.daohen_shuidao += amount;
            case "daohen_toudao" -> v.daohen_toudao += amount;
            case "daohen_tudao" -> v.daohen_tudao += amount;
            case "daohen_xindao" -> v.daohen_xindao += amount;
            case "daohen_xudao" -> v.daohen_xudao += amount;
            case "daohen_xuedao" -> v.daohen_xuedao += amount;
            case "daohen_yandao" -> v.daohen_yandao += amount;
            case "daohen_yindao" -> v.daohen_yindao += amount;
            case "daohen_yingdao" -> v.daohen_yingdao += amount;
            case "daohen_yudao" -> v.daohen_yudao += amount;
            case "daohen_yuedao" -> v.daohen_yuedao += amount;
            case "daohen_yundao" -> v.daohen_yundao += amount;
            case "daohen_yundao2" -> v.daohen_yundao2 += amount;
            // ---- 9 个 dahen_*（本体笔误，少一个 o） ----
            case "dahen_liandao" -> v.dahen_liandao += amount;
            case "dahen_nudao" -> v.dahen_nudao += amount;
            case "dahen_qidao" -> v.dahen_qidao += amount;
            case "dahen_rendao" -> v.dahen_rendao += amount;
            case "dahen_tiandao" -> v.dahen_tiandao += amount;
            case "dahen_xingdao" -> v.dahen_xingdao += amount;
            case "dahen_zhendao" -> v.dahen_zhendao += amount;
            case "dahen_zhidao" -> v.dahen_zhidao += amount;
            case "dahen_zhoudao" -> v.dahen_zhoudao += amount;
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 往本体某个流派熟练度字段上 {@code +=}。
     *
     * <p>与 {@link #addDaohen} 分开而不是复用：道痕要记 {@code run/ledger}
     * （转世扣回），熟练度<b>不记</b>——本体自己会在转世时把 {@code liupai_*}
     * 归零（境界重置），再扣一次会成负数。
     *
     * <p>只收 {@link DaotuMainDao#ALL} 里那 16 条流派。本体实际有 46 个
     * {@code liupai_*}，但其余没有对应道痕、也不在可选主修里，加了没人用。
     *
     * @return false 表示字段名不认识
     */
    public static boolean addLiupai(Player player, String field, double amount) {
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double before = liupaiOfRaw(v, field);
        if (Double.isNaN(before)) {
            return false;
        }
        double after = before + amount;
        if (!writeLiupai(v, field, after)) {
            return false;
        }
        v.markSyncDirty();
        return true;
    }

    /** 读流派熟练度；字段不认识返回 {@code NaN}（与 {@link #daohenOfRaw} 同约定）。 */
    public static double liupaiOfRaw(PlayerVariables v, String key) {
        return switch (key) {
            case "liupai_jiandao" -> v.liupai_jiandao;
            case "liupai_jindao" -> v.liupai_jindao;
            case "liupai_shuidao" -> v.liupai_shuidao;
            case "liupai_xuedao" -> v.liupai_xuedao;
            case "liupai_lidao" -> v.liupai_lidao;
            case "liupai_mudao" -> v.liupai_mudao;
            case "liupai_yudao" -> v.liupai_yudao;
            case "liupai_fengdao" -> v.liupai_fengdao;
            case "liupai_bingdao" -> v.liupai_bingdao;
            case "liupai_jindao2" -> v.liupai_jindao2;
            case "liupai_hundao" -> v.liupai_hundao;
            case "liupai_leidao" -> v.liupai_leidao;
            case "liupai_yandao" -> v.liupai_yandao;
            case "liupai_lvdao" -> v.liupai_lvdao;
            case "liupai_tudao" -> v.liupai_tudao;
            default -> Double.NaN;
        };
    }

    /** 写流派熟练度；字段不认识返回 false。 */
    private static boolean writeLiupai(PlayerVariables v, String field, double value) {
        switch (field) {
            case "liupai_jiandao" -> v.liupai_jiandao = Math.max(0.0, value);
            case "liupai_jindao" -> v.liupai_jindao = Math.max(0.0, value);
            case "liupai_shuidao" -> v.liupai_shuidao = Math.max(0.0, value);
            case "liupai_xuedao" -> v.liupai_xuedao = Math.max(0.0, value);
            case "liupai_lidao" -> v.liupai_lidao = Math.max(0.0, value);
            case "liupai_mudao" -> v.liupai_mudao = Math.max(0.0, value);
            case "liupai_yudao" -> v.liupai_yudao = Math.max(0.0, value);
            case "liupai_fengdao" -> v.liupai_fengdao = Math.max(0.0, value);
            case "liupai_bingdao" -> v.liupai_bingdao = Math.max(0.0, value);
            case "liupai_jindao2" -> v.liupai_jindao2 = Math.max(0.0, value);
            case "liupai_hundao" -> v.liupai_hundao = Math.max(0.0, value);
            case "liupai_leidao" -> v.liupai_leidao = Math.max(0.0, value);
            case "liupai_yandao" -> v.liupai_yandao = Math.max(0.0, value);
            case "liupai_lvdao" -> v.liupai_lvdao = Math.max(0.0, value);
            case "liupai_tudao" -> v.liupai_tudao = Math.max(0.0, value);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 从本体道痕字段上扣减，<b>下限 0</b>。
     *
     * <p>转世时按 {@code run/ledger} 把这局发出去的道痕扣回来。
     * 加下限是因为本体可能已把该字段归零（例如重置境界、或玩家自己花掉了道痕），
     * 扣成负数会让道痕显示成负值。
     *
     * @return false 表示字段名不认识
     */
    public static boolean subDaohen(PlayerVariables v, String key, double amount) {
        if (amount == 0.0) {
            return true;
        }
        double before = DaotuApply.daohenOfRaw(v, key);
        if (Double.isNaN(before)) {
            return false; // 字段名不认识
        }
        double after = Math.max(0.0, before - Math.abs(amount));
        return addDaohen(v, key, after - before);
    }
}
