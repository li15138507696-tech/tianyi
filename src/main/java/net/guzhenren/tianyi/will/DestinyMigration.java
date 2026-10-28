package net.guzhenren.tianyi.will;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 宿命蛊旧档迁移（纯逻辑，不 import 任何 Minecraft 类型）。
 *
 * <h2>映射表（旧 3 阶段 → 新 4 阶段）</h2>
 * <table border="1">
 *   <tr><th>旧记录</th><th>新阶段</th><th>管理标记</th><th>理由</th></tr>
 *   <tr><td>{@code DAMAGED}</td><td>{@code BODY_DAMAGED}</td><td>正常</td>
 *       <td>旧数值（integrity）只作历史数据保留，不再代表宿命强度</td></tr>
 *   <tr><td>{@code REPAIRED}</td><td>{@code BODY_DAMAGED}</td><td><b>待核验</b></td>
 *       <td>旧档「已修复」<b>不自动认定本体完整</b>；完整必须由完成的修复事务产生</td></tr>
 *   <tr><td>{@code DESTROYED}</td><td>{@code BODY_DESTROYED_REVIVABLE}</td><td>正常</td>
 *       <td>「本体被毁、仍有重现可能」；<b>不得</b>映射为 {@code SPLIT_AND_DISTRIBUTED}</td></tr>
 *   <tr><td>未知/损坏</td><td>隔离</td><td>隔离（保留原始数据）</td>
 *       <td>不猜、不丢证据</td></tr>
 * </table>
 *
 * <h2>幂等</h2>
 * 同一记录重复迁移不补发、不重复改规则：已迁移记录（{@link Legacy#migrated()}）直接返回
 * {@code migrated=false} 的同一结果。待核验是<b>管理标记</b>，不是第五个阶段。
 */
public final class DestinyMigration {
    /** 旧记录版本（3.12.0 及以前）。 */
    public static final int LEGACY_VERSION = 1;
    /** 当前记录版本（3.13.0 四阶段）。 */
    public static final int CURRENT_VERSION = 2;

    private DestinyMigration() {
    }

    /** 迁移后的管理状态。待核验不是阶段，只是「还需要人/事务核验」的标记。 */
    public enum Management {
        NORMAL,
        /** 旧档自称「已修复」，但未被真实事务证实，需核验后才能完成修复事务。 */
        PENDING_VERIFICATION,
        /** 记录损坏或版本未知，原样保留证据，不参与判定。 */
        QUARANTINED
    }

    /**
     * 一条待迁移的宿命记录。
     *
     * @param stageName 存档里的阶段名（旧名或新名都可能）
     * @param value     旧数值（integrity）；{@code hasValue=false} 时忽略
     * @param hasValue  是否带旧数值
     * @param version   记录版本；0 表示旧档没有版本字段
     * @param migrated  是否已被迁移过（幂等开关）
     * @param management 已知的管理标记（{@code null} 表示未知，由本次迁移决定）
     */
    public record Legacy(String stageName, double value, boolean hasValue, int version,
                         boolean migrated, Management management) {
        /** 旧档没有版本字段（最常见：{@code tianyi_will} 的 destiny 子标签）。 */
        public static Legacy unversioned(String stageName, double value) {
            return new Legacy(stageName, value, true, 0, false, null);
        }

        /** 旧档带版本。 */
        public static Legacy of(String stageName, double value, int version) {
            return new Legacy(stageName, value, true, version, false, null);
        }

        /** 旧档连数值都没有。 */
        public static Legacy bare(String stageName) {
            return new Legacy(stageName, 0.0, false, 0, false, null);
        }

        /** 已迁移过的记录（重复迁移时用）。 */
        public static Legacy alreadyMigrated(String stageName, Management management) {
            return new Legacy(stageName, 0.0, false, CURRENT_VERSION, true, management);
        }
    }

    /** 迁移结论。 */
    public record Outcome(Destiny.Stage stage, Management management, boolean migrated,
                          String note, Map<String, String> preserved) {
        public boolean quarantined() {
            return management == Management.QUARANTINED;
        }

        public boolean pendingVerification() {
            return management == Management.PENDING_VERIFICATION;
        }
    }

    /** 迁移一条记录。永不抛异常：坏记录进隔离并保留原始数据。 */
    public static Outcome migrate(Legacy in) {
        if (in == null) {
            return quarantine(null, "空记录：隔离");
        }
        Map<String, String> preserved = preserve(in);
        String raw = in.stageName() == null ? "" : in.stageName().trim();
        String key = raw.toUpperCase(Locale.ROOT);

        if (in.migrated()) {
            Destiny.Stage s = parseStage(key);
            if (s == null) {
                return quarantine(preserved, "已迁移记录的阶段名无法识别：隔离并保留原始数据");
            }
            Management m = in.management() == null ? Management.NORMAL : in.management();
            return new Outcome(s, m, false, "已迁移，跳过（幂等）", preserved);
        }

        if (in.version() < 0 || in.version() > CURRENT_VERSION) {
            return quarantine(preserved, "未知记录版本 " + in.version() + "：隔离并保留原始数据");
        }
        if (key.isEmpty()) {
            return quarantine(preserved, "旧记录缺阶段名：隔离并保留原始数据");
        }

        Destiny.Stage current = parseStage(key);
        if (current != null) {
            // 已经是新格式：原样通过，不算本次迁移
            return new Outcome(current, Management.NORMAL, false, "已是新格式阶段，无需迁移", preserved);
        }

        return switch (key) {
            case "DAMAGED" -> new Outcome(Destiny.Stage.BODY_DAMAGED, Management.NORMAL, true,
                    "旧「受损」→ 本体受损；旧数值只作历史数据保留", preserved);
            case "REPAIRED" -> new Outcome(Destiny.Stage.BODY_DAMAGED, Management.PENDING_VERIFICATION, true,
                    "旧「已修复」不自动认定本体完整：标记待核验，完整须由完成的修复事务产生", preserved);
            case "DESTROYED" -> new Outcome(Destiny.Stage.BODY_DESTROYED_REVIVABLE, Management.NORMAL, true,
                    "旧「已毁灭」→ 本体被毁、仍有重现可能；不会自动得到拆分分发", preserved);
            default -> quarantine(preserved,
                    "未知阶段名 " + raw + "：隔离并保留原始数据");
        };
    }

    /** 便捷入口。 */
    public static Outcome migrate(String stageName, double value, boolean hasValue,
                                  int version, boolean alreadyMigrated) {
        return migrate(new Legacy(stageName, value, hasValue, version, alreadyMigrated, null));
    }

    /** 是否为新四阶段的名称。 */
    public static boolean isNewStageName(String name) {
        return parseStage(name == null ? "" : name.trim().toUpperCase(Locale.ROOT)) != null;
    }

    private static Destiny.Stage parseStage(String upperName) {
        if (upperName == null || upperName.isEmpty()) {
            return null;
        }
        for (Destiny.Stage s : Destiny.Stage.values()) {
            if (s.name().equals(upperName)) {
                return s;
            }
        }
        return null;
    }

    private static Map<String, String> preserve(Legacy in) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("legacy.stage", in.stageName() == null ? "" : in.stageName());
        m.put("legacy.version", Integer.toString(in.version()));
        if (in.hasValue()) {
            m.put("legacy.value", Double.toString(in.value()));
        }
        return m;
    }

    private static Outcome quarantine(Map<String, String> preserved, String note) {
        return new Outcome(Destiny.Stage.BODY_DAMAGED, Management.QUARANTINED, false, note,
                preserved == null ? java.util.Collections.emptyMap() : preserved);
    }
}
