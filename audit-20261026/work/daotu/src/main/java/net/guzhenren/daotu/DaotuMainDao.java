package net.guzhenren.daotu;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.entity.player.Player;

/**
 * 玩家的「主修流派」。
 *
 * <h2>为什么要有这个东西</h2>
 * 特殊奖励（每 10 抽）的道痕、流派熟练度、主修蛊虫都要按主修流派给。
 * 本体<b>没有</b>「主修流派」这个字段——只有 46 个 {@code liupai_*} 熟练度（数值）
 * 和 36 个 {@code daohen_*} 道痕。数值最高那条只代表「练得最多」，
 * 不代表玩家想专精它（兼修玩家会被误判）。
 *
 * <p>所以主修由玩家<b>手动指定</b>，存在 {@code daotu/mainDao}。
 *
 * <h2>存放位置与转世</h2>
 * <p>存在 {@code daotu} 根下、<b>不在 {@code run} 里</b>：转世重置 {@code run} 时
 * 不该把主修也清掉——它和「自动弹窗」一样属于玩家偏好，见
 * {@link DaotuRun#autoPrompt} 的同类处理。
 * {@link DaotuOpening#onReincarnation} 已在 {@code wipeAll} 前后显式存取。
 *
 * <p>老存档没有这个键时返回空串，调用方按「未设定」处理。
 */
public final class DaotuMainDao {

    /**
     * 可选主修列表：键 → 中文名。
     *
     * <p>只收本体<b>两侧字段都真实存在</b>的流派——{@code daohen_*} 与
     * {@code liupai_*} 都要有，缺一个就意味着「道痕给不了」或「熟练度给不了」。
     * 已用 {@code _crash3/probe_huodao.py} 逐条核对。
     *
     * <p><b>为什么没有火道</b>：本体既没有 {@code daohen_huodao} 也没有
     * {@code liupai_huodao}（火道在本体里是「炎道」{@code yandao} 的附属概念，
     * 不单独占字段）。旧版 {@code DaotuSpecial.DAOHEN_KEYS} 里写过
     * {@code daohen_huodao}，那是<b>从未生效过的死引用</b>（{@code addDaohen}
     * 对它返回 false，只记一行日志），已一并清掉。
     *
     * <p>用 {@link LinkedHashMap} 保持插入序，界面列表按这个顺序显示。
     */
    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("jiandao", "剑道");
        ALL.put("jindao", "金道");
        ALL.put("shuidao", "水道");
        ALL.put("xuedao", "血道");
        ALL.put("lidao", "力道");
        ALL.put("mudao", "木道");
        ALL.put("yudao", "浴道");
        ALL.put("fengdao", "风道");
        ALL.put("bingdao", "冰道");
        ALL.put("jindao2", "精道");
        ALL.put("hundao", "魂道");
        ALL.put("leidao", "雷道");
        ALL.put("yandao", "炎道");
        ALL.put("lvdao", "律道");
        ALL.put("tudao", "土道");
    }

    private static final String KEY = "mainDao";

    private DaotuMainDao() {
    }

    /** 当前主修流派键（空串 = 未设定）。 */
    public static String of(Player player) {
        return DaotuData.getString(player, KEY, "");
    }

    /** 设定主修。键不在 {@link #ALL} 里时拒绝（防止脏数据）。 */
    public static boolean set(Player player, String key) {
        if (key == null || !ALL.containsKey(key)) {
            return false;
        }
        DaotuData.putString(player, KEY, key);
        return true;
    }

    /** 清除主修。 */
    public static void clear(Player player) {
        DaotuData.putString(player, KEY, "");
    }

    /** 中文名（未设定返回「未定」）。 */
    public static String nameOf(Player player) {
        String k = of(player);
        return k.isEmpty() ? "未定" : ALL.getOrDefault(k, k);
    }

    /** 键 → 中文名（不认识返回键本身）。 */
    public static String label(String key) {
        return ALL.getOrDefault(key, key);
    }
}
