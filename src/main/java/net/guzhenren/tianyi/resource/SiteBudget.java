package net.guzhenren.tianyi.resource;

/**
 * 有限资源点的<b>纯策略</b>：什么时候该检查、一次最多查几个/落地几个、一个 8×8 区块能不能再放、
 * 植物该不该累计生长时间、守护兽能不能补生。
 *
 * <p>本类<b>不 import 任何 Minecraft 类型</b>，因此可以在 {@code _pack/run_tests.py} 里
 * 用纯 javac + java 跑确定性测试（{@code _pack/tests/.../SiteBudgetTest.java}）。
 * 世界侧（{@link net.guzhenren.tianyi.world.FiniteSiteService}）只负责把这些判断翻译成
 * 真实的区块/方块/实体操作，不重复实现阈值。
 *
 * <h2>为什么阈值放在这里而不是 SiteSettings</h2>
 * {@code SiteSettings} 依赖 NeoForge 的 {@code ModConfigSpec}，不能进纯逻辑测试。
 * 数值以本类为唯一来源，{@code SiteSettings} 的同名常量直接引用这里，避免两处各写一个 64。
 *
 * <h2>刻意不做的事</h2>
 * <ul>
 *   <li>不按玩家刷新、不做随机 fallback、不强制加载区块；</li>
 *   <li>不因为守护兽“没看见”就判死，也不补生；</li>
 *   <li>卸载的时间不补算：{@link #mayAccrueGrowth} 的第一个参数就是“已加载”。</li>
 * </ul>
 */
public final class SiteBudget {
    /** 服务端刻检查间隔：每 200 刻（约 10 秒）看一轮。 */
    public static final int CHECK_INTERVAL_TICKS = 200;
    /** 每轮最多“检查”4 个候选位置（便宜预筛；真正昂贵的落地检查每轮至多 1 次）。 */
    public static final int CANDIDATES_PER_CHECK = 4;
    /** 每轮最多落地 1 个点。 */
    public static final int PLACEMENTS_PER_CHECK = 1;
    /** 一个生成记号覆盖的区块边长：8×8 区块至多 1 点。 */
    public static final int REGION_CHUNKS = 8;
    /** 全服初始上限。 */
    public static final int GLOBAL_LIMIT = 64;
    /** 植物累计多少“已加载刻”才长一份：72000 刻 = 3 游戏日。 */
    public static final long GROWTH_TICKS = 72000L;
    /** 植物终身额外预算上限（与 SiteCatalog 的模板一致）。 */
    public static final int LIFETIME_GROWTH_LIMIT = 4;
    /** 玩家离多近才算“亲眼到达”。 */
    public static final int WITNESS_RADIUS = 24;

    private SiteBudget() {}

    /** 只在整 200 刻检查（传入主世界的 game time）；0 刻（刚开服）不检查，避免开服瞬间扎堆。 */
    public static boolean due(long gameTime) {
        return gameTime > 0 && gameTime % CHECK_INTERVAL_TICKS == 0;
    }

    /** 全局还有空位吗。已采空的点会被退役成墓碑，不会占着上限。 */
    public static boolean hasRoom(int siteCount) {
        return siteCount >= 0 && siteCount < GLOBAL_LIMIT;
    }

    /**
     * 这个 8×8 区块记号还能不能放点。
     *
     * <p>参数是<b>“该区域尚未被占用”</b>（调用方传 {@code !regions.containsKey(key)}），
     * 已有活点或墓碑都算占用。
     *
     * <p>历史坑：本方法曾把参数当“已被占用”再取反，与调用方相反，
     * 导致区域恒判不可用、资源点永不生成（玩法被架空）。SiteBudgetTest 抓到了这处反转。
     */
    public static boolean regionAvailable(boolean regionFree) {
        return regionFree;
    }

    /** 本轮还能再看几个候选（便宜预筛计数）。 */
    public static boolean candidatesLeft(int attempted) {
        return attempted >= 0 && attempted < CANDIDATES_PER_CHECK;
    }

    /** 本轮还能再落地几个。 */
    public static boolean placementsLeft(int placed) {
        return placed >= 0 && placed < PLACEMENTS_PER_CHECK;
    }

    /**
     * 植物这一刻该不该累计生长时间。
     *
     * <p>四个条件缺一不可：区块已加载、根基完好、环境合法（土壤/水/光照/群系）、还有空槽位。
     * 最后一条很关键：槽位全满时累计出来的份额没有地方物化，会变成永远发不出去的挂账。
     */
    public static boolean mayAccrueGrowth(boolean loaded, boolean environmentValid,
            boolean rootIntact, boolean hasFreeSlot) {
        return loaded && environmentValid && rootIntact && hasFreeSlot;
    }

    /**
     * 守护兽能否“补生”。
     *
     * <p>永远不能：绑定已经存在时（不论实体是活着、死亡还是未加载）都不许再生一只。
     * 缺失/卸载不等于死亡，也不构成补生理由；只有匹配 UUID 的死亡事件能确认死亡，
     * 而确认死亡之后同样不补生、不给击杀奖励。
     */
    public static boolean maySpawnGuardian(boolean alreadyBound) {
        return !alreadyBound;
    }

    /** 某个槽位是否还需要一次物化：没物化过要，植物挂账增长（所有槽位都已物化）也要。 */
    public static boolean needsSlot(boolean materialized, boolean growthPending) {
        return !materialized || growthPending;
    }

    /** 初始量：模板给出的 [minimum, maximum] 里按 roll 取值，roll 越界按取模收敛，绝不越界。 */
    public static int units(int minimum, int maximum, int roll) {
        if (minimum < 1 || maximum < minimum) throw new IllegalArgumentException("invalid unit range");
        int span = maximum - minimum + 1;
        int offset = Math.floorMod(roll, span);
        return minimum + offset;
    }

    /**
     * 生成记号：{@code 维度:区块X/8:区块Z/8}。
     *
     * <p>格式必须与 {@code SitePlacement.candidate} 写进 {@link FiniteSite#regionMarker()} 的完全一致，
     * 否则这里的事前判断就形同虚设（{@code FiniteSiteState.reserve} 仍会兜底拒绝重复）。
     */
    public static String regionKey(String dimension, int blockX, int blockZ) {
        if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("missing dimension");
        return dimension + ":" + Math.floorDiv(blockX >> 4, REGION_CHUNKS)
                + ":" + Math.floorDiv(blockZ >> 4, REGION_CHUNKS);
    }
}
