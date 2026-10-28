package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.guzhenren.tianyi.bridge.GuzhenrenBridge;
import net.guzhenren.tianyi.will.Immortal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * 寿蛊的自然供给：把 {@link Immortal.LifespanSupply} 这个模型真正接到世界里。
 *
 * <h2>为什么需要这一层</h2>
 * 3.0.0 里 {@code LifespanSupply} 只是纯逻辑模型，游戏里**从来没有被调用过** ——
 * 也就是说「天意通过未来供给制约长生」当时是空话。这一层把它落地成：
 * 到周期就在野外成一颗寿蛊（谁捡到算谁的），歉收期可能这一轮一颗都不成。
 *
 * <h2>三条纪律</h2>
 * <ol>
 *   <li><b>只产出经审计的寿蛊。</b>ID 白名单来自本体
 *       {@code NpcStock.longevityIds()}（{@link GuzhenrenBridge#longevityIds()}）——
 *       也就是本体自己认定属于寿蛊的那些注册 ID。白名单拿不到时<b>一颗都不生成</b>，
 *       宁可这一轮空着，也不发出一个不是寿蛊的东西。</li>
 *   <li><b>不碰玩家背包。</b>产出是世界里的一件掉落物，可被任何人捡走。
 *       本类没有任何扣除或写入库存的代码。</li>
 *   <li><b>不动 NPC 库存。</b>本体 {@code NpcStock.restock(Mob)} 是它自己的补货节奏；
 *       我们不接管、不替换、也不重复给它加货 —— 那会造成双重结算。
 *       {@link #auditLines} 只做<b>清点</b>。</li>
 * </ol>
 *
 * <h2>寿蛊的全部来源（清点结果）</h2>
 * <table border="1">
 *   <tr><th>来源</th><th>谁负责</th><th>本模组是否干预</th></tr>
 *   <tr><td>自然演化（野外成蛊）</td><td><b>本模组</b>（本类）</td><td>只改周期与数量，不改库存</td></tr>
 *   <tr><td>NPC 库存补货</td><td>本体 {@code NpcStock.restock}</td><td><b>不干预</b>（只清点 ID）</td></tr>
 *   <tr><td>存量 / 传承 / 交易</td><td>本体与整合包既有流程</td><td><b>不干预</b></td></tr>
 *   <tr><td>炼制</td><td>本体炼蛊流程</td><td><b>不干预</b>（原著未给炼制法）</td></tr>
 * </table>
 */
public final class ShouGuSupply {
    private ShouGuSupply() {
    }

    /** 生成点距玩家/锚点的最小距离（格）：不在人脸上长出来。 */
    private static final int MIN_DIST = 32;
    private static final int MAX_DIST = 96;

    /**
     * 从本体取出寿蛊 ID 白名单并灌进供给模型。
     *
     * @return 审计到的 ID 数量；0 表示本体接口不可用（此时不会产出任何东西）
     */
    public static int ensureAudited(Immortal.LifespanSupply supply, Logger log) {
        Set<String> ids;
        try {
            ids = GuzhenrenBridge.longevityIds();
        } catch (RuntimeException | LinkageError e) {
            ids = new LinkedHashSet<>();
            if (log != null) {
                log.warn("[天意] 寿蛊来源审计失败（{}），本轮不产出寿蛊", e.toString());
            }
        }
        int n = supply.audit(ids);
        if (log != null && n > 0) {
            log.info("[天意] 寿蛊来源已审计：{} 个注册 ID（来自本体 NpcStock.longevityIds）", n);
        }
        return n;
    }

    /**
     * 每 200 刻调一次：到周期就成蛊。
     *
     * @return 本轮在世界里生成的寿蛊数量
     */
    public static int tick(MinecraftServer server, WillState state, long now, Logger log) {
        Immortal.LifespanSupply sup = state.shouGuSupply(now);
        if (sup.auditedCount() == 0) {
            // 还没审计过（例如桥接层不可用）→ 先审一次；仍然为空就什么都不做
            ensureAudited(sup, log);
            if (sup.auditedCount() == 0) {
                return 0;
            }
        }
        if (!sup.due(now)) {
            return 0;
        }
        int want = sup.settle(now);
        if (want <= 0) {
            // 歉收：这一轮自然没成蛊。不是"取消掉落"，而是本来就没有
            state.setDirty();
            return 0;
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            level.players().forEach(players::add);
        }
        if (players.isEmpty()) {
            return 0;
        }
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            ServerPlayer near = players.get((int) Math.floorMod(now + i, players.size()));
            ServerLevel level = (ServerLevel) near.level();
            BlockPos pos = pickSpot(level, near.blockPosition(), now + i * 5171L);
            if (pos == null) {
                continue;
            }
            String id = sup.pick(now + i);
            if (id.isEmpty() || !sup.allowed(id)) {
                continue; // 白名单为空/不含 → 不生成（绝不发错东西）
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null) {
                continue;
            }
            ItemEntity drop = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                    new ItemStack(item, 1));
            drop.setDefaultPickUpDelay();
            // 打标记：这是天意供给的寿蛊，便于事后审计与回滚
            drop.getPersistentData().putString("tianyi_shougu", id);
            level.addFreshEntity(drop);
            spawned++;
        }
        if (spawned > 0) {
            state.core().note("shougu.supply spawned=" + spawned
                    + " rate=" + String.format("%.2f", sup.rate));
            if (log != null) {
                log.info("[天意] 野外成蛊 {} 颗（供给速率 {}）", spawned,
                        String.format("%.2f", sup.rate));
            }
        }
        state.setDirty();
        return spawned;
    }

    /** 清点结果（给命令用）。 */
    public static List<String> auditLines(WillState state, long now) {
        Immortal.LifespanSupply sup = state.shouGuSupply(now);
        List<String> out = new ArrayList<>();
        out.add("寿蛊来源清点（本模组只干预第 1 条）");
        out.add("  1. 自然演化（野外成蛊）      本模组  审计ID=" + sup.auditedCount()
                + " 速率=" + String.format("%.2f", sup.rate)
                + " 下次=" + sup.nextSupplyAt + " 已产=" + sup.produced);
        out.add("  2. NPC 库存补货              本体 NpcStock.restock  不干预（只清点）");
        out.add("  3. 存量 / 传承 / 交易        本体与整合包既有流程   不干预");
        out.add("  4. 炼制                      本体炼蛊流程           不干预（原著未给炼制法）");
        out.add("  ID 白名单来源：NpcStock.longevityIds()");
        Set<String> ids = sup.auditedIds();
        if (ids.isEmpty()) {
            out.add("  （白名单为空：桥接层不可用，此时不会产出任何寿蛊）");
        } else {
            out.add("  " + ids);
        }
        return out;
    }

    /** 找一个已加载的地表点，距参照点 32–96 格。 */
    private static BlockPos pickSpot(ServerLevel level, BlockPos center, long seed) {
        double angle = (seed % 360L) * Math.PI / 180.0;
        int dist = MIN_DIST + (int) (Math.abs(Math.sin(seed * 0.00037)) * (MAX_DIST - MIN_DIST));
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // 只落在已加载区块：不为了成蛊去强制加载
        return level.isLoaded(pos) ? pos : null;
    }
}
