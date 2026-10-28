package net.guzhenren.daotu;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.world.entity.player.Player;

/**
 * 道痕字段读写。
 *
 * <p><b>v1.4.0 起大幅收缩</b>：流派树 / 觉醒 / 势力档位全部删除，
 * 原来那些「已点节点求和」的 getter（{@code attackPct}、{@code speedPct}、
 * {@code healthPct}…）一并移除——属性成长现在<b>唯一</b>来源是
 * {@link DaotuRunAttributes} 从 {@code run/attrs} 重建的 modifier。
 *
 * <p>这里只留下三件仍然必要的事：
 * <ol>
 *   <li>按字段名读本体道痕（{@link #daohenOf}）；</li>
 *   <li>按字段名读、且能区分「字段不存在」与「值为 0」（{@link #daohenOfRaw}）；</li>
 *   <li>兼容层 {@link #growthAttr}，恒返回 0，供 {@link DaotuAttributes#sync} 调用。</li>
 * </ol>
 */
public final class DaotuApply {

    private DaotuApply() {
    }

    /** 读本体某个道痕字段的当前值（字段名由 {@code DaotuGrowthApply} 给出）。 */
    public static double daohenOf(Player player, String field) {
        if (field == null) {
            return 0.0;
        }
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double raw = daohenOfRaw(v, field);
        return Double.isNaN(raw) ? 0.0 : raw;
    }

    /**
     * 直接从 {@link PlayerVariables} 读某个道痕字段。
     *
     * <p>与 {@link #daohenOf} 的区别：<b>未知字段返回 {@code NaN}</b> 而不是 0，
     * 这样调用方（如转世扣道痕）能区分「这个字段本来就是 0」与「字段名写错了」。
     */
    public static double daohenOfRaw(PlayerVariables v, String key) {
        return switch (key) {
            // ---- 36 个 daohen_* ----
            case "daohen_andao" -> v.daohen_andao;
            case "daohen_bianhuadao" -> v.daohen_bianhuadao;
            case "daohen_bingdao" -> v.daohen_bingdao;
            case "daohen_bingxuedao" -> v.daohen_bingxuedao;
            case "daohen_dandao" -> v.daohen_dandao;
            case "daohen_daodao" -> v.daohen_daodao;
            case "daohen_dudao" -> v.daohen_dudao;
            case "daohen_fengdao" -> v.daohen_fengdao;
            case "daohen_guangdao" -> v.daohen_guangdao;
            case "daohen_gudao" -> v.daohen_gudao;
            case "daohen_huadao" -> v.daohen_huadao;
            case "daohen_huandao" -> v.daohen_huandao;
            case "daohen_hundao" -> v.daohen_hundao;
            case "daohen_jiandao" -> v.daohen_jiandao;
            case "daohen_jindao" -> v.daohen_jindao;
            case "daohen_jindao2" -> v.daohen_jindao2;
            case "daohen_leidao" -> v.daohen_leidao;
            case "daohen_lidao" -> v.daohen_lidao;
            case "daohen_lvdao" -> v.daohen_lvdao;
            case "daohen_mengdao" -> v.daohen_mengdao;
            case "daohen_mudao" -> v.daohen_mudao;
            case "daohen_shidao" -> v.daohen_shidao;
            case "daohen_shuidao" -> v.daohen_shuidao;
            case "daohen_toudao" -> v.daohen_toudao;
            case "daohen_tudao" -> v.daohen_tudao;
            case "daohen_xindao" -> v.daohen_xindao;
            case "daohen_xudao" -> v.daohen_xudao;
            case "daohen_xuedao" -> v.daohen_xuedao;
            case "daohen_yandao" -> v.daohen_yandao;
            case "daohen_yindao" -> v.daohen_yindao;
            case "daohen_yingdao" -> v.daohen_yingdao;
            case "daohen_yudao" -> v.daohen_yudao;
            case "daohen_yuedao" -> v.daohen_yuedao;
            case "daohen_yundao" -> v.daohen_yundao;
            case "daohen_yundao2" -> v.daohen_yundao2;
            // ---- 9 个 dahen_*（本体笔误，少一个 o） ----
            case "dahen_liandao" -> v.dahen_liandao;
            case "dahen_nudao" -> v.dahen_nudao;
            case "dahen_qidao" -> v.dahen_qidao;
            case "dahen_rendao" -> v.dahen_rendao;
            case "dahen_tiandao" -> v.dahen_tiandao;
            case "dahen_xingdao" -> v.dahen_xingdao;
            case "dahen_zhendao" -> v.dahen_zhendao;
            case "dahen_zhidao" -> v.dahen_zhidao;
            case "dahen_zhoudao" -> v.dahen_zhoudao;
            default -> Double.NaN;
        };
    }

    /**
     * 兼容层：恒返回 0。
     *
     * <p>v2 起属性成长已改由 {@link DaotuRunAttributes} 从 {@code run/attrs}
     * 全量重建，不再有「16 路 × 6 阶」的查表累加。
     * 保留签名是为了不动 {@link DaotuAttributes#sync} 的调用点。
     *
     * @param key {@link DaotuAttributes} 的 {@code K_*} 常量
     */
    public static double growthAttr(Player player, String key) {
        return 0.0;
    }
}
