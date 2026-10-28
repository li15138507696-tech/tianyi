package net.guzhenren.tianfu;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 事件注册（通过 {@code NeoForge.EVENT_BUS.register(TianfuEvents.class)}）。 */
public final class TianfuEvents {
    private TianfuEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TianfuEffects.onTick(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TianfuOpening.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TianfuOpening.onClone(player, event.getOriginal());
            TianfuCombat.copyCooldownOnClone(player, event.getOriginal());
            TianfuCombat.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        TianfuCombat.onIncomingDamage(event);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        TianfuSlay.onDeath(event);
        // 清理「X烛」节流记录，避免实体离场后记录长期残留
        TianfuZhuCombat.forget(event.getEntity());
        // 玩家死亡：清掉他的装饰实体（否则会留在死亡点）
        if (event.getEntity() instanceof ServerPlayer sp) {
            TianfuVfxEntity.forget(sp);
            TianfuCombat.forget(sp.getUUID());
        }
    }

    /** 玩家下线：清掉装饰实体，防止在世界里留下孤儿实体。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TianfuVfxEntity.forget(player);
            TianfuCombat.forget(player.getUUID());
        }
    }

    /** 换维度：旧维度的装饰实体不会跟着走，必须清掉。 */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TianfuVfxEntity.forget(player);
        }
    }
}
