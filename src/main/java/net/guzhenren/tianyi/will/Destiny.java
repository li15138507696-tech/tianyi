package net.guzhenren.tianyi.will;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 宿命蛊与星宿意志。
 *
 * <h2>三者不同，不能混成一个「命运开关」</h2>
 * <ul>
 *   <li>{@link DestinyGu} —— 世界<b>唯一</b>的宿命状态机。只有一条正品；阶段只能由
 *       <b>真实完成的事务</b>推进（{@link DestinyGu.Txn}），绝不生成第二个互相冲突的正品。</li>
 *   <li>{@link StarWill} —— 星宿合道之后留下的<b>独立意志</b>。它有自己的目标
 *       （护天庭、护星宿），与自然天意既合作又争夺，而且有资源与局限，
 *       <b>不会无条件永远维护天庭</b>。</li>
 *   <li>自然天意（{@link WillCore}）—— 维持天地平衡的那一层。</li>
 * </ul>
 *
 * <h2>3.13.0：取消「万能封锁系数」</h2>
 * 旧模型把宿命折算成一个总开关（{@code fateLock()}）：宿命一完整就整体封死升仙，
 * 宿命一毁灭就整体解除。那是把「宿命」误当成「天意的电源」。现在：
 * <ul>
 *   <li>阶段只描述<b>本体</b>的状态（四个阶段，见 {@link Stage}）；</li>
 *   <li>能不能做某件事，由 {@link DestinyRules} 按「阶段＋手段＋目标条件＋证据」
 *       给出四态结论（允许/受限/条件不足/未实现），<b>不整体封死普通玩法</b>；</li>
 *   <li>保管/迁移/丢失属于 {@link DestinyCustody} 的账本状态，<b>不混入阶段</b>。</li>
 * </ul>
 *
 * <h2>天意预算与灾劫照旧</h2>
 * {@link DestinyGu#heavenControl()} 按阶段直接给出（0.60–1.00），
 * 天意预算、感知与灾劫发动条件继续工作 —— <b>不会因为宿命重做被一起关掉</b>。
 */
public final class Destiny {
    private Destiny() {
    }

    /**
     * 宿命蛊本体的四个阶段。
     *
     * <p>默认从 {@link #BODY_DAMAGED}（本体受损、星宿合道影响仍在）开始。
     * 旧档由 {@link DestinyMigration} 迁移；<b>丢失载体不是阶段</b>。
     */
    public enum Stage {
        /** 本体受损：宿命仍在，但约束有缺口。 */
        BODY_DAMAGED(0.85, false, "本体受损：宿命仍在，约束有缺口"),
        /** 本体完整：<b>必须由真实完成的修复事务产生</b>，不靠数值阈值。 */
        BODY_WHOLE(1.00, false, "本体完整：只能由完成的修复事务产生"),
        /** 本体被毁，仍有重现可能；<b>不是</b>最终结局。 */
        BODY_DESTROYED_REVIVABLE(0.75, true, "本体被毁，仍有重现可能"),
        /** 拆分并与运结合、分发众生：<b>必须由独立的后续炼制完成事务产生</b>。 */
        SPLIT_AND_DISTRIBUTED(0.60, true, "拆分分发众生：只能由独立的炼制事务产生");

        /** 天意可动用的天道权限份额（喂给 {@link Assessment.Situation#heavenControl}）。 */
        public final double heavenControl;
        /** 宿命对个体的整体约束是否已破缺（供灾劫强度等<b>具体</b>判断，不是总开关）。 */
        public final boolean fateConstraintBroken;
        /** 可读含义（命令/日志用）。 */
        public final String meaning;

        Stage(double heavenControl, boolean fateConstraintBroken, String meaning) {
            this.heavenControl = heavenControl;
            this.fateConstraintBroken = fateConstraintBroken;
            this.meaning = meaning;
        }

        /** 本体是否还在（受损或完整）。 */
        public boolean bodyExists() {
            return this == BODY_DAMAGED || this == BODY_WHOLE;
        }

        /** 本体是否完整。 */
        public boolean isWhole() {
            return this == BODY_WHOLE;
        }

        /** 是否已被拆分分发。 */
        public boolean isDistributed() {
            return this == SPLIT_AND_DISTRIBUTED;
        }
    }

    /**
     * 世界唯一的宿命蛊。整个存档只有一个实例（由 MC 层 SavedData 保证），
     * 构造时带 {@code worldId} 以防被复制成多份。
     *
     * <p>阶段只能由 {@link #completeRepair}/{@link #completeBodyDestruction}/
     * {@link #completeSplitRefinement} 推进，每个都要求<b>非空事务 id</b> 且幂等。
     */
    public static final class DestinyGu {
        /**
         * 阶段迁移事务。摧毁与后续炼制分发是<b>两个独立事务</b>，
         * 各需独立的完成事件与证据；摧毁<b>不会</b>自动得到拆分分发。
         */
        public enum Txn {
            REPAIR, DESTROY_BODY, SPLIT_REFINE;

            /** 该事务能否从给定阶段发起。 */
            public boolean allowedFrom(Stage s) {
                if (s == null) {
                    return false;
                }
                return switch (this) {
                    case REPAIR -> s == Stage.BODY_DAMAGED;
                    case DESTROY_BODY -> s == Stage.BODY_DAMAGED || s == Stage.BODY_WHOLE;
                    case SPLIT_REFINE -> s == Stage.BODY_DESTROYED_REVIVABLE;
                };
            }

            /** 该事务完成后的阶段。 */
            public Stage target() {
                return switch (this) {
                    case REPAIR -> Stage.BODY_WHOLE;
                    case DESTROY_BODY -> Stage.BODY_DESTROYED_REVIVABLE;
                    case SPLIT_REFINE -> Stage.SPLIT_AND_DISTRIBUTED;
                };
            }
        }

        private final String worldId;
        private Stage stage = Stage.BODY_DAMAGED;
        /**
         * 修复任务进度 0..1。<b>仅表示模组修复任务的完成度，不代表宿命强度，不驱动阶段</b>；
         * 阶段只能由完成事务推进。
         */
        private double repairProgress = 0.0;
        /** 星宿是否已合道（默认已合道、影响仍在）。 */
        private boolean starAligned = true;
        /** 上次状态变化的真实原因（供调查与因果记录）。 */
        private String lastCause = "default-body-damaged";
        private long lastChangeAt = 0L;
        /** 最近一次被接受的阶段事务 id；重放同一 id 一律按幂等处理。 */
        private String lastTxnId = "";

        public DestinyGu(String worldId) {
            this.worldId = worldId == null ? UUID.randomUUID().toString() : worldId;
        }

        public String worldId() {
            return worldId;
        }

        public Stage stage() {
            return stage;
        }

        /** 修复任务进度（0..1）。不驱动阶段。 */
        public double repairProgress() {
            return repairProgress;
        }

        public boolean starAligned() {
            return starAligned;
        }

        public String lastCause() {
            return lastCause;
        }

        public long lastChangeAt() {
            return lastChangeAt;
        }

        /** 最近一次被接受的阶段事务 id（空串 = 从未完成过事务）。 */
        public String lastTxnId() {
            return lastTxnId;
        }

        /** 天意可动用的天道权限份额（喂给 {@link Assessment.Situation#heavenControl}）。 */
        public double heavenControl() {
            return stage.heavenControl;
        }

        /**
         * @deprecated 万能封锁系数已取消（3.13.0）。宿命不再是总开关：具体手段由
         *             {@link DestinyRules} 按「阶段＋手段＋目标条件＋证据」四态判定。
         *             此方法只为旧报告键保留，恒返回 {@code 1.0}，<b>不参与任何判定</b>。
         */
        @Deprecated
        public double fateLock() {
            return 1.0;
        }

        /**
         * 记录修复任务进度（只影响展示与后续任务门槛，<b>不推进阶段</b>）。
         */
        public void setRepairProgress(double value, long now, String cause) {
            repairProgress = Knowledge.clamp(value, 0.0, 1.0);
            lastCause = cause == null ? "repair-progress" : cause;
            lastChangeAt = now;
        }

        /**
         * 完成一次修复事务：{@code BODY_DAMAGED → BODY_WHOLE}。
         *
         * @param txnId 真实完成事件的事务 id；空值或重放都不推进
         */
        public Stage completeRepair(String txnId, long now, String cause) {
            return complete(Txn.REPAIR, txnId, now, cause);
        }

        /**
         * 完成一次摧毁本体事务：{@code BODY_DAMAGED|BODY_WHOLE → BODY_DESTROYED_REVIVABLE}。
         * 调用方必须先用 {@link DestinyRules} 核实天外之魔资格与接触条件。
         */
        public Stage completeBodyDestruction(String txnId, long now, String cause) {
            return complete(Txn.DESTROY_BODY, txnId, now, cause);
        }

        /**
         * 完成一次后续炼制分发事务：
         * {@code BODY_DESTROYED_REVIVABLE → SPLIT_AND_DISTRIBUTED}。
         * 摧毁不会自动得到这一阶段。
         */
        public Stage completeSplitRefinement(String txnId, long now, String cause) {
            return complete(Txn.SPLIT_REFINE, txnId, now, cause);
        }

        private Stage complete(Txn txn, String txnId, long now, String cause) {
            if (txnId == null || txnId.isBlank()) {
                return stage; // 没有事务 id 就不算「完成」
            }
            if (txnId.equals(lastTxnId)) {
                return stage; // 幂等：重放同一事务不重复推进
            }
            if (!txn.allowedFrom(stage)) {
                return stage;
            }
            stage = txn.target();
            lastTxnId = txnId;
            lastCause = cause == null ? txn.name().toLowerCase(java.util.Locale.ROOT) : cause;
            lastChangeAt = now;
            return stage;
        }

        /**
         * @deprecated 3.13.0 起不再推进阶段：摧毁本体必须是一次真实完成的
         *             {@link #completeBodyDestruction(String, long, String)} 事务
         *             （需完整天外之魔资格＋接触条件＋证据）。这里只记录证据，阶段不变。
         */
        @Deprecated
        public Stage damage(double amount, long now, String cause) {
            if (cause != null && !cause.isBlank()) {
                lastCause = "damage-evidence:" + cause;
            }
            lastChangeAt = now;
            return stage;
        }

        /**
         * @deprecated 3.13.0 起只推进「修复任务进度」，不推进阶段；
         *             完整必须由 {@link #completeRepair(String, long, String)} 事务产生。
         */
        @Deprecated
        public Stage repair(double amount, long now, String cause) {
            setRepairProgress(repairProgress + Math.max(0.0, amount), now, cause);
            return stage;
        }

        /** 星宿合道解除（星宿意志离开后，天意对天庭的维护义务随之改变）。 */
        public void detachStar(long now, String cause) {
            starAligned = false;
            lastCause = cause == null ? "star-detach" : cause;
            lastChangeAt = now;
        }

        /**
         * 唯一性校验：MC 层读到第二个正品时调用，返回 {@code false} 说明数据被复制坏了。
         */
        public boolean matchesWorld(String otherWorldId) {
            return worldId.equals(otherWorldId);
        }

        /** 从存档恢复（旧签名保留：第二个参数现在表示修复任务进度）。 */
        public void importState(Stage stage, double repairProgress, boolean starAligned,
                                String lastCause, long lastChangeAt) {
            importState(stage, repairProgress, starAligned, lastCause, lastChangeAt, "");
        }

        /** 从存档恢复（含最近事务 id）。 */
        public void importState(Stage stage, double repairProgress, boolean starAligned,
                                String lastCause, long lastChangeAt, String lastTxnId) {
            this.stage = stage == null ? Stage.BODY_DAMAGED : stage;
            this.repairProgress = Knowledge.clamp(repairProgress, 0.0, 1.0);
            this.starAligned = starAligned;
            this.lastCause = lastCause == null ? "" : lastCause;
            this.lastChangeAt = lastChangeAt;
            this.lastTxnId = lastTxnId == null ? "" : lastTxnId;
        }
    }

    /**
     * 星宿意志：独立目标、独立资源、独立局限。
     *
     * <p>它不是天意的皮肤。宿命受损后它想修宿命、护天庭；天意想维持平衡。
     * 两者会抢同一份资源（天道权限），所以有 {@link #contest(WillCore, long)}。
     */
    public static final class StarWill {
        public enum Goal {
            RESTORE_DESTINY, PROTECT_HEAVEN_COURT, RESTRAIN_WILL, HOLD_STARS
        }

        private final EnumMap<Goal, Double> weights = new EnumMap<>(Goal.class);
        /** 星宿意志可动用的份额（与天意争同一池子）。 */
        private double share = 0.30;
        private double exhaustion = 0.0;
        private long lastActAt = 0L;
        private int contestWins = 0;
        private int contestLosses = 0;

        public StarWill() {
            weights.put(Goal.RESTORE_DESTINY, 0.45);
            weights.put(Goal.PROTECT_HEAVEN_COURT, 0.25);
            weights.put(Goal.RESTRAIN_WILL, 0.20);
            weights.put(Goal.HOLD_STARS, 0.10);
        }

        public double share() {
            return share;
        }

        public double exhaustion() {
            return exhaustion;
        }

        public Map<Goal, Double> weights() {
            return new EnumMap<>(weights);
        }

        public Goal currentGoal() {
            Goal best = Goal.RESTORE_DESTINY;
            double bw = -1.0;
            for (Map.Entry<Goal, Double> e : weights.entrySet()) {
                if (e.getValue() > bw) {
                    bw = e.getValue();
                    best = e.getKey();
                }
            }
            return best;
        }

        /** 每刻恢复：星宿不是永动机，用多了会疲惫。 */
        public void recover(long ticks) {
            exhaustion = Knowledge.clamp(exhaustion - ticks * 0.0002, 0.0, 1.0);
            share = Knowledge.clamp(share + ticks * 0.00001, 0.05, 0.45);
        }

        /** 干涉天意的一次尝试。回合由调用方（决策器）负责记账。 */
        public boolean interfere(WillCore will, long now, double jitter) {
            if (exhaustion > 0.8) {
                lastActAt = now;
                return false;
            }
            // 目标冲突越强，干涉越容易成功；但成功率有硬上限，绝不保证
            double conflict = will.starConflict();
            double p = WillConfig.STAR_INTERFERE_BASE * (0.6 + 0.8 * conflict)
                    * (1.0 - exhaustion) * (0.7 + 0.6 * jitter);
            boolean ok = p > 0.5;
            exhaustion = Knowledge.clamp(exhaustion + 0.05, 0.0, 1.0);
            share = Knowledge.clamp(share - 0.01, 0.05, 0.45);
            lastActAt = now;
            if (ok) {
                contestWins++;
            } else {
                contestLosses++;
            }
            return ok;
        }

        /**
         * 天意反过来压制星宿干涉。两者是<b>争夺</b>关系，不是谁注定赢。
         */
        public boolean willCounter(double jitter) {
            double p = WillConfig.WILL_COUNTER_STAR * (1.0 - share) * (0.7 + 0.6 * jitter);
            boolean ok = p > 0.5;
            if (ok) {
                contestLosses++;
                share = Knowledge.clamp(share - 0.02, 0.05, 0.45);
            }
            return ok;
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "星宿意志 目标=%s 份额=%.2f 疲惫=%.2f 胜/负=%d/%d",
                    currentGoal(), share, exhaustion, contestWins, contestLosses);
        }

        public long lastActAt() {
            return lastActAt;
        }

        public int contestWins() {
            return contestWins;
        }

        public int contestLosses() {
            return contestLosses;
        }

        /** 从存档恢复。 */
        public void importState(double share, double exhaustion, int wins, int losses) {
            this.share = Knowledge.clamp(share, 0.05, 0.45);
            this.exhaustion = Knowledge.clamp(exhaustion, 0.0, 1.0);
            this.contestWins = Math.max(0, wins);
            this.contestLosses = Math.max(0, losses);
        }
    }
}
