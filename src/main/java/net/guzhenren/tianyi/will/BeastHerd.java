package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 野生兽群：带<b>栖息地、种群、承载力、捕猎、迁徙、崩溃</b>的一群兽。
 *
 * <h2>要解决的老毛病：不能在玩家身边无限刷怪</h2>
 * 3.0.0 的「兽群」是直接按数量生成一批实体。那不是生态，是刷怪点。
 * 这里改成先有<b>种群</b>，再谈生成：
 * <ul>
 *   <li>{@link #population} 是这个兽群真正有多少只 —— <b>生成一只就少一只</b>；</li>
 *   <li>种群朝 {{@link #capacity}}（栖息地承载力）自然增长，快了就慢下来；</li>
 *   <li>被猎杀会掉种群；杀到低于阈值会变 {@link State#STRAINED}，
 *       杀到见底会 {@link State#COLLAPSED}（崩溃），之后要很久才能回；</li>
 *   <li>栖息地太差或种群过挤会 {@link #migrate 迁徙}，锚点真的会挪。</li>
 * </ul>
 *
 * <h2>天意怎么利用它</h2>
 * {@link #drive} 把兽群「赶向」某个方向 —— 但被赶走的兽是**从种群账上扣的**，
 * 所以天意不能凭空变出无限的兽潮；它只能把已经存在的兽往某人那边赶。
 * 这正对应原著里天意「影响他人成为劫」，而不是自己造兵。
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class BeastHerd {
    /** 兽群状态。 */
    public enum State {
        /** 繁盛：接近承载力。 */
        THRIVING,
        /** 受压：被猎杀得厉害，增长跟不上。 */
        STRAINED,
        /** 崩溃：见底了，需要很长时间恢复。 */
        COLLAPSED
    }

    public final String id;
    public final String beastId;
    public final String dim;

    private long anchorPos;
    /** 当前种群数量（可以是小数：代表幼兽/未成年的折算）。 */
    private double population;
    /** 栖息地承载力。 */
    private final double capacity;
    /** 栖息地质量 0..1：影响增长速率。 */
    private double habitatQuality;
    /** 每日基础增长率（占种群比例）。[平衡参数] */
    private final double growthRate;
    private int harvested = 0;
    private int driven = 0;
    private int slain = 0;
    private State state = State.THRIVING;
    private long lastChangeAt = 0L;
    private String lastCause = "natural";
    private int migrations = 0;

    public BeastHerd(String id, String beastId, String dim, long anchorPos,
                     double population, double capacity, double habitatQuality,
                     double growthRatePerDay, long createdAt) {
        this.id = id;
        this.beastId = requireRegistryId(beastId);
        this.dim = dim == null ? "" : dim;
        this.anchorPos = anchorPos;
        this.capacity = Math.max(1.0, capacity);
        this.population = Knowledge.clamp(population, 0.0, this.capacity);
        this.habitatQuality = Knowledge.clamp(habitatQuality, 0.0, 1.0);
        this.growthRate = Math.max(0.0, growthRatePerDay);
        this.lastChangeAt = createdAt;
    }

    // ---------------------------------------------------------------- 读取

    public long anchorPos() {
        return anchorPos;
    }

    public double population() {
        return population;
    }

    public double capacity() {
        return capacity;
    }

    public double habitatQuality() {
        return habitatQuality;
    }

    public int harvested() {
        return harvested;
    }

    public int driven() {
        return driven;
    }

    public State state() {
        return state;
    }

    public String lastCause() {
        return lastCause;
    }

    public long lastChangeAt() {
        return lastChangeAt;
    }

    public int migrations() {
        return migrations;
    }

    /** 还能支撑多少只（考虑承载力）。 */
    public double headroom() {
        return Math.max(0.0, capacity - population);
    }

    /** 现在能不能「赶」出至少一只兽。 */
    public boolean canDrive(int count) {
        return state != State.COLLAPSED && population >= Math.max(1, count);
    }

    // ---------------------------------------------------------------- 生态

    /**
     * 自然增长：朝承载力靠近，越快满越慢。
     *
     * <p><b>B08 修复</b>：崩溃（见底）不再是永久死亡 —— 只要栖息地还过得去，
     * 隔 {@link #RECOLONIZE_DELAY} 之后会有邻群迁入，补进极少量个体重新开始。
     * 但绝不允许「在玩家身边重新开满额种群」，补入量恒为 1。
     *
     * @param gameDays 经过的游戏日
     * @return 实际增长量
     */
    public double grow(double gameDays, long now) {
        if (gameDays <= 0.0) {
            return 0.0;
        }
        if (state == State.COLLAPSED) {
            // 崩溃后只能靠邻群迁入慢慢恢复，且必须栖息地还过得去
            if (habitatQuality < RECOLONIZE_MIN_HABITAT) {
                return 0.0;
            }
            if (now - lastChangeAt < RECOLONIZE_DELAY) {
                return 0.0;
            }
            population = 1.0;
            lastCause = "recolonized";
            lastChangeAt = now;
            state = State.STRAINED;
            return 1.0;
        }
        double before = population;
        double room = (capacity - population) / capacity;      // 0..1
        double inc = population * growthRate * habitatQuality * Math.max(0.0, room) * gameDays;
        population = Math.min(capacity, population + inc);
        refreshState(now, "natural");
        return population - before;
    }

    /** 邻群迁入所需的最低栖息地质量。 */
    public static final double RECOLONIZE_MIN_HABITAT = 0.5;
    /** 崩溃后到邻群迁入的等待时长（刻）。5 游戏日。 */
    public static final long RECOLONIZE_DELAY = 24_000L * 5L;

    /** 栖息地变化（被采伐、被抽水、被天意扰乱）。 */
    public void habitat(double quality, long now, String cause) {
        habitatQuality = Knowledge.clamp(quality, 0.0, 1.0);
        refreshState(now, cause);
    }

    /**
     * 捕猎：种群真的减少。
     *
     * @return 实际打到的数量（不超过现有种群）
     */
    public int harvest(int want, long now) {
        if (want <= 0 || state == State.COLLAPSED) {
            return 0;
        }
        int can = (int) Math.floor(population);
        int got = Math.min(can, want);
        if (got <= 0) {
            return 0;
        }
        population -= got;
        harvested += got;
        refreshState(now, "hunted");
        return got;
    }

    /**
     * 天意借兽：把 {@code count} 只赶向某处。
     *
     * <p>赶出去的兽<b>从种群账上扣</b>，所以不存在「随手变出兽潮」。
     *
     * @return 实际能赶出去的数量
     */
    public int drive(int count, long now) {
        if (count <= 0 || state == State.COLLAPSED) {
            return 0;
        }
        int can = (int) Math.floor(population);
        int got = Math.min(can, count);
        if (got <= 0) {
            return 0;
        }
        population -= got;
        driven += got;
        refreshState(now, "will-driven");
        return got;
    }

    /**
     * 把「预留但没生成出来」的种群还回去（B07）。
     *
     * <p>生成失败（区块没加载/生成器拒绝）时调用，避免「点了名却扣了数」。
     * 归还量受承载力约束，已崩溃的群不收（要等邻群迁入）。
     *
     * @return 实际归还量
     */
    public int returnPopulation(int count, long now) {
        if (count <= 0 || state == State.COLLAPSED) {
            return 0;
        }
        double before = population;
        population = Math.min(capacity, population + count);
        int back = (int) Math.floor(population - before);
        if (back > 0) {
            lastCause = "drive-returned";
            lastChangeAt = now;
        }
        return back;
    }

    /**
     * 已实体化的个体被杀：<b>不再扣种群</b>（B07）。
     *
     * <p>被赶出去的兽在 {@link #drive} 时已经从账上扣过了，
     * 再扣一次就是重复记账。这里只记战果，供诊断与「猎杀压力」判断。
     */
    public void noteSlain(int n, long now) {
        if (n <= 0) {
            return;
        }
        slain += n;
        lastCause = "slain";
        lastChangeAt = now;
    }

    /** 已实体化后被打死的个体数（不计入种群）。 */
    public int slain() {
        return slain;
    }

    /** 迁徙：锚点真的挪动（栖息地变差或种群过挤时）。 */
    public boolean migrate(long newPos, long now, String cause) {
        if (newPos == anchorPos) {
            return false;
        }
        anchorPos = newPos;
        migrations++;
        lastCause = cause == null ? "migrated" : cause;
        lastChangeAt = now;
        return true;
    }

    /** 是否该迁徙：栖息地差到一定程度，或挤到八成以上。 */
    public boolean shouldMigrate() {
        return state != State.COLLAPSED
                && (habitatQuality < 0.35 || population > capacity * 0.85);
    }

    private void refreshState(long now, String cause) {
        lastCause = cause;
        lastChangeAt = now;
        double ratio = capacity <= 0 ? 0 : population / capacity;
        if (population < 1.0) {
            state = State.COLLAPSED;
        } else if (ratio < 0.25) {
            state = State.STRAINED;
        } else if (ratio > 0.6) {
            state = State.THRIVING;
        } else {
            state = State.STRAINED;
        }
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "Herd{%s %s 种群=%.1f/%.0f 栖息=%.2f %s 猎=%d 赶=%d 迁=%d 因=%s}",
                id, beastId, population, capacity, habitatQuality, state,
                harvested, driven, migrations, lastCause);
    }

    /** 从存档恢复（服务端重启）。整组赋值，避免出现矛盾状态。 */
    public void restore(double population, double habitatQuality, int harvested, int driven,
                        State st, String cause, long lastChange, int migrations,
                        long anchorPosIn) {
        this.population = Knowledge.clamp(population, 0.0, capacity);
        this.habitatQuality = Knowledge.clamp(habitatQuality, 0.0, 1.0);
        this.harvested = Math.max(0, harvested);
        this.driven = Math.max(0, driven);
        this.state = st == null ? State.THRIVING : st;
        this.lastCause = cause == null ? "" : cause;
        this.lastChangeAt = lastChange;
        this.migrations = Math.max(0, migrations);
        if (anchorPosIn != Knowledge.Fact.NO_POS) {
            this.anchorPos = anchorPosIn;
        }
    }

    private static String requireRegistryId(String id) {
        if (id == null || id.indexOf(':') <= 0) {
            throw new IllegalArgumentException("兽群必须用完整注册 ID：" + id);
        }
        return id.toLowerCase(Locale.ROOT);
    }

    // ================================================================ 兽群簿

    /** 全世界的兽群账。 */
    public static final class Book {
        /** 同时存在的兽群上限。 */
        public static final int MAX_HERDS = 48;
        /** 每日基础增长率。[平衡参数] */
        public static final double DEFAULT_GROWTH = 0.04;

        private final Map<String, BeastHerd> byId = new LinkedHashMap<>();

        public BeastHerd add(BeastHerd herd) {
            if (byId.size() >= MAX_HERDS && !byId.containsKey(herd.id)) {
                return null;
            }
            byId.put(herd.id, herd);
            return herd;
        }

        /** 建一处兽群（同一处不重复建）。 */
        public BeastHerd open(String id, String beastId, String dim, long pos,
                              double capacity, double habitat, long now) {
            if (at(dim, pos, 64.0) != null) {
                return at(dim, pos, 64.0);
            }
            BeastHerd h = new BeastHerd(id, beastId, dim, pos,
                    capacity * 0.7, capacity, habitat, DEFAULT_GROWTH, now);
            return add(h);
        }

        public BeastHerd get(String id) {
            return byId.get(id);
        }

        public BeastHerd at(String dim, long pos, double tolerance) {
            BeastHerd best = null;
            double bestD = Double.MAX_VALUE;
            for (BeastHerd h : byId.values()) {
                if (!h.dim.equals(dim)) {
                    continue;
                }
                double d = distance(h.anchorPos, pos);
                if (d <= tolerance && d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
            return best;
        }

        /** 找一处「还有兽可赶」且离目标最近的兽群。 */
        public BeastHerd nearestDrivable(String dim, long pos, double tolerance, int need) {
            BeastHerd best = null;
            double bestD = Double.MAX_VALUE;
            for (BeastHerd h : byId.values()) {
                if (!h.dim.equals(dim) || !h.canDrive(need)) {
                    continue;
                }
                double d = distance(h.anchorPos, pos);
                if (d <= tolerance && d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
            return best;
        }

        /** 推进所有兽群的自然增长与迁徙判定。 */
        public void tick(double gameDays, long now) {
            for (BeastHerd h : byId.values()) {
                h.grow(gameDays, now);
            }
        }

        public List<BeastHerd> all() {
            return new ArrayList<>(byId.values());
        }

        public List<BeastHerd> inDim(String dim) {
            List<BeastHerd> out = new ArrayList<>();
            for (BeastHerd h : byId.values()) {
                if (h.dim.equals(dim)) {
                    out.add(h);
                }
            }
            return out;
        }

        public int size() {
            return byId.size();
        }

        public int countState(State st) {
            int n = 0;
            for (BeastHerd h : byId.values()) {
                if (h.state() == st) {
                    n++;
                }
            }
            return n;
        }

        public double totalPopulation() {
            double n = 0;
            for (BeastHerd h : byId.values()) {
                n += h.population();
            }
            return n;
        }

        private static double distance(long a, long b) {
            int ax = (int) (a >> 38);
            int az = (int) ((a << 26) >> 38);
            int ay = (int) ((a << 52) >> 52);
            int bx = (int) (b >> 38);
            int bz = (int) ((b << 26) >> 38);
            int by = (int) ((b << 52) >> 52);
            double dx = ax - bx;
            double dy = ay - by;
            double dz = az - bz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
