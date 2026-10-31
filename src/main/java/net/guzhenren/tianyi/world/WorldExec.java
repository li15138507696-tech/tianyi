package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.guzhenren.tianyi.will.Assessment;
import net.guzhenren.tianyi.will.Calamity;
import net.guzhenren.tianyi.will.Intent;
import net.guzhenren.tianyi.will.Opportunity;
import net.guzhenren.tianyi.will.Ploy;
import net.guzhenren.tianyi.will.WillConfig;
import net.guzhenren.tianyi.will.WillForce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 执行层：把天意的意图变成<b>世界里真实发生的事</b>。
 *
 * <h2>三条纪律</h2>
 * <ol>
 *   <li><b>不往背包塞东西。</b>扶持只在世界里生成可以被人捡走的实物（掉落物），
 *       谁先到就是谁的 —— 这样才能被截取，也才有「机缘」的味道。</li>
 *   <li><b>不刷在脸上。</b>所有生成都在目标周围一段距离之外，并且有数量上限；
 *       兽群走本体原生生物的自然刷新/自然消失，不做无限刷怪。</li>
 *   <li><b>不凭空造物。</b>用到的每个物品/实体 id 都是从本体 jar 里核对出来的
 *       注册 ID（见各池注释），而不是猜的名字。</li>
 * </ol>
 *
 * <h2>已实现 / 未实现的渠道</h2>
 * <table border="1">
 *   <tr><th>渠道</th><th>落地形式</th><th>状态</th></tr>
 *   <tr><td>RESOURCE</td><td>地表生成未炼化蛊 / 元老蛊（可被任何人捡）</td><td>已实现</td></tr>
 *   <tr><td>HERITAGE</td><td>地表生成蛊方（传承线索的实物载体）</td><td>已实现</td></tr>
 *   <tr><td>RUMOR</td><td>人劫：生成 1~2 名本体修士 NPC 上门，限时后离开</td><td>已实现（仅用于压制）</td></tr>
 *   <tr><td>BEAST</td><td>兽群：周围生成本体一阶妖兽，自然刷新/消失</td><td>已实现</td></tr>
 *   <tr><td>CALAMITY</td><td>地灾/天劫/浩劫/万劫，四阶段推进</td><td>已实现</td></tr>
 *   <tr><td>FACTION</td><td>嫁祸：用本体 {@code guzhenren_library} 的真实势力系统
 *       （{@code FactionBrain.blame}）在两大势力间留下由头，冲突由本体 AI 自己升级</td>
 *       <td>已实现（依赖 guzhenren_library）</td></tr>
 *   <tr><td>CARRIER</td><td>只参与感知与消耗，不作为扶持产出</td><td>按设计</td></tr>
 * </table>
 */
public final class WorldExec {
    private WorldExec() {
    }

    /**
     * 未炼化蛊 / 元老蛊：从本体 jar 的 {@code data/guzhenren/tags/item/dushi.json}
     * （97 项）与 {@code yuanshi.json}（8 项）里抽出的代表项，<b>id 已核对存在</b>。
     */
    private static final String[] WILD_GU_POOL = {
            "guzhenren:wei_lian_hua_cun_guang_yin",
            "guzhenren:wei_lian_hua_yu_she_gu",
            "guzhenren:wei_lian_hua_shui_guang_gu",
            "guzhenren:wei_lian_hua_shi_pi_gu",
            "guzhenren:weilianhuakongsuogu",
            "guzhenren:yuan_lao_gu_1",
            "guzhenren:yuan_lao_gu_4",
    };

    /** 蛊方（传承线索的实物）。取自 {@code tags/item/gufang.json}。 */
    private static final String[] GU_FANG_POOL = {
            "guzhenren:suoshigu_gu_fang",
            "guzhenren:xinghuoliaoyuan_gu_gu_fang",
            "guzhenren:pan_shi_gu_gu_fang",
            "guzhenren:ba_dao_zhan_gu_gu_fang",
            "guzhenren:aibieligufang",
    };

    /**
     * 普通蛊兽池：名录里的 1–2 阶（特殊种 + 百兽）。
     *
     * <p>3.10.0 起不再写死两只，而是从 {@link net.guzhenren.tianyi.will.BeastCatalog}
     * 派生 —— 名录每一条都由 audit_bestiary.py 对过本体 jar。
     */
    private static final String[] BEAST_POOL = pools(1, 2);

    /** 兽王池：3–5 阶（千兽 / 万兽 / 兽皇），高威胁时才出。 */
    private static final String[] BEAST_ELITE = pools(3, 4, 5);

    /** 从名录取若干阶的全部 id。 */
    private static String[] pools(int... tiers) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int t : tiers) {
            for (var e : net.guzhenren.tianyi.will.BeastCatalog.byTier(t)) {
                out.add(e.id());
            }
        }
        return out.toArray(new String[0]);
    }

    /** 本体修士 NPC（人劫的执行者）。 */
    private static final String[] NPC_POOL = {
            "guzhenren:renwunpc_3",
            "guzhenren:renwunpc_5",
    };

    /** 生成物距目标的最小距离（格）：不刷在脸上。 */
    private static final int MIN_SPAWN_DISTANCE = 24;
    private static final int MAX_SPAWN_DISTANCE = 56;

    /** 人劫 NPC 的存活时长（刻）。 */
    public static final long RENJIE_LIFETIME = 24_000L;

    // ---------------------------------------------------------------- 总入口

    /**
     * 执行一条意图。
     *
     * @return 是否在世界里真的做了事
     */
    public static boolean execute(ServerLevel level, WillState state, Intent intent,
                                  Assessment.Situation s) {
        if (intent == null) {
            return false;
        }
        long now = level.getGameTime();
        return switch (intent.kind) {
            case SUPPORT -> support(level, state, intent, s, now);
            case SUPPRESS -> suppress(level, state, intent, s, now);
            case OBSERVE -> observeViaCarrier(level, state, intent, now);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- 扶持

    private static boolean support(ServerLevel level, WillState state, Intent intent,
                                   Assessment.Situation s, long now) {
        ServerPlayer target = findPlayer(level, intent.target);
        if (target == null || target.level() != level) {
            return false;
        }
        Assessment.Actor actor = s.find(intent.target);
        if (actor == null) {
            return false;
        }
        Assessment.Channel ch = intent.channel == null ? Assessment.Channel.RESOURCE : intent.channel;
        // 扶持只走「资源成熟」与「传承线索」两类实物机会；
        // 借人之口只用于压制（人劫），不伪装成协助 —— 见类注释的渠道表
        if (ch != Assessment.Channel.RESOURCE && ch != Assessment.Channel.HERITAGE) {
            return false;
        }

        // ---- B01 修复：申请与兑现分开 ----
        // ① 先把已经成熟、还没兑现的机缘兑现掉（不再新建）。
        if (settleOne(state, level, intent.target, now)) {
            return true;
        }
        // ② 这个人身上已经有未成熟的申请 → 不重复申请（以前每轮都新建，旧的永远兑现不了）
        if (state.core().opportunities().pendingFor(intent.target, now) > 0) {
            return true;
        }
        // ③ 申请一条新的：只排队，等成熟后由调度器兑现
        BlockPos pos = pickSurface(level, target.blockPosition(), now);
        if (pos == null) {
            return false;
        }
        String origin = ch == Assessment.Channel.HERITAGE
                ? "某位蛊师遗留的蛊方在" + dimShort(level) + "显形"
                : "野外蛊材在" + dimShort(level) + "自然成熟";
        Opportunity.Offer offer = state.core().opportunities()
                .propose(Assessment.assess(s), intent.target, ch, actor, now,
                        dimKey(level), pos.asLong(), origin,
                        // 3.14：诱饵门限随强度下调（占比仍受 BAIT_RATIO_CAP 约束）
                        WillForceSettings.force().baitSinThreshold())
                .stream().findFirst().orElse(null);
        if (offer == null) {
            return false;
        }
        state.core().note("opportunity.request " + offer.id + " matureAt=" + offer.matureAt);
        state.setDirty();
        return true;
    }

    /**
     * 兑现一条已经成熟的机缘（按受益人；{@code beneficiary} 为空表示任意）。
     *
     * <p>本次清理后不再在世界上开产地（旧产地系统已整体删除）：成熟的机缘直接落成
     * <b>一件地表实物</b>，带上 {@code tianyi_offer} 标签 —— 谁先捡到就是谁的，
     * 所以仍然可被截取；标签指向唯一那条机缘，所以也只能记一次。
     *
     * <p>开不出来（区块没加载 / 池子里这一轮拿不到 id）就保持未兑现状态，下一轮再试：
     * 不会重复产出，也不会静默丢掉。
     */
    static boolean settleOne(WillState state, ServerLevel level, String beneficiary, long now) {
        for (Opportunity.Offer o : state.core().opportunities().due(now)) {
            if (beneficiary != null && !beneficiary.isEmpty()
                    && !o.intended.isEmpty() && !o.intended.equals(beneficiary)) {
                continue;
            }
            if (!o.dim.equals(dimKey(level))) {
                continue; // 等对应维度被加载时再兑现
            }
            BlockPos pos = unpack(o.pos);
            if (pos == null || !level.isLoaded(pos)) {
                continue;
            }
            // 传承线索落蛊方，其余落野外蛊材；池子里的 id 都已核对过存在
            String id = pick(o.kind == Opportunity.Kind.INHERITANCE_LEAD ? GU_FANG_POOL : WILD_GU_POOL,
                    now);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null) {
                continue; // 拿不到实物 → 下一轮再试，不降级成别的东西
            }
            ItemStack stack = new ItemStack(item, 1);
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, stack);
            drop.setDefaultPickUpDelay();
            drop.getPersistentData().putString("tianyi_offer", o.id);
            level.addFreshEntity(drop);
            o.markSettled(now);
            state.core().note("opportunity.settle " + o.id + " → drop=" + id);
            state.setDirty();
            return true;
        }
        return false;
    }

    /**
     * 全量兑现：把各处已成熟的机缘都落成地表实物（本次清理后不再开产地）。
     *
     * <p>由调度器每轮调用一次 —— 这是 B01 的核心：<b>旧的成熟机缘要有东西管</b>，
     * 不能只在「有人被扶持的那一轮」才检查。
     *
     * @return 本轮兑现的机缘数
     */
    public static int settleDueOpportunities(net.minecraft.server.MinecraftServer server,
                                             WillState state, long now) {
        if (state.core().opportunities().due(now).isEmpty()) {
            return 0;
        }
        int done = 0;
        for (ServerLevel level : server.getAllLevels()) {
            while (settleOne(state, level, "", now)) {
                done++;
                if (done > 8) {
                    return done; // 单轮上限：不一次做完一整个世界
                }
            }
        }
        return done;
    }

    /** 打包坐标 → BlockPos。 */
    private static BlockPos unpack(long packed) {
        if (packed == net.guzhenren.tianyi.will.Knowledge.Fact.NO_POS) {
            return null;
        }
        int x = (int) (packed >> 38);
        int z = (int) ((packed << 26) >> 38);
        int y = (int) ((packed << 52) >> 52);
        return new BlockPos(x, y, z);
    }

    // ---------------------------------------------------------------- 压制

    private static boolean suppress(ServerLevel level, WillState state, Intent intent,
                                    Assessment.Situation s, long now) {
        ServerPlayer target = findPlayer(level, intent.target);
        if (target == null || target.level() != level) {
            return false;
        }
        Assessment.Channel ch = intent.channel == null ? Assessment.Channel.BEAST : intent.channel;
        return switch (ch) {
            case BEAST -> beaSTDrive(level, state, target, now);
            // 人劫必须「酝酿」：先开一个布局，由 WillServer 逐阶段推进，
            // 玩家在酝酿期跑远/藏起来/切断信息都能让它作废。
            case RUMOR -> openRenjiePloy(state, target, now);
            case CALAMITY -> openCalamity(level, state, intent, target, now);
            // 原著：天意剿不掉目标时，会借天道「改易道痕」的权限把资源点搅烂。
            // 本次清理：旧地表产地已删除（新资源点是有限资源点，归 FiniteSiteService 管），
            // 这条渠道只剩「扰动他仙窍里的资源账」一条真实可达的路。
            case RESOURCE -> degradeResourcePoint(state, target, now);
            case FACTION -> factionPin(level, target, now);
            default -> beaSTDrive(level, state, target, now);
        };
    }

    /**
     * 势力牵制：以「嫁祸」在兩個既有势力之间制造实际冲突。
     *
     * <p>用的是本体 {@code guzhenren_library} 真实存在的势力系统
     * （{@code FactionTerritory.of(level)} + {@code FactionBrain.blame/declareWar}），
     * 而不是凭空模拟「势力敌意」。这正对应原著里天意「暗中影响他人成为劫」——
     * 它不能直接命令谁去打谁，只能递一个由头。
     *
     * <p>只递由头（blame），不轻易宣战：宣战会让世界格局剧烈变化，
     * 由 {@link FactionBrain} 自己的 AI 决定要不要升级。
     */
    private static boolean factionPin(ServerLevel level, ServerPlayer target, long now) {
        try {
            var territory = com.guzhenrenlibrary.faction.FactionTerritory.of(level);
            var all = com.guzhenrenlibrary.faction.FactionRegistry.all();
            if (territory == null || all == null || all.size() < 2) {
                return false;
            }
            int i = (int) Math.floorMod(now, all.size());
            int j = (int) Math.floorMod(i + 1 + (now / 7), all.size());
            if (i == j) {
                j = (j + 1) % all.size();
            }
            var a = all.get(i);
            var b = all.get(j);
            // 嫁祸：让 b 认为 a 干了某事，从而在两者之间留下紧张
            com.guzhenrenlibrary.faction.FactionBrain.blame(level, territory, b, a);
            return true;
        } catch (RuntimeException | LinkageError e) {
            // 本体势力系统不可用（版本不匹配 / 未加载）→ 这一条渠道本轮放弃
            return false;
        }
    }

    /**
     * 资源点退化：扰乱仙窍内部资源，留下可调查的痕迹。
     *
     * <p>只动<b>本模组自己的仙窍资源账</b>，不碰本体物品与元石，
     * 因此不会出现「本体流程重复结算」。
     *
     * @return 是否有资源被真的打散
     */
    private static boolean degradeResourcePoint(WillState state, ServerPlayer target, long now) {
        var ap = state.apertureIfPresent(target.getUUID());
        if (ap == null || ap.resources.isEmpty()) {
            return false;
        }
        // 取一项资源，按比例打散（不是清零：原著是「从井然有序变得糟糕透顶」）
        String key = ap.resources.keySet().stream().sorted().toList()
                .get((int) Math.floorMod(now, ap.resources.size()));
        double before = ap.resources.get(key);
        double after = before * 0.55;
        ap.resources.put(key, after);
        ap.damage(0.02);
        state.setDirty();
        state.core().note("resource.degrade " + target.getUUID() + " " + key
                + " " + String.format("%.1f→%.1f", before, after));
        return after < before;
    }

    /**
     * 兽群施压：从最近的兽群账里「赶」一批兽过来。
     *
     * <p>关键：<b>生成一只就少一只</b>。种群不够时 {@link BeastHerds#driveToward}
     * 一只都生不出来 —— 从结构上没有「在玩家身边无限刷怪」这条路。
     * 只有在完全没有兽群账可建（比如维度不支持）时，才退回到最保守的少量生成。
     */
    private static boolean beaSTDrive(ServerLevel level, WillState state, ServerPlayer target,
                                      long now) {
        WillForce force = WillForceSettings.force();
        // 3.13 的规模：3..6 只；3.14 档位只在它上面乘倍率（档位 1 = 1.0，逐位相同）
        int want = 3 + (int) (Math.abs(Math.sin(now * 0.0007)) * 4);
        want = (int) Math.round(want * force.beastDriveFactor());
        want = Math.min(Math.max(1, want), Math.max(1, force.beastDriveMax()));
        int spawned = BeastHerds.driveToward(state, level, target, now, want);
        if (spawned > 0) {
            return true;
        }
        // B07：种群耗尽/兽群崩溃时**不再兜底生成** ——
        // 以前这里会「无种群扣减地放一只」，等于绕过守恒、变相无限刷怪。
        // 现在兽群没货就是没货：天意只能等种群恢复或换手段。
        state.core().note("herd.empty @dim=" + level.dimension().location()
                + "（种群不足，本轮不出兽）");
        return false;
    }

    /** 人劫：一名/几名本体修士上门，限时后离开（不是永久仇恨傀儡）。 */
    private static boolean spawnRenjie(ServerLevel level, ServerPlayer target, int count,
                                       long now, String reason) {
        int n = Math.min(Math.max(1, count), WillConfig.RENJIE_MAX_NPCS);
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            BlockPos pos = pickSurface(level, target.blockPosition(), now + i * 6131L);
            if (pos == null) {
                continue;
            }
            Entity e = spawnEntity(level, pick(NPC_POOL, now + i), pos);
            if (e instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.setTarget(target);
                // 限时：到点由 WillServer 的清理任务移走
                mob.getPersistentData().putLong("tianyi_renjie_until", now + RENJIE_LIFETIME);
                mob.getPersistentData().putString("tianyi_renjie_reason", reason);
                spawned++;
            }
        }
        // 观象·亲见：人真的出现了，玩家才知道自己被盯上（不是后台名单）
        if (spawned > 0
                && JournalStore.noteVisitor(target, spawned, now)) {
            net.guzhenren.tianyi.net.JournalNet.sync(target);
        }
        return spawned > 0;
    }

    /**
     * 开一个人劫布局（酝酿期 {@link WillConfig#RENJIE_MIN_GESTATION}）。
     *
     * <p>三步：埋信息（流言）→ 聚人 → 出手。每一步有到期时间与执行者，
     * 到期由 {@link WillServer} 推进；途中目标跑远、藏起来、失去情报、渠道断了，
     * {@link net.guzhenren.tianyi.will.Ploy#evaluate} 都会判失败并进冷却。
     *
     * @return 是否真的开起来了（数量上限 / 冷却中会返回 false）
     */
    private static boolean openRenjiePloy(WillState state, ServerPlayer target, long now) {
        WillForce force = WillForceSettings.force();
        var book = state.core().ploys();
        if (!book.canOpen(now, force.maxActivePloys())) {
            return false;
        }
        // 3.14：人劫频次靠「酝酿时长 × 档位倍率」与「布局上限」两个既有旋钮调整，
        // 不新增第二种人劫。档位 1 时为 1.0，与 3.13 完全一致。
        long gest = (long) (WillConfig.RENJIE_MIN_GESTATION * force.renjieGestationScale());
        gest = Math.max(WillConfig.RENJIE_MIN_GESTATION / 2L, gest);
        String id = "renjie-" + now + "-" + target.getUUID().toString().substring(0, 8);
        var ploy = book.open(id, "human-tribulation", target.getUUID().toString(),
                "guzhenren:npc", "npc_interest",
                "npc_arrives_or_target_escapes", now, true);
        ploy.anchor(target.blockPosition().asLong(),
                        target.level().dimension().location().toString())
                .step(Ploy.StepKind.SEED_INFO, "guzhenren:npc", now + gest / 4, "流言：有人得了蛊方")
                .step(Ploy.StepKind.ASSEMBLE, "guzhenren:npc", now + gest / 2, "聚人：几名修士动身")
                .step(Ploy.StepKind.STRIKE, "guzhenren:npc", now + gest, "上门");
        state.setDirty();
        state.core().note("ploy.open " + id + " target=" + target.getUUID());
        return true;
    }

    /** 布局成功时真正落下世界效果（只落一次）。 */
    public static boolean applyPloyEffect(ServerLevel level, WillState state,
                                          net.guzhenren.tianyi.will.Ploy ploy, long now) {
        if (ploy.effectApplied) {
            return false;
        }
        if (!"human-tribulation".equals(ploy.goal)) {
            return false;
        }
        ServerPlayer target = findPlayer(level, ploy.target);
        if (target == null) {
            return false;
        }
        boolean ok = spawnRenjie(level, target,
                1 + (int) (Math.abs(Math.sin(now * 0.001)) * 2)
                        + WillForceSettings.force().renjieExtraNpc(),
                now, "renjie");
        if (ok) {
            ploy.effectApplied = true;
            state.core().note("ploy.strike " + ploy.id + " → 人劫上门");
            state.setDirty();
        }
        return ok;
    }
    private static boolean openCalamity(ServerLevel level, WillState state, Intent intent,
                                        ServerPlayer target, long now) {
        WillForce force = WillForceSettings.force();
        int rank = (int) Math.round(
                net.guzhenren.tianyi.world.Observation.probe(target).rank());
        double budget = state.core().budget();
        double heaven = state.core().destiny().heavenControl();
        // 宿命约束是否已破缺：只作灾劫强度/门槛的<b>具体</b>输入，不是总开关
        boolean fateBroken = state.core().destiny().stage().fateConstraintBroken;
        Calamity.Tier tier = pickTier(rank, now, force);
        var aperture = state.aperture(target.getUUID());
        if (!Calamity.conditionsMet(tier, rank, aperture.stability, heaven, budget,
                fateBroken)) {
            return false;
        }
        // 同一目标同时只允许一场灾劫在酝酿/推进
        if (state.core().calamities().countFor(intent.target) > 0) {
            return false;
        }
        // 3.14：档位给的是「密度」与「冷却」，不是「无限降灾」。
        // 档位 1 的 calamityMaxActive=64 / cooldown=0，等于不加限制（与 3.13 一致）。
        if (state.core().calamities().active().size() >= force.calamityMaxActive()) {
            state.core().note("calamity.refused：同时存在的灾劫已达上限 "
                    + force.calamityMaxActive() + "（档位 " + force.label() + "）");
            return false;
        }
        long cooldown = force.calamityCooldown();
        if (cooldown > 0L) {
            for (Calamity.Run r : state.core().calamities().all()) {
                if (r.target.equals(intent.target) && now - r.createdAt < cooldown) {
                    return false;
                }
            }
        }
        Calamity.Spec spec = Calamity.spec(
                tier,
                (0.30 + 0.15 * Math.abs(Math.sin(now * 0.0003))) * force.calamityTargetingFactor(),
                8.0 + rank * 2.0,
                fateBroken,
                force.calamityScaleFactor());
        state.core().calamities().open("cal-" + now + "-" + intent.target.substring(0, 8),
                intent.target, dimKey(level), target.blockPosition().asLong(), spec, now);
        state.setDirty();
        return true;
    }

    /**
     * 3.14：高阶灾劫的门限随档位放松（{@code ease < 1} 更容易出高阶），
     * 但「转数不够就出不来」这条硬门槛不变（仍由 {@link Calamity#conditionsMet} 复核）。
     */
    private static Calamity.Tier pickTier(int rank, long now, WillForce force) {
        double ease = force.calamityTierEase();
        if (rank >= Calamity.Tier.WAN_JIE.minRank
                && Math.abs(Math.sin(now * 0.00013)) > 0.93 * ease) {
            return Calamity.Tier.WAN_JIE;
        }
        if (rank >= Calamity.Tier.HAO_JIE.minRank
                && Math.abs(Math.sin(now * 0.00019)) > 0.75 * ease) {
            return Calamity.Tier.HAO_JIE;
        }
        if (rank >= Calamity.Tier.TIAN_JIE.minRank
                && Math.abs(Math.sin(now * 0.00029)) > 0.5 * ease) {
            return Calamity.Tier.TIAN_JIE;
        }
        return Calamity.Tier.DI_ZAI;
    }

    // ---------------------------------------------------------------- 观测

    /** 观测：天意把载体往目标那边推一点，为后续布局做准备（不改世界）。 */
    private static boolean observeViaCarrier(ServerLevel level, WillState state, Intent intent,
                                             long now) {
        if (intent.target.isEmpty()) {
            return false;
        }
        var node = state.carriers().get("region:" + dimKey(level));
        if (node != null) {
            return state.carriers().consume(node.id, 0.02, now);
        }
        return false;
    }

    // ---------------------------------------------------------------- 工具

    /** 在目标周围找一个地表生成点（距离有下限，避免刷在脸上）。 */
    public static BlockPos pickSurface(ServerLevel level, BlockPos center, long seed) {
        double angle = (seed % 360L) * Math.PI / 180.0;
        int dist = MIN_SPAWN_DISTANCE
                + (int) (Math.abs(Math.sin(seed * 0.00037)) * (MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE));
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // 只落在已加载区块；不为了生成而强制加载
        if (!level.isLoaded(pos)) {
            return null;
        }
        return pos;
    }

    public static Entity spawnEntity(ServerLevel level, String registryId, BlockPos pos) {
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(registryId));
            if (type == null) {
                return null;
            }
            Entity e = type.create(level);
            if (e == null) {
                return null;
            }
            e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            if (level.addFreshEntity(e)) {
                return e;
            }
        } catch (RuntimeException ignored) {
            // 注册 id 不存在 / 生成失败：不做任何兜底伪造
        }
        return null;
    }

    private static String pick(String[] pool, long seed) {
        int i = (int) Math.floorMod(seed, pool.length);
        return pool[i];
    }

    private static String dimKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String dimShort(ServerLevel level) {
        return level.dimension().location().getPath();
    }

    private static ServerPlayer findPlayer(ServerLevel level, String uuid) {
        try {
            java.util.UUID id = java.util.UUID.fromString(uuid);
            return level.getServer().getPlayerList().getPlayer(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 供清理任务用：把人劫 NPC 到期的实体收拾掉。 */
    public static List<LivingEntity> expiredRenjie(ServerLevel level, long now) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity le
                    && le.getPersistentData().contains("tianyi_renjie_until")
                    && le.getPersistentData().getLong("tianyi_renjie_until") <= now) {
                out.add(le);
            }
        }
        return out;
    }

    /** 日志用的一行摘要。 */
    public static String describe(Intent i) {
        return String.format(Locale.ROOT, "%s→%s via %s", i.kind, i.target, i.channel);
    }
}
