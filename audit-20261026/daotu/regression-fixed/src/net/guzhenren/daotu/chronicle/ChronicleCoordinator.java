// Path: src/main/java/net/guzhenren/daotu/chronicle/ChronicleCoordinator.java
package net.guzhenren.daotu.chronicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;
import net.guzhenren.daotu.chronicle.ChronicleModels.CoreSnapshot;
import net.guzhenren.daotu.chronicle.ChronicleModels.FactionJournal;
import net.guzhenren.daotu.chronicle.ChronicleModels.MeritArchive;
import net.guzhenren.daotu.chronicle.ChronicleModels.Milestone;
import net.guzhenren.daotu.chronicle.ChronicleModels.ModuleDecision;
import net.guzhenren.daotu.chronicle.ChronicleModels.ReputationJournal;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 服务端用例边界；只做编排，不持有世界状态。 */
public final class ChronicleCoordinator {
    private final CoreVersionAdapter adapter;
    private final ChronicleRepository repository;
    private final List<ChronicleModule> modules;

    /** 注入非空适配器、仓储及三个无状态模块；不访问世界。 */
    public ChronicleCoordinator(CoreVersionAdapter adapter, ChronicleRepository repository,
                                List<ChronicleModule> modules) {
        this.adapter = adapter;
        this.repository = repository;
        this.modules = List.copyOf(modules);
    }

    /** 首次建档的空状态：采样时间 -1 表示尚无基线，负声望仍是有效观测值。 */
    private static ChronicleState baseline(CoreSnapshot snapshot) {
        return new ChronicleState(
                snapshot.playerId(),
                ChronicleModels.SCHEMA,
                0L,
                snapshot.runId(),
                new FactionJournal(Set.of(), "", "", 0L),
                new MeritArchive(-1.0, -1.0, Set.of(), -1L),
                new ReputationJournal(-1.0, -1.0, Set.of(), -1L));
    }

    /**
     * 采样后依次合并模块决定，每批只递增一次 revision 并提交一次。
     * @param player 非空在线玩家；每 20 tick 或登录显式调用
     * @throws IllegalStateException 非服务端线程；适配器不可用时直接暂停，禁止创建空存档
     */
    public void observe(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (player.getServer() == null || !player.getServer().isSameThread()) {
            throw new IllegalStateException("ChronicleCoordinator 必须在服务端主线程调用");
        }
        Optional<CoreSnapshot> snapshotOpt = adapter.read(player);
        if (snapshotOpt.isEmpty()) {
            // 适配器不可用（本体未装/字段非法）：暂停，不建空存档。
            return;
        }
        CoreSnapshot snapshot = snapshotOpt.get();
        if (!snapshot.opened()) {
            // 未开窍不记录：开局前的势力/身份是空的，记下来只会污染见闻簿。
            return;
        }

        Optional<ChronicleState> loaded = repository.load(player);
        long expectedRevision = loaded.isPresent() ? loaded.get().revision() : -1L;
        ChronicleState working = loaded.orElseGet(() -> baseline(snapshot));

        List<String> collected = new ArrayList<>();
        for (ChronicleModule module : modules) {
            Optional<ModuleDecision> decisionOpt = module.evaluate(snapshot, working);
            if (decisionOpt.isEmpty()) {
                continue;
            }
            ModuleDecision decision = decisionOpt.get();
            working = decision.nextState();
            collected.addAll(decision.entries());
        }

        boolean dirty = !loaded.isPresent()
                || snapshot.runId() != loaded.get().runId()
                || !collected.isEmpty()
                || !working.equals(loaded.get());
        if (!dirty) {
            return;
        }

        // 整批统一提交 revision 与 runId；模块合并期间保留旧 runId，
        // 使每个模块都能识别同一次转世，不能由前一个模块提前消费。
        ChronicleState next = new ChronicleState(
                working.playerId(),
                ChronicleModels.SCHEMA,
                expectedRevision + 1L,
                snapshot.runId(),
                working.faction(),
                working.merit(),
                working.reputation());
        repository.save(player, expectedRevision, next);
    }

    /**
     * 展示独立纪事，不自动弹窗、不读取其他玩家、不改旧协议号。
     * @param source 命令来源；控制台须返回可解释的失败提示
     * @return 展示成功 1，未实现、不可用或不是玩家为 0
     */
    public int show(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[道途·纪事] 该命令只能由玩家执行"));
            return 0;
        }
        Optional<ChronicleState> loaded;
        try {
            loaded = repository.load(player);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal("[道途·纪事] 存档不可读：" + e.getMessage()));
            return 0;
        }
        if (loaded.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[道途·纪事] 尚无记录；开窍后每 20 tick 自动采样一次"), false);
            return 1;
        }
        ChronicleState state = loaded.get();
        source.sendSuccess(() -> Component.literal("§6=== 道途·纪事 §7(revision " + state.revision() + ")"), false);
        source.sendSuccess(() -> Component.literal("§e宗门见闻 §7· 已记录组合 §b"
                + state.faction().discoveredPairs().size() + "§7/§b"
                + ChronicleTables.MAX_FACTION_PAIRS), false);
        if (!state.faction().lastFaction().isEmpty() || !state.faction().lastIdentity().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7  当前 §f"
                    + state.faction().lastFaction() + " §7/ §f" + state.faction().lastIdentity()), false);
        }
        source.sendSuccess(() -> Component.literal("§e功绩档案 §7· 峰值 §b"
                + format(state.merit().peakObserved()) + " §7已解锁 §b"
                + state.merit().unlockedEntries().size()), false);
        for (Milestone milestone : ChronicleTables.MERIT) {
            if (state.merit().unlockedEntries().contains(milestone.id())) {
                source.sendSuccess(() -> Component.literal("§7  ✔ §f" + milestone.displayName()
                        + " §7(" + format(milestone.threshold()) + ")"), false);
            }
        }
        source.sendSuccess(() -> Component.literal("§e声望纪事 §7· 峰值 §b"
                + format(state.reputation().peakObserved()) + " §7已解锁 §b"
                + state.reputation().unlockedEntries().size()), false);
        for (Milestone milestone : ChronicleTables.REPUTATION) {
            if (state.reputation().unlockedEntries().contains(milestone.id())) {
                source.sendSuccess(() -> Component.literal("§7  ✔ §f" + milestone.displayName()
                        + " §7(" + format(milestone.threshold()) + ")"), false);
            }
        }
        return 1;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}