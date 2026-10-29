package net.guzhenren.tianyi;

import net.guzhenren.tianyi.world.LegacyRetire;
import net.guzhenren.tianyi.world.Observation;
import net.guzhenren.tianyi.world.WillState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 事件接线：只做转发，业务在 {@link net.guzhenren.tianyi.world.WillServer}。
 *
 * <p>刻意<b>不</b>每 tick 扫世界：tick 事件里只累加计数，
 * 真正的决策/推进/清理各有自己的周期（见 {@code WillServer} 的类注释）。
 */
public final class TianyiEvents {
    private TianyiEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        net.guzhenren.tianyi.net.JournalNet.clear();
        // 升仙世界账本：启动即加载，重启后保留会话阶段与不可逆碎窍记录。
        net.guzhenren.tianyi.world.AscensionStore.get(event.getServer());
        net.guzhenren.tianyi.world.EncounterService.started();
        net.guzhenren.tianyi.world.FiniteSiteService.started();
        TianyiMod.SERVER.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TianyiMod.SERVER.onServerTick(event.getServer());
        net.guzhenren.tianyi.world.EncounterService.tick(event.getServer());
        // 有限资源点：自己内部按 200 刻节流，默认配置关闭时立即返回。
        net.guzhenren.tianyi.world.FiniteSiteService.tick(event.getServer());
        net.guzhenren.tianyi.net.JournalNet.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            TianyiVoice.forget(player);
            net.guzhenren.tianyi.world.AlchemyWitness.forget(player.getUUID().toString());
        }
        net.guzhenren.tianyi.world.EncounterService.stopping(event.getServer());
        net.guzhenren.tianyi.world.FiniteSiteService.stopping(event.getServer());
        net.guzhenren.tianyi.net.JournalNet.clear();
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            net.guzhenren.tianyi.world.EncounterService.respawn(player);
            net.guzhenren.tianyi.net.JournalNet.sync(player);
            net.guzhenren.tianyi.world.AscensionService.recover(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        net.guzhenren.tianyi.world.EncounterService.joined(event.getEntity());
    }

    @SubscribeEvent
    public static void onInvestigate(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (net.guzhenren.tianyi.world.DestinyRepairService.interact(
                    player, event.getPos(), event.getHand())) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                return;
            }
            if (net.guzhenren.tianyi.world.EncounterService.interact(player, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                return;
            }
            // 有限资源点：只记「谁动过这只桶」，用于把取物归到人头上；不改世界、不取消事件。
            net.guzhenren.tianyi.world.FiniteSiteService.onBlockUsed(player, event.getPos());
            // 宿命蛊：这个容器就在玩家面前（已加载），只读这一个方块实体，不扫世界
            net.guzhenren.tianyi.world.DestinyCustodyService.onContainerUsed(player, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 旧系统退场：归档一次，之后幂等返回
            LegacyRetire.migrate(player, TianyiMod.LOGGER);
            TianyiMod.SERVER.onPlayerLogin(player);
            // 登录即同步一次见闻录（服务端按玩家筛选后下发）
            net.guzhenren.tianyi.net.JournalNet.sync(player);
            net.guzhenren.tianyi.world.EncounterService.sync(player);
            // 宿命蛊：登录只核对自己背包里的实物（不扫世界、不发物品）
            net.guzhenren.tianyi.world.DestinyCustodyService.onLogin(player);
            // 升仙恢复只接受服务端账本；当前无稳定蛊源时保留原记录，不改物品。
            net.guzhenren.tianyi.world.AscensionService.recover(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            net.guzhenren.tianyi.world.EncounterService.changedDimension(player);
            TianyiMod.SERVER.onPlayerChangedDimension(player);
            net.guzhenren.tianyi.net.JournalNet.sync(player);
            // 宿命蛊：跨维度只核对自己背包，不扫目的地
            net.guzhenren.tianyi.world.DestinyCustodyService.onDimensionChanged(player);
        }
    }

    /**
     * 捡起地上的东西：如果那是一件「机缘」，登记领取者。
     * 机缘可以被任何人截取（包括天意原本没打算给的人）。
     *
     * <p>同一件事在情报侧也要留痕：捡到本体真实蛊材就是一次采集，
     * 附近看见的居民才会知道——没人在场就没有人知道。
     */
    @SubscribeEvent
    public static void onPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getItemEntity() != null) {
            TianyiMod.SERVER.onItemPickedUp(player, event.getItemEntity());
            // B06：Post 事件里物品实体已经是「剩下的那堆」，整组拾取时是空栈。
            // 所以用 原始栈 − 当前栈 算真实拾取量，而不是拿剩余量当拾取量。
            int picked = event.getOriginalStack().getCount()
                    - (event.getCurrentStack() == null ? 0 : event.getCurrentStack().getCount());
            if (picked > 0 && !event.getOriginalStack().isEmpty()) {
                var k = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(event.getOriginalStack().getItem());
                if (k != null) {
                    // B05：正在炼蛊的人期间捡到的东西，要从炼成时的背包差异里扣掉
                    net.guzhenren.tianyi.world.AlchemyWitness.notePickup(
                            player.getUUID().toString(), k.toString(), picked);
                }
            }
            net.guzhenren.tianyi.world.FactSource.onPickup(player, event.getItemEntity(),
                    event.getOriginalStack(), event.getCurrentStack(),
                    player.serverLevel().getGameTime());
            // 宿命蛊：刚捡起来的东西此刻已在背包里，这是最可靠的一次核验时刻
            net.guzhenren.tianyi.world.DestinyCustodyService.onPickup(player);
            // 观象·亲见：这次拾取要进玩家自己的见闻录（不是世界账本）
            if (picked > 0 && !event.getOriginalStack().isEmpty()) {
                var jkey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(event.getOriginalStack().getItem());
                if (jkey != null) {
                    String dim = player.level().dimension().location().toString();
                    long at = player.blockPosition().asLong();
                    long gt = player.serverLevel().getGameTime();
                    if (net.guzhenren.tianyi.world.JournalStore.notePickup(player,
                            jkey.toString(), picked, dim, at, gt)) {
                        net.guzhenren.tianyi.net.JournalNet.sync(player);
                    }
                }
            }
        }
    }

    /**
     * 方块被破坏：如果那是一味本体真实蛊材，记一条采集事实并让近处居民目击。
     *
     * <p>候选物品取方块对应的物品形式（{@code asItem().getDefaultInstance()}），
     * 而不是玩家手里的工具——被采走的是地里的东西，不是锄头。
     */
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            if (net.guzhenren.tianyi.world.EncounterService.interact(player, event.getPos())) {
                event.setCanceled(true);
                return;
            }
            net.guzhenren.tianyi.world.FactSource.onBlockBroken(player, level, event.getPos(),
                    event.getState().getBlock().asItem().getDefaultInstance(), level.getGameTime());
            // 有限资源点：先记下动手的人，下一轮对账把减少量算在他头上（不改库存、不补货）。
            net.guzhenren.tianyi.world.FiniteSiteService.onBlockBroken(player, level, event.getPos());
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        net.guzhenren.tianyi.world.EncounterService.onDeath(event.getEntity());
        // 有限资源点：只有匹配 UUID 的守护兽死亡算确认；确认后不补生、不给奖励。
        net.guzhenren.tianyi.world.FiniteSiteService.onGuardianDeath(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            TianyiMod.SERVER.onPlayerDeath(player);
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim
                && event.getSource().getEntity() instanceof ServerPlayer killer
                && !net.guzhenren.tianyi.world.EncounterService.isTrial(victim)) {
            TianyiMod.SERVER.onPlayerKill(killer, victim);
        }
    }

    /** 说明：这里不做任何玩家可见的天意提示 —— 隐性存在是设计约束。 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            net.guzhenren.tianyi.world.EncounterService.logout(player);
            // 宿命蛊：下线前核一次背包，把保管位置落到账本上（只读本人背包，不扫世界）
            net.guzhenren.tianyi.world.DestinyCustodyService.onLogout(player);
            // 下线时落一次盘，避免服务端异常退出丢失天意状态
            WillState.get(player.getServer()).setDirty();
            // 清掉炼蛊见证的背包基线，避免长期运行残留
            TianyiVoice.forget(player);
            net.guzhenren.tianyi.will.AlchemyTracker.forget(player.getUUID().toString());
            net.guzhenren.tianyi.world.AlchemyWitness.forget(player.getUUID().toString());
            net.guzhenren.tianyi.net.JournalNet.forget(player.getUUID());
        }
    }
}
