package net.guzhenren.tianyi.world;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.tianyi.TianyiMod;
import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.encounter.EncounterBeasts;
import net.guzhenren.tianyi.encounter.EncounterRun;
import net.guzhenren.tianyi.encounter.EncounterRun.Phase;
import net.guzhenren.tianyi.net.EncounterActionPayload;
import net.guzhenren.tianyi.net.EncounterSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread authoritative encounter orchestration. No world or NPC secrets are synchronized. */
public final class EncounterService {
    public static final String ENTITY_TAG = "tianyi_encounter", OWNER_TAG = "tianyi_encounter_owner";
    private static final String MARKER_TAG = "tianyi_encounter_marker";
    private static final ResourceLocation CURRENCY = ResourceLocation.fromNamespaceAndPath("guzhenren", "gucaiyuanshi");
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Integer> MISSING_ENTITY = new HashMap<>();
    private static final Set<UUID> SPAWNING = new HashSet<>();
    /** 3.14.1：上一次真正发给该玩家的相遇状态包，用于轮询去重（离线/停服清空）。 */
    private static final Map<UUID, EncounterSyncPayload> LAST_SENT = new HashMap<>();
    private static boolean stopping;
    private static int tick;
    private EncounterService() {}

    public static void started() { stopping = false; tick = 0; LAST_ACTION.clear(); MISSING_ENTITY.clear(); SPAWNING.clear(); LAST_SENT.clear(); }
    public static void stopping(MinecraftServer server) {
        stopping = true;
        EncounterState.get(server).setDirty();
        LAST_ACTION.clear(); MISSING_ENTITY.clear(); SPAWNING.clear(); LAST_SENT.clear();
    }
    private static boolean survival(ServerPlayer p) { return p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL; }
    private static String dimension(ServerPlayer p) { return p.level().dimension().location().toString(); }
    private static ServerLevel level(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(dim)) return l;
        return null;
    }
    private static int cooldown(ServerPlayer p) {
        int min = EncounterSettings.cooldownMin(), max = EncounterSettings.cooldownMax();
        return min + (max > min ? p.getRandom().nextInt(max - min + 1) : 0);
    }
    private static Item currency() { return BuiltInRegistries.ITEM.getOptional(CURRENCY).filter(i -> i != Items.AIR).orElse(null); }
    private static boolean near(ServerPlayer p, EncounterRun r, double radius) {
        return r.position != Long.MIN_VALUE && dimension(p).equals(r.dimension)
                && p.distanceToSqr(BlockPos.of(r.position).getX() + .5, BlockPos.of(r.position).getY() + .5,
                                   BlockPos.of(r.position).getZ() + .5) <= radius * radius;
    }
    private static void tell(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal("§6[天意] §r" + text)); }

    public static void tick(MinecraftServer server) {
        if (stopping || !EncounterSettings.enabled() || ++tick % 20 != 0) return;
        EncounterState state = EncounterState.get(server);
        if (state.quarantined()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EncounterRun r = state.of(p.getUUID());
            if (r.penaltyPending && p.isAlive()) applyPenalty(p, r);
            if (r.phase == Phase.FIGHT) {
                if (!p.isAlive()) { fail(p, r, "挑战中死亡", true); continue; }
                if (!survival(p)) { fail(p, r, "离开生存模式", false); continue; }
                if (!dimension(p).equals(r.dimension)) { fail(p, r, "离开挑战维度", false); continue; }
                r.elapse(20);
                r.outside(!near(p, r, 64), 20);
                if (r.outsideExpired(EncounterSettings.OUTSIDE_TICKS)) fail(p, r, "离开机缘超过64格持续五秒", false);
                else if (r.remainingTicks <= 0) fail(p, r, "挑战超时", false);
                else {
                    ServerLevel l = p.serverLevel();
                    Entity enemy = findEnemy(server, r);
                    if (enemy == null) {
                        // Unloaded/unknown is not death or proof of infrastructure failure.
                        // Keep the paid run and normal explicit timeout; never refund on absence.
                        MISSING_ENTITY.put(p.getUUID(), 0);
                    } else {
                        MISSING_ENTITY.remove(p.getUUID());
                        if (enemy instanceof Mob mob) mob.setTarget(p);
                    }
                }
            } else if (survival(p) && p.isAlive()) {
                r.elapse(20);
                if (r.due()) tryOpen(p, r);
                if ((r.phase == Phase.SEEK || r.phase == Phase.READY) && !dimension(p).equals(r.dimension)) {
                    r.finish(cooldown(p)); r.message = "离开机缘所在维度，未接受挑战，不受处罚。"; cleanup(server, r);
                }
                if ((r.phase == Phase.SEEK || r.phase == Phase.READY)
                        && p.serverLevel().isLoaded(BlockPos.of(r.position)) && !markerExists(server, r)) {
                    r.finish(cooldown(p)); r.message = "机缘现场已损毁，本次结束；已获得的收益保留，不受处罚。";
                    cleanup(server, r); tell(p, r.message);
                }
            }
            if ((r.phase == Phase.SEEK || r.phase == Phase.READY) && near(p, r, 64)) {
                BlockPos pos = BlockPos.of(r.position);
                if (p.serverLevel().isLoaded(pos)) p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, false,
                        pos.getX() + .5, pos.getY() + 1.2, pos.getZ() + .5, 2, .2, .15, .2, .005);
            }
            state.setDirty();
            syncIfChanged(p);
        }
    }

    /** Search only loaded, empty, supported places; never load a chunk or overwrite a block entity. */
    private static BlockPos spot(ServerPlayer p, BlockPos center, int min, int max, boolean marker) {
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < 32; i++) {
            double angle = p.getRandom().nextDouble() * Math.PI * 2;
            int distance = min + p.getRandom().nextInt(max - min + 1);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!l.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos pos = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(pos.getY() - center.getY()) > 8 || !l.isInWorldBounds(pos)
                    || !l.getWorldBorder().isWithinBounds(pos) || !l.isLoaded(pos)
                    || !l.isLoaded(pos.above()) || !l.getBlockState(pos).isAir() || !l.getBlockState(pos.above()).isAir()
                    || !l.getFluidState(pos).isEmpty() || !l.getFluidState(pos.below()).isEmpty()
                    || !l.getBlockState(pos.below()).isFaceSturdy(l, pos.below(), Direction.UP)
                    || l.getBlockEntity(pos.below()) != null || !l.mayInteract(p, pos)
                    || !p.mayUseItemAt(pos, Direction.UP, new ItemStack(Items.BARREL))) continue;
            String ground = BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos.below()).getBlock()).getPath();
            if (ground.contains("magma") || ground.contains("campfire") || ground.contains("cactus")) continue;
            if (!l.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(marker ? 2 : 1)).isEmpty()) continue;
            return pos;
        }
        return null;
    }

    private static void tryOpen(ServerPlayer p, EncounterRun r) {
        if (currency() == null) { r.remainingTicks = 200; return; }
        BlockPos pos = spot(p, p.blockPosition(), 24, 48, true);
        if (pos == null) { r.remainingTicks = 200; return; }
        boolean first = r.id.isEmpty();
        int rank = (int) Math.max(0, Math.min(5, p.getData(GuzhenrenModVariables.PLAYER_VARIABLES).zhuanshu));
        var beast = EncounterBeasts.select(first ? 0 : rank, p.getRandom().nextLong());
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(beast.id()))) { r.remainingTicks = 200; return; }
        ServerLevel l = p.serverLevel();
        if (!l.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3)) { r.remainingTicks = 200; return; }
        var be = l.getBlockEntity(pos);
        if (be == null) { l.removeBlock(pos, false); r.remainingTicks = 200; return; }
        String id = UUID.randomUUID().toString();
        int bonus = EncounterSettings.bonus() * Math.max(1, beast.minRank());
        r.open(id, dimension(p), pos.asLong(), beast.id(), beast.name(), beast.warning(),
                EncounterSettings.base(), EncounterSettings.stake(), bonus);
        be.getPersistentData().putString(MARKER_TAG, id);
        be.getPersistentData().putUUID(OWNER_TAG, p.getUUID());
        be.setChanged();
        r.message = "附近出现机缘。前往带微光的木桶，右键调查可得 " + r.baseReward + " 元石。";
        tell(p, r.message + " 按 J 查阅方位。");
        EncounterState.get(p.getServer()).setDirty();
    }

    public static void action(ServerPlayer p, EncounterActionPayload payload) {
        if (stopping || !EncounterSettings.enabled() || payload == null) return;
        long now = p.getServer().overworld().getGameTime();
        Long last = LAST_ACTION.get(p.getUUID());
        if (last != null && now >= last && now - last < 5) return;
        LAST_ACTION.put(p.getUUID(), now);
        EncounterRun r = EncounterState.get(p.getServer()).existing(p.getUUID());
        if (r == null || !r.id.equals(payload.encounterId()) || !survival(p) || !p.isAlive()) return;
        switch (payload.action()) {
            case INVESTIGATE -> investigate(p, r);
            case WAGER -> wager(p, r);
            case FINISH -> {
                if (r.phase == Phase.FIGHT) fail(p, r, "主动认输", false);
                else if ((r.phase == Phase.SEEK || r.phase == Phase.READY) && near(p, r, 4)) {
                    r.finish(cooldown(p)); r.message = "见好就收，本次机缘结束。"; cleanup(p.getServer(), r); tell(p, r.message);
                }
            }
        }
        EncounterState.get(p.getServer()).setDirty(); sync(p);
    }

    private static void investigate(ServerPlayer p, EncounterRun r) {
        if (r.phase != Phase.SEEK || !near(p, r, 4) || currency() == null || !markerExists(p.getServer(), r)) return;
        if (!r.investigate()) return;
        EncounterState.get(p.getServer()).setDirty();
        give(p, r.baseReward);
        r.message = "已获得 " + r.baseReward + " 元石，可用于本体交易或恢复真元。可收手，也可押注挑战「" + r.enemyName + "」。";
        tell(p, r.message);
    }

    private static void wager(ServerPlayer p, EncounterRun r) {
        if (r.phase != Phase.READY || !near(p, r, 4) || !markerExists(p.getServer(), r) || currency() == null) return;
        if (p.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) { tell(p, "和平难度不能开启妖兽挑战，可见好就收。"); return; }
        BlockPos spawn = spot(p, BlockPos.of(r.position), 8, 12, false);
        if (spawn == null) { tell(p, "附近没有安全的妖兽落点，未扣押金。请稍后再试。"); return; }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(r.enemyId)).orElse(null);
        Entity entity = type == null ? null : type.create(p.serverLevel());
        if (!(entity instanceof Mob mob)) { tell(p, "挑战妖兽不可用，未扣押金。"); return; }
        mob.moveTo(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0);
        mob.getPersistentData().putString(ENTITY_TAG, r.id);
        mob.getPersistentData().putUUID(OWNER_TAG, p.getUUID());
        mob.setPersistenceRequired();
        mob.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
        AABB bounds = mob.getBoundingBox();
        for (int cx = net.minecraft.util.Mth.floor(bounds.minX) >> 4; cx <= net.minecraft.util.Mth.floor(bounds.maxX) >> 4; cx++) {
            for (int cz = net.minecraft.util.Mth.floor(bounds.minZ) >> 4; cz <= net.minecraft.util.Mth.floor(bounds.maxZ) >> 4; cz++) {
                if (!p.serverLevel().hasChunk(cx, cz)) { mob.discard(); tell(p, "妖兽落点尚未加载，未扣押金。"); return; }
            }
        }
        if (!p.serverLevel().noCollision(mob) || p.serverLevel().containsAnyLiquid(bounds)) {
            mob.discard(); tell(p, "妖兽体型无法安全落地，未扣押金，请稍后重试。"); return;
        }
        boolean added;
        SPAWNING.add(mob.getUUID());
        try { added = mob.isAlive() && p.serverLevel().addFreshEntity(mob); }
        finally { SPAWNING.remove(mob.getUUID()); }
        if (!added || !mob.isAlive() || mob.isRemoved()) { mob.discard(); tell(p, "妖兽未能出现，未扣押金。"); return; }
        if (!GuzhenrenBridge.consumeCurrency(p, r.stake)) { mob.discard(); tell(p, "押注需要 " + r.stake + " 元石；余额不足，挑战未开启。"); return; }
        if (!r.startBattle(mob.getUUID().toString(), EncounterSettings.BATTLE_TICKS)) {
            mob.discard(); give(p, r.stake); return;
        }
        EncounterState.get(p.getServer()).setDirty();
        mob.setTarget(p); MISSING_ENTITY.remove(p.getUUID());
        r.message = "已押注 " + r.stake + " 元石。180秒内击败「" + r.enemyName + "」；死亡、退出、切维度或离场均按明示规则失败。";
        tell(p, r.message);
    }

    private static void give(ServerPlayer p, int amount) {
        Item item = currency();
        if (item == null) throw new IllegalStateException("Missing base currency " + CURRENCY);
        boolean overflow = false;
        for (int left = amount; left > 0;) {
            int size = Math.min(left, item.getDefaultInstance().getMaxStackSize());
            ItemStack stack = new ItemStack(item, size); left -= size;
            p.getInventory().add(stack);
            if (!stack.isEmpty()) {
                ItemEntity drop = p.drop(stack, false);
                if (drop != null) { drop.setTarget(p.getUUID()); drop.setNoPickUpDelay(); }
                else {
                    TianyiMod.LOGGER.error("[天意机缘] 结算落物被拒绝 owner={} undelivered={}；不自动重放，请核对账本", p.getUUID(), stack.getCount() + left);
                    tell(p, "结算落物被服务器拒绝，已记录诊断，请联系管理员核对；不会自动重复发放。");
                    break;
                }
                overflow = true;
            }
        }
        p.containerMenu.broadcastChanges();
        if (overflow) tell(p, "背包已满，剩余元石落在脚边，请拾取。");
    }

    public static void onDeath(LivingEntity entity) {
        if (!EncounterSettings.enabled()) return;
        if (!(entity.level() instanceof ServerLevel l)) return;
        if (entity instanceof ServerPlayer p) {
            EncounterRun r = EncounterState.get(l.getServer()).existing(p.getUUID());
            if (r != null && r.phase == Phase.FIGHT) fail(p, r, "挑战中死亡", true);
            return;
        }
        if (!isTrial(entity) || !entity.getPersistentData().hasUUID(OWNER_TAG)) return;
        UUID owner = entity.getPersistentData().getUUID(OWNER_TAG);
        EncounterRun r = EncounterState.get(l.getServer()).existing(owner);
        if (r == null || r.phase != Phase.FIGHT || !r.id.equals(entity.getPersistentData().getString(ENTITY_TAG))
                || !entity.getUUID().toString().equals(r.enemyUuid)) return;
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(owner);
        if (p != null && !survival(p)) { fail(p, r, "离开生存模式", false); return; }
        if (p == null || !p.isAlive() || !near(p, r, 32)) {
            if (p != null) fail(p, r, "妖兽被击败时未在现场32格内", false);
            return;
        }
        if (r.win(cooldown(p))) {
            EncounterState.get(l.getServer()).setDirty();
            give(p, Math.addExact(r.stake, r.bonus));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EncounterSettings.SPEED_TICKS, 0));
            r.message = "挑战成功：返还 " + r.stake + " 元石，额外获得 " + r.bonus + " 元石，迅捷I持续120秒。";
            cleanupMarker(l.getServer(), r); tell(p, r.message);
            EncounterState.get(l.getServer()).setDirty(); sync(p);
        }
    }

    private static void applyPenalty(ServerPlayer p, EncounterRun r) {
        if (!p.isAlive() || !r.consumePenalty()) return;
        EncounterState.get(p.getServer()).setDirty();
        var v = p.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        v.zhenyuan = Math.max(0, v.zhenyuan * EncounterSettings.MANA_RETAINED);
        v.markSyncDirty();
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EncounterSettings.WEAKNESS_TICKS, 0));
        tell(p, "挑战失败：失去 " + r.stake + " 元石押注，当前真元减少50%，虚弱I持续60秒。原因：" + r.message);
    }
    private static void fail(ServerPlayer p, EncounterRun r, String reason, boolean defer) {
        if (!r.lose(reason, cooldown(p))) return;
        cleanup(p.getServer(), r);
        if (!defer) applyPenalty(p, r);
        else tell(p, "挑战失败，押注已失去；真元与虚弱处罚将在复活后结算。");
        EncounterState.get(p.getServer()).setDirty(); sync(p);
    }
    public static void respawn(ServerPlayer p) {
        EncounterRun r = EncounterState.get(p.getServer()).existing(p.getUUID());
        if (EncounterSettings.enabled() && r != null && r.penaltyPending) { applyPenalty(p, r); EncounterState.get(p.getServer()).setDirty(); }
        sync(p);
    }
    public static void logout(ServerPlayer p) {
        LAST_ACTION.remove(p.getUUID()); MISSING_ENTITY.remove(p.getUUID());
        // 3.14.1：重连后客户端状态是空的，必须重新发一次，所以下线时丢掉去重记录。
        LAST_SENT.remove(p.getUUID());
        if (stopping || !EncounterSettings.enabled()) return;
        EncounterRun r = EncounterState.get(p.getServer()).existing(p.getUUID());
        if (r != null && r.phase == Phase.FIGHT) fail(p, r, "退出游戏", false);
    }
    public static void changedDimension(ServerPlayer p) {
        if (!EncounterSettings.enabled()) return;
        EncounterRun r = EncounterState.get(p.getServer()).existing(p.getUUID());
        if (r != null && r.phase == Phase.FIGHT) fail(p, r, "离开挑战维度", false);
    }
    public static boolean isTrial(Entity e) { return !e.getPersistentData().getString(ENTITY_TAG).isEmpty(); }

    /** Event interaction returns true for every owned marker, including someone else's marker. */
    public static boolean interact(ServerPlayer p, BlockPos pos) {
        var be = p.serverLevel().getBlockEntity(pos);
        if (be == null || be.getPersistentData().getString(MARKER_TAG).isEmpty()) return false;
        if (EncounterState.get(p.getServer()).quarantined()) return true;
        if (!be.getPersistentData().hasUUID(OWNER_TAG) || !be.getPersistentData().getUUID(OWNER_TAG).equals(p.getUUID())) {
            tell(p, "这是其他修士的机缘。"); return true;
        }
        EncounterRun r = EncounterState.get(p.getServer()).existing(p.getUUID());
        if (r == null || r.terminal() || !r.id.equals(be.getPersistentData().getString(MARKER_TAG))) {
            p.serverLevel().removeBlock(pos, false);
            return true;
        }
        action(p, new EncounterActionPayload(EncounterActionPayload.Action.INVESTIGATE, r.id));
        if (r.phase == Phase.READY) tell(p, "按 J 查看「押注深入」或「见好就收」；押注前会再次确认代价。");
        return true;
    }
    public static void joined(Entity e) {
        if (SPAWNING.contains(e.getUUID())) return;
        if (!(e.level() instanceof ServerLevel l) || !isTrial(e) || !e.getPersistentData().hasUUID(OWNER_TAG)) return;
        if (l.getServer().overworld() == null) return;
        if (EncounterState.get(l.getServer()).quarantined()) return;
        EncounterRun r = EncounterState.get(l.getServer()).existing(e.getPersistentData().getUUID(OWNER_TAG));
        if (r == null || r.phase != Phase.FIGHT || !r.enemyUuid.equals(e.getUUID().toString())
                || !r.id.equals(e.getPersistentData().getString(ENTITY_TAG))) e.discard();
    }
    private static boolean markerExists(MinecraftServer server, EncounterRun r) {
        ServerLevel l = level(server, r.dimension);
        if (l == null || r.position == Long.MIN_VALUE || !l.isLoaded(BlockPos.of(r.position))) return false;
        var be = l.getBlockEntity(BlockPos.of(r.position));
        return be != null && be.getPersistentData().getString(MARKER_TAG).equals(r.id);
    }
    private static Entity findEnemy(MinecraftServer server, EncounterRun r) {
        ServerLevel l = level(server, r.dimension);
        if (l == null || r.enemyUuid.isEmpty()) return null;
        try { return l.getEntity(UUID.fromString(r.enemyUuid)); } catch (IllegalArgumentException ex) { return null; }
    }
    private static void cleanupMarker(MinecraftServer server, EncounterRun r) {
        if (markerExists(server, r)) level(server, r.dimension).removeBlock(BlockPos.of(r.position), false);
    }
    private static void cleanup(MinecraftServer server, EncounterRun r) {
        cleanupMarker(server, r);
        Entity enemy = findEnemy(server, r);
        if (enemy != null) enemy.discard();
        // Unloaded tagged entities are removed on EntityJoinLevel, never force-loaded.
    }
    public static void sync(ServerPlayer p) {
        EncounterSyncPayload payload = payloadOf(p);
        if (payload == null) return;
        // 显式路径一律发，并把「刚发出去的那份」记进去重表：这样重连/重生后紧接着的
        // 轮询对比的是客户端**刚刚收到**的内容，不会因为旧记录而误判成"不用发"。
        LAST_SENT.put(p.getUUID(), payload);
        PacketDistributor.sendToPlayer(p, payload);
    }

    /**
     * 3.14.1：给 {@link #tick} 的每秒轮询用。
     *
     * <p>{@link EncounterSyncPayload} 是客户端这份状态的<b>唯一</b>来源（客户端 handler 只是整份替换），
     * 所以「内容完全相同的包」等价于「不发」。以前每个在线玩家每秒都无条件收一包，WAITING/SEEK 阶段
     * 内容几乎不变（{@code remainingTicks} 在 SEEK/READY 根本不递减），纯属白跑 codec + 网络写。
     * 登录、重生、切维度、动作回执这些显式路径仍然走 {@link #sync}，一定发。
     */
    private static void syncIfChanged(ServerPlayer p) {
        EncounterSyncPayload payload = payloadOf(p);
        if (payload == null) return;
        if (payload.equals(LAST_SENT.get(p.getUUID()))) return;
        LAST_SENT.put(p.getUUID(), payload);
        PacketDistributor.sendToPlayer(p, payload);
    }

    private static EncounterSyncPayload payloadOf(ServerPlayer p) {
        if (p == null || p.connection == null || stopping) return null;
        EncounterRun r = EncounterState.get(p.getServer()).of(p.getUUID());
        if (r == null) return null;
        String phase = r.phase.name();
        String message = r.message;
        if (!EncounterSettings.enabled()) { phase = "WAITING"; message = "机缘玩法已由服务器关闭。"; }
        if (r.phase == Phase.READY) message += " " + r.warning;
        if (message.length() > 1024) message = message.substring(0, 1024);
        return new EncounterSyncPayload(r.id, phase, r.dimension, r.position,
                Math.max(0, (r.remainingTicks + 19) / 20), r.enemyId, r.enemyName,
                r.baseReward, r.stake, r.bonus, message);
    }
}
