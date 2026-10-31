package net.guzhenren.tianyi.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.guzhenren.tianyi.journal.PlayerJournal;
import net.guzhenren.tianyi.journal.SiteNotice;
import net.guzhenren.tianyi.net.JournalNet;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 把有限资源点的事实写进<b>这个玩家自己</b>的《见闻录》。
 *
 * <p>三条硬规则：
 * <ol>
 *   <li><b>只写本人亲眼所见</b>：同维度、距离在 {@link SiteNotice#WITNESS_RADIUS} 内，
 *       到达还要能在现场找到属于本站的实物（{@link SitePlacement#clue}）。
 *       别人的采集、没人在场的变化、后台评分、全图分布，一概不写。</li>
 *   <li><b>不泄露坐标</b>：条目里的 {@code pos} 是<b>玩家自己</b>站的位置，不是资源点的坐标；
 *       标题与正文里也没有站点坐标或剩余总量以外的账本信息。</li>
 *   <li><b>复用既有同步</b>：只调 {@link JournalNet#sync}，不新增 payload、不改协议。</li>
 * </ol>
 *
 * <p>{@link #WRITTEN} 只是本进程内的“已经写过”缓存：省掉每 10 秒重复解析同一份 NBT。
 * 跨会话的去重仍然由 {@link JournalStore#note} + 条目 key 负责。
 *
 * <p><b>未实机验证</b>：这里的世界事实来自 {@link SitePlacement}，只有进游戏才看得见。
 */
public final class SiteEvidence {
    /** 玩家 UUID → 本进程已经写过的 key。停服清空；超过上限整体清空，不做无界增长。 */
    private static final Map<UUID, Set<String>> WRITTEN = new HashMap<>();
    private static final int MAX_KEYS = 8192;
    /**
     * {@link #WRITTEN} 里所有集合大小之和的滚动计数。
     *
     * <p>3.14.1：以前每次 {@link #remember} 都用 {@code WRITTEN.values().stream().mapToInt(Set::size).sum()}
     * 现算一遍——每写一条见闻就把全体玩家×全部 key 数一遍。计数只在 add 成功时 +1、clear 时归零，
     * 结果与现算完全相同，但不再是热路径上的 O(玩家×key)。
     */
    private static int writtenKeys;

    private SiteEvidence() {}

    public static void forget() {
        WRITTEN.clear();
        writtenKeys = 0;
    }

    /** 走到跟前：现场确实有属于本站的实物。 */
    public static boolean arrived(ServerPlayer player, FiniteSite site) {
        if (!nearSite(player, site)) return false;
        // 3.14.1：先查本进程写过的 key，再决定要不要做现场扫描（visible 要逐个槽位读方块/方块实体）。
        // emit 里对同一个 key 本来就会直接 return false，所以顺序调整不改变结果。
        if (alreadyWritten(player, SiteNotice.key(site.siteId(), SiteNotice.Event.ARRIVED, site.revision()))) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || !visible(level, site)) return false;
        return emit(player, site, SiteNotice.Event.ARRIVED, 0);
    }

    /** 亲手取走一份，并看见还剩多少。 */
    public static boolean harvested(ServerPlayer player, FiniteSite site, int remaining) {
        if (!nearSite(player, site)) return false;
        return emit(player, site, SiteNotice.Event.HARVESTED, Math.max(0, remaining));
    }

    /** 亲眼看见看守的兽（实体真的在、真的是本站那只、真的在视野距离内）。 */
    public static boolean guardianSeen(ServerPlayer player, FiniteSite site) {
        if (player == null || site == null || site.guardians().isEmpty()) return false;
        // 3.14.1：同上，先查写过的 key，避免每 10 秒重复做一次实体/方块查询。
        if (alreadyWritten(player, SiteNotice.key(site.siteId(), SiteNotice.Event.GUARDIAN_SEEN, site.revision()))) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (SitePlacement.guardianStatus(level, site) != SitePlacement.GuardianStatus.ALIVE_LOADED) return false;
        Entity guardian = level.getEntity(site.guardians().get(0));
        if (guardian == null) return false;
        // 距离按守护兽本体算，不按站点中心算：它可能已经走开一段。
        if (!SiteNotice.witnessed(player.distanceToSqr(
                guardian.getX(), guardian.getY(), guardian.getZ()))) return false;
        return emit(player, site, SiteNotice.Event.GUARDIAN_SEEN, 0);
    }

    /** 亲见守护兽倒下（由匹配 UUID 的死亡事件触发，调用方已确认就在眼前）。 */
    public static boolean guardianSlain(ServerPlayer player, FiniteSite site) {
        return emit(player, site, SiteNotice.Event.GUARDIAN_SLAIN, 0);
    }

    /** 亲见桶/根被毁，此处从此绝了。 */
    public static boolean carrierLost(ServerPlayer player, FiniteSite site) {
        if (!nearSite(player, site)) return false;
        return emit(player, site, SiteNotice.Event.CARRIER_LOST, 0);
    }

    private static boolean nearSite(ServerPlayer player, FiniteSite site) {
        if (player == null || site == null || site.terminal()) return false;
        return SiteNotice.witnessed(player.distanceToSqr(
                site.x() + 0.5, site.y() + 0.5, site.z() + 0.5));
    }

    /** 条目落盘。到这里之前，调用方必须已经用自己的方式确认了“就在眼前”。 */
    private static boolean emit(ServerPlayer player, FiniteSite site, SiteNotice.Event event, int count) {
        if (player == null || site == null || site.terminal()) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (!site.dimension().equals(level.dimension().location().toString())) return false;
        String key = SiteNotice.key(site.siteId(), event, site.revision());
        if (alreadyWritten(player, key)) return false;

        PlayerJournal.Entry entry = SiteNotice.entry(event, site.siteId(), site.kind(), site.revision(), count,
                level.dimension().location().toString(), player.blockPosition().asLong(), level.getGameTime());
        boolean added = JournalStore.note(player, entry);
        // 无论新增还是跨会话已存在，本进程都不必再试同一个 key。
        remember(player, key);
        if (!added) return false;
        JournalNet.sync(player);
        return true;
    }

    /** 现场事实：至少有一个已物化的槽位，其方块/容器仍然确实是本站的。 */
    private static boolean visible(ServerLevel level, FiniteSite site) {
        for (FiniteSite.Slot slot : site.slots()) {
            if (!slot.materialized()) continue;
            BlockPos pos = new BlockPos(slot.x(), slot.y(), slot.z());
            if (!level.hasChunkAt(pos)) continue;
            if (SitePlacement.clue(level, site, pos).isPresent()) return true;
        }
        return false;
    }

    private static boolean alreadyWritten(ServerPlayer player, String key) {
        Set<String> keys = WRITTEN.get(player.getUUID());
        return keys != null && keys.contains(key);
    }

    private static void remember(ServerPlayer player, String key) {
        if (writtenKeys > MAX_KEYS) {
            WRITTEN.clear();
            writtenKeys = 0;
        }
        if (WRITTEN.computeIfAbsent(player.getUUID(), id -> new HashSet<>()).add(key)) {
            writtenKeys++;
        }
    }
}
