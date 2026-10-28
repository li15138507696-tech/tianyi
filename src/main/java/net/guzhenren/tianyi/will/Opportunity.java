package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 机缘：天意扶持的<b>世界中的真实机会</b>。
 *
 * <h2>三条硬约束</h2>
 * <ol>
 *   <li><b>不许凭空塞背包。</b>每个机会都带 {@link #origin} —— 它在世界里是怎么形成的
 *       （哪个区块的资源成熟了、哪条线索因为某人死了而变得可发现、哪个势力因为实际
 *       冲突被拖住）。{@link Forge} 只产出「有出处」的机会。</li>
 *   <li><b>可以被截取。</b>{@link #claim} 对任何主体开放：天意想给 A 的机会，B 先到就是 B 的。
 *       这才是机缘，不是发奖励。</li>
 *   <li><b>可以是诱饵。</b>{@link #bait} 标记的机会同时是后续布局的第一步；
 *       但占比受 {@link WillConfig#BAIT_RATIO_CAP} 约束 —— 不能让所有机会都是陷阱。</li>
 * </ol>
 */
public final class Opportunity {
    private Opportunity() {
    }

    /** 机会的种类。每一种都必须落到具体世界机制上。 */
    public enum Kind {
        /** 资源成熟/出现：矿脉、灵植、蛊材在真实区块里长成。 */
        RESOURCE_MATURITY,
        /** 传承线索：因为某个持有者死亡/失窃而变得可发现。 */
        INHERITANCE_LEAD,
        /** NPC 提供信息或协助：NPC 基于自身利益主动行动。 */
        NPC_ASSIST,
        /** 敌对势力因实际冲突被牵制。 */
        FACTION_PIN,
        /** 成长窗口：条件齐备时出现的一段有利时段。 */
        GROWTH_WINDOW
    }

    /** 一个机会。 */
    public static final class Offer {
        public final String id;
        public final Kind kind;
        /** 天意「打算」给谁；空字符串表示公开机会，谁都能捡。 */
        public final String intended;
        public final String dim;
        public final long pos;
        /** 形成过程（可调查的出处）。 */
        public final String origin;
        public final long createdAt;
        public final long matureAt;
        public final long expiresAt;
        /** 是否同时是诱饵（后续布局的第一步）。 */
        public final boolean bait;
        /** 诱饵指向的布局 id。 */
        public final String ployId;
        public String claimedBy = "";
        public long claimedAt = 0L;
        public boolean consumed;
        /**
         * 是否已经兑现成世界里的实物（产地）。
         *
         * <p>B01 修复的关键字段：申请只排队，成熟后由调度器兑现；
         * <b>只有兑现成功才置 true</b>，所以重复调度不会产出第二处产地。
         */
        public boolean settled;
        public long settledAt = 0L;

        public Offer(String id, Kind kind, String intended, String dim, long pos, String origin,
                     long createdAt, long matureAt, long expiresAt, boolean bait, String ployId) {
            this.id = id;
            this.kind = kind;
            this.intended = intended == null || intended.isBlank() ? "" : intended;
            this.dim = dim == null ? "" : dim;
            this.pos = pos;
            this.origin = origin == null ? "" : origin;
            this.createdAt = createdAt;
            this.matureAt = matureAt;
            this.expiresAt = expiresAt;
            this.bait = bait;
            this.ployId = ployId == null ? "" : ployId;
        }

        public boolean mature(long now) {
            return now >= matureAt;
        }

        public boolean expired(long now) {
            return now > expiresAt;
        }

        public boolean available(long now) {
            return !consumed && claimedBy.isEmpty() && mature(now) && !expired(now);
        }

        /**
         * 还没兑现、已成熟、也没过期 → 该被调度器兑现了。
         *
         * <p>B01：申请与兑现分开。{@link #due} 只回答"该不该兑现"，
         * 真正调用世界侧应在兑现成功后再 {@link #markSettled}。
         */
        public boolean due(long now) {
            return !settled && !expired(now) && mature(now);
        }

        /**
         * 标记已兑现。
         *
         * @return {@code false} 表示之前已兑现过（幂等：不会产出第二份）
         */
        public boolean markSettled(long now) {
            if (settled) {
                return false;
            }
            settled = true;
            settledAt = now;
            return true;
        }

        /**
         * 领取。任何人都能截取 —— 这是「机缘可以被别人截取」的实现点。
         *
         * @return {@code true} 表示领取成功
         */
        public boolean claim(String claimant, long now) {
            if (claimant == null || claimant.isBlank()) {
                return false;
            }
            if (!available(now)) {
                return false;
            }
            claimedBy = Knowledge.requireIdentity(claimant);
            claimedAt = now;
            consumed = true;
            return true;
        }

        /** 是否被「非预期对象」截取（用于天意的因果记录，不通知玩家）。 */
        public boolean intercepted() {
            return consumed && !intended.isEmpty() && !intended.equals(claimedBy);
        }

        public String describe() {
            return String.format(Locale.ROOT, "Offer{%s %s @%s %s mature+%d bait=%s %s}",
                    id, kind, dim, origin, matureAt - createdAt, bait,
                    consumed ? ("claimed by " + claimedBy) : "open");
        }
    }

    /**
     * 机会铸造：**只能**从世界状态里推出机会，不能凭空生成。
     */
    public static final class Forge {
        private final Map<String, Offer> offers = new LinkedHashMap<>();

        /**
         * 依据评估结果与可用渠道提出机会。
         *
         * @param channel 只允许 {@link Assessment.Channel#RESOURCE}、
         *                {@code HERITAGE}、{@code RUMOR}、{@code FACTION}
         *                —— 扶持不走灾劫
         */
        public List<Offer> propose(Assessment.Report r, String beneficiaryId,
                                   Assessment.Channel channel, Assessment.Actor beneficiary,
                                   long now, String dim, long pos, String origin) {
            return propose(r, beneficiaryId, channel, beneficiary, now, dim, pos, origin,
                    WillConfig.BAIT_SIN_THRESHOLD);
        }

        /**
         * 3.14：诱饵门限可随天意强度下调（门限越低，诱饵越多）。
         *
         * <p>注意诱饵占比<b>仍然</b>受 {@link WillConfig#BAIT_RATIO_CAP} 硬约束 ——
         * 档位只能让诱饵变多，不能把全部机缘都变成陷阱。
         * 传 {@code baitSinThreshold >= 1.0} 时永不产生诱饵（档位 0 就是这么关掉的）。
         *
         * @param baitSinThreshold {@code |sin(...)| > 该门限} 才算诱饵
         */
        public List<Offer> propose(Assessment.Report r, String beneficiaryId,
                                   Assessment.Channel channel, Assessment.Actor beneficiary,
                                   long now, String dim, long pos, String origin,
                                   double baitSinThreshold) {
            List<Offer> out = new ArrayList<>();
            if (channel == null || beneficiary == null) {
                return out;
            }
            Kind kind = switch (channel) {
                case RESOURCE -> Kind.RESOURCE_MATURITY;
                case HERITAGE -> Kind.INHERITANCE_LEAD;
                case RUMOR -> Kind.NPC_ASSIST;
                case FACTION -> Kind.FACTION_PIN;
                case CARRIER -> Kind.GROWTH_WINDOW;
                // 灾劫/兽群不是「机缘」，锻造层拒绝
                case CALAMITY, BEAST -> null;
            };
            if (kind == null) {
                return out;
            }
            // 气运只影响「这个机会多大程度上能被他碰到」，不影响天意的喜恶
            double luck = beneficiary.effectiveLuck();
            double luckFactor = 1.0 + Knowledge.clamp(luck / 200.0, -0.5, 0.5);
            long gest = (long) (WillConfig.OPPORTUNITY_MIN_GESTATION
                    + (WillConfig.OPPORTUNITY_MAX_GESTATION - WillConfig.OPPORTUNITY_MIN_GESTATION)
                    * (1.0 - Knowledge.clamp(beneficiary.confidence, 0.0, 1.0)));
            gest = (long) (gest / Math.max(0.35, luckFactor));

            String id = "op-" + now + "-" + kind.name().toLowerCase(Locale.ROOT)
                    + "-" + offers.size();
            boolean bait = false;
            String ploy = "";
            double baitRatio = baitRatio(now);
            if (baitRatio < WillConfig.BAIT_RATIO_CAP
                    && Math.abs(Math.sin(now * 0.0007 + pos)) > baitSinThreshold) {
                bait = true; // 少数机会是诱饵，比例受硬上限约束
            }
            Offer o = new Offer(id, kind, beneficiaryId, dim, pos, origin, now,
                    now + gest, now + WillConfig.OPPORTUNITY_TTL, bait, ploy);
            offers.put(id, o);
            out.add(o);
            return out;
        }

        private double baitRatio(long now) {
            if (offers.isEmpty()) {
                return 0.0;
            }
            long baits = offers.values().stream().filter(o -> o.bait).count();
            return (double) baits / offers.size();
        }

        /** 领取（任何主体都能调）。 */
        public boolean claim(String offerId, String claimant, long now) {
            Offer o = offers.get(offerId);
            return o != null && o.claim(claimant, now);
        }

        public Offer get(String id) {
            return offers.get(id);
        }

        public void tick(long now) {
            offers.entrySet().removeIf(e -> e.getValue().expired(now)
                    && now - e.getValue().expiresAt > WillConfig.OPPORTUNITY_TTL);
        }

        public List<Offer> available(long now) {
            List<Offer> out = new ArrayList<>();
            for (Offer o : offers.values()) {
                if (o.available(now)) {
                    out.add(o);
                }
            }
            return out;
        }

        /** 该兑现的机缘（已成熟、未兑现、未过期）。 */
        public List<Offer> due(long now) {
            List<Offer> out = new ArrayList<>();
            for (Offer o : offers.values()) {
                if (o.due(now)) {
                    out.add(o);
                }
            }
            return out;
        }

        /** 某个受益人身上「已申请但还没成熟」的机缘数（限流用）。 */
        public int pendingFor(String beneficiary, long now) {
            int n = 0;
            for (Offer o : offers.values()) {
                if (o.intended.equals(beneficiary) && !o.settled && !o.expired(now)) {
                    n++;
                }
            }
            return n;
        }

        /** 已兑现的数量（诊断用）。 */
        public int settledCount() {
            int n = 0;
            for (Offer o : offers.values()) {
                if (o.settled) {
                    n++;
                }
            }
            return n;
        }

        public List<Offer> active() {
            return new ArrayList<>(offers.values());
        }

        /**
         * 从存档恢复一条机缘（B01：申请要能过重启，否则一重启就没法兑现了）。
         */
        public void restore(Offer o) {
            if (o != null) {
                offers.put(o.id, o);
            }
        }

        public int interceptedCount() {
            int n = 0;
            for (Offer o : offers.values()) {
                if (o.intercepted()) {
                    n++;
                }
            }
            return n;
        }
    }
}
