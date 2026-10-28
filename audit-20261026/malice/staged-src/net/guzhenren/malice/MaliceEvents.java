package net.guzhenren.malice;

import dev.xkmc.l2core.capability.attachment.GeneralCapabilityHolder;
import dev.xkmc.l2hostility.content.capability.chunk.ChunkDifficulty;
import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import dev.xkmc.l2hostility.events.HostilityInitEvent;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 恶意初始化钩子。等级倍率只处理 INIT 阶段：该阶段提供新计算的原始等级；
 * COPY 已继承源实体的最终等级，ARMOR / WEAPON 是同一轮初始化的装备阶段。
 * 依据事件阶段判断边界，避免复制叠倍率和 reinit 数值恰好相等时被误判为重复。
 */
public final class MaliceEvents {

    /** 恶意的原始等级（未经难度倍率）。 */
    private static final String TAG_BASE = "GuzhenrenMaliceBase";
    /** 最近一次 INIT 结算后的等级，供诊断命令查看。 */
    private static final String TAG_APPLIED = "GuzhenrenMaliceApplied";

    /** 已经做过一次「能力是否挂载」诊断的实体类型（每种只记一条日志）。 */
    private static final Set<EntityType<?>> PROBED = ConcurrentHashMap.newKeySet();

    private MaliceEvents() {
    }

    /**
     * 诊断：分档怪第一次进入世界时，记一条「恶意的能力到底挂没挂上」。
     *
     * <p>恶意不挂 {@code MobTraitCap} 时，怪物不会拿到等级、不会抽词条、头顶也不会显示 ——
     * 表现和「没装适配」一模一样。这条日志能把两种可能分开：
     * 已挂载 = 我们的数据没问题，问题在等级/倍率；未挂载 = 恶意的 isProper 判定没过
     * （多半是白名单被别的数据包覆盖，或恶意的挂载链路被别的模组打断）。
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        EntityType<?> type = entity.getType();
        if (!MaliceTags.isTiered(type)) {
            return;
        }

        // ---- 诊断：每种类型只打一次（这个开关只控制日志，绝不能拦后面的保险丝）----
        if (PROBED.add(type)) {
            boolean attached;
            try {
                attached = holder().getExisting(entity).isPresent();
            } catch (Throwable t) {
                attached = false;
            }
            GuzhenrenMalice.LOGGER.info("[蛊真人×恶意] 首次见到 {}：恶意能力{}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type),
                    attached ? "已挂载" : "未挂载 —— 已启用补挂");
        }

        // ---- 保险丝：每一只分档妖兽都要过，不能被上面的 PROBED 短路 ----
        // 放到本 tick 末尾再查一次（让原版/本体的 finalizeSpawn 先跑完），仍缺失就主动补挂。
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> ensureAttached(entity));
        }
    }

    @SuppressWarnings("unchecked")
    private static GeneralCapabilityHolder<LivingEntity, MobTraitCap> holder() {
        return (GeneralCapabilityHolder<LivingEntity, MobTraitCap>) LHMiscs.MOB.type();
    }

    /**
     * 强制让恶意接管一只分档妖兽。
     *
     * <p><b>为什么需要它</b>：恶意的能力只在 {@code FinalizeSpawnEvent} 里挂载。
     * 本体的妖兽如果由脚本/库层直接 {@code addFreshEntity} 生成（10.20 的势力系统、
     * 蛊师召唤、地牢摆怪等都可能这么干），就<b>永远不会经过 finalizeSpawn</b> ——
     * 表现就是「这只妖兽怎么打都没有等级、也吃不到词条」。
     *
     * <p>这里在实体进场后的下一 tick 补一次：没有能力就 {@code getOrCreate}，
     * 没初始化就按所在区块的难度补 {@code init}（init 内部会再发 Pre/Post 事件，
     * 所以难度倍率与排除规则照样生效）。
     */
    private static void ensureAttached(LivingEntity entity) {
        if (entity.isRemoved() || !entity.isAlive()) {
            return;
        }
        if (!MaliceTags.isTiered(entity.getType())) {
            return;
        }
        try {
            GeneralCapabilityHolder<LivingEntity, MobTraitCap> holder = holder();
            var existing = holder.getExisting(entity);
            if (existing.isPresent() && existing.get().isInitialized()) {
                return;
            }
            MobTraitCap cap = existing.isPresent() ? existing.get() : holder.getOrCreate(entity);
            if (cap.isInitialized()) {
                return;
            }
            var chunk = ChunkDifficulty.at(entity.level(), entity.blockPosition());
            if (chunk.isEmpty()) {
                GuzhenrenMalice.LOGGER.warn("[蛊真人×恶意] 补挂跳过 {}：所在区块没有难度数据",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                return;
            }
            cap.init(entity.level(), entity, chunk.get());
            GuzhenrenMalice.LOGGER.debug("[蛊真人×恶意] 补挂成功：{}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        } catch (Throwable t) {
            GuzhenrenMalice.LOGGER.warn("[蛊真人×恶意] 补挂失败 {}：{}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), t.toString());
        }
    }

    @SubscribeEvent
    public static void onInitPre(HostilityInitEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity.getType().is(MaliceTags.EXCLUDE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInitPost(HostilityInitEvent.Post event) {
        if (event.getPhase() != HostilityInitEvent.InitPhase.INIT) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!MaliceTags.isTiered(entity.getType())) {
            return;
        }
        // 玩家自己驯服的妖兽不跟着难度膨胀（本体妖兽大多是 TamableAnimal，
        // 恶意的 allowTraitOnOwnable=false 只挡词条，等级还得我们自己挡住）。
        // 主人离线或在别的维度时 getOwner() 会返回 null，归属以 UUID 为准。
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }
        MobTraitCap cap = event.getData();
        int lv = cap.getLevel();
        if (lv <= 0) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        int base = lv; // INIT（包括 reinit）每次都提供本轮的新原始等级。

        double mul = DifficultyState.get(server).current().levelMul();
        int target = Math.max(1, (int) Math.round(base * mul));
        if (target != lv) {
            cap.setLevel(entity, target);
            // setLevel 只改 MAX_HEALTH 修饰符，当前血量还停在旧上限。
            // 恶意自己在 TraitManager.fill 末尾会 setHealth(getMaxHealth())，
            // 我们改完等级要补一次同样的满血，否则刷出来的怪只有半管血。
            entity.setHealth(entity.getMaxHealth());
        }
        data.putInt(TAG_BASE, base);
        data.putInt(TAG_APPLIED, cap.getLevel());
    }
}
