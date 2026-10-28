package net.guzhenren.tianyi.world;

import net.guzhenren.tianyi.action.WorldView;
import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.fact.WorldFact;
import net.minecraft.server.level.ServerLevel;

/**
 * 服务端的 {@link WorldView}：把「这一维度此刻能不能出手」摊成几个纯读数。
 *
 * <h2>为什么不做成「传一个 ServerLevel 就完事」</h2>
 * 判断层（{@code action/impl}）要能在离线测试里被喂假视图，所以它只认接口。
 * 这个类就是接口与 {@link ServerLevel} 之间的唯一接缝：所有 Minecraft 与本体桥接的
 * 调用都收在这里，{@code action/impl} 只负责「拿这个视图问一句、然后照做」。
 *
 * <h2>渠道语义</h2>
 * 渠道不通就必须是 {@code BLOCKED}，不是「效果打个折」。所以每个渠道都对应一个
 * <b>真实存在的外部对象</b>：载体网络、势力账本、行程表、居民登记、商贸货架。
 * 对象在 → 渠道通；对象不在（本体没装、该维度还没建账、载体被清干净）→ 渠道不通。
 * 认不出的渠道名一律不通：宁可不动手，也不能因为拼错一个字符串就偷偷生效。
 */
public final class LiveWorld implements WorldView {

    private final ServerLevel level;
    private final long now;
    private final double reach;
    private final double budget;

    /** 目标位置打包值；默认「无名址」。 */
    private long pos = WorldFact.NO_POS;
    /** 目标身份；默认无目标。 */
    private String targetId = "";
    /** 执行者身份；默认无执行者。 */
    private String executorId = "";

    public LiveWorld(ServerLevel level, long now, double reach, double budget) {
        this.level = level;
        this.now = now;
        this.reach = reach < 0.0 ? 0.0 : Math.min(reach, 1.0);
        this.budget = Math.max(0.0, budget);
    }

    /** 供 {@code action/impl} 下转后调桥接；判断层看不到这个方法。 */
    public ServerLevel level() {
        return level;
    }

    /** 补上目标位置（返回自身，便于一次写完一个视图）。 */
    public LiveWorld pos(long packed) {
        this.pos = packed;
        return this;
    }

    public LiveWorld target(String id) {
        this.targetId = id == null ? "" : id;
        return this;
    }

    public LiveWorld executor(String id) {
        this.executorId = id == null ? "" : id;
        return this;
    }

    // ------------------------------------------------------------ WorldView

    @Override
    public long now() {
        return now;
    }

    @Override
    public String dim() {
        return level.dimension().location().toString();
    }

    @Override
    public long pos() {
        return pos;
    }

    @Override
    public double reach() {
        return reach;
    }

    @Override
    public boolean channelOpen(String channel) {
        return channelOpen(level, channel);
    }

    @Override
    public double budget() {
        return budget;
    }

    @Override
    public String targetId() {
        return targetId;
    }

    @Override
    public String executorId() {
        return executorId;
    }

    // ------------------------------------------------------------ 渠道

    /**
     * 这条渠道在这个维度上通不通。
     *
     * <p>任何异常都按「不通」处理：本体在别人的 tick 里抛错，不能顺着我们的调用栈
     * 传进服务端主循环——那会变成整个 tick 崩掉。
     */
    public static boolean channelOpen(ServerLevel level, String channel) {
        if (level == null || channel == null) {
            return false;
        }
        try {
            return switch (channel) {
                // 载体被清干净的维度：天意在这里既看不见也动不了手
                case "carrier" -> carrierReach(level) > 0.0;
                case "faction" -> GuzhenrenBridge.territory(level) != null;
                case "journey" -> journeyOpen(level);
                case "npc" -> GuzhenrenBridge.residents(level) != null;
                // 商贸渠道的实体就是本体那套价目/货币；货币取不到说明货架也没搭起来
                case "trade" -> GuzhenrenBridge.currency() != null;
                default -> false;
            };
        } catch (Throwable t) {
            return false;
        }
    }

    private static double carrierReach(ServerLevel level) {
        if (level.getServer() == null) {
            return 0.0;
        }
        return WillState.get(level.getServer()).carriers()
                .reachInDim(level.dimension().location().toString(), level.getGameTime());
    }

    /** 行程表对象在不在。用 {@code var} 承接，本类不 import 本体类型。 */
    private static boolean journeyOpen(ServerLevel level) {
        var territory = GuzhenrenBridge.territory(level);
        return territory != null && territory.journeys() != null;
    }
}
