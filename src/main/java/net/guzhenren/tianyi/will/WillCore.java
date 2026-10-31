package net.guzhenren.tianyi.will;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 天意的世界决策器 —— 纯逻辑，不碰 Minecraft。
 *
 * <h2>它按什么行动</h2>
 * <ol>
 *   <li><b>维持天地平衡</b>：谁的实力份额越过阈值，就压谁（不看善恶）。</li>
 *   <li><b>制约威胁</b>：威胁最高者优先处理；处理手段先选「借人施劫」，
 *       直接降灾只是其中一种渠道，且要过条件。</li>
 *   <li><b>利用棋子</b>：棋子的价值由「可撬动 × 社会位置 × 资源」算，与善恶无关 ——
 *       魔头可以是好刀，高德者可以是好旗。</li>
 *   <li><b>不确定就先探</b>：情报置信度低于 {@link WillConfig#CONFIDENCE_ACT_FLOOR}
 *       时只派观测，不动手。这是「天意不是全知」在行为层的落点。</li>
 * </ol>
 *
 * <h2>为什么不给玩家看</h2>
 * 本类产出的一切判定都是<b>内部因果记录</b>（{@link #causes()}），只用于测试与
 * 服务端日志；玩家侧只有世界过程本身可感（资源变了、兽群来了、人劫发生了），
 * 没有「好感度」「威胁评分」「奖励到账」这类面板。
 */
public final class WillCore {
    private final Knowledge.Store knowledge;
    private final Destiny.DestinyGu destiny;
    private final Destiny.StarWill star;
    private final Ploy.PloyBook ploys;
    private final Calamity.Book calamities;
    private final Opportunity.Forge opportunities;

    /** 当前对每个主体的姿态（扶持/压制/观测/放任）。 */
    private final Map<String, Intent.Posture> posture = new HashMap<>();
    /**
     * 时间窗口内的压制次数（B04）。
     *
     * <p>以前这是「终身累计」，达到 {@link WillConfig#MAX_SAME_ACTION} 就永不再压制；
     * 现在只在一个可恢复的时间窗口内计数，窗口一到自动清零。
     */
    private final Map<String, Integer> windowCount = new HashMap<>();
    /** 每个目标当前窗口的起点（刻）。 */
    private final Map<String, Long> windowStart = new HashMap<>();
    /** 布局失败冷却到期时刻。 */
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    /** 意志余量（消耗与恢复）。 */
    private double budget = 1.0;
    private long lastBudgetAt = 0L;
    private double starConflict = 0.35;

    /** 内部因果记录：只给测试和日志看，不给玩家看。 */
    private final List<String> causes = new ArrayList<>();

    public WillCore(Knowledge.Store knowledge, String worldId) {
        this.knowledge = knowledge;
        this.destiny = new Destiny.DestinyGu(worldId);
        this.star = new Destiny.StarWill();
        this.ploys = new Ploy.PloyBook();
        this.calamities = new Calamity.Book();
        this.opportunities = new Opportunity.Forge();
    }

    public Knowledge.Store knowledge() {
        return knowledge;
    }

    public Destiny.DestinyGu destiny() {
        return destiny;
    }

    public Destiny.StarWill star() {
        return star;
    }

    public Ploy.PloyBook ploys() {
        return ploys;
    }

    public Calamity.Book calamities() {
        return calamities;
    }

    public Opportunity.Forge opportunities() {
        return opportunities;
    }

    public List<String> causes() {
        return List.copyOf(causes);
    }

    /** 追加一条内部因果记录（供接线层登记世界动作；仍然只进日志与测试）。 */
    public void note(String cause) {
        if (cause != null && !cause.isBlank()) {
            causes.add(cause);
        }
        while (causes.size() > 200) {
            causes.remove(0);
        }
    }

    public double budget() {
        return budget;
    }

    public Intent.Posture postureOf(String id) {
        return posture.getOrDefault(id, Intent.Posture.LAISSEZ_FAIRE);
    }

    /** 星宿与天意的目标冲突程度（0..1），供 {@link Destiny.StarWill#interfere} 使用。 */
    public double starConflict() {
        return starConflict;
    }

    public void starConflict(double v) {
        this.starConflict = Knowledge.clamp(v, 0.0, 1.0);
    }

    // ---------------------------------------------------------------- 预算

    /** 每轮恢复意志余量：恢复速率受宿命阶段影响（宿命毁灭后天道权限下降）。 */
    public void recover(long now) {
        long dt = Math.max(0L, now - lastBudgetAt);
        lastBudgetAt = now;
        if (dt == 0L) {
            return;
        }
        double rate = 0.00035 * destiny.heavenControl();
        budget = Knowledge.clamp(budget + dt * rate, 0.0, 1.0);
        star.recover(dt);
    }

    private boolean spend(double cost) {
        if (budget + 1e-9 < cost) {
            return false;
        }
        budget -= cost;
        return true;
    }

    // ---------------------------------------------------------------- 决策

    /**
     * 跑一轮决策。
     *
     * @param s 世界快照（含可用渠道、意志余量、天道权限）
     * @return 本轮意图（可能为空：天意什么都不做也是正常结果）
     */
    public List<Intent> decide(Assessment.Situation s) {
        // ---- 3.14：强度 0 就是零干预 ----
        // 这一行必须在**产生任何意图之前**：档位 0 不是「概率降低」，
        // 而是天意这一轮彻底不出手（audit_will 会核对这条顺序）。
        if (s.force != null && s.force.isOff()) {
            causes.add("force.off @" + s.now + " 强度 0：天意不出手");
            return List.of();
        }
        WillForce force = s.force == null ? WillForce.gentle() : s.force;
        recover(s.now);
        budget = Math.min(budget, s.willBudget);
        knowledge.purge(s.now);
        ploys.tick(s.now, cooldownUntil);
        calamities.tick(s.now);

        Assessment.Report r = Assessment.assess(s);
        List<Intent> out = new ArrayList<>();

        // ---- 0) 星宿意志先动：它会消耗一部分天道权限，并可能打偏天意的算盘
        boolean starBlocked = star.interfere(this, s.now,
                0.5 + 0.5 * Math.sin(s.now / 1200.0));
        if (starBlocked) {
            causes.add("star.interfere blocked will round @" + s.now
                    + " goal=" + star.currentGoal());
        }

        // ---- 1) 宿命层面的动议：本体完整时按封锁行事
        if (destiny.stage() == Destiny.Stage.BODY_WHOLE && destiny.starAligned()
                && r.imbalanced && r.dominant != null && !starBlocked) {
            out.add(new Intent(Intent.Kind.HOLD_HEAVEN, r.dominant,
                    "宿命完整，天意按封锁行事", Intent.Posture.SUPPRESS, 0.32));
        }

        // ---- 2) 压制：失衡的头号对象，或威胁最高者
        List<String> suppressTargets = new ArrayList<>(r.suppressCandidates);
        if (r.imbalanced && r.dominant != null && !suppressTargets.contains(r.dominant)) {
            suppressTargets.add(0, r.dominant);
        }
        int acts = 0;
        for (String id : suppressTargets) {
            if (acts >= force.maxSuppressPerRound()) {
                break;
            }
            Assessment.Actor a = s.find(id);
            if (a == null || a.hidden) {
                continue;
            }
            // 不确定性闸门：看不到就不动手
            if (a.confidence < WillConfig.CONFIDENCE_ACT_FLOOR) {
                out.add(new Intent(Intent.Kind.OBSERVE, id,
                        "情报置信度不足，先派观测", Intent.Posture.OBSERVE, 0.05));
                continue;
            }
            // 叠加限制：改成「可恢复的时间窗口配额」（B04）。
            // 以前这里是一个永久的 actionCount，达到 2 就终身不再压制 ——
            // 那不符合「持续根据局势奖惩」，也不是「最多同时叠加两次」。
            // 3.14：窗口配额上限随档位变化（档位 1 仍等于 WillConfig.MAX_SAME_ACTION）。
            if (!quotaLeft(id, s.now, force.maxSameAction())) {
                causes.add("suppress." + id + " refused: window quota used ("
                        + windowCount.getOrDefault(id, 0) + "/" + force.maxSameAction() + ")");
                continue;
            }
            Long cool = cooldownUntil.get(id);
            if (cool != null && cool > s.now) {
                continue;
            }
            // 渠道选择：高社会代价 → 借人施劫；低代价 → 直接用世界手段
            Assessment.Channel channel = chooseSuppressChannel(s, a, r);
            if (!s.channels.contains(channel)) {
                channel = s.channels.contains(Assessment.Channel.BEAST)
                        ? Assessment.Channel.BEAST : null;
            }
            if (channel == null) {
                causes.add("suppress." + id + " skipped: no channel");
                continue;
            }
            double cost = channelCost(channel) * force.budgetCostScale();
            if (!spend(cost)) {
                causes.add("suppress." + id + " skipped: budget: " + Knowledge.fmt(budget));
                break;
            }
            // 注意：这里**不再**先记配额 —— 配额由真实执行结果结算
            // （WillServer 在执行成功后调 noteActionExecuted）。B04。
            posture.put(id, Intent.Posture.SUPPRESS);
            out.add(new Intent(Intent.Kind.SUPPRESS, id,
                    "威胁 " + Knowledge.fmt(r.threatOf(id))
                            + " / 社会代价 " + Knowledge.fmt(r.socialCostOf(id))
                            + " → 渠道 " + channel,
                    Intent.Posture.SUPPRESS, cost, channel));
            acts++;
        }

        // ---- 3) 扶持：棋子价值最高者。魔头与高德者一视同仁
        for (String id : r.supportCandidates) {
            if (acts >= force.maxActionPerRound()) {
                break;
            }
            Assessment.Actor a = s.find(id);
            if (a == null || a.hidden) {
                continue;
            }
            if (postureOf(id) == Intent.Posture.SUPPRESS) {
                // 正在压制的不扶持（下一轮评估可以翻转，见 switchPosture）
                continue;
            }
            if (r.threatOf(id) >= force.suppressThreshold()
                    && r.socialCostOf(id) < 0.1) {
                continue;
            }
            double supportCost = 0.18 * force.budgetCostScale();
            if (!spend(supportCost)) {
                break;
            }
            Assessment.Channel ch = s.channels.contains(Assessment.Channel.RESOURCE)
                    ? Assessment.Channel.RESOURCE : Assessment.Channel.HERITAGE;
            if (!s.channels.contains(ch)) {
                ch = s.channels.contains(Assessment.Channel.RUMOR)
                        ? Assessment.Channel.RUMOR : null;
            }
            if (ch == null) {
                continue;
            }
            posture.put(id, Intent.Posture.SUPPORT);
            out.add(new Intent(Intent.Kind.SUPPORT, id,
                    "棋子价值 " + Knowledge.fmt(r.utilityOf(id))
                            + " / 道德 " + Knowledge.fmt(a.morality)
                            + " → 渠道 " + ch,
                    Intent.Posture.SUPPORT, supportCost, ch));
            acts++;
        }

        // ---- 4) 收手：意志耗尽或世界稳定时，天意会退场
        if (budget < 0.12) {
            out.add(new Intent(Intent.Kind.WITHDRAW, "", "意志余量不足，暂时收手",
                    Intent.Posture.LAISSEZ_FAIRE, 0.0));
        }

        // ---- 5) 星宿对天意的反制
        if (starBlocked && !out.isEmpty()) {
            out.add(new Intent(Intent.Kind.OBSERVE, "",
                    "星宿意志的干涉让本轮部分动作落空", Intent.Posture.OBSERVE, 0.0));
        }

        for (Intent i : out) {
            if (i.kind == Intent.Kind.SUPPRESS || i.kind == Intent.Kind.SUPPORT
                    || i.kind == Intent.Kind.HOLD_HEAVEN) {
                causes.add(String.format(Locale.ROOT, "%s.%s @%d cost=%.2f why=%s",
                        i.kind, i.target, s.now, i.cost, i.reason));
            }
        }
        return out;
    }

    /** 压制渠道选择：社会代价高的对象优先「借人施劫」。 */
    private Assessment.Channel chooseSuppressChannel(Assessment.Situation s,
                                                     Assessment.Actor a, Assessment.Report r) {
        WillForce f = s.force == null ? WillForce.gentle() : s.force;
        // ---- 3.14：强度 ≥ 2 时，既有灾劫渠道真的会被选到 ----
        // 灾劫不是新渠道，而是 Assessment.Channel.CALAMITY 原本就存在、
        // 只是在默认档位下几乎不会被选中（历史上还被那个默认关闭的旧压制开关整段关掉）。
        // 这里只对「头号威胁 / 失衡的头号对象」加一条确定性取舍，且同一窗口内结论不变。
        if (f.calamityChance() > 0.0 && s.channels.contains(Assessment.Channel.CALAMITY)) {
            boolean priority = a.id.equals(r.topThreat) || a.id.equals(r.dominant);
            if (priority && WillForce.roll("calamity-urge", a.id, s.now / WillForce.CALAMITY_WINDOW)
                    < f.calamityChance()) {
                return Assessment.Channel.CALAMITY;
            }
        }
        double social = r.socialCostOf(a.id);
        if (social >= 0.15) {
            if (s.channels.contains(Assessment.Channel.RUMOR)) {
                return Assessment.Channel.RUMOR;   // 借人之口：人劫/流言
            }
            if (s.channels.contains(Assessment.Channel.FACTION)) {
                return Assessment.Channel.FACTION; // 借势力相争
            }
        }
        if (s.channels.contains(Assessment.Channel.BEAST)) {
            return Assessment.Channel.BEAST;
        }
        if (s.channels.contains(Assessment.Channel.CALAMITY)) {
            return Assessment.Channel.CALAMITY;
        }
        return Assessment.Channel.RESOURCE;
    }

    private double channelCost(Assessment.Channel c) {
        return switch (c) {
            case RUMOR -> 0.10;
            case FACTION -> 0.16;
            case BEAST -> 0.14;
            case RESOURCE -> 0.12;
            case HERITAGE -> 0.12;
            case CALAMITY -> 0.34;
            case CARRIER -> 0.08;
        };
    }

    // ---------------------------------------------------------------- 姿态翻转

    /**
     * 按新评估翻转姿态：玩家改变局势后，天意能从扶持转压制（或反过来）。
     *
     * @return 是否发生了翻转
     */
    public boolean switchPosture(Assessment.Report r, String id) {
        return switchPosture(r, id, WillForce.gentle());
    }

    /** 3.14：姿态翻转的门槛可随档位下调（档位 1 与旧签名完全等价）。 */
    public boolean switchPosture(Assessment.Report r, String id, WillForce force) {
        WillForce f = force == null ? WillForce.gentle() : force;
        Intent.Posture now = postureOf(id);
        double threat = r.threatOf(id);
        double utility = r.utilityOf(id);
        Intent.Posture next = now;
        if (threat >= f.suppressThreshold() && utility < f.supportThreshold()) {
            next = Intent.Posture.SUPPRESS;
        } else if (utility >= f.supportThreshold() && threat < f.suppressThreshold()) {
            next = Intent.Posture.SUPPORT;
        }
        if (next != now) {
            posture.put(id, next);
            causes.add("posture." + id + " " + now + " -> " + next
                    + " (threat=" + Knowledge.fmt(threat) + " utility=" + Knowledge.fmt(utility) + ")");
            return true;
        }
        return false;
    }

    /** 布局失败后登记冷却，防止在同一目标上无限叠加同类惩罚。 */
    public void onPloyFailed(String ployId, String targetId, long now) {
        ploys.fail(ployId, now);
        cooldownUntil.merge(targetId, now + WillConfig.PLOY_FAIL_COOLDOWN, Math::max);
        causes.add("ploy." + ployId + " failed on " + targetId + " → cooldown until "
                + (now + WillConfig.PLOY_FAIL_COOLDOWN));
    }

    /** 反制造成的推算噪声：用来压低该主体的置信度。 */
    public void observeCounterNoise(Assessment.Actor a) {
        double noise = Counter.totalNoise(a.counters);
        if (noise > 0.0) {
            a.confidence = a.confidence * (1.0 - noise);
        }
    }

    public Map<String, Intent.Posture> postures() {
        return new LinkedHashMap<>(posture);
    }

    public Map<String, Integer> actionCounts() {
        return new LinkedHashMap<>(windowCount);
    }

    /** 各目标的窗口起点（持久化用）。 */
    public Map<String, Long> windowStarts() {
        return new LinkedHashMap<>(windowStart);
    }

    /**
     * 窗口内还剩不剩压制配额（B04）。
     *
     * <p>窗口长度 {@link WillConfig#ACTION_WINDOW}；窗口一过计数自动归零，
     * 所以「压过两次」不会变成终身禁止。
     */
    public boolean quotaLeft(String targetId, long now) {
        return quotaLeft(targetId, now, WillConfig.MAX_SAME_ACTION);
    }

    /** 3.14：配额上限随档位变化（档位 1 仍等于 {@link WillConfig#MAX_SAME_ACTION}）。 */
    public boolean quotaLeft(String targetId, long now, int maxSame) {
        int cap = Math.max(1, maxSame);
        Long start = windowStart.get(targetId);
        if (start == null || now - start >= WillConfig.ACTION_WINDOW) {
            windowStart.put(targetId, now);
            windowCount.put(targetId, 0);
            return true;
        }
        return windowCount.getOrDefault(targetId, 0) < cap;
    }

    /**
     * 结算一次<b>真的执行成功</b>的动作（B04）。
     *
     * <p>只有世界侧确认动作落下去了才扣配额 —— 生成失败、落点不可用、
     * 布局开不起来都不消耗，避免"失败也耗掉机会"。
     */
    public void noteActionExecuted(String targetId, long now) {
        Long start = windowStart.get(targetId);
        if (start == null || now - start >= WillConfig.ACTION_WINDOW) {
            windowStart.put(targetId, now);
            windowCount.put(targetId, 0);
        }
        windowCount.merge(targetId, 1, Integer::sum);
        causes.add("action.executed." + targetId + " window="
                + windowCount.get(targetId) + "/" + WillConfig.MAX_SAME_ACTION);
    }

    /** 当窗口内已用配额。 */
    public int quotaUsed(String targetId) {
        return windowCount.getOrDefault(targetId, 0);
    }

    public Map<String, Long> cooldowns() {
        return new HashMap<>(cooldownUntil);
    }

    /** 从存档恢复决策器状态（重启 / 重连）。 */
    public void importState(double budget, Map<String, Intent.Posture> postures,
                            Map<String, Integer> counts, Map<String, Long> cooldowns,
                            long lastBudgetAt) {
        this.budget = Knowledge.clamp(budget, 0.0, 1.0);
        this.lastBudgetAt = lastBudgetAt;
        posture.clear();
        if (postures != null) {
            posture.putAll(postures);
        }
        windowCount.clear();
        if (counts != null) {
            windowCount.putAll(counts);
        }
        cooldownUntil.clear();
        if (cooldowns != null) {
            cooldownUntil.putAll(cooldowns);
        }
    }

    /** 恢复时间窗口起点（B04：窗口配额必须过重启）。 */
    public void importWindowStarts(Map<String, Long> starts) {
        windowStart.clear();
        if (starts != null) {
            windowStart.putAll(starts);
        }
    }

    public long lastBudgetAt() {
        return lastBudgetAt;
    }

    /** 供状态导出。 */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budget", budget);
        m.put("stage", destiny.stage().name());
        m.put("repairProgress", destiny.repairProgress());
        m.put("heavenControl", destiny.heavenControl());
        m.put("starGoal", star.currentGoal().name());
        m.put("starShare", star.share());
        m.put("ploys", ploys.active().size());
        m.put("calamities", calamities.active().size());
        m.put("opportunities", opportunities.active().size());
        m.put("postures", new LinkedHashMap<>(posture));
        m.put("facts", knowledge.size());
        return m;
    }
}
