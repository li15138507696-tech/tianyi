package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 一局（一个转世周期）的全部成长状态。
 *
 * <p>存在 {@code daotu/run} 子标签里，与势力 / 流派节点 / 觉醒并列。
 * 转世只删这一个子标签，其余（势力、节点）按规格书保持原状。
 *
 * <p><b>为什么属性存「已累加的绝对值」而不是「抽选记录」</b>：
 * {@link DaotuRunAttributes} 每次从 {@code attrs} 全量重建 modifier，
 * 这样重选、转世、离线登录都不需要重放历史，也不怕中途改过数值表。
 */
public final class DaotuRun {

    /**
     * 一转攒满一次抽选所需的历练值。
     *
     * <p>v1.4.0 起阈值不再是固定 240，改为<b>按转数分档递增</b>：
     * 一转在 240 基础上减 40% → 144，之后每转 ×1.5。
     * 见 {@link #threshold(double)}。
     */
    public static final double BASE_THRESHOLD = 240.0;

    /** 一转相对基准的折扣（减 40%）。 */
    public static final double FIRST_ZHUAN_DISCOUNT = 0.6;

    /** 每转递增倍数。 */
    public static final double THRESHOLD_GROWTH = 1.5;

    /**
     * 五转（最高转数）的阈值，供界面画进度条用。
     *
     * <p>= 144 × 1.5^4 = 729。
     */
    public static final double MAX_THRESHOLD = threshold(5.0);

    /**
     * 转数 → 攒满一次抽选所需的历练值。
     *
     * <pre>
     * 一转 144.0   二转 216.0   三转 324.0   四转 486.0   五转 729.0
     * </pre>
     *
     * <p>为什么随转数增长：高转玩家一次击杀给的历练本来就多（见
     * {@code DaotuLiLian} 的转数加成），若阈值不变，五转玩家会一秒钟抽十几次，
     * 属性瞬间爆表。阈值同步放大，才能让「一次抽选」在整局里都保持分量。
     *
     * <p>为什么一转要减 40%：新手期最需要正反馈，第一世应该很快就能抽到第一个选项。
     */
    public static double threshold(double zhuanshu) {
        double z = Math.max(1.0, Math.min(5.0, zhuanshu));
        return BASE_THRESHOLD * FIRST_ZHUAN_DISCOUNT
                * Math.pow(THRESHOLD_GROWTH, z - 1.0);
    }

    /** 当前玩家所处转数的阈值。 */
    public static double thresholdOf(Player player) {
        return threshold(zhuanshu(player));
    }

    /**
     * 兼容旧调用点：默认按一转算。
     *
     * @deprecated 改用 {@link #thresholdOf(Player)} 或 {@link #threshold(double)}
     */
    @Deprecated
    public static final double POOL_THRESHOLD = BASE_THRESHOLD * FIRST_ZHUAN_DISCOUNT;

    private static final String RUN = "run";

    private DaotuRun() {
    }

    private static CompoundTag run(Player player, boolean create) {
        return DaotuData.sub(player, RUN, create);
    }

    // ---------------------------------------------------------------- 池与抽次

    public static double pool(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0.0 : t.getDouble("pool");
    }

    public static void setPool(Player player, double value) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.putDouble("pool", Math.max(0.0, value));
            tag.put(RUN, r);
        });
    }

    /** 累加历练，返回加完后的池值。负数忽略。 */
    public static double addPool(Player player, double delta) {
        if (delta <= 0.0) {
            return pool(player);
        }
        final double[] out = new double[1];
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            double v = r.getDouble("pool") + delta;
            r.putDouble("pool", v);
            tag.put(RUN, r);
            out[0] = v;
        });
        return out[0];
    }

    public static int draws(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0 : t.getInt("draws");
    }

    public static void setDraws(Player player, int value) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.putInt("draws", Math.max(0, value));
            tag.put(RUN, r);
        });
    }

    public static int runId(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0 : t.getInt("id");
    }

    /** 只读本体转数（1..5）。绝不写回。 */
    public static double zhuanshu(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return 1.0;
        }
        return sp.getData(net.guzhenren.network.GuzhenrenModVariables.PLAYER_VARIABLES).zhuanshu;
    }

    // ---------------------------------------------------------------- 属性

    public static double attr(Player player, String key) {
        CompoundTag t = run(player, false);
        if (t == null || !t.contains("attrs", Tag.TAG_COMPOUND)) {
            return 0.0;
        }
        return t.getCompound("attrs").getDouble(key);
    }

    public static void setAttr(Player player, String key, double value) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            CompoundTag a = r.contains("attrs", Tag.TAG_COMPOUND)
                    ? r.getCompound("attrs") : new CompoundTag();
            if (Math.abs(value) <= 1.0e-9) {
                a.remove(key);
            } else {
                a.putDouble(key, value);
            }
            r.put("attrs", a);
            tag.put(RUN, r);
        });
    }

    public static Map<String, Double> attrs(Player player) {
        CompoundTag t = run(player, false);
        Map<String, Double> out = new LinkedHashMap<>();
        if (t == null || !t.contains("attrs", Tag.TAG_COMPOUND)) {
            return out;
        }
        CompoundTag a = t.getCompound("attrs");
        for (String k : a.getAllKeys()) {
            out.put(k, a.getDouble(k));
        }
        return out;
    }

    // ---------------------------------------------------------------- buff

    public static List<String> buffs(Player player) {
        CompoundTag t = run(player, false);
        List<String> out = new ArrayList<>();
        if (t == null || !t.contains("buffs", Tag.TAG_LIST)) {
            return out;
        }
        ListTag l = t.getList("buffs", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    public static void addBuff(Player player, String id) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            ListTag l = r.contains("buffs", Tag.TAG_LIST)
                    ? r.getList("buffs", Tag.TAG_STRING) : new ListTag();
            for (int i = 0; i < l.size(); i++) {
                if (l.getString(i).equals(id)) {
                    return;
                }
            }
            l.add(StringTag.valueOf(id));
            r.put("buffs", l);
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 道痕账本

    public static Map<String, Double> ledger(Player player) {
        CompoundTag t = run(player, false);
        Map<String, Double> out = new LinkedHashMap<>();
        if (t == null || !t.contains("ledger", Tag.TAG_COMPOUND)) {
            return out;
        }
        CompoundTag g = t.getCompound("ledger");
        for (String k : g.getAllKeys()) {
            out.put(k, g.getDouble(k));
        }
        return out;
    }

    public static void addLedger(Player player, String key, double amount) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            CompoundTag g = r.contains("ledger", Tag.TAG_COMPOUND)
                    ? r.getCompound("ledger") : new CompoundTag();
            g.putDouble(key, g.getDouble(key) + amount);
            r.put("ledger", g);
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 已获词条

    /** 已获得的词条 id 列表（{@code run/talents}）。 */
    public static List<String> talents(Player player) {
        CompoundTag t = run(player, false);
        List<String> out = new ArrayList<>();
        if (t == null || !t.contains("talents", Tag.TAG_LIST)) {
            return out;
        }
        ListTag l = t.getList("talents", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    public static void addTalent(Player player, String id) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            ListTag l = r.contains("talents", Tag.TAG_LIST)
                    ? r.getList("talents", Tag.TAG_STRING) : new ListTag();
            for (int i = 0; i < l.size(); i++) {
                if (l.getString(i).equals(id)) {
                    return; // 去重
                }
            }
            l.add(StringTag.valueOf(id));
            r.put("talents", l);
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 界面偏好

    /**
     * 攒满历练时是否<b>自动弹出</b>界面（默认开）。
     *
     * <p>关掉后，攒满只在聊天栏提示一次「按 M 打开」，不再打断战斗。
     * 存在 {@code daotu/autoPrompt}（不在 {@code run} 里）。
     * <b>注意</b>：{@code wipeAll} 删的是整个 {@code daotu} 根，所以转世时它其实
     * 会被一起抹掉——{@code DaotuOpening.onReincarnation} 已显式存取快照再写回，
     * 保证界面偏好跨世保留。
     * 老存档没有这个键时按<b>开</b>处理，保证升级前的行为不变。
     *
     * <p>为什么存玩家数据而不是全局配置文件：多人服里每个人的习惯不同，
     * 一个人不想被打断，不代表所有人都想。
     */
    public static boolean autoPrompt(Player player) {
        return DaotuData.getBoolean(player, "autoPrompt", true);
    }

    public static void setAutoPrompt(Player player, boolean on) {
        // 存在 daotu 根下、不在 run 里：转世重置 run 时不该把界面偏好也清掉
        DaotuData.putBoolean(player, "autoPrompt", on);
    }

    // ---------------------------------------------------------------- 待选候选

    /** 待选候选的编码串（空串 = 无待选）。 */
    public static String offer(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? "" : t.getString("offer");
    }

    /** 待选候选对应的抽次（0 = 无待选）。 */
    public static int offerDraw(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0 : t.getInt("offerDraw");
    }

    public static void setOffer(Player player, int drawIndex, String encoded) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.putString("offer", encoded);
            r.putInt("offerDraw", drawIndex);
            tag.put(RUN, r);
        });
    }

    public static void clearOffer(Player player) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.remove("offer");
            r.remove("offerDraw");
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 已选历史

    /**
     * 已选定的抽选记录，格式 {@code 抽次|key|amount|percent}。
     *
     * <p>v1.4.0 起<b>只读</b>：选完即锁定，界面只展示不能改。
     * 保留是为了让玩家能回看「这一局我都选了什么」。
     */
    public static List<String> history(Player player) {
        CompoundTag t = run(player, false);
        List<String> out = new ArrayList<>();
        if (t == null || !t.contains("history", Tag.TAG_LIST)) {
            return out;
        }
        ListTag l = t.getList("history", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    public static void addHistory(Player player, String entry) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            ListTag l = r.contains("history", Tag.TAG_LIST)
                    ? r.getList("history", Tag.TAG_STRING) : new ListTag();
            l.add(StringTag.valueOf(entry));
            // 只留最近 40 条，避免 NBT 无限膨胀
            while (l.size() > 40) {
                l.remove(0);
            }
            r.put("history", l);
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 重开一局

    /** 只删 {@code run} 子标签。势力 / 节点 / 觉醒保留。 */
    public static void resetRun(Player player) {
        DaotuData.update(player, tag -> tag.remove(RUN));
    }

    /** 起一局新的：id +1，池归零。必须在 {@link #resetRun} 之后调用。 */
    public static void beginRun(Player player, int newId) {
        DaotuData.update(player, tag -> {
            CompoundTag r = new CompoundTag();
            r.putInt("id", newId);
            r.putDouble("pool", 0.0);
            r.putInt("draws", 0);
            tag.put(RUN, r);
        });
    }

    private static CompoundTag child(CompoundTag daotuTag) {
        return daotuTag.contains(RUN, Tag.TAG_COMPOUND)
                ? daotuTag.getCompound(RUN) : new CompoundTag();
    }
}
