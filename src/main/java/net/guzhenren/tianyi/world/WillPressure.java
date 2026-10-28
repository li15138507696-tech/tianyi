package net.guzhenren.tianyi.world;

import net.guzhenren.tianyi.will.WillForce;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

/**
 * 天意压迫感：把档位变成<b>玩家看得见的世界现象</b>（3.14）。
 *
 * <h2>这里只做「现象」，不做「说明」</h2>
 * 本类<b>不给玩家发任何文字</b>：不聊天栏、不标题、不弹窗、不下发后台评分。
 * 玩家能知道的只有三件事：
 * <ol>
 *   <li>天变了（转阴、闷雷、偏离的落雷）；</li>
 *   <li>最近那处产地忽然枯了（{@link net.guzhenren.tianyi.will.ResourceSite#scramble} 真的扣了储量）；</li>
 *   <li>兽群朝这边挪了过来（从 {@link net.guzhenren.tianyi.will.BeastHerd} 的种群账上赶出来的，
 *       赶一只少一只）。</li>
 * </ol>
 * 这三件事都会进<b>玩家自己的</b>《见闻录》({@link JournalStore#notePressure}) ——
 * 是「他自己观察到的」，不是天意告诉他的。
 *
 * <h2>不做的事（硬约束）</h2>
 * <ul>
 *   <li>不刷物品、不给钱、不写玩家背包：产地枯损只减储量，兽群只搬已有的个体；</li>
 *   <li>不强制加载区块、不全地图扫描：只看已加载位置里、被载体覆盖的在线玩家；</li>
 *   <li>不新增灾劫种类、不新增兽种：兽只从 {@link BeastHerds} 走；灾劫只在
 *       {@link WorldExec} 里开既有四等。</li>
 * </ul>
 *
 * <h2>幂等</h2>
 * 每一次现象都由 {@link WillForce#fires} 决定「是不是本窗口唯一的那一次」，
 * 该判定只依赖（窗口号、主体、种类）三个确定性输入，因此同一窗口不会重复发生，
 * 重启也不会补发一次 —— 不需要额外的运行时状态表。
 */
public final class WillPressure {
    /** 与 {@link Observation} 同口径的「天意真的看得见」门限。 */
    private static final double SEE_FLOOR = 0.15;

    private WillPressure() {
    }

    /**
     * 巡查一次。由 {@link WillServer} 按 {@link WillForce#phenomenaPeriod()} 调用。
     *
     * @return 本刻真正落下的现象数（只用于日志，不发给玩家）
     */
    public static int tick(MinecraftServer server, WillState state, WillForce force,
                           long now, Logger log) {
        if (server == null || state == null || force == null || !force.phenomenaEnabled()) {
            return 0;
        }
        double authority = Math.min(1.0, Math.max(0.0,
                state.core().destiny().heavenControl()));
        int marks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                marks += pressureOn(level, state, force, player, now, authority);
            }
        }
        if (marks > 0) {
            state.setDirty();
            log.debug("[天意·压迫] {} 档位落点 {} 处 @{}", force.label(), marks, now);
        }
        return marks;
    }

    /** 对单个玩家施加一次现象（幂等：同一窗口最多命中一次）。 */
    private static int pressureOn(ServerLevel level, WillState state, WillForce force,
                                  ServerPlayer player, long now, double authority) {
        String dim = level.dimension().location().toString();
        String id = player.getUUID().toString();
        BlockPos pos = player.blockPosition();
        // 天意不是全知：这个区域的载体看不见他，就什么都不会发生
        double reach = state.carriers().reachAt(dim, pos.asLong(), now, 1024.0);
        if (reach <= SEE_FLOOR) {
            return 0;
        }
        int out = 0;
        // 「被盯上」时天象更容易落下来：这里读的是天意自己已经记下的姿态（内部判定），
        // 只是让世界现象更对得上「有人在针对我」的直觉，不向玩家解释任何东西。
        boolean watched = state.core().postureOf(id)
                == net.guzhenren.tianyi.will.Intent.Posture.SUPPRESS;
        double omenWeight = watched ? 2.0 : 1.0;
        // ① 天象：只改天气，不改任何账
        if (force.fires("omen", id, now, WillForce.OMEN_WINDOW)
                && WillForce.roll("omen", id, now / WillForce.OMEN_WINDOW)
                < force.omenChance() * authority * omenWeight) {
            level.setWeatherParameters(0, force.omenTicks(), true, force.omenThunders());
            if (JournalStore.notePressure(player, "omen", omenWords(now), now)) {
                net.guzhenren.tianyi.net.JournalNet.sync(player);
            }
            out++;
        }
        // ② 偏离的落雷（档位 ≥ 2）：落在离他 5~12 格外，方向由确定性哈希决定
        if (force.boltChance() > 0.0
                && force.fires("bolt", id, now, WillForce.BOLT_WINDOW)
                && WillForce.roll("bolt", id, now / WillForce.BOLT_WINDOW)
                < force.boltChance() * authority) {
            if (strike(level, player, now)) {
                out++;
            }
        }
        // ③ 产地枯损（档位 ≥ 2 更常见）：只打散**真的存在**的产地的储量
        if (force.spoilChance() > 0.0
                && force.fires("spoil", id, now, WillForce.SPOIL_WINDOW)
                && WillForce.roll("spoil", id, now / WillForce.SPOIL_WINDOW)
                < force.spoilChance() * authority) {
            int hit = state.sites().scrambleNear(dim, pos.asLong(),
                    force.spoilRadius(), force.spoilRatio(), now);
            if (hit > 0) {
                state.core().note("force.spoil " + hit + " sites near " + id);
                if (JournalStore.notePressure(player, "spoil", "那片蛊材忽然枯了", now)) {
                    net.guzhenren.tianyi.net.JournalNet.sync(player);
                }
                out++;
            }
        }
        // ④ 兽潮（档位 3）：只能从真实兽群里赶，种群不够就是 0，绝无凭空生成
        if (force.beastWaveEnabled()
                && force.fires("wave", id, now, WillForce.WAVE_WINDOW)
                && WillForce.roll("wave", id, now / WillForce.WAVE_WINDOW)
                < force.beastWaveChance() * authority) {
            int spawned = BeastHerds.driveToward(state, level, player, now, force.beastWaveWant());
            if (spawned > 0) {
                state.core().note("force.wave " + spawned + " near " + id);
                if (JournalStore.notePressure(player, "wave", "兽群忽然朝这边聚过来了", now)) {
                    net.guzhenren.tianyi.net.JournalNet.sync(player);
                }
                out++;
            } else {
                // 种群不足：如实记成「没出兽」，不兜底补一只
                state.core().note("herd.empty @force-wave dim=" + dim);
            }
        }
        return out;
    }

    /** 一记偏离的落雷：不劈在玩家身上，也不强制加载区块。 */
    private static boolean strike(ServerLevel level, ServerPlayer player, long now) {
        double a = WillForce.roll("bolt-angle", player.getUUID().toString(), now / 400L)
                * Math.PI * 2.0;
        double d = 5.0 + WillForce.roll("bolt-dist", player.getUUID().toString(), now / 400L) * 7.0;
        BlockPos base = player.blockPosition();
        BlockPos p = new BlockPos(base.getX() + (int) Math.round(Math.cos(a) * d),
                base.getY(), base.getZ() + (int) Math.round(Math.sin(a) * d));
        if (!level.isLoaded(p)) {
            return false;
        }
        Entity bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return false;
        }
        bolt.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(bolt);
        return true;
    }

    /** 天象的措辞：只描述看见的，不解释缘由。 */
    private static String omenWords(long now) {
        String[] pool = {
                "天忽然阴了下来",
                "远处闷雷不断",
                "风向莫名其妙地反了",
                "云压得很低",
                "这天色不像时候",
        };
        int i = (int) Math.floorMod(now / WillForce.OMEN_WINDOW, pool.length);
        return pool[i];
    }
}
