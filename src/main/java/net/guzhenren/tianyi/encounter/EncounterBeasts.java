package net.guzhenren.tianyi.encounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

import net.guzhenren.tianyi.will.BeastCatalog;

/**
 * 奇遇可用的蛊兽名单：<b>全部来自 {@link BeastStats}</b>（本体字节码抽取的原生属性），
 * 而 {@link BeastStats} 的成员又全部来自 {@link BeastCatalog}（知识库 6.2 名录，
 * 已逐条对过本体 jar）。
 *
 * <h2>3.10.1 的变化</h2>
 * 以前这里只有手写的 6 只（灰狼/土狼/熊/红熊/灰熊/电狼）。现在：
 * <ul>
 *   <li><b>名单 = 名录</b>：85 只蛊兽全部可遇，不再是 6 只；</li>
 *   <li><b>属性 = 抽取值</b>：生命/护甲/攻击来自实体类
 *       {@code createAttributes()} 的字节码常量，不是估的；</li>
 *   <li><b>门槛 = 阶与血取高</b>：见 {@link #minRankOf}。</li>
 * </ul>
 *
 * <h2>门槛为什么不能只看阶</h2>
 * 本体自己的数值并不统一：同为特殊种，电狼原生 60 血，而豹、彪、赤狐都是 1000 血。
 * 只按阶定门槛会让新手在 0 级就撞上 1000 血的豹。所以取
 * {@code max(阶对应的门槛, 血量对应的门槛)} —— <b>血多的兽一律更晚出现</b>。
 */
public final class EncounterBeasts {
    /** 一只可遇蛊兽。属性是 {@code createAttributes()} 的原生值。 */
    public record Beast(String id, String name, int minRank, double health, double armor,
                        double attack, String warning) {}

    /** 新手默认遭遇（旧行为保持不变）。 */
    public static final String DEFAULT_ID = "guzhenren:hui_lang";

    private static final List<Beast> ALL = build();

    private EncounterBeasts() {
    }

    /** 全部可遇蛊兽（不可变）。 */
    public static List<Beast> all() {
        return ALL;
    }

    /** 阶 → 基础门槛：1 阶 0、2 阶 1、3 阶 2、4 阶 3、5 阶 4。 */
    public static int tierRank(int tier) {
        return switch (tier) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 4;
            default -> 0;
        };
    }

    /** 血量 → 门槛（本体数值不统一，所以必须单独看血）。 */
    public static int healthRank(double hp) {
        if (hp < 100.0) {
            return 0;
        }
        if (hp < 300.0) {
            return 1;
        }
        if (hp < 800.0) {
            return 2;
        }
        if (hp < 2000.0) {
            return 3;
        }
        return 4;
    }

    /** 实际门槛：阶与血取高。 */
    public static int minRankOf(int tier, double hp) {
        return Math.max(tierRank(tier), healthRank(hp));
    }

    private static List<Beast> build() {
        List<Beast> out = new ArrayList<>();
        for (BeastStats.Row r : BeastStats.all()) {
            out.add(new Beast(r.id(), r.name(), minRankOf(r.tier(), r.hp()),
                    r.hp(), r.armor(), r.atk(), warn(r)));
        }
        // 默认兽排最前，其余按门槛、再按 id，保证顺序稳定
        out.sort(Comparator
                .comparing((Beast b) -> !b.id().equals(DEFAULT_ID))
                .thenComparingInt(Beast::minRank)
                .thenComparing(Beast::id));
        return List.copyOf(out);
    }

    /** 生成给玩家看的说明（含真实数值，不吹不藏）。 */
    private static String warn(BeastStats.Row r) {
        String base = daoName(r.dao()) + "·" + BeastCatalog.tierName(r.tier())
                + "。原生生命 " + fmt(r.hp()) + "、护甲 " + fmt(r.armor())
                + "、攻击 " + fmt(r.atk()) + "。";
        String tail;
        if (r.tier() >= 5) {
            tail = "兽皇级：单挑等同自杀，先备好杀招与退路。";
        } else if (r.tier() == 4) {
            tail = "万兽级：护甲与血量都厚，带破甲手段再来。";
        } else if (r.hp() >= 800.0) {
            tail = "血量远超同级，别按体型判断强弱。";
        } else if (r.tier() <= 1) {
            tail = "野外围兽，成群出现时也不好对付。";
        } else {
            tail = "会使用本流派蛊虫，注意其状态效果。";
        }
        return base + tail + "（数值为初始属性，未含词条与状态效果。）";
    }

    private static String daoName(String dao) {
        try {
            return BeastCatalog.Dao.valueOf(dao).cn;
        } catch (IllegalArgumentException e) {
            return "无";
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /**
     * 选一只。
     *
     * <p>未知/新手门槛（{@code rank <= 0}）恒为默认兽；
     * 其余在「门槛 ≤ rank」的集合里按种子确定性地选。
     */
    public static Beast select(int rank, long seed) {
        if (rank <= 0) {
            return ALL.getFirst();
        }
        List<Beast> eligible = ALL.stream().filter(b -> b.minRank() <= rank).toList();
        if (eligible.isEmpty()) {
            return ALL.getFirst();
        }
        return eligible.get(new SplittableRandom(seed).nextInt(eligible.size()));
    }

    /** 按 id 查（找不到返回 {@code null}）。 */
    public static Beast byId(String id) {
        for (Beast b : ALL) {
            if (b.id().equals(id)) {
                return b;
            }
        }
        return null;
    }

    /** 某一阶（名录阶）的全部可遇兽，保持名录顺序。 */
    public static List<Beast> byTier(int tier) {
        List<Beast> out = new ArrayList<>();
        for (BeastStats.Row r : BeastStats.all()) {
            if (r.tier() == tier) {
                Beast b = byId(r.id());
                if (b != null) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    /** 摘要（日志/诊断用）。 */
    public static String summary() {
        return "奇遇蛊兽池{" + ALL.size() + " 只，来源=BeastStats/BeastCatalog}";
    }
}
