package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 全存档回溯（春秋蝉）：快照模型、完整性校验、分支标识与恢复前置条件。
 *
 * <h2>这里只放「可以离线判定的部分」</h2>
 * 真正的区块/方块实体/实体/玩家数据抓取与写回在 MC 层
 * （{@code world.SnapshotService}）。本类负责：
 * <ul>
 *   <li>快照由哪些<b>分区</b>组成、每个分区的摘要与字节数；</li>
 *   <li>整体摘要与<b>完整性校验</b>（任何一个分区摘要不符即拒绝恢复）；</li>
 *   <li><b>分支标识</b>：回溯会产生新分支，不允许在同一条时间线上无声覆盖；</li>
 *   <li>恢复<b>前置条件</b>：世界必须已关闭、玩家已告知、快照已验证。</li>
 * </ul>
 *
 * <h2>明确写下来的边界</h2>
 * <ol>
 *   <li><b>只能回到真的保存并校验过的节点。</b>没有快照的时间点不存在，不假装能回去。</li>
 *   <li><b>现实玩家已经知道的信息无法被游戏抹除。</b>回溯能还原世界，不能还原人脑。</li>
 *   <li><b>施术者只带走意志与记忆。</b>当前背包、资源、装备一律不跟着回过去 ——
 *       这是防复制物品的关键约束，由 {@link #carriesInventory()} 恒假来固定。</li>
 *   <li><b>保存范围即恢复范围。</b>快照没覆盖的区块不会被"还原成想象的样子"。</li>
 * </ol>
 */
public final class History {
    private History() {
    }

    /** 快照分区。覆盖用户要求的全部持久状态类别。 */
    public enum Section {
        /** 区块与方块。 */
        CHUNK,
        /** 方块实体（容器、炉、符文台等）。 */
        BLOCK_ENTITY,
        /** 实体（NPC、蛊兽、掉落物）。 */
        ENTITY,
        /** 玩家（位置、状态、本体变量）。 */
        PLAYER,
        /** NPC 关系（好感/敌意/债务）。 */
        NPC_RELATION,
        /** 势力（声望、地盘、资源）。 */
        FACTION,
        /** 仙窍（等级、仙元、内部资源、内部时间）。 */
        APERTURE,
        /** 世界阶段（宿命、星宿、载体网络）。 */
        WORLD_STAGE,
        /** 本模组自己的持久数据（知识库、布局、灾劫）。 */
        MOD_DATA
    }

    /** 一个分区的结果。 */
    public record Part(Section section, int entries, long bytes, String digest) {
    }

    /** 一个快照。不可变。 */
    public static final class Snapshot {
        public final String id;
        public final String branch;
        public final String parentBranch;
        public final long takenAt;
        /** 保存范围描述（哪些维度、哪些区块半径）。 */
        public final String scope;
        public final List<Part> parts;
        public final String digest;
        public final boolean verified;
        public final String note;

        public Snapshot(String id, String branch, String parentBranch, long takenAt,
                        String scope, List<Part> parts, String digest, boolean verified,
                        String note) {
            this.id = id;
            this.branch = branch;
            this.parentBranch = parentBranch;
            this.takenAt = takenAt;
            this.scope = scope;
            this.parts = List.copyOf(parts);
            this.digest = digest;
            this.verified = verified;
            this.note = note == null ? "" : note;
        }

        public long totalBytes() {
            long n = 0L;
            for (Part p : parts) {
                n += p.bytes();
            }
            return n;
        }

        public Part part(Section s) {
            for (Part p : parts) {
                if (p.section() == s) {
                    return p;
                }
            }
            return null;
        }

        public String describe() {
            return String.format(Locale.ROOT, "Snapshot{%s branch=%s at=%d parts=%d %d B %s}",
                    id, branch, takenAt, parts.size(), totalBytes(), verified ? "VERIFIED" : "UNVERIFIED");
        }
    }

    /** 快照仓库。 */
    public static final class Store {
        private final Map<String, Snapshot> byId = new LinkedHashMap<>();
        private final Set<String> branches = new LinkedHashSet<>();

        public void add(Snapshot s) {
            byId.put(s.id, s);
            branches.add(s.branch);
        }

        public Snapshot get(String id) {
            return byId.get(id);
        }

        public List<Snapshot> onBranch(String branch) {
            List<Snapshot> out = new ArrayList<>();
            for (Snapshot s : byId.values()) {
                if (s.branch.equals(branch)) {
                    out.add(s);
                }
            }
            return out;
        }

        public Snapshot latest(String branch) {
            Snapshot best = null;
            for (Snapshot s : byId.values()) {
                if (branch != null && !s.branch.equals(branch)) {
                    continue;
                }
                if (!s.verified) {
                    continue; // 只能回到验证过的节点
                }
                if (best == null || s.takenAt > best.takenAt) {
                    best = s;
                }
            }
            return best;
        }

        public Set<String> branches() {
            return new LinkedHashSet<>(branches);
        }

        public int size() {
            return byId.size();
        }

        /** 生成新分支名：回溯不能原地覆盖时间线。 */
        public String forkBranch(String from) {
            int n = 1;
            String name;
            do {
                name = from + "-r" + n;
                n++;
            } while (branches.contains(name));
            return name;
        }
    }

    /** 恢复计划：能不能做、卡在哪、边界是什么。 */
    public record RewindPlan(String snapshotId, boolean canProceed, List<String> blockers,
                             List<String> boundaryNotes, String newBranch) {
    }

    /**
     * 规划一次回溯。
     *
     * @param worldClosed   世界是否已经保存并关闭（运行时零散覆盖区块文件是不允许的）
     * @param playersWarned 是否已把「玩家已知的信息无法抹除」这件事告知在线玩家
     */
    public static RewindPlan plan(Store store, String snapshotId,
                                  boolean worldClosed, boolean playersWarned) {
        List<String> blockers = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Snapshot s = store.get(snapshotId);
        if (s == null) {
            blockers.add("快照不存在：" + snapshotId);
            return new RewindPlan(snapshotId, false, blockers, notes, "");
        }
        if (!s.verified) {
            blockers.add("快照未通过完整性校验，拒绝恢复");
        }
        if (!worldClosed) {
            blockers.add("世界尚未保存并关闭：不允许在运行期零散覆盖区块文件");
        }
        if (!playersWarned) {
            blockers.add("尚未告知在线玩家：回溯不能抹除他们已经知道的信息");
        }
        if (s.part(Section.CHUNK) == null) {
            blockers.add("快照不含区块分区，无法还原地形");
        }
        notes.add("保存范围=" + s.scope + "；范围之外的区块不会被还原");
        notes.add("施术者只带走意志与记忆，背包/资源/装备不随回溯转移");
        notes.add("回溯生成新分支 " + store.forkBranch(s.branch) + "，原分支只读保留");
        notes.add("现实玩家已知的信息无法由游戏抹除");
        return new RewindPlan(s.id, blockers.isEmpty(), blockers, notes,
                store.forkBranch(s.branch));
    }

    /**
     * 快照是否通过完整性校验。
     *
     * @param actual 由 MC 层回读磁盘算出的「分区 → 摘要」表
     */
    public static boolean verify(Snapshot s, Map<Section, String> actual) {
        if (s == null || actual == null) {
            return false;
        }
        for (Part p : s.parts) {
            String got = actual.get(p.section());
            if (got == null || !got.equals(p.digest())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 回溯是否会把物品带回去。<b>恒 {@code false}。</b>
     *
     * <p>这一条是防复制的硬约束：任何"背包跟着回去"的实现都会让玩家
     * 用一次回溯刷出双份资源。同时它也对应原著里施术者只保留意志与记忆。
     */
    public static boolean carriesInventory() {
        return false;
    }

    /**
     * 施术者是否保留记忆。仅施术者本人。
     */
    public static boolean remembers(String casterId, String whoId) {
        return casterId != null && casterId.equals(whoId);
    }

    /**
     * 天意是否因为「系统记了日志」就自动获得未来的信息。
     *
     * <p>恒 {@code false}：天意只有在通过春秋蝉等原著渠道<b>参与</b>了回溯时才可能
     * 保留/获得相关信息，系统自身的日志不算天意的情报。
     */
    public static boolean willLearnsFromLogs() {
        return false;
    }
}
