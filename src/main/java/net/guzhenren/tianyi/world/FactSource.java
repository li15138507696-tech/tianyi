package net.guzhenren.tianyi.world;

import java.util.UUID;

import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.fact.WorldFact;
import net.guzhenren.tianyi.will.WillConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 世界侧的事实来源：<b>世界真的发生了什么 → 写成一条事实 → 只有看见的人才被告知</b>。
 *
 * <h2>这一层守的是「NPC 不是全知的」</h2>
 * 服务器随时能遍历所有居民并"顺手告诉他们"，那样每条事实都会瞬间传遍全服，
 * 情报系统等于没有。所以这里分成两段：
 * <ol>
 *   <li>事件本身 → {@link WorldFact}（只写"发生过"，且按 id 幂等，同一次行为只留一条）；</li>
 *   <li>{@link #witnessScan} 只把这条事实交给<b>当时就在近处</b>的居民，
 *       其余人只能靠 {@code IntelNet} 一跳一跳地听说。</li>
 * </ol>
 * 因此「范围内一个居民都没有」时，本类<b>不写入任何情报</b>——事实进总账，
 * 但没有任何人去讲它，这正是一次无人目击的采集该有的样子。
 *
 * <h2>采样口径</h2>
 * 目击半径取 {@link WillConfig#CARRIER_REACH} 的一半：载体触达是「天意能摸到多远」，
 * 目击是「人肉眼能看到多远」，后者必须更小，否则等于让天意替 NPC 看东西。
 * 距离用水平距离（{@code dx²+dz²}）：同一根柱子上下几层的居民，
 * 看见地面上的事是合理的，用三维距离会漏掉太多真实目击。
 */
public final class FactSource {
    private FactSource() {
    }

    /** 亲见的基础可信度：目击是最强的一手情报，但仍不是 1.0（会看错、会说错）。 */
    private static final double WITNESS_CONFIDENCE = 0.8;
    /** 目击允许的高度差（格）：超过它就不算直接目击（隔层/隔岩）。[平衡参数] */
    private static final double VISIBLE_DY = 16.0;

    // ------------------------------------------------------------ 事件入口

    /**
     * 方块被破坏。
     *
     * @param tool 调用方给的候选物品（破坏方块时传该方块对应的物品形式，
     *             见 {@code TianyiEvents}），只有它是本体真实蛊材才算一次采集
     */
    public static void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos,
                                     ItemStack tool, long now) {
        if (player == null || level == null || pos == null) {
            return;
        }
        if (!GuzhenrenBridge.isGuMaterial(tool)) {
            return; // 不是蛊材：既不记事实，也不惊动任何人
        }
        record(level, pos, player, "采集 " + nameOf(tool), now);
    }

    /**
     * 捡起物品。位置取物品实体所在地（它才是"谁在哪儿捡到的"）。
     *
     * <p>数量用<b>原始栈 − 当前栈</b>算真实拾取量（B06）：
     * NeoForge 的 {@code Post} 事件里物品实体已经是"剩下的那堆"，
     * 整组拾取时它已经是空栈 —— 以前拿它判 {@code isGuMaterial} 会直接漏记，
     * 拿它的数量当"拾取量"会把剩余堆当成捡了那么多。
     *
     * @param original 拾取前的栈（{@code getOriginalStack()}）
     * @param current  拾取后的栈（{@code getCurrentStack()}，可能为空）
     */
    public static void onPickup(ServerPlayer player, Entity itemEntity, ItemStack original,
                                ItemStack current, long now) {
        if (player == null || original == null || original.isEmpty()) {
            return;
        }
        int left = current == null ? 0 : current.getCount();
        int picked = original.getCount() - left;
        if (picked <= 0) {
            return; // 只记真实增加的数量
        }
        if (!GuzhenrenBridge.isGuMaterial(original)) {
            return;
        }
        ServerLevel level = levelOf(itemEntity, player);
        BlockPos at = itemEntity != null ? itemEntity.blockPosition() : player.blockPosition();
        record(level, at, player, "拾取 " + nameOf(original) + " ×" + picked, now);
    }

    // ------------------------------------------------------------ 写事实

    private static void record(ServerLevel level, BlockPos pos, ServerPlayer player,
                               String detail, long now) {
        if (level == null || level.getServer() == null) {
            return;
        }
        String actor = actorOf(player);
        String dim = level.dimension().location().toString();
        // 行为人是"这一世的身份"：身份换了，旧事实仍记在旧身份上，新行为记在新身份上
        WorldFact fact = WorldFact.of(WorldFact.Kind.HARVEST, actor, "", dim, pos.asLong(),
                now, WITNESS_CONFIDENCE, detail);
        IntelState state = IntelState.get(level.getServer());
        if (!state.facts().observe(fact)) {
            return; // 同一时间桶内的重复观测：不重复入库，也不重复通知
        }
        state.setDirty();
        witnessScan(level, fact, state, now);
    }

    /**
     * 把一条事实交给近处的居民。
     *
     * @return 真的被告知的人数（0 = 无人目击，此时什么都没写）
     */
    public static int witnessScan(ServerLevel level, WorldFact fact, IntelState state, long now) {
        if (level == null || fact == null || state == null || !fact.positionKnown()) {
            return 0;
        }
        String dim = level.dimension().location().toString();
        if (!dim.equals(fact.dim)) {
            return 0;
        }
        BlockPos origin = BlockPos.of(fact.pos);
        double limit = WillConfig.CARRIER_REACH / 2.0;
        double limit2 = limit * limit;
        int taught = 0;
        for (var resident : GuzhenrenBridge.allResidents(level)) {
            if (resident == null || resident.deceased() || !resident.positionKnown()) {
                continue;
            }
            if (!dim.equals(resident.dimension())) {
                continue;
            }
            // B11 修复：以前只比 X/Z，隔墙、上下层的居民也算「亲眼看见」。
            // 现在要求三维距离在感知范围内，且高度差不超过 VISIBLE_DY。
            double dx = resident.x() - (origin.getX() + 0.5);
            double dz = resident.z() - (origin.getZ() + 0.5);
            double dy = resident.y() - (origin.getY() + 0.5);
            if (Math.abs(dy) > VISIBLE_DY) {
                continue; // 上下层/隔着岩层：不算直接目击
            }
            if (dx * dx + dz * dz + dy * dy > limit2) {
                continue;
            }
            // 身份一律完整 UUID 字符串：名字会改，UUID 不会
            if (state.net().teach(resident.npcId().toString(), fact, fact.baseConfidence, now)) {
                taught++;
            }
        }
        if (taught > 0) {
            state.setDirty();
        }
        return taught;
    }

    // ------------------------------------------------------------ 工具

    /** 行为人：优先当前激活的身份，没有身份才退回玩家本体 UUID。 */
    private static String actorOf(ServerPlayer player) {
        UUID identity = GuzhenrenBridge.activeIdentity(player);
        return (identity != null ? identity : player.getUUID()).toString();
    }

    private static ServerLevel levelOf(Entity itemEntity, ServerPlayer player) {
        if (itemEntity != null && itemEntity.level() instanceof ServerLevel sl) {
            return sl;
        }
        return player.serverLevel();
    }

    private static String nameOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "?" : stack.getHoverName().getString();
    }
}
