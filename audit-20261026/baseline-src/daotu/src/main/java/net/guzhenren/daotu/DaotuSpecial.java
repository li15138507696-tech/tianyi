package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.network.GuzhenrenModVariables;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * 每 10 次抽选的特殊奖励。
 *
 * <h2>v1.5.0 语义变更：从「系统随机发」改成「玩家自己选」</h2>
 * 旧实现（v1.4.x）在 {@code drawIndex % 10 == 0} 时直接 {@code grant()}：
 * 3/5 概率随机给一个蛊虫 buff、2/5 概率随机给一条道痕。
 * 两个问题：
 * <ul>
 *   <li><b>玩家没有参与感</b>——攒了十次的大奖是系统塞的，可能塞个完全用不上的道；</li>
 *   <li><b>蛊虫 buff 与主修无关</b>——练剑道的给你发个水道增幅，纯浪费。</li>
 * </ul>
 * 现在改成：到点时<b>生成一批候选存进 {@code run/offer}</b>，玩家点选后才发奖，
 * 与普通抽选完全同一条流程（见 {@link DaotuDraw#tryDraw} / {@link DaotuDraw#choose}）。
 *
 * <h2>候选内容（v1.5.0）</h2>
 * <ol>
 *   <li><b>主修道痕</b>——按 {@link DaotuMainDao} 给，量大（单次 {@link #DAOHEN_BASE}
 *       起，随转数缩放）；</li>
 *   <li><b>主修流派熟练度</b>——本体 {@code liupai_*} 字段，按道痕的 1/10 给
 *       （本体十绝体正是 daohen 2000 / liupai 200，这个比例是本体自定的）；</li>
 *   <li><b>气运上限</b>——本体 {@code qiyun_shangxian}，全流派通用。</li>
 * </ol>
 *
 * <p><b>蛊虫 buff 与「主修蛊虫」都已移除</b>（原 3/5 概率那条路径，以及
 * v1.5.0 中途加过又撤掉的蛊虫物品项）。
 * {@link DaotuBuffs} 与 {@code run/buffs} 仍保留——词条（{@link DaotuTalentApply}）
 * 也可能给 buff，界面的「特殊增益」区还要显示它们。
 *
 * <h2>为什么撤掉「主修蛊虫」</h2>
 * 曾按「转数 + 流派气」映射过一批物品（{@code YIZHUANJIANQI} 一转剑气 …
 * {@code WU_ZHUANJIANQI} 五转剑气）。用户指出那批是「气」不是「蛊虫」。
 * 重新穷举本体 5607 个物品后确认：按转数分级且<b>真以 GU 结尾</b>的蛊虫只有
 * 21 个，而且是掠影蛊、蛇信蛊、羽毛蛊、人道无为蛊这类剧情/功能蛊，
 * <b>完全不对应流派</b>——「流派 × 转数 → 代表蛊虫」这个映射在本体里并不存在。
 * 与其编一张自己都不确定的表，不如不做。
 *
 * <h2>未设主修时</h2>
 * 主修相关的两项（道痕 / 熟练度）都不出现，只给气运。
 * 玩家会看到候选少，聊天栏提示去页脚设主修。
 */
public final class DaotuSpecial {

    /** 单次特殊给的道痕量（一转基准）。 */
    public static final double DAOHEN_BASE = 100.0;

    /** 单次特殊给的气运上限增量。 */
    public static final double QIYUN_BASE = 5.0;

    /**
     * 「全流派道痕」词条（{@code DAOHEN_ALL}）要发的字段清单。
     *
     * <p>保留在这里而不是挪去 {@link DaotuGrowthApply}：调用方
     * {@link DaotuTalentApply} 已经在引用这个名字，且它就是「一批道痕字段」的语义。
     *
     * <p><b>v1.5.0 修正</b>：原来含 {@code daohen_huodao}，但本体<b>根本没有这个字段</b>
     * （火道不单独占道痕，见 {@link DaotuMainDao}）。那条一直是死引用——
     * {@code addDaohen} 对它返回 false，只记一行日志，玩家毫无感知。
     * 现已移除，清单与 {@link DaotuMainDao#ALL} 保持一致。
     */
    public static final String[] DAOHEN_KEYS = {
        "daohen_jiandao", "daohen_jindao", "daohen_shuidao", "daohen_xuedao",
        "daohen_lidao", "daohen_mudao", "daohen_yudao", "daohen_fengdao",
        "daohen_bingdao", "daohen_jindao2", "daohen_hundao", "daohen_leidao",
        "daohen_yandao", "daohen_lvdao", "daohen_tudao",
    };

    /** 道痕 → 流派熟练度 的比例（本体口径：daohen 2000 / liupai 200）。 */
    private static final double LIUPAI_RATIO = 0.1;

    /** 特殊奖励的候选数（比普通抽选的 5 少，因为项本来就少）。 */
    public static final int OPTIONS = 3;

    /** 特殊奖励的 key 前缀，避免与普通属性候选混淆。 */
    public static final String PREFIX = "special:";

    /** 该候选 key 是否是特殊奖励（{@link DaotuDraw#choose} 用它分流）。 */
    public static boolean isSpecial(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    private DaotuSpecial() {
    }

    /**
     * 生成本次特殊奖励的候选。
     *
     * <p><b>不发奖、不扣池</b>——和普通抽选一样，结算在 {@link DaotuDraw#choose}。
     *
     * @param player    玩家
     * @param drawIndex 本次抽次（必为 10 的倍数）
     * @param rnd       随机源（候选顺序打乱用）
     * @return 候选列表；永不为空（至少有气运）
     */
    public static List<DaotuDrawTable.Candidate> candidates(
            Player player, int drawIndex, RandomSource rnd) {
        double scale = DaotuDrawTable.zhuanScale(DaotuRun.zhuanshu(player));
        String dao = DaotuMainDao.of(player);
        List<DaotuDrawTable.Candidate> out = new ArrayList<>();

        // ---- 1. 主修道痕 ----
        if (!dao.isEmpty()) {
            double amount = DAOHEN_BASE * scale;
            out.add(new DaotuDrawTable.Candidate(
                    PREFIX + "daohen:" + dao,
                    DaotuMainDao.label(dao) + "道痕 +" + fmt(amount),
                    amount, false));
        }

        // ---- 2. 主修流派熟练度 ----
        if (!dao.isEmpty()) {
            double amount = DAOHEN_BASE * scale * LIUPAI_RATIO;
            out.add(new DaotuDrawTable.Candidate(
                    PREFIX + "liupai:" + dao,
                    DaotuMainDao.label(dao) + "熟练 +" + fmt(amount),
                    amount, false));
        }

        // ---- 3. 气运上限（全流派通用，永远有） ----
        double qy = QIYUN_BASE * scale;
        out.add(new DaotuDrawTable.Candidate(
                PREFIX + "qiyun",
                "气运上限 +" + fmt(qy),
                qy, false));

        // 打乱顺序，避免主修道痕永远是第 1 个（玩家会无脑点 1）
        for (int i = out.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            DaotuDrawTable.Candidate t = out.get(i);
            out.set(i, out.get(j));
            out.set(j, t);
        }
        return out;
    }

    /**
     * 发奖。由 {@link DaotuDraw#choose} 在候选 key 以 {@link #PREFIX} 开头时调用。
     *
     * @return 是否成功（键不认识返回 false）
     */
    public static boolean grant(ServerPlayer player, String key, double amount) {
        if (!key.startsWith(PREFIX)) {
            return false;
        }
        String body = key.substring(PREFIX.length());

        if (body.equals("qiyun")) {
            return grantQiyun(player, amount);
        }
        int colon = body.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String kind = body.substring(0, colon);
        String dao = body.substring(colon + 1);

        switch (kind) {
            case "daohen" -> {
                return grantDaohen(player, dao, amount);
            }
            case "liupai" -> {
                return grantLiupai(player, dao, amount);
            }
            default -> {
                return false;
            }
        }
    }

    // ---------------------------------------------------------------- 各项落地

    /** 道痕：写本体 {@code daohen_*}，并记 {@code run/ledger} 以便转世扣回。 */
    private static boolean grantDaohen(ServerPlayer player, String dao, double amount) {
        String field = "daohen_" + dao;
        var v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (!DaotuGrowthApply.addDaohen(v, field, amount)) {
            DaotuMod.LOGGER.warn("特殊奖励道痕字段不存在: {}", field);
            return false;
        }
        v.markSyncDirty();
        DaotuRun.addLedger(player, field, amount);
        player.displayClientMessage(Component.literal(
                "§6[道途] §d特殊奖励 §7· §f" + DaotuMainDao.label(dao)
                        + "道痕 §b+" + fmt(amount)), false);
        return true;
    }

    /** 流派熟练度：写本体 {@code liupai_*}。转世时随本体字段一起归零，不记 ledger。 */
    private static boolean grantLiupai(ServerPlayer player, String dao, double amount) {
        String field = "liupai_" + dao;
        if (!DaotuGrowthApply.addLiupai(player, field, amount)) {
            DaotuMod.LOGGER.warn("特殊奖励流派熟练度字段不存在: {}", field);
            return false;
        }
        player.displayClientMessage(Component.literal(
                "§6[道途] §d特殊奖励 §7· §f" + DaotuMainDao.label(dao)
                        + "熟练 §b+" + fmt(amount)), false);
        return true;
    }

    /**
     * 气运上限：{@code qiyun_shangxian += amount}，下限 0。
     *
     * <p>只抬上限、不改当前气运 {@code qiyun}——本体每秒执行
     * {@code if (qiyun < qiyun_shangxian) qiyun = qiyun_shangxian}，
     * 抬上限后当前值会自然跟上。直接改 {@code qiyun} 会被下一秒的削平覆盖。
     */
    private static boolean grantQiyun(ServerPlayer player, double amount) {
        // 天命「天弃之命」本世气运恒为 0：此时发放会被本体每 tick 清零，
        // 与其给玩家一条假的「+X」提示，不如直接跳过并说明原因。
        if (DaotuTianfuCompat.luckForfeit(player)) {
            player.displayClientMessage(Component.literal(
                    "§6[道途] §7· §f天命「天弃之命」本世气运恒为 0，气运上限奖励不生效"), false);
            return false;
        }
        var v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double before = v.qiyun_shangxian;
        v.qiyun_shangxian = Math.max(0.0, before + amount);
        v.markSyncDirty();
        player.displayClientMessage(Component.literal(
                "§6[道途] §d特殊奖励 §7· §f气运上限 §b+" + fmt(amount)), false);
        return true;
    }

    // ---------------------------------------------------------------- 工具

    /** 特殊奖励 key → 中文名（历史记录、界面、日志都用它）。 */
    public static String labelOf(String key) {
        if (!isSpecial(key)) {
            return key;
        }
        String body = key.substring(PREFIX.length());
        if (body.equals("qiyun")) {
            return "气运上限 +" + fmt(QIYUN_BASE);
        }
        int colon = body.indexOf(':');
        if (colon < 0) {
            return body;
        }
        String kind = body.substring(0, colon);
        String arg = body.substring(colon + 1);
        return switch (kind) {
            case "daohen" -> DaotuMainDao.label(arg) + "道痕";
            case "liupai" -> DaotuMainDao.label(arg) + "熟练";
            default -> body;
        };
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }
}
