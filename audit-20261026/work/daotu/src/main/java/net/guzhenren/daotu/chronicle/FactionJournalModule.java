// Path: src/main/java/net/guzhenren/daotu/chronicle/FactionJournalModule.java
package net.guzhenren.daotu.chronicle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.guzhenren.daotu.chronicle.ChronicleModels.ChronicleState;
import net.guzhenren.daotu.chronicle.ChronicleModels.CoreSnapshot;
import net.guzhenren.daotu.chronicle.ChronicleModels.FactionJournal;
import net.guzhenren.daotu.chronicle.ChronicleModels.ModuleDecision;

/** 宗门见闻簿；shenfen 已由旧道途占用，只观察、不接管头衔。 */
public final class FactionJournalModule extends ChronicleModule {
    public static final String ID = "faction_journal";

    /** 无歧义组合键：长度前缀 + 原文，避免分隔符碰撞。 */
    static String pairKey(String faction, String identity) {
        String a = faction == null ? "" : faction;
        String b = identity == null ? "" : identity;
        return a.length() + ":" + a + "|" + b.length() + ":" + b;
    }

    /** {@inheritDoc} 组合键须无歧义编码，禁止简单拼接分隔符。 */
    @Override
    public Optional<ModuleDecision> evaluate(CoreSnapshot snapshot, ChronicleState state) {
        if (!snapshot.playerId().equals(state.playerId())) {
            throw new IllegalArgumentException("所有者不匹配");
        }
        FactionJournal previous = state.faction();
        String faction = snapshot.faction() == null ? "" : snapshot.faction();
        String identity = snapshot.identity() == null ? "" : snapshot.identity();
        String key = pairKey(faction, identity);
        String previousKey = pairKey(previous.lastFaction(), previous.lastIdentity());

        boolean newRun = snapshot.runId() != state.runId();
        boolean tickAdvanced = snapshot.gameTick() != previous.lastObservedTick();
        if (!newRun && key.equals(previousKey) && !tickAdvanced) {
            return Optional.empty();
        }

        Set<String> pairs = new LinkedHashSet<>(previous.discoveredPairs());
        List<String> entries = new ArrayList<>();
        // 转世保留已发现组合（见闻是玩家履历，不随本局重置）。
        if (pairs.size() < ChronicleTables.MAX_FACTION_PAIRS && pairs.add(key)) {
            entries.add("faction:" + key);
        }
        FactionJournal next = new FactionJournal(Set.copyOf(pairs), faction, identity, snapshot.gameTick());
        return Optional.of(new ModuleDecision(ID,
                new ChronicleState(state.playerId(), state.schema(), state.revision(), state.runId(),
                        next, state.merit(), state.reputation()),
                List.copyOf(entries)));
    }
}