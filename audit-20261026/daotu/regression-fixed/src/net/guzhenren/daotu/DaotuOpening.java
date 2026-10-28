package net.guzhenren.daotu;

import java.util.Map;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 开窍状态判定与生命周期钩子。
 *
 * <p><b>开窍判定</b>沿用本体口径：scoreboard {@code kaiqiao} 非 0。
 * 本体 {@code YizhuanrendaoxiwangguItem.use} 在开窍仪式末尾把 {@code kaiqiao} 置 1，
 * 因此读它即可，不必自己记标记。
 *
 * <p><b>不自动弹窗</b>：天命附属已在 {@code UikongqiaoguanbiProcedure} 的 TAIL 注入并
 * 自动 {@code setScreen}。若道途也弹，两个界面会互相覆盖。道途只发一条聊天提示。
 */
public final class DaotuOpening {

    /** 本体 scoreboard 的开窍标记名。 */
    public static final String KAIQIAO_SCORE = "kaiqiao";

    private DaotuOpening() {
    }

    /** 是否已用希望蛊开窍（本体口径：scoreboard kaiqiao != 0）。 */
    public static boolean hasOpenedQiao(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        var scoreboard = sp.getServer().getScoreboard();
        var objective = scoreboard.getObjective(KAIQIAO_SCORE);
        if (objective == null) {
            return false;
        }
        return scoreboard.getOrCreatePlayerScore(sp, objective).get() != 0;
    }

    /** 开窍收尾：标记已结算，并发一条提示（不弹窗）。 */
    public static void onCloseOpenQiao(net.minecraft.world.entity.Entity entity) {
        try {
            if (!(entity instanceof ServerPlayer player) || player.level().isClientSide()) {
                return;
            }
            if (!hasOpenedQiao(player)) {
                return; // 尚未真正开窍，不结算
            }
            if (DaotuData.isApplied(player)) {
                return; // 幂等
            }
            DaotuData.markApplied(player);
            player.displayClientMessage(Component.literal(
                    "§b[道途] 仙途初启。按 §eM §b打开道途，看你的成长之路。"), false);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onCloseOpenQiao 失败（已忽略）", t);
        }
    }

    /** 登录：补发一次提示（若已开窍但尚未开始成长），并做旧存档迁移。 */
    public static void onLogin(ServerPlayer player) {
        try {
            // 进世界提示一次：金底加粗标签 + 白色加粗键名，聊天栏里一眼能看到
            player.displayClientMessage(Component.literal(
                    "§6§l[道途] §r§e按 §f§lM §e打开道途界面"), false);
            if (!hasOpenedQiao(player)) {
                return;
            }
            if (!DaotuData.isApplied(player)) {
                DaotuData.markApplied(player);
            }
            // 旧「出身」存档折算（幂等；v2 起不再有 16 路阶位）
            DaotuMigrate.migrateIfNeeded(player);
            // 刷新头衔
            DaotuGrowthApply.refreshTitles(player);
            // 属性按 run/attrs 全量重建（离线期间改过数值表也能自愈）
            DaotuAttributes.sync(player);
            if (DaotuRun.draws(player) == 0) {
                player.displayClientMessage(Component.literal(
                        "§b[道途] 仙途初启。击杀、游历、修炼皆积累历练，"
                                + "攒满即弹出选择。按 §eM §b查看。"), false);
            }
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onLogin 失败（已忽略）", t);
        }
    }

    /** 克隆时只深拷贝道途自己的数据根，再重挂属性修正。 */
    public static void onClone(ServerPlayer player, ServerPlayer original) {
        try {
            // NeoForge 只自动复制 PlayerPersisted，普通根标签须由模组自己迁移。
            var source = original.getPersistentData();
            if (source.contains(DaotuData.ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                player.getPersistentData().put(DaotuData.ROOT,
                        source.getCompound(DaotuData.ROOT).copy());
            }
            DaotuAttributes.sync(player);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onClone 失败（已忽略）", t);
        }
    }

    /**
     * 转世重置。顺序不可调换（见规格书 §7.1）：
     * <ol>
     *   <li>读 {@code run/ledger} 快照 —— 必须在删树之前</li>
     *   <li>按快照扣回道痕（{@code subDaohen}，下限 0）—— 也必须在删树之前，
     *       因为账本就存在 {@code run} 里</li>
     *   <li>{@link DaotuData#wipeAll} 删整个 {@code daotu} 树</li>
     *   <li>重建 {@code run}（必须在 3 之后，否则会被一起删掉）</li>
     *   <li>{@code markApplied} 防止重复「仙途初启」</li>
     *   <li>{@code DaotuAttributes.sync} 清掉全部 modifier</li>
     *   <li>播报</li>
     * </ol>
     */
    public static void onReincarnation(ServerPlayer player) {
        try {
            // 1. 读账本（删树前）
            Map<String, Double> ledger = DaotuRun.ledger(player);
            int oldId = DaotuRun.runId(player);
            int oldDraws = DaotuRun.draws(player);

            // 2. 扣回道痕（删树前，因为账本在 run 里）
            if (!ledger.isEmpty()) {
                PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
                for (Map.Entry<String, Double> e : ledger.entrySet()) {
                    if (!DaotuGrowthApply.subDaohen(v, e.getKey(), e.getValue())) {
                        DaotuMod.LOGGER.warn("转世扣道痕：未知字段 {}", e.getKey());
                    }
                }
                v.markSyncDirty();
            }

            // 3. 删整棵树（run 一起清）—— 但界面偏好与主修流派要跨世保留，
            //    先存下来，wipeAll 之后写回（它们属于「玩家习惯/志向」，
            //    不该跟着转世重置：没有理由让玩家每一世都重选一遍专精）
            boolean keepAutoPrompt = DaotuRun.autoPrompt(player);
            String keepMainDao = DaotuMainDao.of(player);
            DaotuData.wipeAll(player);

            // 4. 重建 run —— 必须在 3 之后
            DaotuRun.beginRun(player, oldId + 1);

            // 4b. 恢复偏好与主修（放在 4 之后：beginRun 不碰根键，但顺序上仍以最后写最稳）
            DaotuRun.setAutoPrompt(player, keepAutoPrompt);
            if (!keepMainDao.isEmpty()) {
                DaotuMainDao.set(player, keepMainDao);
            }

            // 5. 标记已开窍结算
            DaotuData.markApplied(player);

            // 6. 清属性（此时 run/attrs 为空，modifier 全部移除）
            DaotuAttributes.sync(player);

            // 7. 播报
            player.displayClientMessage(Component.literal(
                    "§6[道途] 寿元耗尽·总结算：本局 §e" + oldDraws
                            + " §6次抽选的属性已清空，道痕已扣回。下世重新历练。"), false);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onReincarnation 失败（已忽略）", t);
        }
    }
}
