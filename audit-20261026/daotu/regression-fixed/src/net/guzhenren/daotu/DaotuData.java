package net.guzhenren.daotu;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家道途数据，全部存在 PersistentData 的 {@code daotu} 子标签里，不占用本体 PlayerVariables 字段。
 *
 * <p>与「天命」附属的 {@code tianfu} 子标签完全隔离，两者可同装。
 *
 * <p><b>v1.4.0 收缩</b>：势力 / 档位 / 流派节点 / 觉醒四组字段已随功能一起删除。
 * 旧存档里的这些标签会被静默忽略（不会报错，也不会自动清理），
 * 转世时 {@link #wipeAll} 会把它们一并抹掉。
 *
 * <pre>
 * migrated    bool    旧「出身」存档已折算
 * origin      String  旧字段：1.0.0 的出身 id，仅用于迁移读取，迁移后清空
 * originAt    long    旧字段：选出身时间戳
 * applied     bool    本世开窍已结算（防重入）
 * run         compound 本局成长状态，见 DaotuRun
 * lastX/Y/Z   double  上次坐标（算游历距离）
 * biomes      list    已到访群系（去重）
 * </pre>
 */
public final class DaotuData {
    public static final String ROOT = "daotu";

    private DaotuData() {
    }

    static CompoundTag root(Player player, boolean create) {
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains(ROOT, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            pd.put(ROOT, new CompoundTag());
        }
        return pd.getCompound(ROOT);
    }

    /** 原子地读-改-写，避免 getCompound 在缺失时返回游离 tag。 */
    static void update(Player player, Consumer<CompoundTag> mutator) {
        CompoundTag pd = player.getPersistentData();
        CompoundTag tag = pd.contains(ROOT, Tag.TAG_COMPOUND) ? pd.getCompound(ROOT) : new CompoundTag();
        mutator.accept(tag);
        pd.put(ROOT, tag);
    }

    /**
     * 读 {@code daotu} 下的某个子标签。
     *
     * <p>{@code create == false} 时缺失返回 {@code null}，调用方必须判空。
     */
    static CompoundTag sub(Player player, String key, boolean create) {
        CompoundTag tag = root(player, create);
        if (tag == null) {
            return null;
        }
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            tag.put(key, new CompoundTag());
        }
        return tag.getCompound(key);
    }

    // ------------------------------------------------------------------ 开窍结算

    public static boolean isApplied(Player player) {
        CompoundTag tag = root(player, false);
        return tag != null && tag.getBoolean("applied");
    }

    public static void markApplied(Player player) {
        update(player, tag -> tag.putBoolean("applied", true));
    }

    /** 旧「出身」存档是否已折算。 */
    public static boolean migrated(Player player) {
        CompoundTag tag = root(player, false);
        return tag != null && tag.getBoolean("migrated");
    }

    public static void markMigrated(Player player) {
        update(player, tag -> tag.putBoolean("migrated", true));
    }

    // ------------------------------------------------------------------ 旧出身（仅迁移读取）

    public static String origin(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? "" : tag.getString("origin");
    }

    public static void clearOrigin(Player player) {
        update(player, tag -> {
            tag.remove("origin");
            tag.remove("originAt");
        });
    }

    // ------------------------------------------------------------------ 转世清空

    /** 寿元耗尽转世：删掉整个 daotu 标签（本局属性、历练池、历史全部重置）。 */
    public static void wipeAll(Player player) {
        player.getPersistentData().remove(ROOT);
    }

    // ------------------------------------------------------------------ 通用

    public static boolean getBoolean(Player player, String key, boolean fallback) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains(key, Tag.TAG_BYTE) ? fallback : tag.getBoolean(key);
    }

    public static void putBoolean(Player player, String key, boolean value) {
        update(player, tag -> tag.putBoolean(key, value));
    }

    public static double getDouble(Player player, String key, double fallback) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains(key, Tag.TAG_DOUBLE) ? fallback : tag.getDouble(key);
    }

    public static void putDouble(Player player, String key, double value) {
        update(player, tag -> tag.putDouble(key, value));
    }

    public static String getString(Player player, String key, String fallback) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains(key, Tag.TAG_STRING) ? fallback : tag.getString(key);
    }

    public static void putString(Player player, String key, String value) {
        update(player, tag -> tag.putString(key, value));
    }
}
