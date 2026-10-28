package net.guzhenren.tianyi.will;

import java.util.Locale;

/**
 * 天意的一步意图。
 *
 * <p><b>这不是给玩家看的通知。</b>意图只存在于服务端内存与内部因果记录里，
 * 玩家能感受到的是它造成的世界过程（资源成熟了、兽群来了、有人上门了），
 * 没有「天意施加了压制」这种弹出文字。
 */
public final class Intent {
    /** 意图种类。 */
    public enum Kind {
        /** 扶持：给出真实机会（资源成熟、传承线索、NPC 协助、势力牵制）。 */
        SUPPORT,
        /** 压制：用世界手段抬高阻力（兽群、人劫、灾劫、势力敌意）。 */
        SUPPRESS,
        /** 观测：只派人/派载体去看，不动手。 */
        OBSERVE,
        /** 收手：本轮退场。 */
        WITHDRAW,
        /** 维持天庭/封锁（宿命完整时的特别动议）。 */
        HOLD_HEAVEN,
        /** 布局：长期多步计划（由 {@link PloyBook} 托管）。 */
        PLOY
    }

    /** 天意对某个主体当前的态度。玩家不可见。 */
    public enum Posture {
        SUPPORT, SUPPRESS, OBSERVE, LAISSEZ_FAIRE
    }

    public final Kind kind;
    public final String target;
    public final String reason;
    public final Posture posture;
    public final double cost;
    public final Assessment.Channel channel;

    public Intent(Kind kind, String target, String reason, Posture posture, double cost) {
        this(kind, target, reason, posture, cost, null);
    }

    public Intent(Kind kind, String target, String reason, Posture posture, double cost,
                  Assessment.Channel channel) {
        this.kind = kind;
        this.target = target == null ? "" : target;
        this.reason = reason == null ? "" : reason;
        this.posture = posture;
        this.cost = cost;
        this.channel = channel;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Intent{%s %s cost=%.2f ch=%s why=%s}",
                kind, target, cost, channel, reason);
    }
}
