// src/main/java/net/guzhenren/tianfu/v2/TianfuEconomy.java
package net.guzhenren.tianfu.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.guzhenren.tianfu.TianfuPool;
import net.guzhenren.tianfu.TianfuTalents;

/**
 * 无副作用的预算与互斥规则层。
 *
 * <p>本类只做「拟稿」：任何方法都不触碰 PlayerVariables / NBT / 网络，只返回
 * {@link TianfuSelection.Result}。所有拒绝走 {@link TianfuSelection.Rejection}，
 * 不抛业务异常；失败时返回的快照与入参**完全同一个实例**，调用方可用引用相等判断"零写入"。
 */
public final class TianfuEconomy {
    private TianfuEconomy() { }

    // ------------------------------------------------------------------ 目录与可用性

    /**
     * 目录价。旧机缘（{@link TianfuPool.Kind#JIYUAN}）返回 null：历史只读，不得新购。
     * 未知 id 也返回 null，由调用方区分 LOCKED 与 UNKNOWN_ID。
     */
    private static Integer catalogCost(String id) {
        TianfuPool.Spec spec = TianfuPool.byId(id);
        if (spec != null) {
            return spec.kind() == TianfuPool.Kind.JIYUAN ? null : Integer.valueOf(spec.cost());
        }
        TianfuTalents.Talent talent = TianfuTalents.byId(id);
        return talent == null ? null : Integer.valueOf(talent.cost());
    }

    /** 已购但当前不可用（附属未装）的条目可以撤销，但不能新购。 */
    private static boolean availableForPurchase(String id) {
        return TianfuTalents.byId(id) != null || TianfuPool.byId(id) != null;
    }

    private static TianfuSelection.Result fail(TianfuSelection.Snapshot state, TianfuSelection.Rejection reason) {
        return new TianfuSelection.Result(false, reason, state);
    }

    private static TianfuSelection.Result ok(TianfuSelection.Snapshot state, int points, int grant,
            List<String> purchased, Map<String, TianfuSelection.Receipt> receipts,
            TianfuSelection.Origin origin, TianfuSelection.Receipt originReceipt) {
        TianfuSelection.Snapshot next = new TianfuSelection.Snapshot(
                state.schema(),
                state.revision() + 1,
                points,
                state.difficulty(),
                state.confirmed(),
                origin,
                originReceipt,
                List.copyOf(purchased),
                Map.copyOf(receipts),
                grant,
                Set.copyOf(state.originEffectKeys()));
        return new TianfuSelection.Result(true, TianfuSelection.Rejection.NONE, next);
    }

    // ------------------------------------------------------------------ 购买

    /**
     * 单笔购买拟稿：服务端查目录价，检查 24 硬上限、余额、唯一体质及同族层级；旧 JIYUAN 仅历史只读。
     *
     * @param state 当前不可变权威快照
     * @param id    稳定条目 id，未知 id 返回 UNKNOWN_ID
     * @return 通过时携带新快照，失败携带原快照；不抛业务拒绝异常
     */
    public static TianfuSelection.Result buy(TianfuSelection.Snapshot state, String id) {
        if (state == null) {
            return new TianfuSelection.Result(false, TianfuSelection.Rejection.UNKNOWN_ID, null);
        }
        if (id == null || id.isEmpty()) {
            return fail(state, TianfuSelection.Rejection.UNKNOWN_ID);
        }
        if (state.confirmed()) {
            return fail(state, TianfuSelection.Rejection.LOCKED);
        }
        if (state.difficulty() < 0) {
            return fail(state, TianfuSelection.Rejection.NO_DIFFICULTY);
        }
        if (state.purchased().contains(id) || state.receipts().containsKey(id)) {
            return fail(state, TianfuSelection.Rejection.CONFLICT);
        }
        Integer costBoxed = catalogCost(id);
        if (costBoxed == null) {
            // 目录里存在但是旧机缘 → 只读；完全不存在 → 未知 id。
            return fail(state, TianfuPool.byId(id) != null
                    ? TianfuSelection.Rejection.LOCKED
                    : TianfuSelection.Rejection.UNKNOWN_ID);
        }
        if (!availableForPurchase(id)) {
            return fail(state, TianfuSelection.Rejection.UNKNOWN_ID);
        }
        int cost = costBoxed.intValue();

        List<String> purchased = new ArrayList<>(state.purchased());
        Map<String, TianfuSelection.Receipt> receipts = new LinkedHashMap<>(state.receipts());
        int points = state.points();
        int grant = state.negativeGrant();

        // 体质 / 凡体：同 kind 只允许一个。
        TianfuPool.Spec spec = TianfuPool.byId(id);
        if (spec != null && (spec.kind() == TianfuPool.Kind.SIJUE || spec.kind() == TianfuPool.Kind.BODY)) {
            for (String owned : state.purchased()) {
                TianfuPool.Spec other = TianfuPool.byId(owned);
                if (other != null && other.kind() == spec.kind()) {
                    return fail(state, TianfuSelection.Rejection.CONFLICT);
                }
            }
        }

        // 同族层级：重选时按旧成交价退款，再扣新价，原子替换。
        TianfuMindTable.Level level = TianfuMindTable.LEVELS.get(id);
        if (level != null) {
            for (String owned : state.purchased()) {
                TianfuMindTable.Level other = TianfuMindTable.LEVELS.get(owned);
                if (other == null || !other.family().equals(level.family())) {
                    continue;
                }
                TianfuSelection.Receipt old = state.receipts().get(owned);
                if (old == null) {
                    return fail(state, TianfuSelection.Rejection.LEGACY_PRICE_UNKNOWN);
                }
                points += old.paidCost();
                grant -= old.grant();
                purchased.remove(owned);
                receipts.remove(owned);
                break;
            }
        }

        // 补点占用：负成本整条计入，不做部分发放。
        int newGrant = grant + Math.max(0, -cost);
        if (newGrant > TianfuSelection.NEGATIVE_CAP) {
            return fail(state, TianfuSelection.Rejection.NEGATIVE_CAP);
        }
        if (cost > points) {
            return fail(state, TianfuSelection.Rejection.INSUFFICIENT_POINTS);
        }

        points -= cost;
        purchased.add(id);
        receipts.put(id, new TianfuSelection.Receipt(id, cost, Math.max(0, -cost), false));
        return ok(state, points, newGrant, purchased, receipts, state.origin(), state.originReceipt());
    }

    // ------------------------------------------------------------------ 撤销

    /**
     * 按成交价撤销，负成本撤销需余额足以扣回，失败不改状态。
     *
     * @param state 权威快照；@param id 已购条目
     * @return Receipt 原价结算后的拟稿或失败，未知价格不猜价
     */
    public static TianfuSelection.Result revoke(TianfuSelection.Snapshot state, String id) {
        if (state == null) {
            return new TianfuSelection.Result(false, TianfuSelection.Rejection.UNKNOWN_ID, null);
        }
        if (id == null || id.isEmpty()) {
            return fail(state, TianfuSelection.Rejection.UNKNOWN_ID);
        }
        if (state.confirmed()) {
            return fail(state, TianfuSelection.Rejection.LOCKED);
        }
        TianfuSelection.Receipt receipt = state.receipts().get(id);
        if (receipt == null) {
            // 已购但没有成交账：无法证明价格时不猜价，也不清 id。
            return fail(state, TianfuSelection.Rejection.LEGACY_PRICE_UNKNOWN);
        }
        int points = state.points() + receipt.paidCost();
        if (points < 0) {
            return fail(state, TianfuSelection.Rejection.INSUFFICIENT_POINTS);
        }
        int grant = state.negativeGrant() - receipt.grant();
        if (grant < 0) {
            return fail(state, TianfuSelection.Rejection.CONFLICT);
        }
        List<String> purchased = new ArrayList<>(state.purchased());
        purchased.remove(id);
        Map<String, TianfuSelection.Receipt> receipts = new LinkedHashMap<>(state.receipts());
        receipts.remove(id);
        return ok(state, points, grant, purchased, receipts, state.origin(), state.originReceipt());
    }

    // ------------------------------------------------------------------ 出身

    private static TianfuOriginTable.Region findRegion(String regionId) {
        if (regionId == null || regionId.isEmpty()) {
            return null;
        }
        for (TianfuOriginTable.Region region : TianfuOriginTable.REGIONS) {
            if (region.id().equals(regionId)) {
                return region;
            }
        }
        return null;
    }

    private static TianfuOriginTable.Background findBackground(String backgroundId) {
        if (backgroundId == null || backgroundId.isEmpty()) {
            return null;
        }
        for (TianfuOriginTable.Background background : TianfuOriginTable.BACKGROUNDS) {
            if (background.id().equals(backgroundId)) {
                return background;
            }
        }
        return null;
    }

    /**
     * 地域与类型一次替换；旧类型先反向结算，再扣新类型，净额不足拒绝。
     *
     * @param state  权威快照；@param origin 双 ID，空值仅用于老档兼容读取
     * @return 成功拟稿或完整原快照；家族给点计入统一 24 上限
     */
    public static TianfuSelection.Result selectOrigin(TianfuSelection.Snapshot state, TianfuSelection.Origin origin) {
        if (state == null) {
            return new TianfuSelection.Result(false, TianfuSelection.Rejection.UNKNOWN_ID, null);
        }
        if (state.confirmed()) {
            return fail(state, TianfuSelection.Rejection.LOCKED);
        }
        if (state.difficulty() < 0) {
            return fail(state, TianfuSelection.Rejection.NO_DIFFICULTY);
        }
        TianfuSelection.Origin target = origin == null
                ? new TianfuSelection.Origin("", "") : origin;
        String regionId = target.regionId() == null ? "" : target.regionId();
        String backgroundId = target.backgroundId() == null ? "" : target.backgroundId();
        TianfuOriginTable.Region region = findRegion(regionId);
        TianfuOriginTable.Background background = findBackground(backgroundId);
        if (!regionId.isEmpty() && region == null) {
            return fail(state, TianfuSelection.Rejection.UNKNOWN_ID);
        }
        if (!backgroundId.isEmpty() && background == null) {
            return fail(state, TianfuSelection.Rejection.UNKNOWN_ID);
        }

        int points = state.points();
        int grant = state.negativeGrant();
        TianfuSelection.Receipt previous = state.originReceipt();
        if (previous != null) {
            points += previous.paidCost();
            grant -= previous.grant();
        }
        if (grant < 0) {
            return fail(state, TianfuSelection.Rejection.CONFLICT);
        }
        int cost = background == null ? 0 : background.cost();
        int newGrant = grant + Math.max(0, -cost);
        if (newGrant > TianfuSelection.NEGATIVE_CAP) {
            return fail(state, TianfuSelection.Rejection.NEGATIVE_CAP);
        }
        if (cost > points) {
            return fail(state, TianfuSelection.Rejection.INSUFFICIENT_POINTS);
        }
        points -= cost;
        TianfuSelection.Receipt receipt = cost == 0
                ? null
                : new TianfuSelection.Receipt(backgroundId, cost, Math.max(0, -cost), false);
        return ok(state, points, newGrant, state.purchased(), state.receipts(),
                new TianfuSelection.Origin(regionId, backgroundId), receipt);
    }

    // ------------------------------------------------------------------ 随机批次

    /**
     * 随机批次沿用旧协议，重新验证整批每一项，不能只验证净成本。
     *
     * @param state 权威快照；@param ids 服务端待接受集合，不能信任客户端价格
     * @return 任一互斥/限额/余额不合法则整批拒绝，不部分落库
     */
    public static TianfuSelection.Result acceptRoll(TianfuSelection.Snapshot state, List<String> ids) {
        if (state == null) {
            return new TianfuSelection.Result(false, TianfuSelection.Rejection.UNKNOWN_ID, null);
        }
        if (ids == null || ids.isEmpty()) {
            return fail(state, TianfuSelection.Rejection.UNKNOWN_ID);
        }
        Set<String> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) {
            return fail(state, TianfuSelection.Rejection.CONFLICT);
        }
        TianfuSelection.Snapshot working = state;
        for (String id : ids) {
            TianfuSelection.Result step = buy(working, id);
            if (!step.ok()) {
                return fail(state, step.reason());
            }
            working = step.snapshot();
        }
        // 整批只在全部通过后才返回最终快照；中途失败返回原快照，零写入。
        return new TianfuSelection.Result(true, TianfuSelection.Rejection.NONE, working);
    }

    // ------------------------------------------------------------------ 规划

    private static List<String> shuffled(List<String> source, Random rng) {
        List<String> copy = new ArrayList<>(source);
        Collections.shuffle(copy, rng);
        return copy;
    }

    /**
     * 以新点数方案生成可行待选批次，不能调用先抽 5 点机缘的旧 generate 流程。
     *
     * @param state 权威快照，未确认且已选择难度；出身占用预算已包含于余额
     * @param rng   非 null 服务端随机源，不能取客户端种子
     * @return 不可变候选 ID 列表；无可行解返回空列表且不写任何状态
     */
    public static List<String> planRoll(TianfuSelection.Snapshot state, Random rng) {
        if (rng == null) {
            throw new NullPointerException("rng");
        }
        if (state == null || state.confirmed() || state.difficulty() < 0) {
            return List.of();
        }

        List<String> negatives = new ArrayList<>();
        List<String> positives = new ArrayList<>();
        for (TianfuTalents.Talent talent : TianfuTalents.all()) {
            if (!availableForPurchase(talent.id())) {
                continue;
            }
            if (talent.cost() < 0) {
                negatives.add(talent.id());
            } else if (talent.cost() > 0) {
                positives.add(talent.id());
            }
        }
        List<String> sijues = new ArrayList<>();
        for (TianfuPool.Spec spec : TianfuPool.sijues()) {
            sijues.add(spec.id());
        }
        for (TianfuPool.Spec spec : TianfuPool.entries()) {
            if (spec.cost() > 0) {
                positives.add(spec.id());
            }
        }
        // 旧机缘不参与新抽，也不参与补点规划。

        List<String> picked = new ArrayList<>();
        int points = state.points();
        int grant = state.negativeGrant();

        // 1) 先补点：不超过 24 上限，且最多 6 条，补到 12 点即停，避免整批全是负面。
        int negativesPicked = 0;
        for (String id : shuffled(negatives, rng)) {
            if (negativesPicked >= 6 || grant >= 12) {
                break;
            }
            Integer costBoxed = catalogCost(id);
            if (costBoxed == null) {
                continue;
            }
            int cost = costBoxed.intValue();
            int add = Math.max(0, -cost);
            if (grant + add > TianfuSelection.NEGATIVE_CAP) {
                continue;
            }
            picked.add(id);
            points -= cost;
            grant += add;
            negativesPicked++;
        }

        // 2) 大件：可负担的十绝体（唯一体质，最多一个）。
        for (String id : shuffled(sijues, rng)) {
            Integer costBoxed = catalogCost(id);
            if (costBoxed == null || costBoxed.intValue() > points) {
                continue;
            }
            picked.add(id);
            points -= costBoxed.intValue();
            break;
        }

        // 3) 剩余点填普通条目，直到没有可负担项或凑满 10 条。
        for (String id : shuffled(positives, rng)) {
            if (picked.size() >= 10) {
                break;
            }
            Integer costBoxed = catalogCost(id);
            if (costBoxed == null || costBoxed.intValue() <= 0 || costBoxed.intValue() > points) {
                continue;
            }
            picked.add(id);
            points -= costBoxed.intValue();
        }

        if (picked.isEmpty()) {
            return List.of();
        }
        // 4) 整批校验：任何一条不合法就整批作废，返回空列表且不写状态。
        TianfuSelection.Result check = acceptRoll(state, picked);
        return check.ok() ? List.copyOf(picked) : List.of();
    }
}
