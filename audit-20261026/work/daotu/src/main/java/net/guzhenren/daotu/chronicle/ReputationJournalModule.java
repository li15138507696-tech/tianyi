// Path: src/main/java/net/guzhenren/daotu/chronicle/ReputationJournalModule.java
package net.guzhenren.daotu.chronicle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;
import net.guzhenren.daotu.chronicle.ChronicleModels.CoreSnapshot;
import net.guzhenren.daotu.chronicle.ChronicleModels.Milestone;
import net.guzhenren.daotu.chronicle.ChronicleModels.ModuleDecision;
import net.guzhenren.daotu.chronicle.ChronicleModels.ReputationJournal;

/** 声望纪事，不介入本体声望增减及 NPC 交易判定。 */
public final class ReputationJournalModule extends ChronicleModule {
    public static final String ID = "reputation_journal";

    /** {@inheritDoc} 保留负声望原值，首次采样只建立基线。 */
    @Override
    public Optional<ModuleDecision> evaluate(CoreSnapshot snapshot, ChronicleState state) {
        if (!snapshot.playerId().equals(state.playerId())) {
            throw new IllegalArgumentException("所有者不匹配");
        }
        ReputationJournal previous = state.reputation();
        double current = snapshot.reputation();
        boolean newRun = snapshot.runId() != state.runId();
        boolean noBaseline = previous.lastObservedTick() < 0L;

        if (newRun || noBaseline) {
            ReputationJournal base = new ReputationJournal(current,
                    noBaseline ? current : Math.max(current, previous.peakObserved()), previous.unlockedEntries(),
                    snapshot.gameTick());
            return Optional.of(new ModuleDecision(ID,
                    new ChronicleState(state.playerId(), state.schema(), state.revision(), state.runId(),
                            state.faction(), state.merit(), base),
                    List.of()));
        }

        if (current == previous.lastObserved() && snapshot.gameTick() == previous.lastObservedTick()) {
            return Optional.empty();
        }

        // 负声望原样保留：只与门槛比较，不做 0 下限钳制。
        double peak = Math.max(previous.peakObserved(), current);
        Set<String> unlocked = new LinkedHashSet<>(previous.unlockedEntries());
        List<String> entries = new ArrayList<>();
        for (Milestone milestone : ChronicleTables.REPUTATION) {
            if (previous.peakObserved() < milestone.threshold()
                    && peak >= milestone.threshold() && unlocked.add(milestone.id())) {
                entries.add(milestone.id());
            }
        }
        ReputationJournal next = new ReputationJournal(current, peak, Set.copyOf(unlocked), snapshot.gameTick());
        return Optional.of(new ModuleDecision(ID,
                new ChronicleState(state.playerId(), state.schema(), state.revision(), state.runId(),
                        state.faction(), state.merit(), next),
                List.copyOf(entries)));
    }
}