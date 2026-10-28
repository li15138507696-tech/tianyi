package net.guzhenren.daotu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 事件注册（通过 {@code NeoForge.EVENT_BUS.register(DaotuEvents.class)}）。
 *
 * <p><b>v1.4.0</b>：觉醒系统删除后，原 {@code DaotuCombat} 的血战不退 / 杀神一击
 * 一并移除；战斗侧改由 {@link DaotuCombatEffects} 承接并入的词条效果
 * （暴击 / 闪避 / 减伤 / 吸血 / 增伤）。
 */
public final class DaotuEvents {
    private DaotuEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DaotuRuntime.onTick(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DaotuOpening.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getOriginal() instanceof ServerPlayer original) {
            DaotuOpening.onClone(player, original);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // 词条效果：闪避 + 减伤
        DaotuCombatEffects.onIncomingDamage(event);
        // 受伤历练：被打也给一点，鼓励硬碰硬而非永远躲着
        if (event.getEntity() instanceof ServerPlayer sp && !sp.level().isClientSide()) {
            DaotuLiLian.onHurt(sp, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // 词条效果：暴击 + 残血/满血增伤
        DaotuCombatEffects.onLivingDamage(event);
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        // 词条效果：吸血（必须在 Post，Pre 时还不知道最终扣了多少血）
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            DaotuCombatEffects.healAfterDamage(sp, event.getNewDamage());
        }
    }

    /** 击杀历练：生物死亡时，若最后一击来自玩家则结算。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer) {
            return; // 玩家死亡不算「击杀历练」
        }
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            DaotuLiLian.onKill(killer, victim);
        }
    }
}
