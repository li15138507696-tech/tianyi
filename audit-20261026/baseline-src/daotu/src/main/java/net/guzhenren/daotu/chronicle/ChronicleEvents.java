// Path: src/main/java/net/guzhenren/daotu/chronicle/ChronicleEvents.java
package net.guzhenren.daotu.chronicle;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 事件接线；客户端事件不触发任何存档。 */
public final class ChronicleEvents {
    private final ChronicleCoordinator coordinator;
    private final ChronicleRepository repository;

    /** 仅注入与主类相同的协调器和仓储。 */
    public ChronicleEvents(ChronicleCoordinator coordinator, ChronicleRepository repository) {
        this.coordinator = coordinator;
        this.repository = repository;
    }

    /** @param event 登录事件；仅服务端玩家建立基线，不补发历史条目，不弹窗。 */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            coordinator.observe(player);
        }
    }

    /** @param event Post tick 事件；只每 20 tick 采样一次，适配器不可用时停止。 */
    @SubscribeEvent
    public void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % ChronicleTables.SAMPLE_PERIOD_TICKS != 0) {
            return;
        }
        coordinator.observe(player);
    }

    /** @param event 克隆事件；显式深拷贝独立存档，不把普通死亡当作转世。 */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer replacement) {
            repository.copyForClone(original, replacement);
        }
    }
}