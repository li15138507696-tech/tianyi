// Path: src/main/java/net/guzhenren/daotu/chronicle/ChronicleModels.java
package net.guzhenren.daotu.chronicle;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 新玩法的数据定义；构造器不校验、不读写世界，校验责任在协调器及仓储。 */
public final class ChronicleModels {
    public static final int SCHEMA = 1;
    public static final String STORAGE_ROOT = "daotu_chronicle";
    private ChronicleModels() {}

    /** 服务端同一 tick 采样；原样保存本体字符串，不推断阵营枚举。数值必须有限。 */
    public record CoreSnapshot(UUID playerId, long gameTick, int runId, boolean opened,
                               String faction, String identity, double contribution,
                               double reputation) {}

    /** 宗门见闻：仅记录观察到的势力与身份组合，最多 128 项；不发物品。 */
    public record FactionJournal(Set<String> discoveredPairs, String lastFaction,
                                 String lastIdentity, long lastObservedTick) {}

    /** 功绩档案：首次采样只建立基线；下降时重建基线，不把消费后恢复当作任务奖励。 */
    public record MeritArchive(double lastObserved, double peakObserved,
                               Set<String> unlockedEntries, long lastObservedTick) {}

    /** 声望纪事：只按配置阈值收集一次性纪念条目，不回写本体声望。 */
    public record ReputationJournal(double lastObserved, double peakObserved,
                                     Set<String> unlockedEntries, long lastObservedTick) {}

    /** UUID 是所有者；revision 乐观版本从 0 起，runId 仅用于本局基线切换。 */
    public record ChronicleState(UUID playerId, int schema, long revision, int runId,
                                  FactionJournal faction, MeritArchive merit,
                                  ReputationJournal reputation) {}

    /** 纯计算结果，所有集合必须不可变；entries 只能是本模块条目 ID。 */
    public record ModuleDecision(String moduleId, ChronicleState nextState,
                                  List<String> entries) {}

    /** 数据表行：数值门槛为道途设计参数，不声称来自本体。 */
    public record Milestone(String id, double threshold, String displayName) {}
}
