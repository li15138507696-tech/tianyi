package net.guzhenren.tianfu;

import net.guzhenren.init.GuzhenrenModParticleTypes;
import net.guzhenren.init.GuzhenrenModSounds;
import net.guzhenren.procedures.UikongqiaoquedingProcedure;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * 开窍入口与收尾。
 *
 * <ul>
 *   <li><b>唯一开窍开关</b>：希望蛊右键 → {@link #onUseHopeGu}（由
 *       {@code YizhuanrendaoxiwangguItemMixin} 在 {@code YizhuanrendaoxiwangguItem.use()} HEAD 调用）。
 *       本体随机空窍资质（kongqiao 1–10）与本体资质面板已被整体取消；</li>
 *   <li><b>收尾</b>：{@code UikongqiaoquedingProcedure.execute} 的 TAIL 回调
 *       {@link #onCloseOpenQiao}——标记开窍已结算 + 提示 + 未选难度时弹出附属天赋界面。</li>
 * </ul>
 *
 * <p>整个流程中 kongqiao 保持本体初始值 0：本体的档位随机结算（真元上限 / 气运 / 命格 /
 * 十绝体质）与 {@code ZhenyuanliangzengzhangProcedure} 的区间反推全部天然不命中，
 * 玩家基础真元回复仅在购买机缘写入对应档位后才有（附属商店驱动一切）。
 */
public final class TianfuOpening {
    private TianfuOpening() {
    }

    // ------------------------------------------------------------------ 希望蛊开窍（唯一开关）

    /**
     * 右键希望蛊：附属天赋系统的唯一开窍开关。
     *
     * <p>复刻本体的开窍仪式（音效 / kaiqiao 标记 / 消耗 / 粒子 / 成就），但<b>不</b>复刻随机
     * kongqiao 与资质面板；随后保持 kongqiao=0 调本体收尾。
     *
     * @param stack 玩家手持的希望蛊（未开窍时消耗一只）
     */
    public static void onUseHopeGu(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        Level level = player.level();
        if (hasOpenedQiao(player)) {
            return; // 已开窍：无效果、不消耗（与本体原行为一致）
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 1. 仪式音效：本体 guzhenren:guchong_cuidong_4
        level.playSound(null, BlockPos.containing(x, y, z),
                GuzhenrenModSounds.GUCHONG_CUIDONG_4.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);

        // 2. 开窍标记 kaiqiao=1（本体口径）
        setKaiqiao(level, player, 1);

        // 3. 消耗一只希望蛊（本体为无条件 shrink）
        stack.shrink(1);

        // 4. 粒子：FLASH 一次 + XIWANGGULIZI×100（与本体一致）
        level.addParticle(ParticleTypes.FLASH, x, y, z, 0.0D, 1.0D, 0.0D);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(GuzhenrenModParticleTypes.XIWANGGULIZI.get(),
                    x, y, z, 100, 1.0D, 1.0D, 1.0D, 0.05D);
        }

        // 5. 成就：本体 guzhenren:cchengjiukaiqiao
        awardAdvancement(player, "guzhenren:cchengjiukaiqiao");

        // 6. 开窍收尾：kongqiao=0 → 本体各档位随机分支全不命中，仅初始化 jieduan/zhuanshu；
        //    既有 UikongqiaoquedingProcedureMixin 的 TAIL 钩子随即 markApplied + 提示 + 弹附属界面。
        // 用 queding 而不是 guanbi：9.8 没有 guanbi 这个类，收尾就在 queding；
        // 10.x 的 queding 会转调 guanbi。两个版本都能跑。
        UikongqiaoquedingProcedure.execute(level, player);
    }

    /** 是否已以希望蛊开窍（本体 scoreboard {@code kaiqiao} 非 0）。 */
    public static boolean hasOpenedQiao(Player player) {
        return kaiqiaoScore(player.level(), player) != 0;
    }

    /**
     * 供诊断命令读取的原始 {@code kaiqiao} 分数。
     *
     * <p>{@link #hasOpenedQiao} 只回布尔，排查「按 K 没反应」时需要看到<b>具体值</b>
     * （0 = 没开窍 / 1 = 已开窍 / 其它 = 数据异常）。
     */
    public static int kaiqiaoScoreOf(Player player) {
        return kaiqiaoScore(player.level(), player);
    }

    /**
     * 转世：把本体 scoreboard {@code kaiqiao} 归零。
     *
     * <p>本体的转世只重置 {@code kongqiao/tizhi/shiqiao} 等字段，<b>不会</b>重置 {@code kaiqiao}
     * 标记；附属显式归零后，希望蛊重新成为唯一开窍入口（K 键重新被门禁拦截），
     * 玩家必须重新用希望蛊开窍才能开启新一轮。
     */
    public static void resetKaiqiao(Player player) {
        setKaiqiao(player.level(), player, 0);
    }

    // ------------------------------------------------------------------ 开窍收尾

    /**
     * 关闭开窍界面之后（本体收尾逻辑已跑完）：标记本世已结算 + 提示 K 键 + 未选难度时弹附属界面。
     *
     * <p>唯一入口是 Mixin 在 {@code UikongqiaoquedingProcedure.execute} 的 TAIL 回调。
     */
    public static void onCloseOpenQiao(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (TianfuData.isApplied(player)) {
            return; // 本世开窍已结算，防重入
        }
        TianfuData.markApplied(player);

        player.displayClientMessage(Component.literal("§b[天赋] 希望蛊已开窍，按 K 打开天赋界面"), false);

        // 未选开局难度 → 自动弹出天赋界面让玩家先选难度
        if (TianfuData.difficulty(player) == TianfuDifficulty.NOT_CHOSEN) {
            TianfuNet.sendState(player);
        }
    }

    // ------------------------------------------------------------------ 生命周期

    /** 登录：重建属性修正（transient 修正不落盘，需每次登录重建）+ 一行按键提示。 */
    public static void onLogin(ServerPlayer player) {
        TianfuAttributes.sync(player);
        // 进世界提示一次：金底加粗标签 + 白色加粗键名，聊天栏里一眼能看到
        player.displayClientMessage(Component.literal(
                "§6§l[天命] §r§e按 §f§lK §e打开天赋界面"), false);
    }

    /** 死亡重生：把 tianfu 数据显式带过去，避免 PersistentData 在克隆时丢失。 */
    public static void onClone(ServerPlayer newPlayer, Entity oldPlayer) {
        CompoundTag oldData = oldPlayer.getPersistentData();
        if (oldData.contains(TianfuData.ROOT, Tag.TAG_COMPOUND)) {
            newPlayer.getPersistentData().put(TianfuData.ROOT, oldData.getCompound(TianfuData.ROOT).copy());
        }
        TianfuAttributes.sync(newPlayer);
    }

    // ------------------------------------------------------------------ 本体 scoreboard 读写

    private static int kaiqiaoScore(Level level, Entity entity) {
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective("kaiqiao");
        return objective != null
                ? scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entity.getScoreboardName()), objective).get()
                : 0;
    }

    private static void setKaiqiao(Level level, Entity entity, int value) {
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective("kaiqiao");
        if (objective == null) {
            objective = scoreboard.addObjective("kaiqiao", ObjectiveCriteria.DUMMY,
                    Component.literal("kaiqiao"), ObjectiveCriteria.RenderType.INTEGER, true, null);
        }
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entity.getScoreboardName()), objective).set(value);
    }

    private static void awardAdvancement(ServerPlayer player, String id) {
        AdvancementHolder holder = player.server.getAdvancements().get(ResourceLocation.parse(id));
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        for (String criteria : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criteria);
        }
    }
}
