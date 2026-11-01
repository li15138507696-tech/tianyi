package net.guzhenren.tianyi.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * 宿命蛊<b>取得路径</b>的纯逻辑：材料表、幂等键、前置判定顺序、缺口文案、note 校验。
 *
 * <h2>为什么这些必须是纯逻辑</h2>
 * <p>「能不能取」是一串判定，不是一次世界写入。判定只要混进世界类型，就只能进游戏才验证得了，
 * 而判定恰恰是最容易被改坏、又最需要单测的部分（{@code _pack/tests/.../DestinySeekTest}）。
 * 因此本类不 import 任何 {@code net.minecraft.*} / {@code net.neoforged.*}，
 * 只吃布尔事实、只吐判定与文案；世界侧由 {@code world/DestinySeekService} 负责取事实并执行。
 *
 * <h2>它刻意不做的事</h2>
 * <ul>
 *   <li>不产生物品、不写背包、不扣材料——那都是世界侧的原子动作。</li>
 *   <li>不按玩家名/寿命/转数改命运，也不提供任何跳过前置的开关。</li>
 *   <li>不决定「谁是这一只」：实体化只能由 {@code DestinyCustodyService.materializeAt} 做，
 *       账本说已有正品就一律拒绝。</li>
 * </ul>
 *
 * <p><b>未实机</b>：真实群系、真实守护兽、真实死亡事件与真实背包扣除都只能在游戏里看见；
 * 这里能证明的只有「判定的顺序、材料数量、幂等键与缺口文案」。
 */
public final class DestinySeek {
    /** 取得只能在守护资源地（{@code tianyi:guarded_cache}）上发生。 */
    public static final String TEMPLATE = "tianyi:guarded_cache";
    /** 解除隔离/取得理由的长度上限。note 必须真的写了东西，不能是空白。 */
    public static final int NOTE_MAX = 200;

    /**
     * 一次取得要付的材料。<b>数量是平衡参数</b>，不是校验参数：
     * 守护资源地一只桶通常装 4–8 份同种蛊材，元石露头一份 3–6 颗，
     * 所以这套价目约等于「清掉两处不同地形的守护资源地 + 两处露天矿」——
     * 想调价只改这里，不要再往判定里塞数字。
     */
    public record Cost(String item, int count, String cn) {
        public Cost {
            if (!FiniteSite.resourceId(item)) throw new IllegalArgumentException("invalid material id");
            if (count < 1 || count > 64) throw new IllegalArgumentException("invalid material count");
            if (cn == null || cn.isBlank()) throw new IllegalArgumentException("missing material name");
        }
    }

    /** 蛊材 ×3 + 蛊材 ×3 + 元石 ×6（三种都要，缺一不可；全部是本体已有的真实物品）。 */
    public static final List<Cost> COSTS = List.of(
            new Cost("guzhenren:gu_caidanxincao", 3, "蛊材·丹心草"),
            new Cost("guzhenren:gucaileimingcao", 3, "蛊材·雷鸣草"),
            new Cost("guzhenren:gucaiyuanshi", 6, "元石"));

    /** 判定结论。顺序即优先级：越前面的越先被报告，玩家一次只看得到最该补的那一条。 */
    public enum Reject {
        NONE,
        NO_PLAYER,
        NO_SITE,
        NOT_GUARDED,
        TERRAIN_NOT_STREAM,
        TERRAIN_GUARDIAN_MISMATCH,
        GUARDIAN_MISSING,
        GUARDIAN_NOT_SLAIN,
        MATERIALS_MISSING,
        CUSTODY_LOCKED,
        CUSTODY_PRESENT,
        ALREADY_CLAIMED
    }

    /**
     * 一次取得尝试的<b>事实快照</b>。全部由世界侧取证后填入，本类只判真假。
     *
     * @param playerPresent          执行者是玩家
     * @param sitePresent            脚下（见证半径内）有已物化的守护资源地
     * @param guarded                该点是 {@link FiniteSite.Kind#GUARDED}
     * @param streamTerrain          该点所在地形是四道流派地形之一（{@link SiteTerrain#hasTerrainGuardian}）
     * @param guardianMatchesTerrain 该点绑定的守护兽正是地形映射指定的那一只（{@link SiteTerrain#GUARDIAN_BY_BIOME}）
     * @param guardianBound          该点账本上绑着守护兽 UUID
     * @param slainByRequester       账本记着「这只守护兽被<b>本人</b>打死」（UUID 匹配的死亡事件）
     * @param materialsComplete      材料足够
     * @param custodyWritable        宿命账本可写（未隔离、存档可改）
     * @param custodyEmpty           账本上没有正品
     * @param alreadyClaimed         同一「点 + 人」的幂等键已经被用过
     */
    public record Facts(boolean playerPresent, boolean sitePresent, boolean guarded,
            boolean streamTerrain, boolean guardianMatchesTerrain, boolean guardianBound,
            boolean slainByRequester, boolean materialsComplete, boolean custodyWritable,
            boolean custodyEmpty, boolean alreadyClaimed) {
    }

    /** 按固定顺序返回<b>第一条</b>不满足的前置；全部满足时返回 {@link Reject#NONE}。 */
    public static Reject firstReject(Facts facts) {
        if (facts == null) return Reject.NO_PLAYER;
        if (!facts.playerPresent()) return Reject.NO_PLAYER;
        if (!facts.sitePresent()) return Reject.NO_SITE;
        if (!facts.guarded()) return Reject.NOT_GUARDED;
        if (!facts.streamTerrain()) return Reject.TERRAIN_NOT_STREAM;
        if (!facts.guardianMatchesTerrain()) return Reject.TERRAIN_GUARDIAN_MISMATCH;
        if (!facts.guardianBound()) return Reject.GUARDIAN_MISSING;
        if (!facts.slainByRequester()) return Reject.GUARDIAN_NOT_SLAIN;
        if (!facts.materialsComplete()) return Reject.MATERIALS_MISSING;
        if (!facts.custodyWritable()) return Reject.CUSTODY_LOCKED;
        if (!facts.custodyEmpty()) return Reject.CUSTODY_PRESENT;
        if (facts.alreadyClaimed()) return Reject.ALREADY_CLAIMED;
        return Reject.NONE;
    }

    /** 失败原因（面向玩家/管理员的明确说法，不含坐标与幕后评分）。 */
    public static String explain(Reject reject) {
        if (reject == null) return "取得被拒绝（原因未知）";
        return switch (reject) {
            case NONE -> "全部前置满足";
            case NO_PLAYER -> "取得宿命蛊必须由在场的玩家执行（实物要落进世界）";
            case NO_SITE -> "附近没有已物化的守护资源地：宿命蛊只在守护资源地取得（需在 24 格内，且容器确实属于该点）";
            case NOT_GUARDED -> "这个资源点不是守护资源地（宿命蛊只在守护资源地取得）";
            case TERRAIN_NOT_STREAM ->
                    "这个守护资源地不在四道流派地形上（雷泽 / 碎金丘陵 / 赤焰烬土 / 万仞千山），宿命蛊不在普通地形取得";
            case TERRAIN_GUARDIAN_MISMATCH -> "这个点上的守护兽与所在地形的映射不符，不予认定";
            case GUARDIAN_MISSING -> "这个点没有绑定守护兽，无法确认「先击败守护兽」";
            case GUARDIAN_NOT_SLAIN -> "还没有击败这个点的守护兽（只认这一只、被你亲手打死的那次死亡事件）";
            case MATERIALS_MISSING -> "材料不足";
            case CUSTODY_LOCKED -> "宿命蛊保管账本当前不可写（隔离中或存档不可改），先处理账本再取";
            case CUSTODY_PRESENT -> "账本上已经有正品：全存档只能有一只，不会再出现第二只";
            case ALREADY_CLAIMED -> "这次取得已经成功过一次（同一地点同一人只发放一次）";
        };
    }

    /**
     * 材料缺口文案：够则返回空串，不够则列出「还差什么、差几个」。
     *
     * @param have 按物品 id 数背包现有数量的取值器（世界侧提供，本类不碰背包）
     */
    public static String shortfall(List<Cost> costs, ToIntFunction<String> have) {
        if (costs == null || have == null) return "";
        StringBuilder text = new StringBuilder();
        for (Cost cost : costs) {
            int mine = Math.max(0, have.applyAsInt(cost.item()));
            if (mine >= cost.count()) continue;
            if (text.length() > 0) text.append('、');
            text.append(cost.cn()).append(" ×").append(cost.count() - mine);
        }
        return text.length() == 0 ? "" : "还差：" + text;
    }

    /** 材料齐不齐（与 {@link #shortfall} 同源，避免「文案说够、判定说不够」这种两份真相）。 */
    public static boolean materialsComplete(List<Cost> costs, ToIntFunction<String> have) {
        if (costs == null || have == null) return false;
        for (Cost cost : costs) {
            if (have.applyAsInt(cost.item()) < cost.count()) return false;
        }
        return true;
    }

    /** 幂等键：同一「点 + 人」只成功一次，重启/重连/重复输入都不再发放。 */
    public static String key(UUID siteId, UUID playerId) {
        if (siteId == null || playerId == null) throw new IllegalArgumentException("missing id");
        return "suminggu|" + siteId + "|" + playerId;
    }

    /**
     * 地形映射一致性：守护兽必须就是 {@link SiteTerrain#GUARDIAN_BY_BIOME} 为这片地形指定的那一只。
     * 没有映射的地形（原版群系）一律不成立——这不是「猜」，是缺少证据就不认。
     */
    public static boolean terrainGuardianMatches(String biomeId, String guardian) {
        if (biomeId == null || biomeId.isBlank() || guardian == null || guardian.isBlank()) return false;
        String mapped = SiteTerrain.GUARDIAN_BY_BIOME.get(biomeId);
        return mapped != null && mapped.equals(guardian);
    }

    /** 该点的守护兽候选池里是否有地形映射指定的那一只（生成期口径，供文档/诊断核对）。 */
    public static boolean terrainGuardianInPool(String biomeId) {
        List<String> pool = pool();
        if (pool.isEmpty()) return false;
        String mapped = biomeId == null ? null : SiteTerrain.GUARDIAN_BY_BIOME.get(biomeId);
        return mapped != null && pool.contains(mapped);
    }

    private static List<String> pool() {
        SiteCatalog.Candidate candidate = SiteCatalog.candidate(TEMPLATE);
        return candidate == null ? List.of() : candidate.guardianCandidates();
    }

    /** 材料清单的可读文案（命令层/文档共用一份，避免两边写岔）。 */
    public static List<String> describeCosts() {
        List<String> lines = new ArrayList<>();
        for (Cost cost : COSTS) {
            lines.add(cost.cn() + " ×" + cost.count() + "（" + cost.item() + "）");
        }
        return lines;
    }

    /** note 校验：去空白后为空即无效；超长截断而不是拒绝（理由仍然留得下）。返回空串表示没写。 */
    public static String note(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.length() <= NOTE_MAX ? trimmed : trimmed.substring(0, NOTE_MAX);
    }
}
