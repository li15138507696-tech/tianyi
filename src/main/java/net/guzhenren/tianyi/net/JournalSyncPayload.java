package net.guzhenren.tianyi.net;

import java.util.ArrayList;
import java.util.List;

import net.guzhenren.tianyi.journal.PlayerJournal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 见闻录下行：<b>只发这个玩家自己有依据知道的条目</b>。
 *
 * <p>服务端在 {@code JournalStore} 里按玩家读档、按玩家筛选，然后才构造这个包。
 * 客户端拿到的就是全部内容 —— 不存在"发过去再隐藏"。
 *
 * <p>条数上限 {@link #MAX_SENT}：面板只需要最近的一屏，没必要把整份档案都推过去。
 */
public record JournalSyncPayload(boolean titleUnlocked, long now, List<Row> rows, String title)
        implements CustomPacketPayload {

    /** 单次最多下发多少条。 */
    public static final int MAX_SENT = 60;

    /**
     * 通道 id：<b>必须用 fromNamespaceAndPath 显式给命名空间</b>。
     *
     * <p>踩过的坑（3.8.0 实机崩在 RegisterPayloadHandlersEvent）：
     * {@code CustomPacketPayload.createType("tianyi:journal")} 内部走的是
     * {@code ResourceLocation.withDefaultNamespace(path)} —— 它把整串当 <b>path</b>、
     * 命名空间写死成 minecraft，于是得到非法 id {@code minecraft:tianyi:journal} 并抛
     * {@code ResourceLocationException}，整包在加载期就失败。
     */
    public static final Type<JournalSyncPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tianyi", "journal"));

    /** 一行的全部可显示字段（纯数据，客户端不解引用任何世界对象）。 */
    public record Row(String id, int kind, int source, String title, String detail,
                      String dim, long pos, long observedAt, long verifiedAt,
                      boolean favorite, boolean doubtful, String caseId, String caseTitle,
                      String caseMissing) {
    }

    public static final StreamCodec<FriendlyByteBuf, JournalSyncPayload> CODEC =
            StreamCodec.of(JournalSyncPayload::write, JournalSyncPayload::read);

    /** 由服务端的一份见闻录构造（已经过玩家筛选）。 */
    public static JournalSyncPayload of(PlayerJournal j, long now) {
        List<Row> rows = new ArrayList<>();
        List<PlayerJournal.Entry> all = j.all();
        // 取最近的若干条：按知道的时间倒序
        all.sort((a, b) -> Long.compare(b.observedAt, a.observedAt));
        for (PlayerJournal.Entry e : all) {
            if (rows.size() >= MAX_SENT) {
                break;
            }
            rows.add(new Row(e.id, e.kind.ordinal(), e.source.ordinal(), e.title, e.detail,
                    e.dim, e.pos, e.observedAt, e.lastVerifiedAt, e.favorite, e.doubtful,
                    e.caseId, e.caseTitle, e.caseMissing));
        }
        return new JournalSyncPayload(j.titleUnlocked(), now, rows, j.title());
    }

    private static void write(FriendlyByteBuf buf, JournalSyncPayload p) {
        buf.writeBoolean(p.titleUnlocked);
        buf.writeLong(p.now);
        buf.writeUtf(p.title);
        buf.writeVarInt(p.rows.size());
        for (Row r : p.rows) {
            buf.writeUtf(r.id);
            buf.writeVarInt(r.kind);
            buf.writeVarInt(r.source);
            buf.writeUtf(r.title);
            buf.writeUtf(r.detail);
            buf.writeUtf(r.dim);
            buf.writeLong(r.pos);
            buf.writeLong(r.observedAt);
            buf.writeLong(r.verifiedAt);
            buf.writeBoolean(r.favorite);
            buf.writeBoolean(r.doubtful);
            buf.writeUtf(r.caseId);
            buf.writeUtf(r.caseTitle);
            buf.writeUtf(r.caseMissing);
        }
    }

    private static JournalSyncPayload read(FriendlyByteBuf buf) {
        boolean unlocked = buf.readBoolean();
        long now = buf.readLong();
        String title = buf.readUtf();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf()));
        }
        return new JournalSyncPayload(unlocked, now, rows, title);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
