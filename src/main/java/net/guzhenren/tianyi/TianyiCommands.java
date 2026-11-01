package net.guzhenren.tianyi;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.guzhenren.tianyi.resource.DestinySeek;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.guzhenren.tianyi.resource.SiteCommandArgs;
import net.guzhenren.tianyi.resource.SiteInventory;
import net.guzhenren.tianyi.will.Assessment;
import net.guzhenren.tianyi.will.Counter;
import net.guzhenren.tianyi.will.Destiny;
import net.guzhenren.tianyi.will.DestinyCustody;
import net.guzhenren.tianyi.will.DestinyRules;
import net.guzhenren.tianyi.will.Immortal;
import net.guzhenren.tianyi.world.DestinyCustodyService;
import net.guzhenren.tianyi.world.DestinyCustodyState;
import net.guzhenren.tianyi.world.DestinySeekService;
import net.guzhenren.tianyi.world.FiniteSiteService;
import net.guzhenren.tianyi.world.FiniteSiteState;
import net.guzhenren.tianyi.world.Observation;
import net.guzhenren.tianyi.world.SitePlacement;
import net.guzhenren.tianyi.world.SnapshotService;
import net.guzhenren.tianyi.world.WillState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /tianyi …}：<b>管理/诊断</b>命令。
 *
 * <h2>为什么这些不是玩家功能</h2>
 * 天意对普通玩家是隐性的。这些子命令只在管理员手里作为观测与运维工具：
 * 看世界状态、抓快照、执行回溯、查自己的仙窍、给某个主体挂反制做测试。
 * 普通玩家既没有权限，也不会收到任何「天意对你做了什么」的通知。
 */
public final class TianyiCommands {
    private TianyiCommands() {
    }

    private static final int OP = 2;
    private static final int ADMIN = 3;

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tianyi")
                .then(Commands.literal("status")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::status))
                .then(Commands.literal("observe")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::observe))
                .then(Commands.literal("destiny")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::destiny)
                        // 3.13.0 保管账本：查询 + 两个受控操作（都要 note，都只动保管，不动阶段）
                        .then(Commands.literal("custody")
                                .requires(s -> s.hasPermission(OP))
                                .executes(TianyiCommands::custodyStatus)
                                .then(Commands.literal("status")
                                        .executes(TianyiCommands::custodyStatus))
                                .then(Commands.literal("recover")
                                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                                .executes(TianyiCommands::custodyRecover)))
                                .then(Commands.literal("release")
                                        .then(Commands.argument("serial", StringArgumentType.word())
                                                .then(Commands.argument("note",
                                                                StringArgumentType.greedyString())
                                                        .executes(TianyiCommands::custodyRelease)))))
                        // 摧毁与后续炼制分发：本版本没有入口，只回 NOT_IMPLEMENTED
                        .then(Commands.literal("destroy")
                                .requires(s -> s.hasPermission(OP))
                                .executes(TianyiCommands::destinyDestroy))
                        .then(Commands.literal("refine")
                                .requires(s -> s.hasPermission(OP))
                                .executes(TianyiCommands::destinyRefine)))
                // 3.14.2 有限资源点运维：查/退役/解除单点隔离。只动账本与隔离状态，
                // 不补货、不改阶段语义、不写任何玩家背包。
                .then(Commands.literal("site")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::siteList)
                        .then(Commands.literal("list")
                                .executes(TianyiCommands::siteList))
                        .then(Commands.literal("status")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TianyiCommands::siteStatus)))
                        .then(Commands.literal("retire")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TianyiCommands::siteRetire)))
                        .then(Commands.literal("release")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TianyiCommands::siteReleaseNoNote)
                                        .then(Commands.literal("--note")
                                                .then(Commands.argument("text",
                                                                StringArgumentType.greedyString())
                                                        .executes(TianyiCommands::siteRelease))))))
                // 3.14.2 宿命蛊取得路径：玩家入口（不设权限门槛）。
                // 前置全部在服务端判定，命令层只负责转述——不给跳过前置的后门。
                .then(Commands.literal("seek")
                        .then(Commands.literal("suminggu")
                                .executes(TianyiCommands::seekSumingGu)))
                .then(Commands.literal("carriers")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::carriers))
                .then(Commands.literal("shougu")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::shougu))
                .then(Commands.literal("herds")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::herds))
                .then(Commands.literal("identity")
                        .requires(s -> s.hasPermission(OP))
                        .executes(TianyiCommands::identity))
                .then(Commands.literal("aperture")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(TianyiCommands::aperture))
                .then(Commands.literal("counter")
                        .requires(s -> s.hasPermission(OP))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .executes(TianyiCommands::counter)))
                .then(Commands.literal("snapshot")
                        .requires(s -> s.hasPermission(ADMIN))
                        .executes(c -> snapshot(c, "全维度 / 全部已落盘区块")))
                .then(Commands.literal("rewind")
                        .requires(s -> s.hasPermission(ADMIN))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> rewind(c, false))
                                .then(Commands.literal("confirm")
                                        .executes(c -> rewind(c, true))))));
    }

    // ---------------------------------------------------------------- 查询

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        for (String line : TianyiMod.SERVER.statusLines(server)) {
            reply(ctx, line);
        }
        return 1;
    }

    private static int observe(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        WillState state = WillState.get(server);
        long now = server.overworld().getGameTime();
        Assessment.Situation s = Observation.observe(server, state, now);
        reply(ctx, Observation.describe(s));
        Assessment.Report r = Assessment.assess(s);
        reply(ctx, "评估：" + r.note);
        reply(ctx, "扶持候选=" + r.supportCandidates + " 压制候选=" + r.suppressCandidates);
        var causes = state.core().causes();
        int from = Math.max(0, causes.size() - 6);
        for (int i = from; i < causes.size(); i++) {
            reply(ctx, "因果：" + causes.get(i));
        }
        return 1;
    }

    private static int destiny(CommandContext<CommandSourceStack> ctx) {
        WillState state = WillState.get(ctx.getSource().getServer());
        Destiny.DestinyGu d = state.core().destiny();
        reply(ctx, "宿命蛊（世界唯一）阶段=" + d.stage()
                + " 修复进度=" + f(d.repairProgress())
                + " 天道权限=" + f(d.heavenControl()));
        reply(ctx, "星宿合道=" + d.starAligned() + " 最近变化原因=" + d.lastCause()
                + " @" + d.lastChangeAt());
        reply(ctx, state.core().star().describe());
        return 1;
    }

    // ---------------------------------------------------------------- 宿命蛊保管（3.13.0）

    /**
     * 保管状态：阶段、修复进度、账本状态、绑定、隔离与待核验原因。
     *
     * <p>只读：这条命令<b>不会</b>实体化、转移、解除隔离，也不会推进阶段。
     */
    private static int custodyStatus(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        WillState state = WillState.get(server);
        Destiny.DestinyGu d = state.core().destiny();
        DestinyCustodyState st = DestinyCustodyService.state(server);
        reply(ctx, "宿命蛊（世界唯一）阶段=" + d.stage()
                + " 修复进度=" + f(d.repairProgress())
                + " 天道权限=" + f(d.heavenControl()) + "（阶段只能由完成的真实事务推进）");
        reply(ctx, "保管账本：" + st.describe());
        reply(ctx, "世界身份=" + d.worldId() + " 账本身份="
                + (st.custody().worldId().isBlank() ? "（未绑定）" : st.custody().worldId())
                + " 绑定序号=" + (st.boundSerial().isBlank() ? "（未绑定）" : st.boundSerial())
                + " 事务号序列=" + st.txnSeq());
        if (st.custody().pendingVerification()) {
            reply(ctx, "待核验=是（载体丢失/未核实；这只是保管状态，不是阶段）");
        }
        if (st.custody().quarantined()) {
            reply(ctx, "隔离中：" + st.custody().quarantineReason()
                    + "（解除用 custody release <serial> <note>）");
        }
        if (!st.mutationReady()) {
            reply(ctx, "账本存档不可改：" + st.failureReason());
        }
        reply(ctx, "最近核对：" + (st.unverifiedReason().isBlank() ? "（无记录）" : st.unverifiedReason())
                + " 看见实物=" + st.checkedVessels() + " @" + st.lastCheckedAt());
        var dup = st.custody().duplicates();
        reply(ctx, "复制证据=" + dup.size() + " 条"
                + (dup.isEmpty() ? "" : "（副本一律不认定为真）"));
        for (int i = 0; i < Math.min(3, dup.size()); i++) {
            reply(ctx, "  副本 serial=" + dup.get(i).serial() + " note=" + dup.get(i).note());
        }
        var iso = st.isolation();
        for (int i = Math.max(0, iso.size() - 3); i < iso.size(); i++) {
            reply(ctx, "  证据：" + iso.get(i));
        }
        reply(ctx, "未实现：摧毁本体入口、后续炼制分发、命败、监天塔、不败福地、爱情蛊——"
                + "本体没有这些接口，本命令不提供也没有假效果");
        return 1;
    }

    /**
     * 恢复丢失的载体：<b>一次世界级实体化事务</b>，旧凭证随之作废。
     *
     * <p>账本上已经有正品时必定失败；隔离中必须先 {@code release}。
     * 载体落进世界（掉落物），<b>不写玩家背包</b>，也不指定寿命/转数。
     */
    private static int custodyRecover(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        String note = StringArgumentType.getString(ctx, "note");
        if (note == null || note.isBlank()) {
            reply(ctx, "必须写明理由（note）：实体化是一次真实事务，不能无痕发生");
            return 0;
        }
        if (player == null) {
            reply(ctx, "实体化必须由玩家执行：载体要落进世界，控制台没有位置可落");
            return 0;
        }
        DestinyCustody.Result r = DestinyCustodyService.materialize(player, note);
        DestinyCustodyState st = DestinyCustodyService.state(player.getServer());
        if (!r.ok()) {
            reply(ctx, "实体化被拒绝（" + r.reject() + "）：" + r.detail());
            return 0;
        }
        reply(ctx, "已实体化：" + r.detail() + "；载体落在你脚下，凭证已由账本重新签发");
        reply(ctx, "旧凭证已作废：账本 代次=" + st.custody().generation()
                + " 保管版本=" + st.custody().custodyRevision());
        return 1;
    }

    /** 解除隔离：必须给出序号与理由，且必须有具名核验人（就是执行者）。 */
    private static int custodyRelease(CommandContext<CommandSourceStack> ctx) {
        String serial = StringArgumentType.getString(ctx, "serial");
        String note = StringArgumentType.getString(ctx, "note");
        ServerPlayer player = ctx.getSource().getPlayer();
        String reviewer = player == null ? "console" : player.getUUID().toString();
        if (note == null || note.isBlank()) {
            reply(ctx, "解除隔离必须写明理由（note）：核验要留证据");
            return 0;
        }
        DestinyCustodyState st = DestinyCustodyService.state(ctx.getSource().getServer());
        if (!st.mutationReady()) {
            reply(ctx, "账本存档不可改：" + st.failureReason());
            return 0;
        }
        if (!st.custody().quarantined()) {
            reply(ctx, "账本没有处于隔离状态（无需解除）");
            return 0;
        }
        boolean ok = st.liftQuarantine(reviewer, note);
        if (!ok) {
            reply(ctx, "解除隔离被拒绝（证据不足或核验人缺失）");
            return 0;
        }
        reply(ctx, "已解除隔离（核验人=" + reviewer + " serial=" + serial + "）："
                + "副本仍不认定为真，正品以账本为准");
        return 1;
    }

    /**
     * 摧毁本体：<b>本版本没有入口</b>。
     *
     * <p>故意不给「点一下就命败/毁蛊」的实现：那需要完整天外之魔资格的证据链、
     * 与本体实际接触的判定，以及一次独立的完成事务。缺任何一项都只能是条件不足。
     */
    private static int destinyDestroy(CommandContext<CommandSourceStack> ctx) {
        WillState state = WillState.get(ctx.getSource().getServer());
        Destiny.Stage stage = state.core().destiny().stage();
        DestinyRules.Answer a = DestinyRules.evaluate(
                DestinyRules.Query.of(DestinyRules.Act.DESTROY_BODY).withStage(stage));
        reply(ctx, "NOT_IMPLEMENTED：摧毁本体没有命令入口，阶段不会被任何命令推进");
        reply(ctx, "缺：完整天外之魔资格的证据链、与本体实际接触的判定、独立的摧毁完成事务与证据");
        reply(ctx, "只读规则查询：" + DestinyRules.describe(a) + "；当前阶段=" + stage.name());
        return 1;
    }

    /** 后续炼制与分发：<b>本版本没有入口</b>，摧毁也不会自动得到「拆分分发」。 */
    private static int destinyRefine(CommandContext<CommandSourceStack> ctx) {
        WillState state = WillState.get(ctx.getSource().getServer());
        Destiny.Stage stage = state.core().destiny().stage();
        DestinyRules.Answer a = DestinyRules.evaluate(
                DestinyRules.Query.of(DestinyRules.Act.REFINE_DISTRIBUTE).withStage(stage));
        reply(ctx, "NOT_IMPLEMENTED：后续炼制与分发没有命令入口");
        reply(ctx, "缺：炼制流程本体、分发对象与数量的真实事务、分发后的保管与回收账目");
        reply(ctx, "只读规则查询：" + DestinyRules.describe(a) + "；当前阶段=" + stage.name());
        return 1;
    }

    // ---------------------------------------------------------------- 有限资源点（3.14.2）

    /**
     * {@code /tianyi site [list]}：最多 {@link SiteCommandArgs#MAX_LIST} 条，按维度/坐标排序。
     *
     * <p>只报账本事实（id / 模板 / 坐标 / 阶段 / 剩余实物 / 守护数量），
     * 不输出全图，也不输出任何幕后评分——那属于后台，不属于任何人的可见信息。
     */
    private static int siteList(CommandContext<CommandSourceStack> ctx) {
        FiniteSiteState ledger = FiniteSiteState.get(ctx.getSource().getServer());
        if (!ledger.mutationReady()) {
            reply(ctx, "资源点账本当前不可写：" + ledger.failureReason());
            return 0;
        }
        List<FiniteSite> all = new ArrayList<>(ledger.sites().values());
        all.sort(Comparator.comparing(FiniteSite::dimension)
                .thenComparingInt(FiniteSite::x)
                .thenComparingInt(FiniteSite::y)
                .thenComparingInt(FiniteSite::z));
        int shown = Math.min(SiteCommandArgs.MAX_LIST, all.size());
        reply(ctx, "有限资源点：共 " + all.size() + " 处，按维度/坐标列出前 " + shown
                + " 处（另有墓碑 " + ledger.tombstones().size() + " 处）");
        for (int i = 0; i < shown; i++) {
            FiniteSite s = all.get(i);
            reply(ctx, "  " + SiteCommandArgs.shortId(s.siteId().toString())
                    + " 模板=" + s.template()
                    + " 坐标=" + s.dimension() + " " + s.x() + "," + s.y() + "," + s.z()
                    + " 阶段=" + s.phase()
                    + " 剩余实物=" + s.inventory().physicalRemaining()
                    + " 守护=" + s.guardians().size());
        }
        if (all.size() > shown) {
            reply(ctx, "  （其余 " + (all.size() - shown) + " 处未列出：用 site status <id 或唯一前缀> 单点查看）");
        }
        reply(ctx, "这里只是账本事实：真实方块/容器/守护兽是否还在要进游戏看；不输出全图与幕后评分");
        return 1;
    }

    /**
     * {@code /tianyi site status <id>}：阶段、revision、库存各项、槽位物化进度、
     * {@code SitePlacement.guardianStatus}、最近失败原因。
     */
    private static int siteStatus(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        FiniteSiteState ledger = FiniteSiteState.get(server);
        String token = StringArgumentType.getString(ctx, "id");
        UUID id = resolveSite(ledger, token);
        if (id == null) {
            reply(ctx, resolveFailure(ledger, token));
            return 0;
        }
        FiniteSite site = ledger.sites().get(id);
        if (site == null) {
            reply(ctx, "该点已退役：id=" + id + " 墓碑记号="
                    + ledger.tombstones().getOrDefault(id, "（无）")
                    + "（记号保留，同一区域不再生成第二处）");
            return 1;
        }
        reply(ctx, "资源点 " + id);
        reply(ctx, "  模板=" + site.template() + " v" + site.templateVersion()
                + " 类型=" + site.kind() + " 阶段=" + site.phase() + " revision=" + site.revision());
        reply(ctx, "  坐标=" + site.dimension() + " " + site.x() + "," + site.y() + "," + site.z()
                + " 记号码=" + site.regionMarker() + " 根基完好=" + site.rootIntact());
        SiteInventory inv = site.inventory();
        reply(ctx, "  库存：初始=" + inv.initial() + " 成长=" + inv.verifiedPlantGrowth()
                + "/" + inv.lifetimeGrowthBudget() + " 未物化=" + inv.unmaterialized()
                + " 剩余实物=" + inv.physicalRemaining() + " 已转出=" + inv.transferredOut()
                + " 已毁=" + inv.destroyed());
        int materialized = 0;
        for (FiniteSite.Slot slot : site.slots()) {
            if (slot.materialized()) materialized++;
        }
        reply(ctx, "  槽位物化：" + materialized + "/" + site.slots().size()
                + " 已加载成长刻=" + site.loadedGrowthTicks());
        ServerLevel level = levelOf(server, site.dimension());
        reply(ctx, "  守护兽状态=" + (level == null ? "维度未加载（无法核对）"
                : SitePlacement.guardianStatus(level, site).name())
                + " 绑定=" + site.guardians().size() + " 只"
                + (site.guardians().isEmpty() ? "" : " 首只 UUID=" + site.guardians().get(0)));
        int pending = 0;
        for (FiniteSite.Transaction tx : ledger.transactions().values()) {
            if (tx.siteId().equals(id) && tx.phase() != FiniteSite.TxPhase.COMMITTED) pending++;
        }
        reply(ctx, "  未完成事务=" + pending + "（>0 时退役/解除隔离都会被拒绝，先核对实物）");
        reply(ctx, "  最近失败原因=" + (FiniteSiteService.lastFailure().isBlank()
                ? "（本进程没有记录）" : FiniteSiteService.lastFailure()));
        if (site.phase() == FiniteSite.Phase.QUARANTINED) {
            reply(ctx, "  该点已隔离停发：确认现场后可 /tianyi site release "
                    + SiteCommandArgs.shortId(id.toString()) + " --note <理由>");
        }
        reply(ctx, "边界：本命令只读账本与一个已加载点的守护兽状态，不改世界、不补货、不动阶段语义");
        return 1;
    }

    /**
     * {@code /tianyi site retire <id>}：<b>只在安全条件下</b>退役。
     *
     * <p>安全条件＝{@code DEPLETED} / {@code DESTROYED}，或 {@code MATERIALIZING} 且剩余实物为 0
     * （槽位再也放不下去的卡死点）。复用既有 {@link FiniteSiteState#retire}；
     * 卡死点先用 {@link FiniteSite#abandon} 把未物化的虚拟库存收尾（记为 destroyed），再退役。
     * <b>不补货、不改阶段语义、不重放任何写入。</b>
     */
    private static int siteRetire(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        FiniteSiteState ledger = FiniteSiteState.get(server);
        if (!ledger.mutationReady()) {
            reply(ctx, "资源点账本当前不可写：" + ledger.failureReason());
            return 0;
        }
        String token = StringArgumentType.getString(ctx, "id");
        UUID id = resolveSite(ledger, token);
        if (id == null) {
            reply(ctx, resolveFailure(ledger, token));
            return 0;
        }
        FiniteSite site = ledger.sites().get(id);
        if (site == null) {
            reply(ctx, ledger.tombstones().containsKey(id)
                    ? "该点早已退役（墓碑记号=" + ledger.tombstones().get(id) + "）"
                    : "找不到资源点：" + token);
            return 0;
        }
        int pending = 0;
        for (FiniteSite.Transaction tx : ledger.transactions().values()) {
            if (tx.siteId().equals(id) && tx.phase() != FiniteSite.TxPhase.COMMITTED) pending++;
        }
        if (pending > 0) {
            reply(ctx, "拒绝退役：该点还有 " + pending + " 条未完成事务，退役会让未核对的写入消失。先核对现场");
            return 0;
        }
        if (site.phase() == FiniteSite.Phase.MATERIALIZING && site.inventory().physicalRemaining() == 0) {
            // 卡死点：槽位再也放不下去。按模型收尾（未物化的虚拟库存记为 destroyed），再退役。
            FiniteSite abandoned;
            try {
                abandoned = site.abandon();
            } catch (IllegalStateException e) {
                reply(ctx, "拒绝退役：这个点的阶段不允许收尾（" + e.getMessage() + "）");
                return 0;
            }
            if (!ledger.replace(site, abandoned)) {
                reply(ctx, "拒绝退役：账本比对失败（这个点刚被别的流程改过），请重试");
                return 0;
            }
            TianyiMod.LOGGER.info("[天意·资源点] 卡死点收尾 id={} 未物化={} 剩余实物={}",
                    id, site.inventory().unmaterialized(), site.inventory().physicalRemaining());
            reply(ctx, "已按未物化收尾：虚拟库存 " + site.inventory().unmaterialized()
                    + " 份记为 destroyed（不补货、不重放写入、不改阶段语义）");
        } else if (site.phase() != FiniteSite.Phase.DEPLETED && site.phase() != FiniteSite.Phase.DESTROYED) {
            reply(ctx, "拒绝退役：只在安全条件下退役（DEPLETED / DESTROYED，"
                    + "或 MATERIALIZING 且剩余实物=0）。当前阶段=" + site.phase()
                    + " 剩余实物=" + site.inventory().physicalRemaining());
            return 0;
        }
        if (!ledger.retire(id)) {
            reply(ctx, "退役失败：账本拒绝（墓碑已满，或仍有未提交事务）");
            return 0;
        }
        TianyiMod.LOGGER.info("[天意·资源点] 退役 id={} 进入时阶段={}", id, site.phase());
        reply(ctx, "已退役：" + SiteCommandArgs.shortId(id.toString())
                + "（记号留在墓碑里，同一区域不再生成第二处；不补货、不回滚已发生的采集）");
        return 1;
    }

    /** {@code /tianyi site release <id>}（没写 --note）：只回用法，不做任何事。 */
    private static int siteReleaseNoNote(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, "用法：/tianyi site release <id> --note <理由>");
        reply(ctx, "解除单点隔离必须写明理由：核验要留证据（会记进服务端日志与本回话，不改账本 schema）");
        return 0;
    }

    /**
     * {@code /tianyi site release <id> --note <text>}：解除<b>单点</b>隔离。
     *
     * <p>复用既有 {@link FiniteSiteState#releaseQuarantine} 语义：回到账本能证明的那一档，
     * 不补货、不重放写入、不动阶段语义；仍有未完成事务时拒绝。
     */
    private static int siteRelease(CommandContext<CommandSourceStack> ctx) {
        String note = DestinySeek.note(StringArgumentType.getString(ctx, "text"));
        if (note.isEmpty()) {
            reply(ctx, "解除隔离必须写明理由（--note）：核验要留证据");
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        FiniteSiteState ledger = FiniteSiteState.get(server);
        if (!ledger.mutationReady()) {
            reply(ctx, "资源点账本当前不可写：" + ledger.failureReason());
            return 0;
        }
        String token = StringArgumentType.getString(ctx, "id");
        UUID id = resolveSite(ledger, token);
        if (id == null) {
            reply(ctx, resolveFailure(ledger, token));
            return 0;
        }
        FiniteSite site = ledger.sites().get(id);
        if (site == null) {
            reply(ctx, "该点已退役（墓碑里的点没有隔离可解）");
            return 0;
        }
        if (site.phase() != FiniteSite.Phase.QUARANTINED) {
            reply(ctx, "这个点没有处于隔离状态，无需解除（当前阶段=" + site.phase() + "）");
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        String reviewer = player == null ? "console" : player.getUUID().toString();
        if (!ledger.releaseQuarantine(id)) {
            reply(ctx, "解除隔离被拒绝：该点仍有未完成事务，或版本号已耗尽（先 site status 看清）");
            return 0;
        }
        TianyiMod.LOGGER.info("[天意·资源点] 解除隔离 id={} reviewer={} note={}", id, reviewer, note);
        reply(ctx, "已解除隔离：" + SiteCommandArgs.shortId(id.toString())
                + " 阶段=" + ledger.sites().get(id).phase()
                + "（回到账本能证明的那一档；不补货、不重放写入）");
        reply(ctx, "核验人=" + reviewer + " 理由=" + note);
        return 1;
    }

    /** 玩家入口：{@code /tianyi seek suminggu}。判定全在服务端，命令层只转述。 */
    private static int seekSumingGu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            reply(ctx, "取得宿命蛊必须由在场的玩家执行：实物要落进世界，控制台没有位置可落");
            return 0;
        }
        DestinySeekService.Result r = DestinySeekService.seek(player);
        if (!r.ok()) {
            reply(ctx, "取得被拒绝：" + r.detail());
            if (r.reject() == DestinySeek.Reject.MATERIALS_MISSING) {
                reply(ctx, "材料清单：" + String.join("、", DestinySeek.describeCosts())
                        + "（数量是平衡参数，见 docs/3.14.2）");
            }
            return 0;
        }
        for (String line : r.lines()) {
            reply(ctx, line);
        }
        return 1;
    }

    /** 维度名 → 已加载的 {@link ServerLevel}（找不到就返回 null，绝不为了查询去加载世界）。 */
    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }

    /** token → 站点 UUID：完整 id 或唯一前缀；歧义/找不到都返回 null。 */
    private static UUID resolveSite(FiniteSiteState ledger, String token) {
        String full = SiteCommandArgs.siteId(token, knownIds(ledger));
        if (full.isEmpty()) return null;
        try {
            return UUID.fromString(full);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    /** 账本里全部 id（含墓碑）：解析前缀时两者都算，退役的点也要能用 id 查到。 */
    private static List<String> knownIds(FiniteSiteState ledger) {
        List<String> known = new ArrayList<>();
        for (UUID id : ledger.sites().keySet()) known.add(id.toString());
        for (UUID id : ledger.tombstones().keySet()) known.add(id.toString());
        return known;
    }

    /** 解析失败时说清楚是「打错了」还是「打少了」——猜一个点去动手比报错危险得多。 */
    private static String resolveFailure(FiniteSiteState ledger, String token) {
        return SiteCommandArgs.ambiguous(token, knownIds(ledger))
                ? "id 前缀有歧义：" + token + "（请多打几位，或直接给完整 id）"
                : "找不到资源点：" + token + "（可用完整 id 或其唯一前缀）";
    }

    private static int carriers(CommandContext<CommandSourceStack> ctx) {
        WillState state = WillState.get(ctx.getSource().getServer());
        long now = ctx.getSource().getServer().overworld().getGameTime();
        reply(ctx, state.carriers().describe(now));
        for (var n : state.carriers().all()) {
            reply(ctx, "  载体 " + n.id + " " + n.kind + " dim=" + n.dim
                    + " 完整=" + f(n.integrity) + " 触达=" + f(n.effectiveReach(now))
                    + (n.blind(now) ? " [盲区]" : ""));
        }
        return 1;
    }

    private static int aperture(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        WillState state = WillState.get(p.getServer());
        Immortal.Aperture ap = state.aperture(p.getUUID());
        reply(ctx, "仙窍：" + ap.describe());
        reply(ctx, "内部已过 " + ap.innerTicks + " 刻（倍率 " + f(ap.innerTimeRatio) + "）");
        if (ap.resources.isEmpty()) {
            reply(ctx, "内部资源：空");
        } else {
            ap.resources.forEach((k, v) -> reply(ctx, "  " + k + " × " + f(v)));
        }
        return 1;
    }

    /** 寿蛊来源清点：本模组只干预「自然演化」一条，其余全部不碰。 */
    private static int shougu(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        WillState state = WillState.get(server);
        long now = server.overworld().getGameTime();
        for (String line : net.guzhenren.tianyi.world.ShouGuSupply.auditLines(state, now)) {
            reply(ctx, line);
        }
        return 1;
    }

    /** 野生兽群清点：栖息地 / 种群 / 承载力 / 捕猎 / 迁徙。 */
    private static int herds(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        WillState state = WillState.get(server);
        long now = server.overworld().getGameTime();
        for (String line : net.guzhenren.tianyi.world.BeastHerds.lines(state, now)) {
            reply(ctx, line);
        }
        return 1;
    }

    /** 身份证据链清点：每案的判定、可信度、证据类别与矛盾分。 */
    private static int identity(CommandContext<CommandSourceStack> ctx) {
        WillState state = WillState.get(ctx.getSource().getServer());
        var led = state.identity();
        reply(ctx, "身份案：共 " + led.size() + " 宗，已识破 " + led.identifiedCount() + " 宗");
        for (var c : led.all()) {
            reply(ctx, "  " + c.summary());
        }
        if (led.size() == 0) {
            reply(ctx, "  （还没有证据：有载体看着的当众行为才会写进身份案）");
        }
        reply(ctx, "  规则：至少 3 类独立证据且可信 ≥6.0 才识破；重复流言折价；矛盾压低但不抹除");
        return 1;
    }

    // ---------------------------------------------------------------- 反制

    /** 给执行者挂一个反制手段，并按该手段真正作用到感知/载体上（用于测试）。 */
    private static int counter(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Counter.Kind kind;
        try {
            kind = Counter.Kind.valueOf(StringArgumentType.getString(ctx, "kind").toUpperCase());
        } catch (IllegalArgumentException e) {
            reply(ctx, "未知反制：" + StringArgumentType.getString(ctx, "kind")
                    + "（可选 ANDU / SHENBUZHI / TIANXIAOYISAN / LIANDAO_PURGE / TIANJI"
                    + " / TIANWAI_FULL / TIANWAI_PARTIAL）");
            return 0;
        }
        WillState state = WillState.get(p.getServer());
        long now = p.serverLevel().getGameTime();
        Assessment.Actor a = new Assessment.Actor(p.getUUID().toString(),
                Assessment.ActorKind.PLAYER, "");
        var res = Counter.apply(kind, a, state.core().knowledge(), state.core().destiny(),
                now, 1.0);
        if (res == null) {
            reply(ctx, "该手段的前提不满足，未生效");
            return 0;
        }
        Observation.grantCounter(p, kind);
        state.setDirty();
        reply(ctx, "已施加 " + kind + "：" + res.note());
        reply(ctx, "改变环节=" + res.changed() + " 盲区=" + res.coverTicks() + "刻");
        reply(ctx, "注意：任何手段都不等于免疫攻击（灾劫照常结算）");
        return 1;
    }

    // ---------------------------------------------------------------- 回溯

    private static int snapshot(CommandContext<CommandSourceStack> ctx, String scope) {
        MinecraftServer server = ctx.getSource().getServer();
        WillState state = WillState.get(server);
        var snap = SnapshotService.capture(server, state, scope, TianyiMod.LOGGER);
        if (snap == null) {
            reply(ctx, "快照失败（详见服务端日志）");
            return 0;
        }
        reply(ctx, "快照 " + snap.id + " 已生成：" + snap.describe());
        return 1;
    }

    private static int rewind(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        MinecraftServer server = ctx.getSource().getServer();
        WillState state = WillState.get(server);
        String id = StringArgumentType.getString(ctx, "id");
        if (!confirmed) {
            reply(ctx, "回溯会把整个存档还原到快照 " + id + " 的时刻，并生成新分支。");
            reply(ctx, "边界：只能回到已保存并校验过的节点；现实玩家已知的信息无法被抹除；");
            reply(ctx, "      施术者只带走意志与记忆，背包/资源/装备不随回溯转移。");
            reply(ctx, "确认请执行：/tianyi rewind " + id + " confirm");
            return 1;
        }
        var req = SnapshotService.requestRewind(server, state, id, true, TianyiMod.LOGGER);
        if (!req.accepted()) {
            reply(ctx, "已拒绝：" + String.join("；", req.blockers()));
            return 0;
        }
        for (String note : req.notes()) {
            reply(ctx, note);
        }
        reply(ctx, "待恢复标记已写入。服务器即将停机，请运行 _pack/rewind_apply.py 完成交换。");
        // 受控停机：运行期绝不零散覆盖区块文件
        new Thread(() -> {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            server.halt(false);
        }, "tianyi-rewind-halt").start();
        return 1;
    }

    // ---------------------------------------------------------------- 工具

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSystemMessage(Component.literal("§7[天意] §r" + text));
    }

    private static String f(double v) {
        return String.format("%.2f", v);
    }
}
