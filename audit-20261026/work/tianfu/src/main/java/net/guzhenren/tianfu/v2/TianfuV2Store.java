// src/main/java/net/guzhenren/tianfu/v2/TianfuV2Store.java
package net.guzhenren.tianfu.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.guzhenren.tianfu.TianfuData;
import net.guzhenren.tianfu.TianfuPool;
import net.guzhenren.tianfu.TianfuTalents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * NBT 仓储：只使用 {@code tianfu} 根，不建立 SQLite/YAML 第二份账。
 *
 * <p>写入一律「先构好完整 tag，再一次性 {@code put}」，因此提交要么全成要么全不成。
 * 老档缺失新字段时按「无出身、无成交账」读取，绝不默认北原/家族，也不扣补点数。
 */
public final class TianfuV2Store {
    public static final String SCHEMA = "v2_schema";
    public static final String ORIGIN_REGION = "v2_origin_region";
    public static final String ORIGIN_BACKGROUND = "v2_origin_background";
    public static final String RECEIPTS = "v2_receipts";
    public static final String ORIGIN_RECEIPT = "v2_origin_receipt";
    public static final String REVISION = "v2_revision";
    public static final String ORIGIN_EFFECT_KEYS = "v2_origin_effect_keys";

    /** 旧根里既有的键名，本类只读不改语义。 */
    private static final String KEY_POINTS = "points";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_CONFIRMED = "confirmed";
    private static final String KEY_PURCHASED = "purchased";

    private TianfuV2Store() { }

    // ------------------------------------------------------------------ 读取

    /** 取 tianfu 根；缺失返回 null，不创建。 */
    private static CompoundTag root(Player player) {
        CompoundTag pd = player.getPersistentData();
        return pd.contains(TianfuData.ROOT, Tag.TAG_COMPOUND) ? pd.getCompound(TianfuData.ROOT) : null;
    }

    private static TianfuSelection.Receipt readReceipt(CompoundTag tag) {
        return new TianfuSelection.Receipt(
                tag.getString("id"),
                tag.getInt("paidCost"),
                tag.getInt("grant"),
                tag.getBoolean("legacy"));
    }

    private static void writeReceipt(CompoundTag parent, String key, TianfuSelection.Receipt receipt) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", receipt.id() == null ? key : receipt.id());
        tag.putInt("paidCost", receipt.paidCost());
        tag.putInt("grant", receipt.grant());
        tag.putBoolean("legacy", receipt.legacy());
        parent.put(key, tag);
    }

    private static List<String> readPurchased(CompoundTag tag) {
        List<String> out = new ArrayList<>();
        ListTag list = tag.getList(KEY_PURCHASED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    private static Map<String, TianfuSelection.Receipt> readReceipts(CompoundTag tag) {
        Map<String, TianfuSelection.Receipt> out = new LinkedHashMap<>();
        if (!tag.contains(RECEIPTS, Tag.TAG_COMPOUND)) {
            return out;
        }
        CompoundTag receipts = tag.getCompound(RECEIPTS);
        for (String key : receipts.getAllKeys()) {
            if (receipts.contains(key, Tag.TAG_COMPOUND)) {
                out.put(key, readReceipt(receipts.getCompound(key)));
            }
        }
        return out;
    }

    private static Set<String> readEffectKeys(CompoundTag tag) {
        Set<String> out = new LinkedHashSet<>();
        ListTag list = tag.getList(ORIGIN_EFFECT_KEYS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    /** 补点占用由成交账推导，不依赖客户端上传或单独计数器。 */
    private static int deriveGrant(Map<String, TianfuSelection.Receipt> receipts,
            TianfuSelection.Receipt originReceipt) {
        int grant = 0;
        for (TianfuSelection.Receipt receipt : receipts.values()) {
            grant += Math.max(0, receipt.grant());
        }
        if (originReceipt != null) {
            grant += Math.max(0, originReceipt.grant());
        }
        return grant;
    }

    /**
     * 只读；缺失出身返回两个空字符串，不创建任何 NBT，不扣补点。
     *
     * @param player 非 null 玩家；仅在逻辑服务端读取权威账
     * @return 不可变完整状态，含原 purchased 中未知/不可用 ID
     * @throws NullPointerException player 为 null
     */
    public static TianfuSelection.Snapshot read(Player player) {
        Objects.requireNonNull(player, "player");
        CompoundTag tag = root(player);
        if (tag == null) {
            return new TianfuSelection.Snapshot(0, 0L, 0, net.guzhenren.tianfu.TianfuDifficulty.NOT_CHOSEN, false,
                    new TianfuSelection.Origin("", ""), null,
                    List.of(), Map.of(), 0, Set.of());
        }
        int schema = tag.getInt(SCHEMA);
        long revision = tag.getLong(REVISION);
        int points = tag.getInt(KEY_POINTS);
        int difficulty = tag.contains(KEY_DIFFICULTY) ? tag.getInt(KEY_DIFFICULTY) : -1;
        boolean confirmed = tag.getBoolean(KEY_CONFIRMED);
        String region = tag.getString(ORIGIN_REGION);
        String background = tag.getString(ORIGIN_BACKGROUND);
        TianfuSelection.Receipt originReceipt = null;
        if (tag.contains(ORIGIN_RECEIPT, Tag.TAG_COMPOUND)) {
            CompoundTag receiptTag = tag.getCompound(ORIGIN_RECEIPT);
            // 1.3.4 wrapped this receipt once more under "origin". Preserve its paid price.
            if (receiptTag.contains("origin", Tag.TAG_COMPOUND)) {
                receiptTag = receiptTag.getCompound("origin");
            }
            originReceipt = readReceipt(receiptTag);
        }
        Map<String, TianfuSelection.Receipt> receipts = readReceipts(tag);
        return new TianfuSelection.Snapshot(
                schema,
                revision,
                points,
                difficulty,
                confirmed,
                new TianfuSelection.Origin(region == null ? "" : region, background == null ? "" : background),
                originReceipt,
                List.copyOf(readPurchased(tag)),
                Map.copyOf(receipts),
                deriveGrant(receipts, originReceipt),
                Set.copyOf(readEffectKeys(tag)));
    }

    // ------------------------------------------------------------------ 迁移

    /** 旧价：先查冻结表，再查当前目录；都查不到返回 null，不猜价。 */
    private static Integer legacyCost(String id) {
        Integer frozen = TianfuLegacyPrices.BY_ID.get(id);
        if (frozen != null) {
            return frozen;
        }
        TianfuPool.Spec spec = TianfuPool.byId(id);
        if (spec != null) {
            return Integer.valueOf(spec.cost());
        }
        TianfuTalents.Talent talent = TianfuTalents.byId(id);
        return talent == null ? null : Integer.valueOf(talent.cost());
    }

    /**
     * 老档只建立历史成本账；以旧余额为基线，不按新基础点重算。
     *
     * @param player 非 null 服务端玩家；必须在服务端线程调用
     * @return 迁移后不可变快照；未知旧价格拒绝交易但保留 ID 与余额
     * @throws IllegalStateException 数据损坏导致无法安全建账；不得部分写入
     */
    public static TianfuSelection.Snapshot migrate(Player player) {
        Objects.requireNonNull(player, "player");
        TianfuSelection.Snapshot current = read(player);
        if (current.schema() >= TianfuSelection.SCHEMA) {
            return current;
        }
        CompoundTag pd = player.getPersistentData();
        CompoundTag source = root(player);
        CompoundTag tag = source == null ? new CompoundTag() : source.copy();

        Map<String, TianfuSelection.Receipt> receipts = new LinkedHashMap<>(current.receipts());
        for (String id : current.purchased()) {
            if (receipts.containsKey(id)) {
                continue;
            }
            Integer cost = legacyCost(id);
            if (cost == null) {
                // 未知旧价：保留 id 与余额，不建账；该条目的撤销会被拒绝而不是猜价。
                continue;
            }
            receipts.put(id, new TianfuSelection.Receipt(
                    id, cost.intValue(), Math.max(0, -cost.intValue()), true));
        }

        CompoundTag receiptTag = new CompoundTag();
        for (Map.Entry<String, TianfuSelection.Receipt> entry : receipts.entrySet()) {
            writeReceipt(receiptTag, entry.getKey(), entry.getValue());
        }
        tag.put(RECEIPTS, receiptTag);
        tag.putInt(SCHEMA, TianfuSelection.SCHEMA);
        if (!tag.contains(REVISION)) {
            tag.putLong(REVISION, 0L);
        }
        if (!tag.contains(ORIGIN_REGION, Tag.TAG_STRING)) {
            tag.putString(ORIGIN_REGION, "");
        }
        if (!tag.contains(ORIGIN_BACKGROUND, Tag.TAG_STRING)) {
            tag.putString(ORIGIN_BACKGROUND, "");
        }
        pd.put(TianfuData.ROOT, tag);
        return read(player);
    }

    // ------------------------------------------------------------------ 提交

    private static void writePurchased(CompoundTag tag, List<String> purchased) {
        ListTag list = new ListTag();
        for (String id : purchased) {
            list.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        tag.put(KEY_PURCHASED, list);
    }

    /**
     * 原子比较 revision 后提交 points/purchased/receipts/origin；失败不写。
     *
     * @param player 非 null 服务端玩家
     * @param change 预期版本及完整新快照，不允许客户端提供成交价
     * @return 版本匹配并完整提交才为 true；过期为 false
     * @throws IllegalArgumentException 负余额或成交账不一致
     */
    public static boolean commit(Player player, TianfuSelection.Change change) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(change, "change");
        TianfuSelection.Snapshot current = read(player);
        if (current.revision() != change.expectedRevision()) {
            return false;
        }
        TianfuSelection.Snapshot next = change.next();
        if (next.points() < 0) {
            throw new IllegalArgumentException("负余额不允许落库: " + next.points());
        }
        int derived = deriveGrant(next.receipts(), next.originReceipt());
        if (derived != next.negativeGrant()) {
            throw new IllegalArgumentException(
                    "补点账不一致: snapshot=" + next.negativeGrant() + " derived=" + derived);
        }

        CompoundTag pd = player.getPersistentData();
        CompoundTag source = root(player);
        CompoundTag tag = source == null ? new CompoundTag() : source.copy();

        tag.putInt(KEY_POINTS, next.points());
        tag.putInt(KEY_DIFFICULTY, next.difficulty());
        tag.putBoolean(KEY_CONFIRMED, next.confirmed());
        tag.putInt(SCHEMA, TianfuSelection.SCHEMA);
        tag.putLong(REVISION, next.revision());
        writePurchased(tag, next.purchased());

        CompoundTag receiptTag = new CompoundTag();
        for (Map.Entry<String, TianfuSelection.Receipt> entry : next.receipts().entrySet()) {
            writeReceipt(receiptTag, entry.getKey(), entry.getValue());
        }
        tag.put(RECEIPTS, receiptTag);

        TianfuSelection.Origin origin = next.origin();
        tag.putString(ORIGIN_REGION, origin == null || origin.regionId() == null ? "" : origin.regionId());
        tag.putString(ORIGIN_BACKGROUND,
                origin == null || origin.backgroundId() == null ? "" : origin.backgroundId());
        if (next.originReceipt() == null) {
            tag.remove(ORIGIN_RECEIPT);
        } else {
            writeReceipt(tag, ORIGIN_RECEIPT, next.originReceipt());
        }

        ListTag keys = new ListTag();
        for (String key : next.originEffectKeys()) {
            keys.add(net.minecraft.nbt.StringTag.valueOf(key));
        }
        tag.put(ORIGIN_EFFECT_KEYS, keys);

        pd.put(TianfuData.ROOT, tag);
        return true;
    }
}
