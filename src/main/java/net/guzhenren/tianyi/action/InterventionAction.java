package net.guzhenren.tianyi.action;

/**
 * 一次具体干预。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@link #id()} 必须唯一且稳定（用于去重、写日志、持久化）；</li>
 *   <li>{@link #channel()} 声明它依赖哪条渠道 —— 渠道不通就必须返回
 *       {@link ActResult.Status#BLOCKED}，<b>不能假装改成「降低一点数值」</b>；</li>
 *   <li>{@link #execute(WorldView)} 必须反映真实结果。桥接不可用就 {@code FAILED}，
 *       条件不成立就 {@code DENIED}，没到时机就 {@code DEFERRED}。</li>
 * </ul>
 *
 * <p>本接口是纯类型；实现类允许放在 {@code action.impl} 并下转
 * {@code world.LiveWorld} 去调本体桥接。判断层只依赖本接口。
 */
public interface InterventionAction {
    /** 唯一 id，如 {@code "npc.investigate"}。 */
    String id();

    /** 依赖的渠道名，与 {@link WorldView#channelOpen(String)} 对应。 */
    String channel();

    /** 基础干预预算消耗（0..1）。 */
    double cost();

    /**
     * 现在条件成不成立（不消耗、不执行、可反复调用）。
     *
     * <p>返回 {@code false} 时调用方应记 {@code DENIED}，而不是硬跑。
     */
    boolean applicable(WorldView view);

    /** 真正执行。实现方必须自己兜住本体异常，不得让它穿透 tick。 */
    ActResult execute(WorldView view);
}
