// src/main/java/net/guzhenren/tianfu/net/TianfuV2StatePayload.java
package net.guzhenren.tianfu.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.guzhenren.tianfu.TianfuMod;
import net.guzhenren.tianfu.v2.TianfuSelection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C 新快照；独立record保证旧7参数State与Screen接口不变。 */
public record TianfuV2StatePayload(TianfuSelection.Snapshot state) implements CustomPacketPayload {
    public static final Type<TianfuV2StatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "v2_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TianfuV2StatePayload> STREAM_CODEC =
            StreamCodec.of(TianfuV2StatePayload::encode, TianfuV2StatePayload::decode);

    /** 集合上限：purchased/receipts 512，originEffectKeys 16。 */
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_EFFECT_KEYS = 16;
    private static final int MAX_ID = 64;
    private static final int MAX_SHORT = 32;

    /**
     * 客户端最近一次快照缓存。
     *
     * <p>刻意放在**公共侧**：本类不引用任何客户端类，专服启动不会加载 Screen。
     * 七页界面从这里取，而不是让载荷去调 Screen.refreshV2。
     */
    private static volatile TianfuSelection.Snapshot latest;
    /** Invalidates work queued by a previous connection or LocalPlayer lifecycle. */
    private static volatile long cacheGeneration;
    private static volatile long receivedStates;

    /** Receipt counter for either state channel; does not depend on client-only classes. */
    public static long receivedStates() { return receivedStates; }

    /** Called on the client main thread for any S2C state, even an unchanged revision. */
    public static void markStateReceived() { receivedStates++; }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 最近一次收到的权威快照；无则 null。 */
    public static TianfuSelection.Snapshot latest() {
        return latest;
    }

    /** 断线/换世界时清缓存，避免把上一存档的账带到下一局。 */
    public static synchronized void clear() {
        cacheGeneration++;
        latest = null;
    }

    private static synchronized void accept(long generation, TianfuSelection.Snapshot incoming) {
        if (generation != cacheGeneration || incoming == null) {
            return;
        }
        TianfuSelection.Snapshot cached = latest;
        if (cached == null || incoming.revision() > cached.revision()) {
            latest = incoming;
        }
    }

    private static void writeReceipt(RegistryFriendlyByteBuf buf, TianfuSelection.Receipt receipt) {
        buf.writeUtf(receipt.id() == null ? "" : receipt.id(), MAX_ID);
        buf.writeInt(receipt.paidCost());
        buf.writeVarInt(receipt.grant());
        buf.writeBoolean(receipt.legacy());
    }

    private static TianfuSelection.Receipt readReceipt(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_ID);
        int paidCost = buf.readInt();
        int grant = buf.readVarInt();
        boolean legacy = buf.readBoolean();
        if (grant < 0) {
            throw new IllegalArgumentException("负补点");
        }
        return new TianfuSelection.Receipt(id, paidCost, grant, legacy);
    }

    /**
     * 编码完整不可变快照，字符串32/64上限、购买与成交账至多512项；不发送本体任意字段。
     * @param buf 注册表感知网络缓冲；@param payload 服务端快照
     * @throws IllegalArgumentException 本地快照不满足上限或账本不变量
     */
    private static void encode(RegistryFriendlyByteBuf buf, TianfuV2StatePayload payload) {
        try {
        TianfuSelection.Snapshot state = payload.state();
        if (state == null) {
            throw new IllegalArgumentException("空快照");
        }
        if (state.points() < 0 || state.negativeGrant() < 0) {
            throw new IllegalArgumentException("负余额或负补点");
        }
        if (state.purchased().size() > MAX_ENTRIES || state.receipts().size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("集合超上限");
        }
        if (state.originEffectKeys().size() > MAX_EFFECT_KEYS) {
            throw new IllegalArgumentException("效果键超上限");
        }
        buf.writeVarInt(state.schema());
        buf.writeLong(state.revision());
        buf.writeVarInt(state.points());
        buf.writeVarInt(state.difficulty());
        buf.writeBoolean(state.confirmed());
        TianfuSelection.Origin origin = state.origin();
        buf.writeUtf(origin == null || origin.regionId() == null ? "" : origin.regionId(), MAX_SHORT);
        buf.writeUtf(origin == null || origin.backgroundId() == null ? "" : origin.backgroundId(), MAX_SHORT);
        TianfuSelection.Receipt originReceipt = state.originReceipt();
        buf.writeBoolean(originReceipt != null);
        if (originReceipt != null) {
            writeReceipt(buf, originReceipt);
        }
        buf.writeVarInt(state.purchased().size());
        for (String id : state.purchased()) {
            buf.writeUtf(id, MAX_ID);
        }
        buf.writeVarInt(state.receipts().size());
        for (TianfuSelection.Receipt receipt : state.receipts().values()) {
            writeReceipt(buf, receipt);
        }
        buf.writeVarInt(state.negativeGrant());
        buf.writeVarInt(state.originEffectKeys().size());
        for (String key : state.originEffectKeys()) {
            buf.writeUtf(key, MAX_ID);
        }
        } catch (Throwable e) {
            TianfuMod.LOGGER.error("天赋 V2 状态编码失败", e);
            throw e;
        }
    }

    /**
     * 严格按encode线序解码，检查长度、重复ID与非负计数，禁止按当前目录重算余额。
     * @param buf 来自服务端的缓冲
     * @return 完整快照载荷；恶意或损坏长度抛IllegalArgumentException
     */
    private static TianfuV2StatePayload decode(RegistryFriendlyByteBuf buf) {
        int schema = buf.readVarInt();
        long revision = buf.readLong();
        int points = buf.readVarInt();
        int difficulty = buf.readVarInt();
        boolean confirmed = buf.readBoolean();
        String region = buf.readUtf(MAX_SHORT);
        String background = buf.readUtf(MAX_SHORT);
        TianfuSelection.Receipt originReceipt = buf.readBoolean() ? readReceipt(buf) : null;
        if (points < 0) {
            throw new IllegalArgumentException("负余额");
        }

        int purchasedSize = buf.readVarInt();
        if (purchasedSize < 0 || purchasedSize > MAX_ENTRIES) {
            throw new IllegalArgumentException("购买集合超上限");
        }
        List<String> purchased = new ArrayList<>(purchasedSize);
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < purchasedSize; i++) {
            String id = buf.readUtf(MAX_ID);
            if (!seen.add(id)) {
                throw new IllegalArgumentException("重复购买ID: " + id);
            }
            purchased.add(id);
        }

        int receiptSize = buf.readVarInt();
        if (receiptSize < 0 || receiptSize > MAX_ENTRIES) {
            throw new IllegalArgumentException("成交账超上限");
        }
        Map<String, TianfuSelection.Receipt> receipts = new LinkedHashMap<>();
        for (int i = 0; i < receiptSize; i++) {
            TianfuSelection.Receipt receipt = readReceipt(buf);
            if (receipts.put(receipt.id(), receipt) != null) {
                throw new IllegalArgumentException("重复成交ID: " + receipt.id());
            }
        }

        int negativeGrant = buf.readVarInt();
        if (negativeGrant < 0) {
            throw new IllegalArgumentException("负补点");
        }
        int keySize = buf.readVarInt();
        if (keySize < 0 || keySize > MAX_EFFECT_KEYS) {
            throw new IllegalArgumentException("效果键超上限");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < keySize; i++) {
            keys.add(buf.readUtf(MAX_ID));
        }

        TianfuSelection.Snapshot snapshot = new TianfuSelection.Snapshot(
                schema, revision, points, difficulty, confirmed,
                new TianfuSelection.Origin(region, background), originReceipt,
                List.copyOf(purchased), Map.copyOf(receipts), negativeGrant, Set.copyOf(keys));
        return new TianfuV2StatePayload(snapshot);
    }

    /**
     * 客户端线程缓存快照；先到的新快照不会被后到的旧 revision 覆盖。
     *
     * <p>本方法不引用客户端类，专服与客户端同一条代码路径。
     * @param payload 只读快照；@param context NeoForge上下文
     */
    public static void handle(TianfuV2StatePayload payload, IPayloadContext context) {
        long generation = cacheGeneration;
        context.enqueueWork(() -> { if (generation == cacheGeneration) { markStateReceived(); accept(generation, payload.state()); } });
    }
}
