package net.guzhenren.tianyi.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * 旧天意系统的退场与数据归档。
 *
 * <h2>退场的是什么</h2>
 * 旧玩法骨架里的这些入口不再提供任何收益，也不再出现在界面上：
 * 天机积分、签到、摇命、永久词条商城、命契商店、百世书奖励。
 *
 * <h2>为什么是「归档」而不是「清空」</h2>
 * <ol>
 *   <li><b>不动本体进度。</b>本体的转数、寿元、道痕、蛊虫栏一概不碰 ——
 *       迁移不得以清档为代价。</li>
 *   <li><b>不强制死亡/转世。</b>旧系统里「百世书」相关状态只归档，不触发转世。</li>
 *   <li><b>可回退。</b>旧数据整棵子树搬到 {@code tianyi/archive_v3} 下，
 *       需要时可以捞回来，不是 delete。</li>
 *   <li><b>幂等。</b>{@code tianyi/meta/retired} 一旦写上，重复登录不会再迁移一次。</li>
 * </ol>
 */
public final class LegacyRetire {
    private LegacyRetire() {
    }

    /** 迁移版本。改动归档结构时 +1。 */
    public static final int VERSION = 3;

    /** 需要归档的旧子系统子树。 */
    private static final String[] OLD_SUBTREES = {
            "econ",   // 天机积分 / 签到 / 摇命 / 账本
            "quest",  // 谕令 / 日课 / 禁谕 / 试炼
            "open",   // 天命词条（永久词条商城的产物）
            "path",   // 道途熟练度（与本体流派重复）
            "ai",     // 已移除的 AI 降谕
            "shop",   // 神之商店
    };

    /**
     * 对单个玩家执行归档。
     *
     * @return 是否发生了实际迁移（首次登录返回 {@code true}）
     */
    public static boolean migrate(ServerPlayer player, Logger log) {
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains("tianyi", Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag root = pd.getCompound("tianyi");
        CompoundTag meta = root.contains("meta", Tag.TAG_COMPOUND)
                ? root.getCompound("meta") : new CompoundTag();
        if (meta.getInt("retired") >= VERSION) {
            return false; // 已迁过：幂等
        }

        CompoundTag archive = root.contains("archive_v3", Tag.TAG_COMPOUND)
                ? root.getCompound("archive_v3") : new CompoundTag();
        List<String> moved = new ArrayList<>();
        for (String key : OLD_SUBTREES) {
            if (root.contains(key)) {
                archive.put(key, root.get(key)); // 整棵子树搬走
                root.remove(key);
                moved.add(key);
            }
        }
        // 旧版可能把奖励直接写在根上
        for (String legacyKey : new String[]{"tianji", "signedDay", "streak", "pity", "life"}) {
            if (root.contains(legacyKey)) {
                archive.put("root_" + legacyKey, root.get(legacyKey));
                root.remove(legacyKey);
                moved.add(legacyKey);
            }
        }

        ListTag history = archive.contains("_moved", Tag.TAG_LIST)
                ? archive.getList("_moved", Tag.TAG_STRING) : new ListTag();
        for (String m : moved) {
            history.add(StringTag.valueOf(m));
        }
        archive.put("_moved", history);
        root.put("archive_v3", archive);

        meta.putInt("retired", VERSION);
        meta.putLong("retiredAt", System.currentTimeMillis());
        meta.putString("note", "旧天机/签到/摇命/商城/百世书奖励已退场，数据归档可回退");
        root.put("meta", meta);
        pd.put("tianyi", root);

        if (!moved.isEmpty()) {
            log.info("[天意] 旧系统数据已归档：玩家 {} → {} 个子树（{}）",
                    player.getGameProfile().getName(), moved.size(), String.join(",", moved));
        }
        return true;
    }

    /** 归档摘要（供管理命令查）。 */
    public static String describe(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound("tianyi");
        if (!root.contains("archive_v3", Tag.TAG_COMPOUND)) {
            return "无旧数据（未迁移过或本来就没有）";
        }
        CompoundTag archive = root.getCompound("archive_v3");
        ListTag moved = archive.getList("_moved", Tag.TAG_STRING);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < moved.size(); i++) {
            keys.add(moved.getString(i));
        }
        return "归档版本=" + root.getCompound("meta").getInt("retired")
                + " 已归档=" + keys;
    }
}
