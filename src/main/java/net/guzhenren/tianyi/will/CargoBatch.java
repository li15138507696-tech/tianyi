package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 货物批次：一次真实的物流运输，以及它的<b>幂等结算</b>。
 *
 * <h2>为什么需要「批次」</h2>
 * 产地出货如果直接刷在玩家脚下，就没有运输、没有路线、没有可被截断的环节 ——
 * 天意也就失去了「借人之手、断人之货」这种手段。批次把「产出」和「到手」拆开：
 * 货物先从产地<b>预留</b>出来，经一段路程，到点才结算。
 *
 * <h2>幂等结算是本类的核心纪律</h2>
 * 同一批货只能结算一次。{@link #settle} 第二次调用返回空清单，
 * 而且这个「已结算」状态必须能<b>过重启</b>（{@link Book} 记在结算簿里，
 * 由 {@code WillState} 落盘）。这一条防的是最容易出的严重 bug：
 * 玩家在结算瞬间重连/重启，货被发两遍。
 *
 * <h2>三种结局</h2>
 * <ul>
 *   <li>{@link State#DELIVERED} 送达并结算；</li>
 *   <li>{@link State#BLOCKED} 路线被切断（载体被清、护送人死了、目标离线），
 *       货物<b>退回产地</b>，不吞不吞；</li>
 *   <li>{@link State#LOST} 被劫（有人中途截下），货物归劫货的人。</li>
 * </ul>
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class CargoBatch {
    /** 状态。 */
    public enum State {
        /** 在途。 */
        IN_TRANSIT,
        /** 已送达并结算。 */
        DELIVERED,
        /** 路线被切断，货已退回。 */
        BLOCKED,
        /** 被劫。 */
        LOST
    }

    public final String id;
    /** 起运点（产地）。 */
    public final String fromDim;
    public final long fromPos;
    /** 目的地（收货人所在处）。 */
    public final String toDim;
    public final long toPos;
    /** 交货对象（完整 UUID）。 */
    public final String consignee;
    public final long createdAt;
    public final long arriveAt;

    /** 货物清单：注册 ID → 数量。 */
    private final Map<String, Integer> cargo = new LinkedHashMap<>();
    private State state = State.IN_TRANSIT;
    private long settledAt = 0L;
    private String cause = "";

    public CargoBatch(String id, String fromDim, long fromPos, String toDim, long toPos,
                      String consignee, long createdAt, long arriveAt) {
        this.id = id;
        this.fromDim = fromDim == null ? "" : fromDim;
        this.fromPos = fromPos;
        this.toDim = toDim == null ? "" : toDim;
        this.toPos = toPos;
        this.consignee = consignee == null || consignee.isBlank()
                ? "" : Knowledge.requireIdentity(consignee);
        this.createdAt = createdAt;
        this.arriveAt = Math.max(createdAt, arriveAt);
    }

    // ---------------------------------------------------------------- 装配

    /** 装货（只接受完整注册 ID；数量必须为正）。 */
    public CargoBatch load(String registryId, int count) {
        if (registryId == null || registryId.indexOf(':') <= 0) {
            throw new IllegalArgumentException("货物必须是完整注册 ID：" + registryId);
        }
        if (count <= 0) {
            return this;
        }
        cargo.merge(registryId.toLowerCase(Locale.ROOT), count, Integer::sum);
        return this;
    }

    public Map<String, Integer> manifest() {
        return new LinkedHashMap<>(cargo);
    }

    public int totalCount() {
        int n = 0;
        for (int v : cargo.values()) {
            n += v;
        }
        return n;
    }

    public boolean empty() {
        return cargo.isEmpty();
    }

    public State state() {
        return state;
    }

    public String cause() {
        return cause;
    }

    public long settledAt() {
        return settledAt;
    }

    public boolean arrived(long now) {
        return now >= arriveAt;
    }

    // ---------------------------------------------------------------- 结算

    /**
     * 到点结算。
     *
     * <p><b>幂等</b>：只有第一次会返回货；之后再调都是空清单。
     * 这是防「重连/重启导致发两遍货」的唯一正确做法。
     *
     * @return 本次真正交付的货物（空表示什么都没发生）
     */
    public Map<String, Integer> settle(long now, String causeTag) {
        if (state != State.IN_TRANSIT || !arrived(now)) {
            return Map.of();
        }
        state = State.DELIVERED;
        settledAt = now;
        cause = causeTag == null ? "delivered" : causeTag;
        Map<String, Integer> out = new LinkedHashMap<>(cargo);
        cargo.clear();
        return out;
    }

    /** 路线被切断：货退回，之后也不会再结算。 */
    public Map<String, Integer> block(long now, String why) {
        if (state != State.IN_TRANSIT) {
            return Map.of();
        }
        state = State.BLOCKED;
        settledAt = now;
        cause = why == null ? "blocked" : why;
        Map<String, Integer> back = new LinkedHashMap<>(cargo);
        cargo.clear();
        return back;
    }

    /**
     * 被劫：货归劫货者。
     *
     * @return 被劫走的货
     */
    public Map<String, Integer> lose(long now, String byWhom) {
        if (state != State.IN_TRANSIT) {
            return Map.of();
        }
        state = State.LOST;
        settledAt = now;
        cause = "lost-to:" + (byWhom == null ? "unknown" : byWhom);
        Map<String, Integer> out = new LinkedHashMap<>(cargo);
        cargo.clear();
        return out;
    }

    public String describe() {
        return String.format(Locale.ROOT, "Batch{%s %s→%s %s 货=%d件 到=%d %s}",
                id, fromDim, toDim, state, totalCount(), arriveAt, cause);
    }

    /** 从存档恢复（服务端重启）。 */
    public void restore(Map<String, Integer> goods, State st, long settledAtIn, String causeIn) {
        cargo.clear();
        if (goods != null) {
            goods.forEach((k, v) -> {
                if (k != null && k.indexOf(':') > 0 && v != null && v > 0) {
                    cargo.put(k.toLowerCase(Locale.ROOT), v);
                }
            });
        }
        this.state = st == null ? State.IN_TRANSIT : st;
        this.settledAt = settledAtIn;
        this.cause = causeIn == null ? "" : causeIn;
    }

    // ================================================================ 批次簿

    /**
     * 批次簿 + 结算簿。
     *
     * <p>结算簿（{@code settled} 集合）是幂等的<b>持久依据</b>：
     * 即使批次对象本身在重启后重建，只要这个集合还在，就绝不会重复发货。
     */
    public static final class Book {
        /** 在途批次上限。 */
        public static final int MAX_IN_TRANSIT = 32;

        private final Map<String, CargoBatch> byId = new LinkedHashMap<>();
        /** 已结算的批次 id（幂等依据，必须持久化）。 */
        private final java.util.Set<String> settled = new java.util.LinkedHashSet<>();

        public CargoBatch open(String id, String fromDim, long fromPos, String toDim, long toPos,
                               String consignee, long now, long transitTicks) {
            if (byId.containsKey(id)) {
                return byId.get(id); // 同 id 不重建（调用方重试不会造出两批）
            }
            int inTransit = 0;
            for (CargoBatch b : byId.values()) {
                if (b.state() == State.IN_TRANSIT) {
                    inTransit++;
                }
            }
            if (inTransit >= MAX_IN_TRANSIT) {
                return null;
            }
            CargoBatch b = new CargoBatch(id, fromDim, fromPos, toDim, toPos, consignee,
                    now, now + Math.max(1L, transitTicks));
            byId.put(id, b);
            return b;
        }

        public CargoBatch get(String id) {
            return byId.get(id);
        }

        public boolean alreadySettled(String id) {
            return settled.contains(id);
        }

        /**
         * 推进所有在途批次并结算到点的。
         *
         * <p>返回「本次真正交付的货」列表；同一批次不会出现两次。
         */
        public List<Delivery> tick(long now, java.util.function.Predicate<String> consigneeOnline) {
            List<Delivery> out = new ArrayList<>();
            for (CargoBatch b : byId.values()) {
                if (b.state() != State.IN_TRANSIT) {
                    continue;
                }
                if (!b.arrived(now)) {
                    continue;
                }
                if (settled.contains(b.id)) {
                    // 防御：结算簿说已结过 → 本次绝不重复发
                    continue;
                }
                if (!b.consignee.isEmpty() && consigneeOnline != null
                        && !consigneeOnline.test(b.consignee)) {
                    // 收货人不在：路线断，货退回，不算送达
                    Map<String, Integer> back = b.block(now, "consignee-offline");
                    settled.add(b.id);
                    out.add(new Delivery(b, back, false));
                    continue;
                }
                Map<String, Integer> goods = b.settle(now, "delivered");
                if (goods.isEmpty()) {
                    continue;
                }
                settled.add(b.id);
                out.add(new Delivery(b, goods, true));
            }
            return out;
        }

        /** 记录一次「已经结算过」（供重启后重建时直接补齐结算簿）。 */
        public void markSettled(String id) {
            settled.add(id);
        }

        public int inTransitCount() {
            int n = 0;
            for (CargoBatch b : byId.values()) {
                if (b.state() == State.IN_TRANSIT) {
                    n++;
                }
            }
            return n;
        }

        public List<CargoBatch> all() {
            return new ArrayList<>(byId.values());
        }

        public java.util.Set<String> settledIds() {
            return new java.util.LinkedHashSet<>(settled);
        }

        /** 一次交付结果。 */
        public record Delivery(CargoBatch batch, Map<String, Integer> goods, boolean delivered) {
        }
    }
}
