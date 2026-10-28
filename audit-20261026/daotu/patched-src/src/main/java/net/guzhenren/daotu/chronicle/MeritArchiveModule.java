// Path: src/main/java/net/guzhenren/daotu/chronicle/MeritArchiveModule.java
package net.guzhenren.daotu.chronicle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;
import net.guzhenren.daotu.chronicle.ChronicleModels.CoreSnapshot;
import net.guzhenren.daotu.chronicle.ChronicleModels.MeritArchive;
import net.guzhenren.daotu.chronicle.ChronicleModels.Milestone;
import net.guzhenren.daotu.chronicle.ChronicleModels.ModuleDecision;

/**
 * 功绩档案，依据观测峰值收集纪念条目；不能将贡献变化认定为某一任务完成。
 *
 * <p>本体 {@code KE1} 会把贡献钳在 0..80，且玩家消费会让它下降，因此这里只认
 * <b>峰值</b>：下降只重建基线，不产生任何条目。
 */
public final class MeritArchiveModule extends ChronicleModule {
    public static final String ID = "merit_archive";

    /** {@inheritDoc} 首次及转世只建基线，不补发历史条目。 */
    @Override
    public Optional<ModuleDecision> evaluate(CoreSnapshot snapshot, ChronicleState state) {
        if (!snapshot.playerId().equals(state.playerId())) {
            throw new IllegalArgumentException("所有者不匹配");
        }
        MeritArchive previous = state.merit();
        double current = snapshot.contribution();
        boolean newRun = snapshot.runId() != state.runId();
        boolean noBaseline = previous.lastObservedTick() < 0L;

        if (newRun || noBaseline) {
            // 基线：只记下当前值，不发条目。
            MeritArchive base = new MeritArchive(current,
                    noBaseline ? current : Math.max(current, previous.peakObserved()), previous.unlockedEntries(),
                    snapshot.gameTick());
            return Optional.of(new ModuleDecision(ID,
                    new ChronicleState(state.playerId(), state.schema(), state.revision(), state.runId(),
                            state.faction(), base, state.reputation()),
                    List.of()));
        }

        if (current == previous.lastObserved() && snapshot.gameTick() == previous.lastObservedTick()) {
            return Optional.empty();
        }

        double peak = Math.max(previous.peakObserved(), current);
        Set<String> unlocked = new LinkedHashSet<>(previous.unlockedEntries());
        List<String> entries = new ArrayList<>();
        if (current >= previous.lastObserved()) {
            for (Milestone milestone : ChronicleTables.MERIT) {
                if (previous.peakObserved() < milestone.threshold()
                        && peak >= milestone.threshold() && unlocked.add(milestone.id())) {
                    entries.add(milestone.id());
                }
            }
        }
        // 下降分支：unlocked 不变，peak 也不因下降而缩水。
        MeritArchive next = new MeritArchive(current, peak, Set.copyOf(unlocked), snapshot.gameTick());
        return Optional.of(new ModuleDecision(ID,
                new ChronicleState(state.playerId(), state.schema(), state.revision(), state.runId(),
                        state.faction(), next, state.reputation()),
                List.copyOf(entries)));
    }
}