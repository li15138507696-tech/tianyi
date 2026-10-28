package net.guzhenren.tianfu;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「命运由天」随机天赋生成器。
 *
 * <p>服务端权威：只按「点数 + 随机源」算出一份 id 列表，<b>不碰任何玩家数据</b>——
 * 落库、扣点、锁定都由调用方（{@code TianfuRollPayload} / {@code TianfuRollAcceptPayload}）
 * 负责。这样算法本身是纯函数，审计脚本可以原样复现。
 *
 * <h2>为什么必须「必含一个资质」</h2>
 * 最终确认的门禁要求至少 1 个 SIJUE 或 JIYUAN（见 {@code TianfuConfirmPayload}）。
 * 随机结果若不含资质，接受后会直接卡死、无法确认 —— 所以这里是硬约束，不是偏好。
 *
 * <h2>两层抽取</h2>
 * <ol>
 *   <li><b>第一层</b>：候选 = {十绝体} ∪ {甲, 乙, 丙, 丁}，每个候选占一个槽位、等概率
 *       （点数够时即五等分，各 20%）。买不起的候选先剔除再归一化。</li>
 *   <li><b>第二层</b>：第一层抽中「十绝体」这个类别后，再在 11 个十绝体里抽一次（1/11）；
 *       抽中某个机缘档则直接取该档（每档只有一条）。</li>
 * </ol>
 *
 * <h2>为什么十绝体只在 ≥16 点时出现</h2>
 * 十绝体成本 16，而难度最低档只有 8 点。12 / 8 点档根本买不起，
 * 硬要保证「十绝体 + 机缘」会让低难度档永远无解，所以口径是「十绝体或机缘，二者取一」。
 *
 * <h2>为什么有 64 轮上限</h2>
 * 负成本词条（返还 2 点）会把预算变大，进而又能买下一条 —— 若池子里负词条抽之不尽，
 * 贪心会变成死循环。{@link #MAX_STEPS} 是硬闸。
 */
public final class TianfuRoll {

    /** 每世最多摇几次（含第一次）。服务端记账，重登不重置。 */
    public static final int MAX_ROLLS = 3;

    /** 贪心轮数上限（防负成本词条导致的死循环）。 */
    private static final int MAX_STEPS = 64;

    /** 还有余裕时，额外摇一个凡体的概率。 */
    private static final double BODY_CHANCE = 0.70;

    private TianfuRoll() {
    }

    /**
     * 生成一份随机配置：保证含 1 个资质，并尽量把点数花光。
     *
     * @param points 可用点数（首次摇 = 难度发放值；重摇 = 当前余额）
     * @param rng    随机源（服务端用 {@code level.random} 包装即可）
     * @return 条目 id 列表（顺序即抽取顺序，无重复）
     */
    public static List<String> generate(int points, Random rng) {
        List<String> out = new ArrayList<>();
        int budget = points;
        boolean hasBody = false;

        // ---- ① 第一层抽取：候选 = {十绝体} ∪ {甲, 乙, 丙, 丁}，每个候选一个槽位 ----
        // 买不起的候选直接剔除、剩下的重新归一化：8 / 12 点档自然只剩机缘档，
        // 不会出现「抽到十绝体却买不起」这种需要降级的尴尬。
        List<TianfuPool.Spec> jiyuanPool = affordable(TianfuPool.jiyuans(), budget);
        List<TianfuPool.Spec> sijuePool = affordable(TianfuPool.sijues(), budget);
        boolean sijueSlot = !sijuePool.isEmpty();
        int slots = (sijueSlot ? 1 : 0) + jiyuanPool.size();
        String anchor = null;
        if (slots > 0) {
            int slot = rng.nextInt(slots);
            if (sijueSlot && slot == 0) {
                // ---- ② 第二层：抽中「十绝体」这个类别后，再在 11 个里抽一次 ----
                anchor = pick(sijuePool, rng).id();
                hasBody = true; // 十绝体本身就是体质，后续不再摇凡体
            } else {
                anchor = jiyuanPool.get(sijueSlot ? slot - 1 : slot).id();
            }
        }
        if (anchor != null) {
            out.add(anchor);
            budget -= costOf(anchor);
        }

        // ---- ② 凡体（体质唯一，与十绝体互斥） ----
        if (!hasBody && budget >= TianfuPool.BODY_COST && rng.nextDouble() < BODY_CHANCE) {
            List<TianfuPool.Spec> ok = affordable(TianfuPool.bodies(), budget);
            if (!ok.isEmpty()) {
                TianfuPool.Spec s = pick(ok, rng);
                out.add(s.id());
                budget -= s.cost();
            }
        }

        // ---- ③ 词条贪心：按成本加权，直到没有任何买得起的条目 ----
        List<TianfuPool.Spec> pool = new ArrayList<>(TianfuPool.entries());
        pool.addAll(TianfuPool.malice());
        int steps = 0;
        while (steps++ < MAX_STEPS) {
            List<TianfuPool.Spec> ok = new ArrayList<>();
            for (TianfuPool.Spec s : pool) {
                if (out.contains(s.id())) {
                    continue; // 不重复
                }
                if (s.cost() < 0 || s.cost() <= budget) {
                    ok.add(s); // 负成本永远买得起（会返还点数）
                }
            }
            if (ok.isEmpty()) {
                break;
            }
            TianfuPool.Spec s = weighted(ok, rng);
            out.add(s.id());
            budget -= s.cost();
        }
        return out;
    }

    /** 净成本（可能小于发放点数：无任何可购项时会剩点）。 */
    public static int netCost(List<String> ids) {
        int sum = 0;
        for (String id : ids) {
            sum += costOf(id);
        }
        return sum;
    }

    /** 结果是否含 1 个资质（十绝体或机缘）—— 最终确认的门禁。 */
    public static boolean hasAnchor(List<String> ids) {
        for (String id : ids) {
            TianfuPool.Spec s = TianfuPool.byId(id);
            if (s != null && (s.kind() == TianfuPool.Kind.SIJUE || s.kind() == TianfuPool.Kind.JIYUAN)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 内部

    private static int costOf(String id) {
        TianfuPool.Spec s = TianfuPool.byId(id);
        return s == null ? 0 : s.cost();
    }

    private static List<TianfuPool.Spec> affordable(List<TianfuPool.Spec> all, int budget) {
        List<TianfuPool.Spec> out = new ArrayList<>();
        for (TianfuPool.Spec s : all) {
            if (s.cost() < 0 || s.cost() <= budget) {
                out.add(s);
            }
        }
        return out;
    }

    private static TianfuPool.Spec pick(List<TianfuPool.Spec> ok, Random rng) {
        return ok.get(rng.nextInt(ok.size()));
    }

    /**
     * 按成本加权：越便宜权重越高，避免一上来就把预算砸在「极」上、其余全空。
     * 负成本词条（返还点数）权重固定为 1，保持稀有。
     */
    private static TianfuPool.Spec weighted(List<TianfuPool.Spec> ok, Random rng) {
        int total = 0;
        for (TianfuPool.Spec s : ok) {
            total += weightOf(s.cost());
        }
        int roll = rng.nextInt(total);
        for (TianfuPool.Spec s : ok) {
            roll -= weightOf(s.cost());
            if (roll < 0) {
                return s;
            }
        }
        return ok.get(ok.size() - 1);
    }

    private static int weightOf(int cost) {
        if (cost < 0) {
            return 1;
        }
        return Math.max(1, 18 - cost);
    }
}
