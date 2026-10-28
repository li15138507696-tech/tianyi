// src/main/java/net/guzhenren/tianfu/TianfuApply.java
package net.guzhenren.tianfu;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.guzhenren.tianfu.v2.TianfuSelection;
import net.guzhenren.tianfu.v2.TianfuV2Store;

/**
 * 服务端权威的增量购买：难度门禁 + 点数足额校验 → 原子扣点落库 → 立即生效。
 *
 * <p>点数规则：单一自由点钱包（开局按难度发放），可购全部条目，无数量与槽位限制；
 * 仅要求先选开局难度。
 */
public final class TianfuApply {
    /** Adapter owns both the immediate cost and lifelong constraint for these exact V2 IDs. */
    private static final Set<String> ADAPTER_HARD_COSTS = Set.of(
            "v2_aptitude_shackled", "v2_luck_forfeit", "v2_lifespan_scar");
    private TianfuApply() {
    }

    /**
     * 过滤掉不认识 / 重复的 id，保持客户端顺序。
     *
     * <p>注意：这里用 {@link TianfuTalents#byIdIncludingUnavailable} 而不是
     * {@link TianfuPool#byId} —— 后者只含「当前环境可购买」的条目。若玩家买了
     * 附属联动天赋后又把附属卸载，其 id 会从池子里消失，但 purchased 里仍在、
     * 点数也已经扣了。此时若在这里把它滤掉，就会变成「点数白扣 + 无法撤销」。
     * 所以已购判定必须认得这些条目，只是它们的效果解析不到而已（不崩、不生效）。
     */
    public static Set<String> sanitize(List<String> raw) {
        Set<String> set = new LinkedHashSet<>();
        for (String id : raw) {
            if (TianfuPool.byId(id) != null || TianfuTalents.byIdIncludingUnavailable(id) != null) {
                set.add(id);
            }
        }
        return set;
    }

    /**
     * 「当前真正生效」的天赋集合：未最终确认时返回空集。
     *
     * <p>购买与生效解耦后，凡是要按已购条目算数值的地方（属性修正 / 战斗乘区 / 周期效果）
     * 都必须走这里，否则确认前就能白嫖加成。已确认时等价于 {@link #sanitize}。
     */
    public static Set<String> effective(Player player) {
        if (!TianfuData.isConfirmed(player)) {
            return Set.of();
        }
        return sanitize(TianfuData.purchased(player));
    }

    /** 单条目购买（服务端权威）：只落库，效果等最终确认时统一结算。 */
    /** V2 契约：服务端门禁后调用TianfuEconomy.buy，原子拦截补点超24并保存成交快照，失败返回false。未填充时拒绝执行，不写入玩家状态。 */
    public static boolean buy(ServerPlayer player, String id) {
        if (player == null || id == null || id.isEmpty()) {
            return false;
        }
        if (!TianfuData.isApplied(player) || TianfuData.isConfirmed(player)) {
            return false;
        }
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        net.guzhenren.tianfu.v2.TianfuSelection.Result result =
                net.guzhenren.tianfu.v2.TianfuEconomy.buy(snapshot, id);
        if (!result.ok()) {
            return false;
        }
        if (!net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                new net.guzhenren.tianfu.v2.TianfuSelection.Change(snapshot.revision(), result.snapshot()))) return false;
        TianfuData.markDeferred(player);
        return true;
    }

    /**
     * 撤销已购天赋（服务端权威）。
     *
     * <p>撤销范围：凡体（BODY）+ 词条（ENTRY）。**十绝体/机缘不允许撤销**
     * （原生流程含随机值/Scoreboard 改写，回滚会污染存档）。
     *
     * <p>效果回滚：
     * <ul>
     *   <li>从 {@code purchased} 原子移除 + 退回自由点（正成本条目退回 cost 点；负成本条目扣回 -cost 点）</li>
     *   <li>BODY：tizhi 清零 + Daohen 成对减 1000/100 + Effects.revokeOnce 反向字段回滚 + 回收飞行</li>
     *   <li>ENTRY：Effects.revokeOnce 反向字段回滚</li>
     *   <li>周期效果/战斗乘区/常驻药水：从 purchased 移除后 onTick 自动不再触发，幂等重建自然消失</li>
     * </ul>
     */
    /** V2 契约：按Receipt原价撤销且保持现有体质机缘撤销限制，失败返回false且无副作用。未填充时拒绝执行，不写入玩家状态。 */
    public static boolean revoke(ServerPlayer player, String id) {
        if (player == null || id == null || id.isEmpty()) {
            return false;
        }
        // 已确认/已结算后不可撤销：效果已落到本体字段，逆向回滚会与原生回调打架。
        if (TianfuData.isConfirmed(player) || TianfuData.isSettled(player)) {
            return false;
        }
        // 十绝体/机缘不允许撤销（见上方 javadoc）：原生流程含随机值与 scoreboard 改写。
        net.guzhenren.tianfu.TianfuPool.Spec spec = net.guzhenren.tianfu.TianfuPool.byId(id);
        if (spec != null && (spec.kind() == net.guzhenren.tianfu.TianfuPool.Kind.SIJUE
                || spec.kind() == net.guzhenren.tianfu.TianfuPool.Kind.JIYUAN)) {
            return false;
        }
        net.guzhenren.tianfu.v2.TianfuSelection.Snapshot snapshot =
                net.guzhenren.tianfu.v2.TianfuV2Store.read(player);
        net.guzhenren.tianfu.v2.TianfuSelection.Result result =
                net.guzhenren.tianfu.v2.TianfuEconomy.revoke(snapshot, id);
        if (!result.ok()) {
            return false;
        }
        if (!net.guzhenren.tianfu.v2.TianfuV2Store.commit(player,
                new net.guzhenren.tianfu.v2.TianfuSelection.Change(snapshot.revision(), result.snapshot()))) {
            return false;
        }
        // 未确认前效果不结算，因此无需逆向写本体字段；确认后本方法已被上面的门禁拦住。
        return true;
    }

    /** Explicit confirmation validates every gate before any native effect or lock is written. */
    public static TianfuSelection.Rejection confirmFinal(ServerPlayer player) {
        return settleSelection(player, false);
    }

    /** Compatibility entry for an already-confirmed third-party UI or legacy save. */
    public static void applyAllConfirmed(ServerPlayer player, boolean legacy) {
        if (player == null || !TianfuData.isConfirmed(player)) return;
        TianfuV2Store.migrate(player);
        settleSelection(player, legacy);
    }

    private static TianfuSelection.Rejection settleSelection(ServerPlayer player, boolean legacy) {
        if (player == null || !TianfuOpening.hasOpenedQiao(player)) return TianfuSelection.Rejection.NOT_OPENED;
        if (TianfuData.isSettled(player)) return TianfuData.isConfirmed(player)
                ? TianfuSelection.Rejection.NONE : TianfuSelection.Rejection.LOCKED;
        TianfuSelection.Snapshot snapshot = TianfuV2Store.read(player);
        if (TianfuDifficulty.byIndex(snapshot.difficulty()) == null) return TianfuSelection.Rejection.NO_DIFFICULTY;
        PlayerVariables vars = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        boolean anchor = vars.kongqiao >= 1 && vars.kongqiao <= 10;
        int bodies = 0, sijues = 0;
        for (String id : snapshot.purchased()) {
            var spec = TianfuPool.byId(id);
            if (spec != null) {
                if (spec.kind() == TianfuPool.Kind.BODY) bodies++;
                if (spec.kind() == TianfuPool.Kind.SIJUE) sijues++;
                anchor |= spec.kind() == TianfuPool.Kind.SIJUE || spec.kind() == TianfuPool.Kind.JIYUAN;
            } else if (TianfuTalents.byIdIncludingUnavailable(id) == null) {
                return TianfuSelection.Rejection.UNKNOWN_ID;
            }
        }
        if (!anchor || bodies > 1 || sijues > 1) return TianfuSelection.Rejection.CONFLICT;
        net.guzhenren.tianfu.v2.VersionAdapter adapter =
                new net.guzhenren.tianfu.v2.NeoForge1211Adapter();
        TianfuSelection.Rejection checked = adapter.preflight(player, snapshot);
        if (checked != TianfuSelection.Rejection.NONE) return checked;

        // Native fields, this mod's entire root and player scores are restored on a failed attempt.
        CompoundTag beforeVars = vars.serializeNBT(player.registryAccess());
        CompoundTag beforeTag = player.getPersistentData().contains(TianfuData.ROOT, Tag.TAG_COMPOUND)
                ? player.getPersistentData().getCompound(TianfuData.ROOT).copy() : null;
        var scoreboard = player.getScoreboard();
        var holder = ScoreHolder.forNameOnly(player.getScoreboardName());
        Map<Objective, Integer> beforeScores = new HashMap<>();
        for (Objective objective : scoreboard.getObjectives()) {
            var info = scoreboard.getPlayerScoreInfo(holder, objective);
            if (info != null) beforeScores.put(objective, info.value());
        }
        try {
            applyBaseEffects(player, vars, snapshot, legacy);
            // One-time talents may also change lifespan; validate the actual post-talent balance.
            checked = adapter.preflight(player, snapshot);
            if (checked != TianfuSelection.Rejection.NONE) {
                restoreSettlement(player, vars, beforeVars, beforeTag, beforeScores);
                return checked;
            }
            adapter.settle(player, snapshot);
            adapter.maintain(player, snapshot);
            Set<String> keys = new LinkedHashSet<>(snapshot.originEffectKeys());
            keys.add("settlement_complete");
            TianfuSelection.Snapshot finalState = new TianfuSelection.Snapshot(snapshot.schema(), snapshot.revision() + 1,
                    snapshot.points(), snapshot.difficulty(), true, snapshot.origin(), snapshot.originReceipt(),
                    snapshot.purchased(), snapshot.receipts(), snapshot.negativeGrant(), Set.copyOf(keys));
            if (!TianfuV2Store.commit(player, new TianfuSelection.Change(snapshot.revision(), finalState))) {
                restoreSettlement(player, vars, beforeVars, beforeTag, beforeScores);
                return TianfuSelection.Rejection.STALE_REVISION;
            }
            TianfuData.markSettled(player);
            TianfuData.clearRollPending(player);
            vars.markSyncDirty();
            TianfuAttributes.sync(player);
            return TianfuSelection.Rejection.NONE;
        } catch (RuntimeException | LinkageError failure) {
            restoreSettlement(player, vars, beforeVars, beforeTag, beforeScores);
            TianfuMod.LOGGER.error("[天命] 确认结算失败，已恢复本体字段与天赋账", failure);
            return TianfuSelection.Rejection.UNVERIFIED_EFFECT;
        }
    }

    private static void applyBaseEffects(ServerPlayer player, PlayerVariables vars,
            TianfuSelection.Snapshot snapshot, boolean legacy) {
        Set<String> entries = new LinkedHashSet<>();
        // Apply native physique first, then entry multipliers, origin and finally hard constraints.
        for (String id : snapshot.purchased()) {
            var receipt = snapshot.receipts().get(id);
            boolean alreadyApplied = receipt != null && receipt.legacy()
                    && (legacy || !TianfuData.isDeferred(player));
            var spec = TianfuPool.byId(id);
            if (alreadyApplied) continue;
            // These definitions also contain legacy FIELD_* display primitives. Running both
            // engines would deduct the scar's 400 lifespan twice; settle only in the adapter.
            if (ADAPTER_HARD_COSTS.contains(id)) continue;
            if (spec == null || spec.kind() == TianfuPool.Kind.ENTRY) { entries.add(id); continue; }
            switch (spec.kind()) {
                case BODY -> {
                    vars.tizhi = spec.tizhi();
                    TianfuDaohen.write(vars, id);
                    TianfuEffects.applyOnce(player, vars, Set.of(), spec.tizhi());
                }
                case SIJUE -> TianfuNative.applySijue(player, vars, spec.tizhi());
                case JIYUAN -> { if (!legacy) TianfuNative.applyJiyuan(player, vars, id); }
                default -> { }
            }
        }
        TianfuEffects.applyOnce(player, vars, entries, 0);
    }

    private static void restoreSettlement(ServerPlayer player, PlayerVariables vars, CompoundTag beforeVars,
            CompoundTag beforeTag, Map<Objective, Integer> beforeScores) {
        vars.deserializeNBT(player.registryAccess(), beforeVars);
        vars.markSyncDirty();
        if (beforeTag == null) player.getPersistentData().remove(TianfuData.ROOT);
        else player.getPersistentData().put(TianfuData.ROOT, beforeTag.copy());
        var scoreboard = player.getScoreboard();
        var holder = ScoreHolder.forNameOnly(player.getScoreboardName());
        scoreboard.resetAllPlayerScores(holder);
        beforeScores.forEach((objective, value) -> scoreboard.getOrCreatePlayerScore(holder, objective).set(value));
        try {
            TianfuAttributes.sync(player);
        } catch (RuntimeException | LinkageError failure) {
            TianfuMod.LOGGER.error("[天命] 本体字段与天赋账已恢复，但属性修正需在下次同步时重建", failure);
        }
    }
}
