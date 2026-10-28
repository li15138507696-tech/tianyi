package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 历练系统：<b>单一历练池</b>的唯一动力来源。
 *
 * <p>三路历练来源：
 * <ol>
 *   <li><b>战斗</b>——击杀生物按最大生命给分，杀强敌给得多；受伤给零头。</li>
 *   <li><b>游历</b>——走过距离 + 首次进入新群系。走得越远见识越广。</li>
 *   <li><b>修炼</b>——在线时长按秒给分，打坐（潜行静止）翻倍。</li>
 * </ol>
 *
 * <p><b>v2 改动</b>：v1 把同一份历练按 16 个吸收系数分给 16 条路线，
 * 玩家看到的是一堆同时上涨的数字、每次升阶奖励很小。
 * 现在三路全部汇入 {@link DaotuRun#pool}，攒满 {@link DaotuRun#POOL_THRESHOLD}
 * 就由 {@link DaotuDraw} 抽一次——每次抽选都是明确、可选择的收益。
 *
 * <p><b>防刷</b>：单次击杀上限、单次受伤上限、每秒位移上限、按秒节流。
 * 全部状态存在玩家 PersistentData，只在服务端主线程调用，天然安全。
 */
public final class DaotuLiLian {

    // ---------------------------------------------------------------- 数值配置

    /** 击杀：每点最大生命值折算的历练。 */
    private static final double PER_KILL_HP = 0.8;

    /** 击杀：单次上限，防止刷巨型生物。 */
    private static final double KILL_CAP = 400.0;

    /** 游历：每走多少格折算 1 点历练。 */
    private static final double BLOCKS_PER_POINT = 24.0;

    /** 游历：单秒位移上限，超过视为传送（防瞬移刷）。 */
    private static final double MAX_BLOCKS_PER_TICK = 3.0;

    /** 游历：首次进入新群系的一次性奖励。 */
    private static final double BIOME_BONUS = 25.0;

    /** 修炼：每多少秒给 1 点历练。 */
    private static final int SECONDS_PER_POINT = 12;

    /** 修炼：打坐倍率。 */
    private static final double MEDITATE_MULT = 2.0;

    /** 受伤：单次上限。 */
    private static final double HURT_CAP = 30.0;

    /** 存档键：上次坐标（算走过距离）。 */
    private static final String KEY_LAST_X = "lastX";
    private static final String KEY_LAST_Y = "lastY";
    private static final String KEY_LAST_Z = "lastZ";
    private static final String KEY_HAS_POS = "hasPos";

    /** 存档键：已到访群系（去重用）。 */
    private static final String KEY_BIOMES = "biomes";

    private DaotuLiLian() {
    }

    // ---------------------------------------------------------------- 三路来源

    /**
     * 怪物分档（v1.4.0 新增）。
     *
     * <p>原版「按最大生命线性给分」有个问题：一只 300 血的巨型怪和 30 只 10 血的小怪
     * 给的分一样，但后者要打半天。分档让「难打的怪」明确给得更多，鼓励玩家挑战强敌。
     *
     * <p>判定顺序（从强到弱，先命中先返回）：
     * <ol>
     *   <li><b>首领</b>——自带 Boss 血条，或类名含 {@code boss}；</li>
     *   <li><b>精英</b>——最大生命 ≥ 100，或基础 mod 的高阶命名（王/君/尊/帝/皇/祖）；</li>
     *   <li><b>普通</b>——其它 {@code Monster}；</li>
     *   <li><b>弱小</b>——动物、村民等非 Monster。</li>
     * </ol>
     *
     * <p><b>为什么不硬编码 656 个实体类名</b>：基础 mod 有 656 个生物类，
     * 硬编码名单一改版本就失效。这里用「血条 + 血量 + 命名 + 原版类型」四个
     * 廉价且稳定的信号，对未知生物也有合理默认值。
     */
    private static double killMultiplier(LivingEntity victim) {
        try {
            // 1. 首领：有 Boss 血条（原版 EnderDragon / Wither 也走这条）
            if (victim instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                    || victim instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
                return 8.0;
            }
            String cls = victim.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (cls.contains("boss")) {
                return 8.0;
            }

            // 2. 基础 mod 的高阶命名（王/君/尊/帝/皇/祖 = 一方强者）
            boolean namedElite = cls.contains("wang") || cls.contains("jun")
                    || cls.contains("zun") || cls.contains("di")
                    || cls.contains("huang") || cls.contains("zu");

            double hp = victim.getMaxHealth();

            // 3. 血量分档（最稳的信号：不依赖类名与 mod 内部结构）
            if (hp >= 200.0) {
                return 6.0;
            }
            if (hp >= 100.0) {
                return 3.0;
            }
            if (namedElite && hp >= 40.0) {
                return 3.0;
            }

            // 4. 非 Monster（动物、村民）：打它们没什么历练
            if (!(victim instanceof net.minecraft.world.entity.monster.Monster)) {
                return 0.4;
            }
            return 1.0;
        } catch (Throwable t) {
            return 1.0; // 判定失败就按普通怪算，绝不让异常冒到事件总线
        }
    }

    /** 战斗历练：击杀生物后调用。 */
    public static void onKill(ServerPlayer player, LivingEntity victim) {
        try {
            double hp = Math.max(1.0, victim.getMaxHealth());
            double mult = killMultiplier(victim);
            // 转数加成：高转玩家打同样的怪给得多，抵消阈值递增（144→729）
            double zBonus = 1.0 + (DaotuRun.zhuanshu(player) - 1.0) * 0.35;
            double gain = Math.min(KILL_CAP, hp * PER_KILL_HP) * mult * zBonus;
            award(player, gain, 0.0, 0.0, "击杀 " + victim.getName().getString());
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onKill 失败（已忽略）", t);
        }
    }

    /** 受伤历练：被打也给一点，鼓励硬碰硬而非永远躲着。 */
    public static void onHurt(ServerPlayer player, float amount) {
        try {
            if (amount <= 0.0f) {
                return;
            }
            // 受伤只是战斗的零头，避免「故意挨打刷历练」
            award(player, Math.min(HURT_CAP, amount * 0.5), 0.0, 0.0, null);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onHurt 失败（已忽略）", t);
        }
    }

    /**
     * 游历 + 修炼历练：每秒调用一次（由 {@link DaotuRuntime} 节流）。
     */
    public static void onSecond(ServerPlayer player) {
        try {
            double travel = 0.0;

            // ---- 游历：算这一步走了多远 ----
            BlockPos pos = player.blockPosition();
            boolean hasPos = DaotuData.getBoolean(player, KEY_HAS_POS, false);
            if (hasPos) {
                double lx = DaotuData.getDouble(player, KEY_LAST_X, pos.getX());
                double ly = DaotuData.getDouble(player, KEY_LAST_Y, pos.getY());
                double lz = DaotuData.getDouble(player, KEY_LAST_Z, pos.getZ());
                double dx = pos.getX() - lx;
                double dz = pos.getZ() - lz;
                double dy = pos.getY() - ly;
                // 只算水平距离：竖直方向多半是电梯/摔落，不该给历练
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > MAX_BLOCKS_PER_TICK * 20.0 || Math.abs(dy) > 200.0) {
                    dist = 0.0; // 传送
                }
                if (dist > 0.5) {
                    travel += dist / BLOCKS_PER_POINT;
                }
            }
            DaotuData.putDouble(player, KEY_LAST_X, pos.getX());
            DaotuData.putDouble(player, KEY_LAST_Y, pos.getY());
            DaotuData.putDouble(player, KEY_LAST_Z, pos.getZ());
            DaotuData.putBoolean(player, KEY_HAS_POS, true);

            // ---- 游历：首次进入新群系 ----
            String biome = player.level().getBiome(pos).unwrapKey()
                    .map(k -> k.location().toString()).orElse("");
            if (!biome.isEmpty()) {
                String seen = DaotuData.getString(player, KEY_BIOMES, "");
                if (!seen.contains("|" + biome + "|")) {
                    DaotuData.putString(player, KEY_BIOMES, seen + "|" + biome + "|");
                    travel += BIOME_BONUS;
                }
            }

            // ---- 修炼：按秒累计，打坐翻倍 ----
            boolean meditating = player.isShiftKeyDown()
                    && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4
                    && player.onGround();
            double cultivate = 1.0 / SECONDS_PER_POINT * (meditating ? MEDITATE_MULT : 1.0);

            award(player, 0.0, travel, cultivate, null);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onSecond 失败（已忽略）", t);
        }
    }

    // ---------------------------------------------------------------- 结算

    /**
     * 结算一次历练：全部汇入单池，池够就弹窗让玩家选。
     *
     * <p>v2 取消了 16 路分账——原来同一份历练按 16 个吸收系数分给 16 条路线，
     * 玩家看到的是一堆同时上涨的数字，但每次升阶给的奖励很小。
     * 现在汇成一个池，攒满 {@link DaotuRun#thresholdOf} 就弹一次选择。
     *
     * <p><b>v1.4.0</b>：阈值随转数递增，所以这里每次都要问「当前玩家的阈值是多少」，
     * 不能用固定常量。
     *
     * @param combat    战斗历练
     * @param travel    游历历练
     * @param cultivate 修炼历练
     * @param reason    已废弃（v1 用于升阶播报），保留参数以免改动全部调用点
     */
    public static void award(ServerPlayer player, double combat, double travel,
                             double cultivate, String reason) {
        if (!DaotuOpening.hasOpenedQiao(player)) {
            return;
        }
        double gain = combat + travel + cultivate;
        if (gain <= 0.0) {
            return;
        }
        // v1.5.0：一转~五转每转最多 100 抽。到顶后历练【停止累计】而不是照涨——
        // 否则玩家会看到一池永远用不掉的历练，只会以为系统坏了。
        // 这里提前拦掉，比让玩家攒满再发现抽不了要诚实得多。
        if (DaotuDraw.atCap(player)) {
            return;
        }
        DaotuRun.addPool(player, gain);
        // 攒满就生成候选并弹窗（不扣池；扣池发生在玩家真正选定时）
        DaotuDraw.drawAll(player);
    }

    /** 调试/界面用：当前所在维度 id。 */
    public static String dimensionOf(Player player) {
        return player.level().dimension().location().toString();
    }
}

