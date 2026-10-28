package net.guzhenren.tianyi.will;

import java.util.HashMap;
import java.util.Map;

/**
 * 炼蛊证据：只有「真的炼成了一次」才算证据。
 *
 * <h2>为什么不能只看进度</h2>
 * 本体把炼蛊进度放在 {@code PlayerVariables.LianGuJinDu}（0~100）。
 * 如果只判断「进度是不是 100」，会踩三个坑：
 * <ol>
 *   <li><b>满值滞留</b>：炼成后进度停在 100，每轮都读一次 → 同一次炼蛊被记成无数次；</li>
 *   <li><b>浮点近似</b>：{@code 99.999} 或 {@code 100.0001} 都容易被 {@code >= 100} 判成完成；</li>
 *   <li><b>没产物</b>：进度到了但没有可识别的产物 ID → 记不出「炼出了什么」，这种"证据"没用。</li>
 * </ol>
 *
 * <h2>正确的判据（三条同时成立）</h2>
 * <ol>
 *   <li>进度<b>恰好等于</b> {@code 100.0}（用精确相等，不用 &gt;=）；</li>
 *   <li>这是一次<b>真的从进度不足走到完成</b>的过程（{@link #observe} 见过它在 100 以下）；</li>
 *   <li>有可识别的产物（完整注册 ID）。</li>
 * </ol>
 *
 * <p>完成一次之后立刻复位，因此「进度停在 100」不会重复产生证据。
 *
 * <p>本类不 import 任何 Minecraft 类型，可离线单测。
 */
public final class AlchemyTracker {
    /** 完成判定的精确满值。不要改成 &gt;= 100。 */
    public static final double COMPLETE = 100.0;

    private AlchemyTracker() {
    }

    /** 观测结果。 */
    public record Result(boolean completed, String productId, int count, String why) {
        static Result none(String why) {
            return new Result(false, "", 0, why);
        }
    }

    /** 单个玩家的炼蛊状态（内存态，重启即重建；证据本身落在知识库里，不会丢）。 */
    public static final class Tracker {
        private boolean seenBelow = false;
        private double lastProgress = 0.0;
        private int completions = 0;

        public int completions() {
            return completions;
        }

        public double lastProgress() {
            return lastProgress;
        }

        /**
         * 观测一次本体炼蛊状态。
         *
         * @param progress  本体 {@code LianGuJinDu}
         * @param guFangId  正在炼的蛊方（可为空）
         * @param productId 产物注册 ID（为空则这次不构成证据）
         * @param count     产物数量
         */
        public Result observe(double progress, String guFangId, String productId, int count) {
            double before = lastProgress;
            lastProgress = progress;

            // ① 还没到满值：记下「这次真的在炼」，但不算完成
            if (progress < COMPLETE) {
                if (progress > 0.0) {
                    seenBelow = true;
                } else {
                    // B05：进度归零 = 这一轮结束（成功或被取消/失败），
                    // 「见过不足」必须一起清掉，否则后续满值滞留会被当成一次新的炼成。
                    seenBelow = false;
                }
                return Result.none("in-progress");
            }

            // ② 超过满值（异常/浮点误差）：不认，且不消耗「看过不足」的标记
            if (progress > COMPLETE) {
                return Result.none("over-complete:" + progress);
            }

            // ③ 恰好 100：必须见过它在 100 以下，否则就是满值滞留
            if (!seenBelow) {
                return Result.none("stale-100");
            }
            // ④ 没有产物 → 不构成证据（记不出"炼出了什么"）
            if (productId == null || productId.indexOf(':') <= 0) {
                return Result.none("no-product");
            }
            // ⑤ 数量必须是正的
            if (count <= 0) {
                return Result.none("zero-count");
            }

            seenBelow = false; // 复位：停在 100 不会再来一次
            completions++;
            String why = String.format("completed(fang=%s, from=%.2f)",
                    guFangId == null || guFangId.isEmpty() ? "-" : guFangId, before);
            return new Result(true, productId.toLowerCase(java.util.Locale.ROOT), count, why);
        }
    }

    /** 玩家 UUID → 观测器（内存态；证据写进知识库后才算长期记录）。 */
    private static final Map<String, Tracker> TRACKERS = new HashMap<>();

    public static Tracker of(String playerId) {
        String key = Knowledge.requireIdentity(playerId);
        return TRACKERS.computeIfAbsent(key, k -> new Tracker());
    }

    public static void forget(String playerId) {
        TRACKERS.remove(playerId);
    }

    /** 供测试与诊断：当前被观测的人数。 */
    public static int tracked() {
        return TRACKERS.size();
    }

    // ---------------------------------------------------------------- 产物推断

    /**
     * 背包差异：只返回<b>数量增加</b>的那些物品。
     *
     * <p>本体不暴露「这次炼出了什么」，所以只能靠炼蛊前后的背包差异去推断。
     * 三条纪律：
     * <ul>
     *   <li>只认 <b>after &gt; before</b> 的键；减少的不算（那是消耗，不是产物）；</li>
     *   <li>差值就是这一轮新增的数量；</li>
     *   <li>键必须是完整注册 ID（含命名空间），裸名字一律忽略。</li>
     * </ul>
     */
    public static Map<String, Integer> diff(Map<String, Integer> before,
                                            Map<String, Integer> after) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (after == null || after.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Integer> e : after.entrySet()) {
            String id = e.getKey();
            if (id == null || id.indexOf(':') <= 0) {
                continue;
            }
            int now = e.getValue() == null ? 0 : e.getValue();
            int was = before == null || before.get(id) == null ? 0 : before.get(id);
            if (now > was) {
                out.put(id, now - was);
            }
        }
        return out;
    }

    /**
     * 从差异里挑出最可能的产物：新增数量最多的那个。
     *
     * <p>并列时按 id 排序取第一个 —— 保证同样的输入永远得到同样的结果（可测试）。
     *
     * @return {@code null} 表示没有可识别产物
     */
    public static Map.Entry<String, Integer> topProduct(Map<String, Integer> candidateDiff) {
        if (candidateDiff == null || candidateDiff.isEmpty()) {
            return null;
        }
        String bestId = null;
        int bestCount = -1;
        for (String id : new java.util.TreeSet<>(candidateDiff.keySet())) {
            int c = candidateDiff.get(id);
            if (c > bestCount) {
                bestCount = c;
                bestId = id;
            }
        }
        return bestId == null ? null : Map.entry(bestId, bestCount);
    }
}
