package net.guzhenren.tianyi.action;

/**
 * 一次干预的<b>真实</b>结果。
 *
 * <h2>为什么必须区分五种终态</h2>
 * 天意可以失败，而且失败方式不同，后续决策完全不同：
 * <ul>
 *   <li>{@link Status#OK} —— 真的改了世界（补给进了、护送达成了、货到了）。</li>
 *   <li>{@link Status#DENIED} —— 条件不成立，<b>没动手</b>（信息不足、赔付不起、势力已灭）。</li>
 *   <li>{@link Status#BLOCKED} —— 想动手，但观测或干预渠道被玩家切断了
 *       （载体被清、商队被截、执行者被打退）。这是玩家破局的证据。</li>
 *   <li>{@link Status#FAILED} —— 动手了，本体侧返回失败（桥接不可用、目标已死）。</li>
 *   <li>{@link Status#DEFERRED} —— 还没到时机，本轮不动，下次再说（不是失败）。</li>
 * </ul>
 *
 * <p>这个类是纯数据结构，不 import 任何 Minecraft 类型，因此可以离线测试。
 */
public record ActResult(Status status, String actionId, double cost, String reason,
                        long atTick, String executor) {

    public enum Status {
        OK, DENIED, BLOCKED, FAILED, DEFERRED;

        /** 是否对世界产生了真实改动。 */
        public boolean changed() {
            return this == OK;
        }

        /** 是否说明「渠道被切断」——玩家破局的直接证据。 */
        public boolean blocked() {
            return this == BLOCKED;
        }
    }

    public ActResult {
        if (status == null) {
            status = Status.FAILED;
        }
        if (actionId == null) {
            actionId = "?";
        }
        if (reason == null) {
            reason = "";
        }
        if (executor == null) {
            executor = "";
        }
        if (cost < 0.0) {
            cost = 0.0;
        }
    }

    public static ActResult ok(String actionId, double cost, String reason, long atTick, String executor) {
        return new ActResult(Status.OK, actionId, cost, reason, atTick, executor);
    }

    public static ActResult denied(String actionId, String reason, long atTick, String executor) {
        return new ActResult(Status.DENIED, actionId, 0.0, reason, atTick, executor);
    }

    public static ActResult blocked(String actionId, String reason, long atTick, String executor) {
        return new ActResult(Status.BLOCKED, actionId, 0.0, reason, atTick, executor);
    }

    public static ActResult failed(String actionId, double cost, String reason, long atTick, String executor) {
        return new ActResult(Status.FAILED, actionId, cost, reason, atTick, executor);
    }

    public static ActResult deferred(String actionId, String reason, long atTick, String executor) {
        return new ActResult(Status.DEFERRED, actionId, 0.0, reason, atTick, executor);
    }

    public boolean succeeded() {
        return status == Status.OK;
    }

    /** 失败（含被阻断）后是否应该计入冷却。 */
    public boolean penalise() {
        return status == Status.FAILED || status == Status.BLOCKED;
    }

    /** 一行式，进服务端日志 / 因果记录。 */
    public String line() {
        return actionId + " " + status + (executor.isEmpty() ? "" : "@" + executor)
                + " cost=" + String.format(java.util.Locale.ROOT, "%.3f", cost)
                + (reason.isEmpty() ? "" : " why=" + reason);
    }
}
