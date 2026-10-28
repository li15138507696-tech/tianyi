package net.guzhenren.tianyi.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 见闻录上行动作：收藏 / 标记存疑 / 记入疑局。
 *
 * <p>客户端只发「动作 + 条目 id」，<b>不发内容</b>：服务端会按玩家重新读档、
 * 校验条目确实属于他，再决定改不改。所以改包也无法伪造别人的记录。
 */
public record JournalActionPayload(Action action, String entryId) implements CustomPacketPayload {

    /** 首期允许的操作。REQUEST = 只请求同步，不改任何东西。 */
    public enum Action {
        FAVORITE, DOUBTFUL, TOGGLE_CASE, REQUEST
    }

    /** 通道 id：必须显式命名空间（同 JournalSyncPayload 的注释，createType 会把整串当 path）。 */
    public static final Type<JournalActionPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tianyi",
                    "journal_action"));

    public static final StreamCodec<FriendlyByteBuf, JournalActionPayload> CODEC =
            StreamCodec.of(JournalActionPayload::write, JournalActionPayload::read);

    private static void write(FriendlyByteBuf buf, JournalActionPayload p) {
        buf.writeVarInt(p.action.ordinal());
        buf.writeUtf(p.entryId);
    }

    private static JournalActionPayload read(FriendlyByteBuf buf) {
        int ord = buf.readVarInt();
        String id = buf.readUtf();
        Action a = ord >= 0 && ord < Action.values().length
                ? Action.values()[ord] : Action.FAVORITE;
        return new JournalActionPayload(a, id);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
