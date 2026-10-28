package net.guzhenren.tianfu;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 杀神（tizhi = 54）：本体无杀道字段，不给开局道痕，强度在「杀戮成长」+「杀怪噬道」。
 *
 * <p>阶梯（封顶 ×5）：10 杀 ×1.4 / 50 杀 ×1.96 / 100 杀 ×2.6 / 300 杀 ×3.4 / 500 杀 ×4.2 / 1000 杀 ×5.0。
 * 杀怪噬道：击杀本体敌对生物/兽王/兽皇累积 7 路战斗道痕（与「神·吞天噬地」同一通道与防刷规则）。
 * 附带：击杀瞬间回复少量真元并清除 1 个自身负面效果。代价：死亡 −20% 杀戮值 + 长期不杀衰减。
 */
public final class TianfuSlay {
    public static final TagKey<EntityType<?>> SHENGWU = tag("shengwu");
    public static final TagKey<EntityType<?>> RENZU = tag("renzu");
    public static final TagKey<EntityType<?>> TEXIAO = tag("texiao");

    /** 每游戏日最多 +50 杀戮值。 */
    private static final int DAILY_CAP = 50;
    /** 同种实体连续击杀的衰减复位时间（60 秒）。 */
    private static final long REPEAT_RESET = 1200L;
    /** 长期不杀：每 5 分钟衰减一次。 */
    private static final long DECAY_INTERVAL = 6000L;
    /** 杀怪噬道（杀神 / 吞天噬地）：每次击杀给 7 个战斗道痕字段各 +5（合计 +35 总道痕）。 */
    private static final double DEVOUR_PER_FIELD = 5.0;
    /** 杀怪噬道：每日累积上限（以单字段计），等价每天最多 6 次满额击杀。 */
    private static final int DEVOUR_DAILY_CAP = 30;

    private TianfuSlay() {
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("guzhenren", path));
    }

    public static double multiplier(int slay) {
        if (slay >= 1000) {
            return 5.0;
        }
        if (slay >= 500) {
            return 4.2;
        }
        if (slay >= 300) {
            return 3.4;
        }
        if (slay >= 100) {
            return 2.6;
        }
        if (slay >= 50) {
            return 1.96;
        }
        if (slay >= 10) {
            return 1.4;
        }
        return 1.0;
    }

    public static boolean isSlay(Player player) {
        return player.getData(GuzhenrenModVariables.PLAYER_VARIABLES).tizhi == 54.0;
    }

    public static void onDeath(LivingDeathEvent event) {
        // 代价：杀神死亡后杀戮值 −20%
        if (event.getEntity() instanceof ServerPlayer dead && isSlay(dead)) {
            TianfuData.setSlay(dead, (int) Math.floor(TianfuData.slay(dead) * 0.8));
        }

        // 只统计来源实体就是玩家本人（宠物/陷阱/摔死不计数）
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer sp) || event.getEntity() == sp) {
            return;
        }
        boolean slay = isSlay(sp);
        // 杀神自带「杀怪噬道」；神·吞天噬地（甲等购买）也走本通道，同一套「本体敌对生物」过滤与防刷规则。
        // 一律走 TianfuApply.effective：确认前未生效的天赋不该开始累积杀戮值 / 触发噬元。
        var ids = TianfuApply.effective(sp);
        boolean tuntian = slay || ids.contains("shen_tuntian_shidi");
        boolean shiyuan = ids.contains("shiyuan_zhixing");
        if (!slay && !tuntian && !shiyuan) {
            return;
        }

        LivingEntity victim = event.getEntity();
        int weight = baseWeight(victim);
        // 特效实体不计杀神值，但保留通道以触发「噬元之性」
        if (weight == 0 && !(victim instanceof Player) && !victim.getType().is(TEXIAO)) {
            return;
        }

        long now = sp.level().getGameTime();
        long day = sp.level().getDayTime() / 24000L;

        // 规则 3：每日上限，日出重置
        long storedDay = TianfuData.slayDay(sp);
        int daily = storedDay == day ? TianfuData.slayDaily(sp) : 0;
        if (storedDay != day) {
            TianfuData.setSlayProgress(sp, TianfuData.lastKill(sp), day, 0);
            daily = 0;
        }

        int gain = weight;
        if (victim instanceof Player) {
            gain = 10;                    // PVP
        }

        // 负权重（人族 NPC / 商人）：只扣杀神值，不给任何附带奖励
        if (gain < 0) {
            if (slay) {
                TianfuData.addSlay(sp, gain);
            }
            return;
        }

        // 规则 1：同种实体连续击杀 100% → 50% → 25%，60 秒无击杀复位
        String typeKey = typePath(victim.getType());
        String lastType = TianfuData.getType(sp);
        int streak = TianfuData.getStreak(sp);
        long lastKillTick = TianfuData.lastKill(sp);
        if (typeKey.equals(lastType) && now - lastKillTick < REPEAT_RESET) {
            streak++;
        } else {
            streak = 1;
        }
        double repeat = streak <= 1 ? 1.0 : (streak == 2 ? 0.5 : 0.25);
        gain = (int) Math.round(gain * repeat);

        // 规则 2：每日封顶
        if (daily + gain > DAILY_CAP) {
            gain = Math.max(0, DAILY_CAP - daily);
        }

        if (slay && gain > 0) {
            TianfuData.addSlay(sp, gain);
            daily += gain;
        }
        TianfuData.setSlayProgress(sp, now, day, daily);
        TianfuData.setTypeStreak(sp, typeKey, streak);

        // 附带奖励只在真正拿到杀神值时给（避免 0 增益白嫖真元/清负面）
        PlayerVariables v = sp.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (slay && gain > 0) {
            v.zhenyuan = Math.min(v.zuida_zhenyuan, v.zhenyuan + v.zuida_zhenyuan * 0.03);
            clearOneBadEffect(sp);
        }

        // 杀怪噬道（杀神 / 吞天噬地）：击杀本体敌对生物/兽王/兽皇累积道痕（独立每日上限，防刷）
        if (tuntian && weight > 0) {
            long devDay = TianfuData.devourDay(sp);
            int devDaily = devDay == day ? TianfuData.devourDaily(sp) : 0;
            int perField = (int) Math.round(DEVOUR_PER_FIELD * repeat);
            if (devDaily + perField > DEVOUR_DAILY_CAP) {
                perField = Math.max(0, DEVOUR_DAILY_CAP - devDaily);
            }
            if (perField > 0) {
                addDevourDaohen(v, perField);
                TianfuData.setDevourProgress(sp, day, devDaily + perField);
            }
        }

        // 噬元之性：击杀灵体回复真元/念头（同样只认已生效的天赋）
        if (shiyuan && victim.getType().is(TEXIAO)) {
            v.zhenyuan = Math.min(v.zuida_zhenyuan, v.zhenyuan + v.zuida_zhenyuan * 0.05);
            v.niantou = Math.min(v.niantou_rongliang, v.niantou + v.niantou_rongliang * 0.05);
        }
        v.markSyncDirty();
    }

    /** 每 tick 由 {@link TianfuEffects} 调用：长期不杀的缓慢衰减。 */
    public static void onTick(ServerPlayer player) {
        long now = player.level().getGameTime();
        long lastKill = TianfuData.lastKill(player);
        // 用 0 作哨兵：未初始化时先落一次时间戳，否则守卫恒真、衰减永不触发
        long lastDecay = TianfuData.getLong(player, "decayAt", 0L);
        if (lastDecay <= 0L) {
            TianfuData.putLong(player, "decayAt", now);
            return;
        }
        if (now - lastKill < DECAY_INTERVAL || now - lastDecay < DECAY_INTERVAL) {
            return;
        }
        TianfuData.putLong(player, "decayAt", now);
        int cur = TianfuData.slay(player);
        if (cur <= 0) {
            return;
        }
        TianfuData.addSlay(player, -Math.max(1, (int) Math.ceil(cur * 0.02)));
    }

    /**
     * 杀怪噬道（杀神 / 吞天噬地）：给战斗实际读取的 7 路道痕各加 {@code amount}。
     *
     * <p>本体 {@code daohen_zong} 由 KE1 / 冒名实体每 tick 对全部 {@code daohen_*} 字段求和重算，
     * 直接写 {@code daohen_zong} 会被覆盖，因此必须写具体字段。
     */
    private static void addDevourDaohen(PlayerVariables v, double amount) {
        v.dahen_qidao += amount;
        v.dahen_zhoudao += amount;
        v.daohen_jindao += amount;
        v.daohen_lvdao += amount;
        v.daohen_tudao += amount;
        v.daohen_xuedao += amount;
        v.daohen_yudao += amount;
    }

    private static int baseWeight(LivingEntity victim) {
        EntityType<?> type = victim.getType();
        String path = typePath(type);
        if ("hunshou".equals(path)) {
            return 15;                    // 兽皇
        }
        if ("hunshou_3".equals(path) || "hunshou_4".equals(path)) {
            return 6;                     // 千兽王 / 万兽王
        }
        if ("hunshou_1".equals(path) || "hunshou_2".equals(path)) {
            return 3;                     // 普通兽王 / 百兽王
        }
        if (type.is(TEXIAO)) {
            return 0;                     // 特效实体不计
        }
        if (type.is(RENZU)) {
            return -5;                    // NPC / 商人：0 并 −5
        }
        if (type.is(SHENGWU)) {
            return 1;                     // 本体怪
        }
        return 0;                         // 原版动物/怪物不计
    }

    private static String typePath(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key == null ? "" : key.getPath();
    }

    private static void clearOneBadEffect(ServerPlayer player) {
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (!instance.getEffect().value().isBeneficial()) {
                player.removeEffect(instance.getEffect());
                return;
            }
        }
    }
}
