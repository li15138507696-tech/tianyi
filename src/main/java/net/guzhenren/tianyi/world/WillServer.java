package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.tianyi.will.Assessment;
import net.guzhenren.tianyi.will.Calamity;
import net.guzhenren.tianyi.will.Destiny;
import net.guzhenren.tianyi.will.Immortal;
import net.guzhenren.tianyi.will.Intent;
import net.guzhenren.tianyi.will.Knowledge;
import net.guzhenren.tianyi.will.WillConfig;
import net.guzhenren.tianyi.will.WillForce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * 世界天意的服务端权威调度器。
 *
 * <h2>调度原则（不每 tick 扫全世界）</h2>
 * <ul>
 *   <li>决策：每 {@link WillConfig#DECISION_PERIOD} 刻一轮（默认 200），单轮最多处理
 *       {@link WillConfig#DECISION_BATCH} 个参与者；</li>
 *   <li>灾劫/布局推进：每 20 刻一次，单次最多 {@link WillConfig#ACTIVE_JOBS_PER_TICK} 个；</li>
 *   <li>清理（人劫到期、过期情报、仙窍时间）：每 200 刻；</li>
 *   <li>不强制加载任何区块：只在已加载区域内做事，找不到落点就这一轮放弃。</li>
 * </ul>
 *
 * <h2>玩家可见性</h2>
 * 本类<b>不给普通玩家发任何天意相关提示</b>：没有好感度、没有威胁评分、没有干预清单、
 * 没有「奖励到账」。玩家能感知到的只有世界本身的变化（打雷、兽群、有人上门、地上多了一枚蛊）。
 */
public final class WillServer {
    private final Logger log;
    private int counter;

    public WillServer(Logger log) {
        this.log = log;
    }

    // ---------------------------------------------------------------- 生命周期

    /** 服务器启动后：取世界状态、布置载体、写一条日志。 */
    public void onServerStarted(MinecraftServer server) {
        WillState state = WillState.get(server);
        state.ensureCarriers(server);
        Destiny.DestinyGu d = state.core().destiny();
        log.info("[天意] 世界意志已就位：宿命={} 修复进度={} 星宿合道={} 载体={} 情报={}",
                d.stage(), String.format("%.2f", d.repairProgress()), d.starAligned(),
                state.carriers().count(), state.core().knowledge().size());
    }

    /** 每 tick：轻量计数；重活都在下面的周期里。 */
    public void onServerTick(MinecraftServer server) {
        counter++;
        long now = server.overworld().getGameTime();
        // 3.14：强度档位决定「多久决策一次、要不要真的压制、天象多密」。
        // 档位 0 时整条链路直接空转（不是靠概率压低，而是根本不做）。
        WillForce force = WillForceSettings.force();
        boolean pressure = WillForceSettings.pressureOn();
        // 情报：计划队列每 tick 推一小步（内部按 ACTIVE_JOBS_PER_TICK 限流）；
        // 传播/清理是 O(居民数) 的图扫描，只跟着决策周期走，不跟着 tick 走
        IntelState intel = IntelState.get(server);
        if (pressure) intel.onTick(server, now);
        int period = Math.max(20, force.decisionPeriod());
        if (counter % period == 0) {
            runDecision(server);
            intel.propagate(server, now);
            // 情报 → 行动：把「谁知道了什么」变成「谁打算做什么」。
            // 没有这一步，布局队列永远是空的，调查/追捕写了也不会跑。
            int planned = pressure ? intel.consider(server, now) : 0;
            if (planned > 0) {
                log.info("[天意·情报] 本轮排入 {} 条布局（事实 {} 条 / 持有者 {} 人）",
                        planned, intel.facts().size(), intel.net().holderCount());
            }
        }
        if (counter % 20 == 0 && pressure) {
            advanceJobs(server);
        }
        // 世界现象（天象/雷/产地枯损/兽潮）：档位 ≥ 1 才有，且只落在被载体看见的在线玩家身上
        int phenomenaPeriod = Math.max(20, force.phenomenaPeriod());
        if (counter % phenomenaPeriod == 0) {
            try {
                WillPressure.tick(server, WillState.get(server), force, now, log);
            } catch (RuntimeException e) {
                log.warn("[天意·压迫] 现象巡查异常（已跳过本刻）：{}", e.toString());
            }
        }
        if (counter % 200 == 0) {
            housekeeping(server);
        }
    }

    // ---------------------------------------------------------------- 决策

    private void runDecision(MinecraftServer server) {
        WillState state = WillState.get(server);
        long now = server.overworld().getGameTime();
        WillForce force = WillForceSettings.force();
        // 3.14：档位 0 = 零干预。连观测评估都不做，确保「关闭」是真的关闭。
        if (force.isOff()) {
            return;
        }
        Assessment.Situation s = Observation.observe(server, state, now);
        s.withForce(force);

        // B01：先把已经成熟的机缘兑现掉 —— 旧机缘要有东西管，不靠"有人被扶持的那一轮"
        try {
            int settled = EncounterSettings.legacyResources()
                    ? WorldExec.settleDueOpportunities(server, state, now) : 0;
            if (settled > 0 && log.isDebugEnabled()) {
                log.debug("[天意] 兑现成熟机缘 {} 处", settled);
            }
        } catch (RuntimeException e) {
            log.warn("[天意] 兑现机缘失败：{}", e.toString());
        }

        if (s.actors.isEmpty()) {
            return; // 没人就不决策：天意的动作都要作用在具体对象上
        }

        // B03：每轮按有效情报刷新姿态。压制过的人变弱后要能转回扶持，
        // 否则 switchPosture 实现了却永远不被调用，压制会变成一条单行道。
        Assessment.Report report = Assessment.assess(s);
        for (Assessment.Actor a : s.actors) {
            if (state.core().switchPosture(report, a.id, force) && log.isDebugEnabled()) {
                log.debug("[天意] 姿态变化 {} → {}", a.id, state.core().postureOf(a.id));
            }
        }

        List<Intent> intents;
        try {
            intents = state.core().decide(s);
        } catch (RuntimeException e) {
            log.error("[天意] 决策异常（已跳过本轮）", e);
            return;
        }
        int executed = 0;
        boolean pressure = WillForceSettings.pressureOn();
        for (Intent i : intents) {
            // 压制渠道（SUPPRESS / HOLD_HEAVEN 及其布局、灾劫推进）由旧开关或档位 ≥ 2 放开
            if (!pressure
                    && (i.kind == Intent.Kind.SUPPRESS || i.kind == Intent.Kind.HOLD_HEAVEN)) continue;
            if (executed >= force.decisionBatch()) {
                break;
            }
            ServerLevel level = levelOf(server, i.target);
            if (level == null) {
                continue;
            }
            boolean ok = false;
            try {
                ok = WorldExec.execute(level, state, i, s);
            } catch (RuntimeException e) {
                log.warn("[天意] 执行 {} 失败：{}", i.kind, e.toString());
            }
            if (ok) {
                executed++;
                // B04：配额只在**真的执行成功**后结算；失败不耗机会
                if (i.kind == Intent.Kind.SUPPRESS && !i.target.isEmpty()) {
                    state.core().noteActionExecuted(i.target, now);
                }
            }
        }
        if (executed > 0) {
            state.setDirty();
            if (log.isDebugEnabled()) {
                log.debug("[天意] 本轮执行 {} 条意图 / 共 {} 条 / 预算 {}",
                        executed, intents.size(), String.format("%.2f", state.core().budget()));
            }
        }
        // 因果记录只进服务端日志（普通玩家看不到）
        if (log.isTraceEnabled()) {
            for (String c : state.core().causes()) {
                log.trace("[天意·因果] {}", c);
            }
        }
    }

    /** 找意图目标所在的维度；目标不在线就放弃（天意不跨维度凭空施为）。 */
    private ServerLevel levelOf(MinecraftServer server, String target) {
        try {
            ServerPlayer p = server.getPlayerList().getPlayer(java.util.UUID.fromString(target));
            return p == null ? null : (ServerLevel) p.level();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 布局 / 灾劫推进

    private void advanceJobs(MinecraftServer server) {
        WillState state = WillState.get(server);
        long now = server.overworld().getGameTime();
        int budget = WillConfig.ACTIVE_JOBS_PER_TICK;

        for (Calamity.Run run : state.core().calamities().active()) {
            if (budget-- <= 0) {
                break;
            }
            ServerLevel level = levelByKey(server, run.dim);
            if (level == null) {
                continue;
            }
            ServerPlayer target = playerByUuid(server, run.target);
            double defense = target == null ? 0.0 : defenseOf(target);
            if (target != null && !run.prepared) {
                run.prepared = defense > 0.6;
            }
            Calamity.Phase before = run.phase;
            run.tick(now, defense);
            applyCalamityPhase(level, run, target, before, now);
        }

        // 布局推进：距离按「布局建立时的锚点」到「目标当前位置」实算。
        // 玩家跑远 / 换维度 / 藏进盲区，都会让酝酿中的布局真的作废。
        for (var ploy : state.core().ploys().active()) {
            if (budget-- <= 0) {
                break;
            }
            ServerPlayer target = playerByUuid(server, ploy.target);
            double dist;
            boolean channel;
            boolean hasIntel;
            if (target == null) {
                dist = Double.MAX_VALUE;
                channel = false;
                hasIntel = false;
            } else {
                String dim = target.level().dimension().location().toString();
                dist = sameDim(ploy.anchorDim, dim)
                        ? distance(ploy.anchorPos, target.blockPosition().asLong())
                        : Double.MAX_VALUE;
                // B10 修复：以前 node == null 被当成「渠道可用」，
                // 等于维度里根本没有载体也照常推进布局。现在必须有载体且触达够。
                var node = state.carriers().get("region:" + dim);
                channel = node != null && node.effectiveReach(now) > 0.1;
                // B10 修复：以前「没被 cover」就等于「有情报」，
                // 载体不存在、情报过期时布局还在用实时坐标。现在要求真的有一条**新鲜**情报。
                var tracked = state.core().knowledge().track(
                        target.getUUID().toString(),
                        net.guzhenren.tianyi.will.Knowledge.Kind.POWER, now, Double.NaN);
                hasIntel = tracked.live() && tracked.confidence() > 0.0;
            }
            var verdict = ploy.evaluate(now, new net.guzhenren.tianyi.will.Ploy.Signals(
                    target == null || !hasIntel, hasIntel, dist, channel,
                    target == null ? 0.0 : Observation.powerOf(target)));
            if (verdict == net.guzhenren.tianyi.will.Ploy.Verdict.FAILED) {
                state.core().onPloyFailed(ploy.id, ploy.target, now);
                state.setDirty();
            } else if (verdict == net.guzhenren.tianyi.will.Ploy.Verdict.SUCCEEDED) {
                // 酝酿走完 → 真正落下世界效果（只落一次）
                ServerLevel level = target != null ? (ServerLevel) target.level() : null;
                if (level != null) {
                    WorldExec.applyPloyEffect(level, state, ploy, now);
                }
            }
        }
    }

    /** 把灾劫阶段变成世界里的真实效果。 */
    private void applyCalamityPhase(ServerLevel level, Calamity.Run run, ServerPlayer target,
                                    Calamity.Phase before, long now) {
        if (run.phase == before) {
            return;
        }
        BlockPos center = target != null ? target.blockPosition() : unpack(run.pos);
        switch (run.phase) {
            case GESTATING -> {
                // 酝酿：天象先变，玩家能看到征兆却还不知道是什么
                if (run.spec.tier.ordinal() >= Calamity.Tier.TIAN_JIE.ordinal()) {
                    level.setWeatherParameters(0, 2400, true, true);
                }
                cues(level, center, run.spec);
            }
            case MANIFEST -> {
                cues(level, center, run.spec);
                // 显形：浩劫以上带兽群助势（真实实体，不是贴图）
                if (run.spec.tier.ordinal() >= Calamity.Tier.HAO_JIE.ordinal()) {
                    int n = 2 + (int) (run.spec.scale * 3);
                    for (int i = 0; i < n; i++) {
                        BlockPos p = Helpers.surface(level, center, 8 + i * 3);
                        if (p != null) {
                            WorldExec.spawnEntity(level, "guzhenren:hunshou_1", p);
                        }
                    }
                }
                if (run.spec.tier.ordinal() >= Calamity.Tier.TIAN_JIE.ordinal()) {
                    BlockPos p = Helpers.surface(level, center, 4);
                    if (p != null) {
                        EntityType<?> bolt = EntityType.LIGHTNING_BOLT;
                        Entity e = bolt.create(level);
                        if (e != null) {
                            e.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
                            level.addFreshEntity(e);
                        }
                    }
                }
            }
            case CLIMAX -> cues(level, center, run.spec);
            case AFTERMATH -> settleCalamity(level, run, target);
            default -> {
            }
        }
    }

    /** 余波：结算伤害与道痕（失败只扣道痕/寿元，不直接抹杀）。 */
    private void settleCalamity(ServerLevel level, Calamity.Run run, ServerPlayer target) {
        if (target == null) {
            return;
        }
        if (run.damageDealt > 0.02) {
            DamageSource src = level.damageSources().magic();
            float dmg = (float) (run.damageDealt * 12.0);
            target.hurt(src, dmg);
        }
        PlayerVariables v = target.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (run.daoMarks > 0) {
            v.dahen_tiandao += run.daoMarks;
        } else if (run.daoMarks < 0) {
            // 失败：扣天道道痕，并按比例折算寿元（不抹杀）
            v.dahen_tiandao = Math.max(0.0, v.dahen_tiandao + run.daoMarks);
            v.shouyuan = Math.max(1.0, v.shouyuan - Math.abs(run.daoMarks) * 0.5);
        }
        log.info("[天意] 灾劫结算：{} 目标={} 伤害={} 道痕={}",
                run.spec.tier.cn, target.getName().getString(),
                String.format("%.2f", run.damageDealt), String.format("%.2f", run.daoMarks));
        // 观象·亲见：这一场自己确实挨过了（渡过/受创是两回事，都如实记）
        long gt = level.getGameTime();
        if (JournalStore.noteCalamity(target, run.spec.tier.cn, run.daoMarks >= 0, gt)) {
            net.guzhenren.tianyi.net.JournalNet.sync(target);
        }
    }

    /** 征兆：声音与粒子式的世界反馈（不弹字）。 */
    private void cues(ServerLevel level, BlockPos center, Calamity.Spec spec) {
        for (String key : spec.effectKeys) {
            if (key.startsWith("env:")) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                        8, 1.5, 0.5, 1.5, 0.01);
            }
        }
    }

    // ---------------------------------------------------------------- 清理

    private void housekeeping(MinecraftServer server) {
        WillState state = WillState.get(server);
        long now = server.overworld().getGameTime();
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (LivingEntity le : WorldExec.expiredRenjie(level, now)) {
                le.discard();
                removed++;
            }
            // 仙窍内部时间
            for (ServerPlayer p : level.players()) {
                Immortal.Aperture ap = state.apertureIfPresent(p.getUUID());
                if (ap != null) {
                    ap.advanceTime(200L);
                }
            }
        }
        state.core().knowledge().purge(now);
        state.core().opportunities().tick(now);
        // 寿蛊的自然供给：到周期就在野外成蛊（只产出经审计的寿蛊 ID，不碰任何库存）
        ShouGuSupply.tick(server, state, now, log);
        // 野生兽群：种群增长与迁徙（生成一只就少一只，绝不凭空刷）
        BeastHerds.census(server, state, now, log);
        // 资源产地：按周期把储量变成地上的货（没人靠近就不产，储量攒到上限）
        if (now % WorldSites.PRODUCE_PERIOD < 200L) {
            WorldSites.produce(server, state, now, log);
            // 物流：到点的批次结算（幂等，重启也不会发两遍）
            WorldSites.deliver(server, state, now, log);
        }
        if (removed > 0) {
            log.debug("[天意] 收回 {} 名人劫执行者", removed);
        }
        state.setDirty();
    }

    // ---------------------------------------------------------------- 观测钩子

    /** 玩家登录：确保有仙窍记录，并让天意「重新接触」一次（不是无条件全知）。 */
    public void onPlayerLogin(ServerPlayer player) {
        WillState state = WillState.get(player.getServer());
        state.aperture(player.getUUID());
        long now = player.serverLevel().getGameTime();
        String dim = player.level().dimension().location().toString();
        double reach = state.carriers().reachAt(dim, player.blockPosition().asLong(), now, 1024.0);
        if (reach > 0.15) {
            Observation.noteDefiance(state.core().knowledge(), player, 0.0, now, dim);
        }
        state.setDirty();
    }

    /**
     * 玩家击杀本体妖兽/修士：天意只有在看得到的时候才知道这件事。
     */
    public void onPlayerKill(ServerPlayer killer, LivingEntity victim) {
        WillState state = WillState.get(killer.getServer());
        long now = killer.serverLevel().getGameTime();
        String dim = killer.level().dimension().location().toString();
        double reach = state.carriers().reachAt(dim, killer.blockPosition().asLong(), now, 1024.0);
        int tier = net.guzhenren.tianyi.TianyiBestiary.tierOf(victim);
        // 普通猎兽属于世界行为：记种群和本人见闻，不据此记逆天或损宿命。
        // 杀戮记到兽群账上：猎杀真的会让种群减少，杀到见底就崩溃
        BeastHerds.recordKill(state, victim, now);
        // 藏识·亲见：亲手打过的兽类，特征才算真的知道
        boolean journalDirty = JournalStore.noteBeast(killer,
                net.guzhenren.tianyi.TianyiBestiary.idOf(victim),
                net.guzhenren.tianyi.TianyiBestiary.tierName(tier), now);
        // 身份证据：只要有载体看着，这次杀戮就作为一条「目击」写进身份案
        if (reach > 0.15) {
            state.identity().of(killer.getUUID().toString()).add(
                    new net.guzhenren.tianyi.will.IdentityCase.Evidence(
                            "witness-" + victim.getUUID(), killer.getUUID().toString(),
                            net.guzhenren.tianyi.will.IdentityCase.Kind.WITNESS,
                            0.8 + 0.2 * tier, 0.9,
                            "killer", dim, killer.blockPosition().asLong(), now,
                            "当众杀了" + net.guzhenren.tianyi.TianyiBestiary.tierName(tier)),
                    2400L);
            // 行藏·亲见：这次当众动手，自己身上也多了一条痕迹
            journalDirty |= JournalStore.noteTrace(killer,
                    "当众击杀" + net.guzhenren.tianyi.TianyiBestiary.tierName(tier), now);
        }
        // 普通猎兽（包括兽王）不改变宿命；仅保留真实种群与本人证据。
        state.setDirty();
        if (journalDirty) {
            net.guzhenren.tianyi.net.JournalNet.sync(killer);
        }
    }

    /** 玩家死亡：削弱其仙窍稳定性，并把这件事记成天意看得到的事件。 */
    public void onPlayerDeath(ServerPlayer player) {
        WillState state = WillState.get(player.getServer());
        long now = player.serverLevel().getGameTime();
        Immortal.Aperture ap = state.aperture(player.getUUID());
        ap.damage(0.12);
        String dim = player.level().dimension().location().toString();
        if (state.carriers().reachAt(dim, player.blockPosition().asLong(), now, 1024.0) > 0.15) {
            Observation.noteDefiance(state.core().knowledge(), player, 0.1, now, dim);
        }
        state.setDirty();
    }

    /** 玩家改维度：天意是否跟得过去，取决于目标维度有没有载体。 */
    public void onPlayerChangedDimension(ServerPlayer player) {
        WillState state = WillState.get(player.getServer());
        long now = player.serverLevel().getGameTime();
        String dim = player.level().dimension().location().toString();
        double reach = state.carriers().reachInDim(dim, now);
        if (reach <= 0.15) {
            // 目标维度没有载体：天意在这里是瞎子，先记一条"失去目标"
            state.core().knowledge().cover(player.getUUID().toString(), now,
                    WillConfig.BLIND_MIN_TICKS, "no-carrier-in-dimension");
        }
        state.setDirty();
    }

    // ---------------------------------------------------------------- 机缘被捡走

    /**
     * 捡走某个「机缘」：登记领取者，并在诱饵被识破时给出天象反应。
     *
     * <p>两件事在这里同时发生：
     * <ol>
     *   <li><b>机缘可被截取</b>：谁的 UUID 先登记上就是谁的，天意原本想给的人拿不到。</li>
     *   <li><b>道破即震怒</b>（原著：有人道破天意布局 → 悍雷闪电）：诱饵被捡走意味着
     *       棋路被看穿，天意以局部天象回应。注意这只是<b>天象</b>，不是直接劈死玩家。</li>
     * </ol>
     */
    public void onItemPickedUp(ServerPlayer player, Entity item) {
        WillState state = WillState.get(player.getServer());
        long now = player.serverLevel().getGameTime();

        // ① 产地出产的货：登记归属/掠夺，并在诱饵第一批被捡走时给出天象反应
        String siteId = item.getPersistentData().getString("tianyi_site");
        if (!siteId.isEmpty() && EncounterSettings.legacyResources()) {
            var site = state.sites().get(siteId);
            if (site != null) {
                if (!site.owned()) {
                    site.reassign(player.getUUID().toString(), now);
                } else if (!site.ownedBy(player.getUUID().toString())) {
                    state.core().note("site.poach " + siteId + " by=" + player.getUUID());
                }
                // 观象·亲见：只有产地归属人本人，才把「这次出货」记进自己的见闻录
                if (item instanceof net.minecraft.world.entity.item.ItemEntity ie
                        && site.ownedBy(player.getUUID().toString())) {
                    var stack = ie.getItem();
                    var jkey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem());
                    if (jkey != null && JournalStore.noteSiteYield(player, siteId,
                            jkey.toString(), Math.max(1, stack.getCount()), now)) {
                        net.guzhenren.tianyi.net.JournalNet.sync(player);
                    }
                }
                // 拾取只证明取得物品，不证明道破诱饵；不触发雷雨。
                state.setDirty();
            }
            return;
        }
        // ② 一次性机缘（旧路径）：登记领取者，诱饵被识破同样有天象反应
        String offerId = item.getPersistentData().getString("tianyi_offer");
        if (offerId.isEmpty()) {
            return;
        }
        var offer = state.core().opportunities().get(offerId);
        if (offer == null) {
            return;
        }
        boolean claimed = state.core().opportunities()
                .claim(offerId, player.getUUID().toString(), now);
        if (!claimed) {
            return;
        }
        state.core().note("opportunity.claim " + offerId + " → " + player.getUUID()
                + (offer.intercepted() ? " (被非预期对象截取)" : ""));
        // 拾取不等于识破，旧机缘领取也不触发雷雨。
        state.setDirty();
    }

    /** 道破即震怒：局部雷雨 + 一记偏离的闪电（不劈死玩家）。 */
    private void thunder(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setWeatherParameters(0, 600, true, true);
        BlockPos p = player.blockPosition().offset(
                level.getRandom().nextInt(17) - 8, 0, level.getRandom().nextInt(17) - 8);
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            level.addFreshEntity(bolt);
        }
    }

    // ---------------------------------------------------------------- 工具

    private static double defenseOf(ServerPlayer player) {
        Observation.Probe p = Observation.probe(player);
        double rankPart = Math.min(1.0, p.rank() / 8.0);
        double daohenPart = Math.min(1.0, p.daohenTotal() / 2000.0);
        return Math.min(1.0, rankPart * 0.6 + daohenPart * 0.4);
    }

    /** 两个维度键是否相同（空串视为未知 → 不同）。 */
    private static boolean sameDim(String a, String b) {
        return a != null && !a.isEmpty() && a.equals(b);
    }

    /** 打包坐标之间的距离（位布局与 BlockPos.asLong 一致）。 */
    private static double distance(long a, long b) {
        if (a == Knowledge.Fact.NO_POS || b == Knowledge.Fact.NO_POS) {
            return Double.MAX_VALUE;
        }
        int ax = (int) (a >> 38);
        int az = (int) ((a << 26) >> 38);
        int ay = (int) ((a << 52) >> 52);
        int bx = (int) (b >> 38);
        int bz = (int) ((b << 26) >> 38);
        int by = (int) ((b << 52) >> 52);
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static ServerLevel levelByKey(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(dim)) {
                return l;
            }
        }
        return null;
    }

    private static ServerPlayer playerByUuid(MinecraftServer server, String uuid) {
        try {
            return server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BlockPos unpack(long packed) {
        int x = (int) (packed >> 38);
        int z = (int) ((packed << 26) >> 38);
        int y = (int) ((packed << 52) >> 52);
        return new BlockPos(x, y, z);
    }

    /** 供命令查询的摘要。 */
    public List<String> statusLines(MinecraftServer server) {
        WillState state = WillState.get(server);
        List<String> out = new ArrayList<>();
        var st = state.core().status();
        out.add("宿命=" + st.get("stage") + " 修复进度=" + fmt(st.get("repairProgress"))
                + " 天道权限=" + fmt(st.get("heavenControl")));
        out.add("星宿=" + st.get("starGoal") + " 份额=" + fmt(st.get("starShare")));
        out.add("意志余量=" + fmt(st.get("budget")) + " 情报=" + st.get("facts")
                + " 载体=" + state.carriers().count()
                + " 布局=" + st.get("ploys") + " 灾劫=" + st.get("calamities")
                + " 机缘=" + st.get("opportunities"));
        out.add("结算键=" + state.settledCount() + " 快照=" + state.history().size());
        // 3.14：档位只在管理员摘要里出现，玩家侧没有任何面板/播报
        WillForce force = WillForceSettings.force();
        out.add("天意强度=" + force.intensity() + "（" + force.label() + "）"
                + " 压制渠道=" + (WillForceSettings.pressureOn() ? "开" : "关")
                + " 决策周期=" + force.decisionPeriod()
                + " 现象周期=" + force.phenomenaPeriod());
        return out;
    }

    private static String fmt(Object o) {
        if (o instanceof Double d) {
            return String.format("%.2f", d);
        }
        return String.valueOf(o);
    }

    /** 供灾劫显形时找地表点。 */
    static final class Helpers {
        private Helpers() {
        }

        static BlockPos surface(ServerLevel level, BlockPos center, int dist) {
            int x = center.getX() + (int) (Math.cos(dist) * dist * 2);
            int z = center.getZ() + (int) (Math.sin(dist) * dist * 2);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos p = new BlockPos(x, y, z);
            return level.isLoaded(p) ? p : null;
        }

        /** 按 AABB 找附近实体（后续做「势力牵制」时用）。 */
        static List<Entity> nearby(ServerLevel level, BlockPos center, double r) {
            return level.getEntitiesOfClass(Entity.class, new AABB(center).inflate(r));
        }

        /** 本体实体 id 是否已注册。 */
        static boolean exists(String id) {
            return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)) != null;
        }

        static boolean isMob(Entity e) {
            return e instanceof Mob;
        }
    }
}
