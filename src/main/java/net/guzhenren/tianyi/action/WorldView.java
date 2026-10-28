package net.guzhenren.tianyi.action;

/**
 * 干预执行时的世界视图 —— <b>纯接口</b>，不 import 任何 Minecraft 类型。
 *
 * <h2>为什么用接口而不是直接把 ServerLevel 传进去</h2>
 * 天意的判断必须能离线测试（{@code _pack/run_tests.py} 不加载 Minecraft）。
 * 只要执行器只依赖这个接口，测试里就能塞一个假实现，把「渠道被切断」「商队被截」
 * 「执行者已死」这些分支真的跑一遍，而不是靠读源码说有这个分支。
 *
 * <p>Minecraft 侧的实现在 {@code net.guzhenren.tianyi.world.LiveWorld}，
 * 它持有 {@code ServerLevel} 并把桥接调用包在接口后面。
 */
public interface WorldView {
    /** 当前游戏时间（刻）。 */
    long now();

    /** 维度全名，如 {@code "minecraft:overworld"}。 */
    String dim();

    /** 目标位置打包值（{@code BlockPos.asLong()}），无位置用 {@code Long.MIN_VALUE}。 */
    long pos();

    /**
     * 天意在这一点上的观测触达（0..1）。
     *
     * <p>载体被清、被遮蔽、被隔绝时返回 0 —— 执行器据此判 {@link ActResult.Status#BLOCKED}。
     */
    double reach();

    /**
     * 这条渠道通不通。
     *
     * @param channel 渠道名（{@code "carrier"} / {@code "journey"} / {@code "faction"} /
     *                {@code "npc"} / {@code "trade"}）
     */
    boolean channelOpen(String channel);

    /** 需要消耗的干预预算（0..1）。 */
    double budget();

    /** 目标身份 UUID 字符串；无目标返回空串。 */
    String targetId();

    /** 执行者 id：居民用 UUID，势力用 key。 */
    String executorId();
}
