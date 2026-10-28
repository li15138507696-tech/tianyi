// src/main/java/net/guzhenren/tianfu/TianfuData.java
package net.guzhenren.tianfu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家自有天赋数据，全部存在 PersistentData 的 {@code tianfu} 子标签里，不占用本体 PlayerVariables 字段。
 *
 * <pre>
 * points       int       自由点余额（开局按难度发放；可购全部条目；永久保留）
 * difficulty   int       开局难度在 {@link TianfuDifficulty#OPTIONS} 中的下标（-1=未选；选定后永久保留）
 * purchased    list&lt;str&gt; 已购条目 id（增量购买，即买即生效）
 * applied      bool      本世开窍已结算（防重入：希望蛊开窍收尾只跑一次）
 * confirmed    bool      本世天赋已最终确认（锁定购买/撤销/难度；寿元为0转世 wipeAll 清除，下世重新选）
 * slay/slayDay/slayDaily/lastKill/lastType/streak   杀神杀戮值与防刷进度（永久保留）
 * devourDay/devourDaily                             杀怪噬道每日进度（按日重置）
 * liupai_snap                                       万法皆通的流派熟练度快照（按秒差分补偿用）
 * fly_granted/chenmo_floor 及其它通用键              运行时标记
 * </pre>
 */
public final class TianfuData {
    public static final String ROOT = "tianfu";

    private TianfuData() {
    }

    private static CompoundTag root(Player player, boolean create) {
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
    private static void update(Player player, java.util.function.Consumer<CompoundTag> mutator) {
        CompoundTag pd = player.getPersistentData();
        CompoundTag tag = pd.contains(ROOT, Tag.TAG_COMPOUND) ? pd.getCompound(ROOT) : new CompoundTag();
        mutator.accept(tag);
        pd.put(ROOT, tag);
    }

    // ------------------------------------------------------------------ 开窍结算

    public static boolean isApplied(Player player) {
        CompoundTag tag = root(player, false);
        return tag != null && tag.getBoolean("applied");
    }

    /** 标记本世开窍已结算（希望蛊开窍收尾只跑一次的门）。 */
    public static void markApplied(Player player) {
        update(player, tag -> tag.putBoolean("applied", true));
    }

    // ------------------------------------------------------------------ 最终确认（本世锁定）

    /** 本世天赋是否已最终确认（确认后购买/撤销/难度全部冻结，直到寿元为0转世）。 */
    public static boolean isConfirmed(Player player) {
        CompoundTag tag = root(player, false);
        return tag != null && tag.getBoolean("confirmed");
    }

    /** 最终确认本世天赋选择（锁定；由寿元为0转世的 {@link #wipeAll} 自动清除）。 */
    public static void markConfirmed(Player player) {
        update(player, tag -> tag.putBoolean("confirmed", true));
    }

    /**
     * 本世的「已确认」是否已经真正结算过（上限 / 体质 / 道痕 / 属性落地）。
     *
     * <p>用于兜住第三方 UI：整合包的 {@code TianfuBridge.confirmFinal} 只调
     * {@link #markConfirmed}，不调 {@code TianfuApply.applyAllConfirmed}，
     * 会造成「已锁定但无任何效果」。{@link TianfuEffects#onTick} 据此自愈。
     */
    public static boolean isSettled(Player player) {
        CompoundTag tag = root(player, false);
        return tag != null && tag.getBoolean("settled");
    }

    public static void markSettled(Player player) {
        update(player, tag -> tag.putBoolean("settled", true));
    }

    /**
     * 本世购买是否走「延迟落地」（1.2.8 起：购买只落库，确认时才写上限）。
     *
     * <p>用于兼容旧存档：老版本购买时就把上限 / 体质写进 PlayerVariables 了，
     * 若确认时再按新流程结算一次会重复叠乘（上限翻倍）。因此购买时打上此标记，
     * 确认时据此决定是否需要补写。
     */
    public static boolean isDeferred(Player player) {
        CompoundTag tag = root(player, false);
        return tag != null && tag.getBoolean("deferred");
    }

    public static void markDeferred(Player player) {
        update(player, tag -> tag.putBoolean("deferred", true));
    }

    // ------------------------------------------------------------------ 特效开关

    /**
     * 是否播放天赋特效（体质形态 / 词条光环 / 购买爆发）。默认<b>开</b>。
     *
     * <p>存在 {@code tianfu/vfx}；老存档没有这个键按开处理，升级后行为不变。
     * 关闭后本模组的「体质形态 / 词条光环 / 购买爆发」不再广播粒子
     * （不是客户端隐藏，是根本不发包，对性能是实打实的节省）；
     * 「X烛」状态粒子与侵蚀爆发走另一套代码，不在此列。
     */
    public static boolean vfxEnabled(Player player) {
        return getBoolean(player, "vfx", true);
    }

    public static void setVfxEnabled(Player player, boolean on) {
        putBoolean(player, "vfx", on);
    }

    // ------------------------------------------------------------------ 点数与购买

    /** 自由点余额（开局按难度发放，可购全部条目）。 */
    public static int points(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0 : tag.getInt("points");
    }

    public static void addPoints(Player player, int delta) {
        update(player, tag -> tag.putInt("points", Math.max(0, tag.getInt("points") + delta)));
    }

    // ------------------------------------------------------------------ 开局难度

    /** 本世难度下标（{@link TianfuDifficulty#NOT_CHOSEN} = 未选）。 */
    public static int difficulty(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains("difficulty") ? TianfuDifficulty.NOT_CHOSEN : tag.getInt("difficulty");
    }

    public static void setDifficulty(Player player, int index) {
        update(player, tag -> tag.putInt("difficulty", index));
    }

    /**
     * 转世总结算：清空本模组全部数据（点数 / 已购 / 难度 / 全部标记）。
     * 由 {@code JingJieBaoCunProcedureMixin} 在寿元耗尽（或修炼 gamerule 关闭）的死亡里调用，
     * 下世开窍后重新选难度。
     */
    public static void wipeAll(Player player) {
        player.getPersistentData().remove(ROOT);
    }

    public static List<String> purchased(Player player) {
        CompoundTag tag = root(player, false);
        List<String> out = new ArrayList<>();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList("purchased", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    /** 玩家已购条目里是否含某 id。 */
    public static boolean has(Player player, String id) {
        return purchased(player).contains(id);
    }

    /**
     * 增量购买：原子校验（余额足额 + 未重复）后扣点并把 id 写入 {@code purchased}。
     *
     * @param cost 条目成本；负值 = 返还点数
     * @return 是否购买成功
     */
    /** V2 契约：保留签名，最终统一进入V2原子购买事务，cost由服务端目录核对且记录Receipt。未填充时拒绝执行，不写入玩家状态。 */
    public static boolean purchase(Player player, String id, int cost) {
        if (player == null || id == null || id.isEmpty()) {
            return false;
        }
        // cost 只作兼容参数：真实成交价一律由服务端目录重算并记入 Receipt。
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        net.guzhenren.tianfu.v2.TianfuSelection.Result result =
                net.guzhenren.tianfu.v2.TianfuEconomy.buy(snapshot, id);
        if (!result.ok()) {
            return false;
        }
        if (!net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                new net.guzhenren.tianfu.v2.TianfuSelection.Change(snapshot.revision(), result.snapshot()))) return false;
        markDeferred(player);
        return true;
    }

    /**
     * 撤销已购条目：原子从 purchased 移除 id 并退回点数（退回值 = +cost；cost 为负时相当于扣回之前返还的点数）。
     *
     * @return 是否撤销成功
     */
    /** V2 契约：保留签名，cost只作兼容参数，实际退款以Receipt为准，负余额拒绝。未填充时拒绝执行，不写入玩家状态。 */
    public static boolean revoke(Player player, String id, int cost) {
        if (player == null || id == null || id.isEmpty()) {
            return false;
        }
        // cost 只作兼容参数：退款一律按 Receipt 原成交价，不查新目录。
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        net.guzhenren.tianfu.v2.TianfuSelection.Result result =
                net.guzhenren.tianfu.v2.TianfuEconomy.revoke(snapshot, id);
        if (!result.ok()) {
            return false;
        }
        return net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                new net.guzhenren.tianfu.v2.TianfuSelection.Change(snapshot.revision(), result.snapshot()));
    }

    // ------------------------------------------------------------------ 命运由天（随机天赋）

    /** 本世已摇次数（上限 {@link TianfuRoll#MAX_ROLLS}，服务端记账，重登不重置）。 */
    public static int rollCount(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0 : tag.getInt("roll");
    }

    public static void setRollCount(Player player, int value) {
        update(player, tag -> tag.putInt("roll", Math.max(0, value)));
    }

    /**
     * 待接受的随机结果（{@code rollpending}）。
     *
     * <p>接受前<b>不</b>写入 {@code purchased}：十绝体 / 机缘不可撤销
     * （见 {@code TianfuApply.revoke}），所以「先落库再重摇」这条路是死的，
     * 只能先在待定区预览，接受时一次性落库。
     */
    public static List<String> rollPending(Player player) {
        CompoundTag tag = root(player, false);
        List<String> out = new ArrayList<>();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList("rollpending", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    public static void setRollPending(Player player, List<String> ids) {
        update(player, tag -> {
            ListTag list = new ListTag();
            for (String id : ids) {
                list.add(StringTag.valueOf(id));
            }
            tag.put("rollpending", list);
        });
    }

    public static void clearRollPending(Player player) {
        update(player, tag -> tag.remove("rollpending"));
    }

    /**
     * 接受随机结果：<b>原子</b>地把整份结果写入 {@code purchased}、扣净成本、清空待定区。
     *
     * <p>逐条走 {@link #purchase} 会留下「一半成功一半失败」的中间态，
     * 而随机结果是一个整体（必须同时满足体质唯一 / 机缘唯一 / 含资质），所以一次写完。
     *
     * @param ids     待落库条目
     * @param netCost 净成本（已由生成阶段保证 ≤ 余额；负值表示净返还）
     * @return 是否成功（余额不足或已确认时为 false，且不改动任何字段）
     */
    /** V2 契约：保留签名，整批查价和24限额且原子提交，netCost仅作兼容校验不得信任。未填充时拒绝执行，不写入玩家状态。 */
    public static boolean applyRoll(Player player, List<String> ids, int netCost) {
        if (player == null || ids == null || ids.isEmpty()) {
            return false;
        }
        // netCost 只作兼容参数：整批价格与 24 限额一律由服务端重新核算。
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        net.guzhenren.tianfu.v2.TianfuSelection.Result result =
                net.guzhenren.tianfu.v2.TianfuEconomy.acceptRoll(snapshot, ids);
        if (!result.ok()) {
            return false;
        }
        if (!net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                new net.guzhenren.tianfu.v2.TianfuSelection.Change(snapshot.revision(), result.snapshot()))) return false;
        markDeferred(player);
        return true;
    }

    // ------------------------------------------------------------------ 杀神杀戮值

    public static int slay(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0 : tag.getInt("slay");
    }

    public static long lastKill(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0L : tag.getLong("lastKill");
    }

    public static void setSlay(Player player, int value) {
        update(player, tag -> tag.putInt("slay", Math.max(0, value)));
    }

    public static void addSlay(Player player, int delta) {
        update(player, tag -> tag.putInt("slay", Math.max(0, tag.getInt("slay") + delta)));
    }

    public static void setSlayProgress(Player player, long lastKillTick, long day, int daily) {
        update(player, tag -> {
            tag.putLong("lastKill", lastKillTick);
            tag.putLong("slayDay", day);
            tag.putInt("slayDaily", daily);
        });
    }

    public static long slayDay(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? -1L : tag.getLong("slayDay");
    }

    public static int slayDaily(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0 : tag.getInt("slayDaily");
    }

    // ------------------------------------------------------------------ 杀怪噬道进度

    public static long devourDay(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains("devourDay", Tag.TAG_LONG) ? -1L : tag.getLong("devourDay");
    }

    public static int devourDaily(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0 : tag.getInt("devourDaily");
    }

    public static void setDevourProgress(Player player, long day, int daily) {
        update(player, tag -> {
            tag.putLong("devourDay", day);
            tag.putInt("devourDaily", daily);
        });
    }

    // ------------------------------------------------------------------ 杀怪防刷（同种衰减）

    public static String getType(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains("lastType", Tag.TAG_STRING) ? "" : tag.getString("lastType");
    }

    public static int getStreak(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? 0 : tag.getInt("streak");
    }

    public static void setTypeStreak(Player player, String type, int streak) {
        update(player, tag -> {
            tag.putString("lastType", type);
            tag.putInt("streak", streak);
        });
    }

    // ------------------------------------------------------------------ 万法皆通快照

    /** 万法皆通的流派熟练度快照（缺失时返回空 tag，调用方以当时值为基准，不追补历史）。 */
    public static CompoundTag liupaiSnap(Player player) {
        CompoundTag tag = root(player, false);
        return tag == null ? new CompoundTag() : tag.getCompound("liupai_snap");
    }

    public static void setLiupaiSnap(Player player, CompoundTag snap) {
        update(player, tag -> tag.put("liupai_snap", snap));
    }

    // ------------------------------------------------------------------ 通用键读写

    /** 通用 long 读写（太虚化形的内置 CD 等）。 */
    public static long getLong(Player player, String key, long fallback) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains(key, Tag.TAG_LONG) ? fallback : tag.getLong(key);
    }

    public static void putLong(Player player, String key, long value) {
        update(player, tag -> tag.putLong(key, value));
    }

    /** 通用 bool 读写。 */
    public static boolean getBoolean(Player player, String key, boolean fallback) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains(key, Tag.TAG_BYTE) ? fallback : tag.getBoolean(key);
    }

    public static void putBoolean(Player player, String key, boolean value) {
        update(player, tag -> tag.putBoolean(key, value));
    }

    /** 通用 double 读写（炼蛊进度小数累加器等）。 */
    public static double getDouble(Player player, String key, double fallback) {
        CompoundTag tag = root(player, false);
        return tag == null || !tag.contains(key, Tag.TAG_DOUBLE) ? fallback : tag.getDouble(key);
    }

    public static void putDouble(Player player, String key, double value) {
        update(player, tag -> tag.putDouble(key, value));
    }
}
