package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 资源产地：有<b>储量、恢复、上限、归属、破坏</b>的一处真实产地。
 *
 * <h2>为什么需要「产地」而不是「一次掉落」</h2>
 * 3.0.0 的扶持只是在地表丢一次物品。那既不可调查（拿完就没了），
 * 也没法被天意针对（没有"资源点"这个对象可以毁）。原著里天意剿不掉目标时，
 * 会「借天道改易道痕的权限把资源点搅烂」—— 要能毁，先得有个东西可毁。
 *
 * <h2>五条必须守住的账</h2>
 * <ol>
 *   <li><b>储量</b>：能产出的总量有上限（{@link #stock} / {@link #maxStock}）。</li>
 *   <li><b>恢复</b>：按游戏日缓慢回补，不是无限供给；<b>毁了就不再回</b>。</li>
 *   <li><b>归属</b>：第一个来采的人把它占了；别人再来采算「掠夺」，会被记下来。</li>
 *   <li><b>破坏</b>：可以是天意做的（搅乱道痕），也可以是玩家做的（采尽/毁掉），
 *       无论谁做的都留 {@link #lastCause} 供调查。</li>
 *   <li><b>不凭空生货</b>：{@link #harvest} 吐出的数量永远 ≤ 当前储量，
 *       所以不可能被刷。</li>
 * </ol>
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class ResourceSite {
    /** 产地类型。与扶持渠道一一对应：资源成熟 / 传承线索。 */
    public enum Kind {
        /** 野外蛊材产地（未炼化蛊）。 */
        WILD_GU,
        /** 传承蛊方产地。 */
        GU_FANG
    }

    /** 状态。 */
    public enum State {
        /** 正常产出。 */
        PRODUCTIVE,
        /** 采尽且不会恢复（储量归零、无恢复速率）。 */
        DEPLETED,
        /** 被毁（天意或玩家），永久不再产出。 */
        DESTROYED
    }

    /** 归属未知。 */
    public static final String UNOWNED = "";

    public final String id;
    public final Kind kind;
    public final String dim;
    public final long pos;
    public final double radius;
    /** 产出的具体注册 ID（完整 {@code namespace:path}）。 */
    public final String registryId;
    public final long createdAt;

    private double stock;
    private final double maxStock;
    /** 每游戏日恢复量（0 = 不恢复）。 */
    private double regenPerDay;
    private String owner = UNOWNED;
    private long ownerSince = 0L;
    private State state = State.PRODUCTIVE;
    private String lastCause = "natural";
    private long lastChangeAt = 0L;
    private int yields = 0;
    private int poached = 0;
    /** 这处产地是不是「诱饵」（原著：留机缘作香饵，铺棋路）。 */
    private boolean bait = false;
    /** 诱饵是否已经被识破（玩家采走了第一批货）。 */
    private boolean revealed = false;

    public ResourceSite(String id, Kind kind, String dim, long pos, double radius,
                        String registryId, double maxStock, double regenPerDay,
                        long createdAt) {
        this.id = id;
        this.kind = kind;
        this.dim = dim == null ? "" : dim;
        this.pos = pos;
        this.radius = radius <= 0 ? 32.0 : radius;
        this.registryId = requireRegistryId(registryId);
        this.maxStock = Math.max(1.0, maxStock);
        this.stock = this.maxStock * 0.6; // 新发现的产地不是满的
        this.regenPerDay = Math.max(0.0, regenPerDay);
        this.createdAt = createdAt;
    }

    // ---------------------------------------------------------------- 读取

    public double stock() {
        return stock;
    }

    public double maxStock() {
        return maxStock;
    }

    public double regenPerDay() {
        return regenPerDay;
    }

    public String owner() {
        return owner;
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

    public int yields() {
        return yields;
    }

    public int poached() {
        return poached;
    }

    public boolean owned() {
        return !UNOWNED.equals(owner);
    }

    /** 标记为诱饵 / 查询。 */
    public ResourceSite bait(boolean v) {
        this.bait = v;
        return this;
    }

    public boolean isBait() {
        return bait;
    }

    /**
     * 诱饵被识破：只有第一次有效。
     *
     * <p>对应原著「道破即震怒」——有人看穿了这处机缘，天意才会有天象级反应。
     * 一次布局只怒一次，不重复劈。
     *
     * @return {@code true} 表示这次是第一次识破
     */
    public boolean reveal() {
        if (!bait || revealed) {
            return false;
        }
        revealed = true;
        return true;
    }

    public boolean revealed() {
        return revealed;
    }

    public boolean ownedBy(String who) {
        return owned() && owner.equals(who);
    }

    /** 现在能不能采到东西。 */
    public boolean harvestable() {
        return state == State.PRODUCTIVE && stock >= 1.0;
    }

    // ---------------------------------------------------------------- 恢复

    /**
     * 随时间恢复储量。
     *
     * @param gameDays 本次经过的游戏日（可以是小数）
     * @return 实际恢复量
     */
    public double regen(double gameDays) {
        if (state == State.DESTROYED || regenPerDay <= 0.0 || gameDays <= 0.0) {
            return 0.0;
        }
        double before = stock;
        stock = Math.min(maxStock, stock + regenPerDay * gameDays);
        if (state == State.DEPLETED && stock >= 1.0) {
            state = State.PRODUCTIVE; // 恢复过来了
        }
        return stock - before;
    }

    // ---------------------------------------------------------------- 采集

    /**
     * 采集。
     *
     * <p><b>吐出的数量永远 ≤ 当前储量</b>，所以这里不可能被用来刷物品。
     *
     * @param claimant 采集者（完整 UUID）；第一个采集者成为归属人
     * @param want     想采多少
     * @return 实际采到的数量（整数，不会超过储量）
     */
    public int harvest(String claimant, int want, long now) {
        if (!harvestable() || want <= 0) {
            return 0;
        }
        int can = (int) Math.floor(stock);
        int got = Math.min(can, want);
        if (got <= 0) {
            return 0;
        }
        stock -= got;
        yields += got;
        if (!owned()) {
            owner = Knowledge.requireIdentity(claimant);
            ownerSince = now;
            lastCause = "claimed";
        } else if (!ownedBy(Knowledge.requireIdentity(claimant))) {
            // 不是归属人来采 → 掠夺，记一笔（天意据此可以"借人施劫"）
            poached += got;
            lastCause = "poached-by:" + Knowledge.requireIdentity(claimant);
        }
        lastChangeAt = now;
        if (stock < 1.0 && regenPerDay <= 0.0) {
            state = State.DEPLETED;
            lastCause = "exhausted";
        }
        return got;
    }

    /** 转让归属（势力易主、被夺）。 */
    public void reassign(String newOwner, long now) {
        owner = newOwner == null || newOwner.isBlank()
                ? UNOWNED : Knowledge.requireIdentity(newOwner);
        ownerSince = now;
        lastCause = "reassigned";
        lastChangeAt = now;
    }

    /**
     * 把货退回产地（运输被切断时用）。
     *
     * <p>退回量受 {@link #maxStock} 限制，且已毁的产地不收退货 ——
     * 否则「发出去再退回」就能绕过储量上限，等于刷物品。
     *
     * @return 实际收下的量
     */
    public int returnStock(int count, long now) {
        if (count <= 0 || state == State.DESTROYED) {
            return 0;
        }
        double before = stock;
        stock = Math.min(maxStock, stock + count);
        int accepted = (int) Math.floor(stock - before);
        lastCause = "cargo-returned";
        lastChangeAt = now;
        if (state == State.DEPLETED && stock >= 1.0) {
            state = State.PRODUCTIVE;
        }
        return accepted;
    }

    /**
     * 从存档恢复（服务端重启）。
     *
     * <p>刻意做成一个大参数方法：恢复时字段必须<b>整组</b>一致，
     * 分开设值容易漏掉「储量恢复但状态仍是已毁」这类矛盾状态。
     */
    public void restore(double stock, double regenPerDay, String owner, long ownerSince,
                        State state, String lastCause, long lastChangeAt,
                        int yields, int poached) {
        this.stock = Knowledge.clamp(stock, 0.0, maxStock);
        this.regenPerDay = Math.max(0.0, regenPerDay);
        this.owner = owner == null || owner.isBlank() ? UNOWNED : owner;
        this.ownerSince = ownerSince;
        this.state = state == null ? State.PRODUCTIVE : state;
        this.lastCause = lastCause == null ? "" : lastCause;
        this.lastChangeAt = lastChangeAt;
        this.yields = Math.max(0, yields);
        this.poached = Math.max(0, poached);
        if (this.state == State.DESTROYED) {
            this.stock = 0.0;
            this.regenPerDay = 0.0;
        }
    }

    // ---------------------------------------------------------------- 破坏

    /**
     * 毁掉这处产地。
     *
     * @param cause 可调查的原因（{@code "will:daohen_scramble"} / {@code "player:uav"} …）
     */
    public boolean destroy(String cause, long now) {
        if (state == State.DESTROYED) {
            return false;
        }
        state = State.DESTROYED;
        stock = 0.0;
        regenPerDay = 0.0;
        lastCause = cause == null ? "unknown" : cause;
        lastChangeAt = now;
        return true;
    }

    /** 天意搅乱道痕：储量被打散，恢复变慢（不是立刻全毁）。 */
    public boolean scramble(double ratio, long now) {
        if (state == State.DESTROYED) {
            return false;
        }
        double r = Knowledge.clamp(ratio, 0.0, 1.0);
        stock = stock * (1.0 - r);
        regenPerDay *= (1.0 - r * 0.6);
        lastCause = "will:daohen_scramble";
        lastChangeAt = now;
        if (stock < 1.0 && regenPerDay <= 0.01) {
            state = State.DEPLETED;
        }
        return true;
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "Site{%s %s %s stock=%.1f/%.0f regen=%.2f/d %s owner=%s yields=%d poach=%d cause=%s}",
                id, kind, dim, stock, maxStock, regenPerDay, state,
                owned() ? owner.substring(0, 8) : "-", yields, poached, lastCause);
    }

    private static String requireRegistryId(String id) {
        if (id == null || id.indexOf(':') <= 0) {
            throw new IllegalArgumentException("资源产地必须用完整注册 ID：" + id);
        }
        return id.toLowerCase(Locale.ROOT);
    }

    // ================================================================ 产地簿

    /** 产地簿：管理全世界的产地。 */
    public static final class Book {
        /** 全世界同时存在的产地上限（防止无限膨胀）。 */
        public static final int MAX_SITES = 64;

        private final Map<String, ResourceSite> byId = new LinkedHashMap<>();

        public ResourceSite add(ResourceSite site) {
            if (byId.size() >= MAX_SITES && !byId.containsKey(site.id)) {
                // 满了就先淘汰一处已被毁的
                byId.values().stream()
                        .filter(s -> s.state() == State.DESTROYED)
                        .findFirst()
                        .ifPresent(s -> byId.remove(s.id));
                if (byId.size() >= MAX_SITES) {
                    return null;
                }
            }
            byId.put(site.id, site);
            return site;
        }

        public ResourceSite get(String id) {
            return byId.get(id);
        }

        public ResourceSite at(String dim, long pos, double tolerance) {
            ResourceSite best = null;
            double bestD = Double.MAX_VALUE;
            for (ResourceSite s : byId.values()) {
                if (!s.dim.equals(dim)) {
                    continue;
                }
                double d = distance(s.pos, pos);
                if (d <= Math.max(tolerance, s.radius) && d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
            return best;
        }

        /** 该维度里未毁的产地。 */
        public List<ResourceSite> active(String dim) {
            List<ResourceSite> out = new ArrayList<>();
            for (ResourceSite s : byId.values()) {
                if (s.dim.equals(dim) && s.state() != State.DESTROYED) {
                    out.add(s);
                }
            }
            return out;
        }

        public List<ResourceSite> ownedBy(String who) {
            List<ResourceSite> out = new ArrayList<>();
            for (ResourceSite s : byId.values()) {
                if (s.ownedBy(who)) {
                    out.add(s);
                }
            }
            return out;
        }

        /** 推进所有产地的恢复。 */
        public void regen(double gameDays) {
            for (ResourceSite s : byId.values()) {
                s.regen(gameDays);
            }
        }

        /** 天意在这个区域搅乱道痕：返回被影响的产地数。 */
        public int scrambleNear(String dim, long pos, double radius, double ratio, long now) {
            int hit = 0;
            for (ResourceSite s : byId.values()) {
                if (!s.dim.equals(dim) || s.state() == State.DESTROYED) {
                    continue;
                }
                if (distance(s.pos, pos) <= radius + s.radius) {
                    if (s.scramble(ratio, now)) {
                        hit++;
                    }
                }
            }
            return hit;
        }

        public List<ResourceSite> all() {
            return new ArrayList<>(byId.values());
        }

        public int size() {
            return byId.size();
        }

        public int countState(State st) {
            int n = 0;
            for (ResourceSite s : byId.values()) {
                if (s.state() == st) {
                    n++;
                }
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
