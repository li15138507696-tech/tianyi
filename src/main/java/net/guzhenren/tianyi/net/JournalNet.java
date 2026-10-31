package net.guzhenren.tianyi.net;

import net.guzhenren.tianyi.TianyiMod;
import net.guzhenren.tianyi.client.JournalClientHandler;
import net.guzhenren.tianyi.world.JournalStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 见闻录的网络出口。
 *
 * <h2>数据流（服务端是唯一可信方）</h2>
 * <pre>
 * 真实事件 → JournalStore 按玩家记档 → sync(player) 构造可见范围 → 下发 → 客户端只负责画
 * </pre>
 *
 * <ul>
 *   <li><b>只发这个玩家的</b>：{@link JournalStore#load} 用的是玩家自己的 persistent data，
 *       世界总账根本不在这个包的构造路径上。</li>
 *   <li><b>动作一律重校验</b>：客户端只能提交「动作 + 条目 id」，
 *       服务端按玩家重新读档，条目必须存在且属于他，才改。</li>
 *   <li><b>有频率限制</b>：{@link #SYNC_MIN_GAP} 刻内重复请求只响应一次，
 *       防止刷请求打爆服务端。</li>
 * </ul>
 */
public final class JournalNet {
    private JournalNet() {
    }

    /** 两次同步之间的最小间隔（刻）。 */
    public static final long SYNC_MIN_GAP = 10L;

    /** 每个玩家上次同步的时刻。 */
    private static final java.util.Map<java.util.UUID, Long> LAST_SYNC =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Set<java.util.UUID> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void tick(net.minecraft.server.MinecraftServer server) {
        // 3.14.1：每 tick 都会进来，绝大多数 tick 没有待发对象。空集直接返回，
        // 省掉 Set.copyOf 的每刻一次分配（20 次/秒）。PENDING 非空时行为完全不变。
        if (PENDING.isEmpty()) return;
        for (java.util.UUID id : java.util.Set.copyOf(PENDING)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) forget(id); else sync(player);
        }
    }
    public static void clear() { LAST_SYNC.clear(); PENDING.clear(); }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(EncounterSyncPayload.TYPE, EncounterSyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> JournalClientHandler.onEncounterSync(payload)));
        registrar.playToServer(EncounterActionPayload.TYPE, EncounterActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp)
                        net.guzhenren.tianyi.world.EncounterService.action(sp, payload);
                }));
        registrar.playToClient(JournalSyncPayload.TYPE, JournalSyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> JournalClientHandler.onSync(payload)));
        registrar.playToServer(JournalActionPayload.TYPE, JournalActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        handleAction(sp, payload);
                    }
                }));
    }

    /** 客户端把动作发给服务端（只发动作与 id，不发内容）。 */
    public static void send(JournalActionPayload.Action action, String entryId) {
        PacketDistributor.sendToServer(new JournalActionPayload(action,
                entryId == null ? "" : entryId));
    }

    /** 服务端：把某个玩家的可见条目下发。 */
    public static boolean sync(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        long now = player.getServer().overworld().getGameTime();
        Long last = LAST_SYNC.get(player.getUUID());
        if (last != null && now >= last && now - last < SYNC_MIN_GAP) {
            PENDING.add(player.getUUID());
            return false;
        }
        PENDING.remove(player.getUUID());
        LAST_SYNC.put(player.getUUID(), now);
        var journal = JournalStore.load(player);
        PacketDistributor.sendToPlayer(player,
                JournalSyncPayload.of(journal, now));
        return true;
    }

    /** 服务端：处理面板动作，改完回一次同步。 */
    public static void handleAction(ServerPlayer player, JournalActionPayload payload) {
        if (payload == null) {
            sync(player);
            return;
        }
        if (payload.action() == JournalActionPayload.Action.REQUEST) {
            if (sync(player)) net.guzhenren.tianyi.world.EncounterService.sync(player);
            return;
        }
        long now = player.getServer().overworld().getGameTime();
        boolean changed = switch (payload.action()) {
            case FAVORITE -> JournalStore.handle(player, JournalStore.Action.FAVORITE,
                    payload.entryId(), now);
            case DOUBTFUL -> JournalStore.handle(player, JournalStore.Action.DOUBTFUL,
                    payload.entryId(), now);
            case TOGGLE_CASE -> JournalStore.handle(player, JournalStore.Action.TOGGLE_CASE,
                    payload.entryId(), now);
            case REQUEST -> false;
        };
        if (changed) {
            // 冷却清零，保证玩家马上能看到自己的标注生效
            sync(player);
        }
    }

    /** 登录/退出时清掉限流状态。 */
    public static void forget(java.util.UUID id) {
        LAST_SYNC.remove(id);
        PENDING.remove(id);
    }

    /** 诊断用。 */
    public static String status() {
        return "JournalNet{" + TianyiMod.MODID + " syncGap=" + SYNC_MIN_GAP
                + " tracked=" + LAST_SYNC.size() + "}";
    }
}
