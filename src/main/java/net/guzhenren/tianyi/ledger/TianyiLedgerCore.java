package net.guzhenren.tianyi.ledger;

/**
 * 天机账本核心（2.9.0，**不依赖 Minecraft**，可离线单测）。
 *
 * <h2>为什么把账本抽成纯逻辑</h2>
 * 2.8.0 的账本只有一个 {@code add(delta)}：收入、支出、退款全走同一条路径，
 * 于是「退款」会被当成收入累加进<b>累计天机</b>，进而抬高天意阶位与每日收益上限；
 * 死亡扣天机走 {@code add(负数)} 还会把累计值减下去。这些都是账目污染，
 * 而且改起来必须能验证 —— 所以把规则做成不依赖 MC 的纯函数，用离线测试锁死。
 *
 * <h2>四条不可违反的规则</h2>
 * <ol>
 *   <li><b>收入</b>（{@link #grant}）：余额↑、累计↑、当日已赚↑，受日上限约束；</li>
 *   <li><b>支出</b>（{@link #spend}）：余额↓，累计与当日已赚<b>不变</b>；</li>
 *   <li><b>退款</b>（{@link #refund}）：余额↑，累计与当日已赚<b>都不变</b>，单独记 {@code refunded}；</li>
 *   <li><b>处罚</b>（{@link #penalize}）：余额↓（不低于 0），单独记 {@code penalty}，累计<b>不变</b>。</li>
 * </ol>
 * 累计只增不减，才配当阶位依据；退款不算收入，才能杜绝「付款-退款」刷阶位。
 *
 * <h2>幂等与迁移</h2>
 * {@link #settleOnce} 用一次性标记保证同一动作只结算一次；{@link #migrate} 用
 * {@code schema} 版本号保证迁移可重复执行且结果不变（幂等），并且<b>只会抬高不会压低</b>
 * 已有累计值。
 */
public final class TianyiLedgerCore {

    /** 账本存储抽象：MC 侧接玩家持久数据，测试侧接内存 Map。 */
    public interface Store {
        int getInt(String key, int def);

        void putInt(String key, int value);

        boolean getBool(String key, boolean def);

        void putBool(String key, boolean value);
    }

    /** 当前账本结构版本。读旧数据时按版本迁移，不删旧键。 */
    public static final int SCHEMA = 2;

    public static final String K_SCHEMA = "schema";
    public static final String K_POINTS = "points";
    public static final String K_LIFETIME = "lifetime";
    public static final String K_EARN_DAY = "earnDay";
    public static final String K_EARN = "earnedToday";
    public static final String K_REFUNDED = "refundedTotal";
    public static final String K_PENALTY = "penaltyTotal";

    private TianyiLedgerCore() {
    }

    // ------------------------------------------------------------------ 读

    public static int points(Store s) {
        return Math.max(0, s.getInt(K_POINTS, 0));
    }

    /** 累计天机：只由收入抬高，永不被支出/退款/处罚改动。 */
    public static int lifetime(Store s) {
        return Math.max(0, s.getInt(K_LIFETIME, 0));
    }

    /** 当日已赚（跨日自动归零，不写盘也能读对）。 */
    public static int earnedToday(Store s, int day) {
        return s.getInt(K_EARN_DAY, -1) == day ? Math.max(0, s.getInt(K_EARN, 0)) : 0;
    }

    public static int refundedTotal(Store s) {
        return Math.max(0, s.getInt(K_REFUNDED, 0));
    }

    public static int penaltyTotal(Store s) {
        return Math.max(0, s.getInt(K_PENALTY, 0));
    }

    // ------------------------------------------------------------------ 写

    /**
     * 收入入账（唯一会抬高累计的入口）。
     *
     * @param amount   期望入账数量（≤0 直接忽略）
     * @param day      当前游戏日（用于日上限归零）
     * @param dailyCap 当日可赚上限（≤0 表示不限）
     * @return 实际入账数量
     */
    public static int grant(Store s, int amount, int day, int dailyCap) {
        if (amount <= 0) {
            return 0;
        }
        int earned = earnedToday(s, day);
        int allowed = amount;
        if (dailyCap > 0) {
            allowed = Math.min(amount, Math.max(0, dailyCap - earned));
        }
        if (allowed <= 0) {
            return 0;
        }
        s.putInt(K_POINTS, points(s) + allowed);
        s.putInt(K_LIFETIME, lifetime(s) + allowed);
        s.putInt(K_EARN_DAY, day);
        s.putInt(K_EARN, earned + allowed);
        return allowed;
    }

    /** 支出：只扣余额。余额不足返回 false 且不改变任何计数。 */
    public static boolean spend(Store s, int cost) {
        if (cost <= 0) {
            return true;
        }
        int p = points(s);
        if (p < cost) {
            return false;
        }
        s.putInt(K_POINTS, p - cost);
        return true;
    }

    /**
     * 退款：只加回余额。
     *
     * <p><b>不动累计、不动当日已赚</b>——否则「买→退」就能刷阶位；
     * 单独记 {@code refundedTotal} 便于对账与审计。
     */
    public static void refund(Store s, int amount) {
        if (amount <= 0) {
            return;
        }
        s.putInt(K_POINTS, points(s) + amount);
        s.putInt(K_REFUNDED, refundedTotal(s) + amount);
    }

    /**
     * 处罚：扣余额（不低于 0），单独记 {@code penaltyTotal}，不动累计。
     *
     * @return 实际扣掉的数量
     */
    public static int penalize(Store s, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int p = points(s);
        int taken = Math.min(p, amount);
        if (taken <= 0) {
            return 0;
        }
        s.putInt(K_POINTS, p - taken);
        s.putInt(K_PENALTY, penaltyTotal(s) + taken);
        return taken;
    }

    // ------------------------------------------------------------------ 幂等

    /**
     * 一次性结算标记：第一次调用返回 true 并落标记，之后永远 false。
     *
     * <p>用于「领取 / 付款确认 / 跨世结算 / 契约完成」这类只许生效一次的动作。
     * 服务端必须以此为准，不能靠客户端是否重复发包。
     */
    public static boolean settleOnce(Store s, String actionKey) {
        String k = "once." + actionKey;
        if (s.getBool(k, false)) {
            return false;
        }
        s.putBool(k, true);
        return true;
    }

    public static boolean settled(Store s, String actionKey) {
        return s.getBool("once." + actionKey, false);
    }

    // ------------------------------------------------------------------ 迁移

    /**
     * 旧账本迁移（幂等）。
     *
     * <p>2.8.0 把余额与累计分放在 {@code econ.tianji} / {@code econ.lifetime_tianji}。
     * 这里只在 {@code schema < 2} 时搬一次，并且累计取「旧值」与「新值」的较大者——
     * 迁移可以被重跑，但绝不把玩家的累计往下压。旧键一律保留，便于回滚与对账。
     *
     * @return 是否真的执行了迁移
     */
    public static boolean migrate(Store s, int legacyPoints, int legacyLifetime) {
        if (s.getInt(K_SCHEMA, 0) >= SCHEMA) {
            return false;
        }
        s.putInt(K_POINTS, Math.max(points(s), Math.max(0, legacyPoints)));
        s.putInt(K_LIFETIME, Math.max(lifetime(s), Math.max(0, legacyLifetime)));
        s.putInt(K_SCHEMA, SCHEMA);
        return true;
    }

    public static int schema(Store s) {
        return s.getInt(K_SCHEMA, 0);
    }

    /**
     * 账本自洽检查（给审计与上层守卫用）。
     *
     * <p>注意这里必须读<b>原始值</b>，不能走 {@link #points} 这类钳位访问器——
     * 否则「余额被写成负数」这种数据损坏会被 {@code Math.max(0, …)} 掩盖成正常。
     * 这个坑是离线测试抓出来的（{@code TianyiLedgerCoreTest#testConsistentGuard}）。
     */
    public static boolean consistent(Store s) {
        return s.getInt(K_POINTS, 0) >= 0
                && s.getInt(K_LIFETIME, 0) >= 0
                && s.getInt(K_REFUNDED, 0) >= 0
                && s.getInt(K_PENALTY, 0) >= 0
                && s.getInt(K_EARN, 0) >= 0;
    }
}
