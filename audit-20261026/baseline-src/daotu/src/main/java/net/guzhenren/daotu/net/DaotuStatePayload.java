package net.guzhenren.daotu.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.guzhenren.daotu.DaotuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：道途界面状态快照。
 *
 * <p><b>v1.4.0 收缩</b>：势力 / 档位 / 感悟 / 节点 / 觉醒五个字段全部删除，
 * 新增 {@code threshold}（当前转数下攒满一次抽选所需的历练值）与
 * {@code offer}（攒满后待玩家选择的 5 个候选）。
 *
 * <p><b>两种到达方式</b>由 {@code open} 区分：
 * <ul>
 *   <li>{@code open == true}——玩家主动按 M 请求，或抽选攒满需要弹窗。
 *       客户端<b>一定</b>建界面。</li>
 *   <li>{@code open == false}——服务端因属性变化主动推送。客户端
 *       <b>只刷新已开着的界面</b>，没开就什么都不做。</li>
 * </ul>
 *
 * @param open       是否应当打开界面
 * @param pool       当前历练池
 * @param threshold  当前转数下攒满一次抽选所需的历练值（144 / 216 / 324 / 486 / 729）
 * @param draws      本局已抽选次数
 * @param runId      本局编号（转世 +1）
 * @param zhuanshu   本体转数（1..5，只读；界面用来显示缩放倍率）
 * @param attrs      本局已累加的属性（键 → 值；百分比键存小数，如 0.15 = +15%）
 * @param offer      待选择的候选（攒满且未选时非空；选完即空）
 * @param offerDraw  这批候选对应的抽次（0 = 无待选）
 * @param history    已选定的记录（"抽次|key|amount|percent"，只读展示，不可改）
 * @param buffs      已获得的特殊奖励 buff id
 * @param openedQiao 是否已开窍（未开窍时界面全灰）
 * @param autoPrompt 攒满时是否自动弹窗（v1.4.1 新增；界面页脚的开关读它）
 * @param mainDao    主修流派键（空串 = 未设定；界面页脚按钮显示它，v1.5.0 新增）
 * @param drawCap    本转抽选上限（一转~五转 100，五转后 {@link Integer#MAX_VALUE}；v1.5.0 新增）
 */
public record DaotuStatePayload(boolean open, double pool, double threshold, int draws,
                                int runId, double zhuanshu,
                                Map<String, Double> attrs,
                                List<Offer> offer, int offerDraw,
                                List<String> history,
                                List<String> buffs,
                                boolean openedQiao,
                                boolean autoPrompt,
                                String mainDao,
                                int drawCap) implements CustomPacketPayload {
    public static final Type<DaotuStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DaotuMod.MODID, "state"));

    /**
     * 一个待选候选。
     *
     * @param key     属性键或 {@code buff:xxx} / {@code daohen:xxx}
     * @param label   展示用中文名
     * @param amount  数值（百分比键存小数）
     * @param percent 是否百分比
     */
    public record Offer(String key, String label, double amount, boolean percent) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DaotuStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.open);
                        buf.writeDouble(p.pool);
                        buf.writeDouble(p.threshold);
                        buf.writeVarInt(p.draws);
                        buf.writeVarInt(p.runId);
                        buf.writeDouble(p.zhuanshu);
                        buf.writeVarInt(p.attrs.size());
                        for (Map.Entry<String, Double> e : p.attrs.entrySet()) {
                            buf.writeUtf(e.getKey());
                            buf.writeDouble(e.getValue());
                        }
                        buf.writeVarInt(p.offer.size());
                        for (Offer o : p.offer) {
                            buf.writeUtf(o.key());
                            buf.writeUtf(o.label());
                            buf.writeDouble(o.amount());
                            buf.writeBoolean(o.percent());
                        }
                        buf.writeVarInt(p.offerDraw);
                        buf.writeCollection(p.history, (b, s) -> b.writeUtf(s));
                        buf.writeCollection(p.buffs, (b, s) -> b.writeUtf(s));
                        buf.writeBoolean(p.openedQiao());
                        buf.writeBoolean(p.autoPrompt());
                        buf.writeUtf(p.mainDao());
                        buf.writeVarInt(p.drawCap());
                    },
                    buf -> {
                        boolean open = buf.readBoolean();
                        double pool = buf.readDouble();
                        double threshold = buf.readDouble();
                        int draws = buf.readVarInt();
                        int runId = buf.readVarInt();
                        double zhuanshu = buf.readDouble();
                        Map<String, Double> attrs = new java.util.LinkedHashMap<>();
                        int na = buf.readVarInt();
                        for (int i = 0; i < na; i++) {
                            attrs.put(buf.readUtf(), buf.readDouble());
                        }
                        int no = buf.readVarInt();
                        List<Offer> offer = new ArrayList<>(no);
                        for (int i = 0; i < no; i++) {
                            offer.add(new Offer(buf.readUtf(), buf.readUtf(),
                                    buf.readDouble(), buf.readBoolean()));
                        }
                        int offerDraw = buf.readVarInt();
                        List<String> history = new ArrayList<>(
                                buf.readCollection(ArrayList::new, b -> b.readUtf()));
                        List<String> buffs = new ArrayList<>(
                                buf.readCollection(ArrayList::new, b -> b.readUtf()));
                        boolean openedQiao = buf.readBoolean();
                        boolean autoPrompt = buf.readBoolean();
                        String mainDao = buf.readUtf();
                        int drawCap = buf.readVarInt();
                        return new DaotuStatePayload(open, pool, threshold, draws, runId,
                                zhuanshu, attrs, offer, offerDraw, history, buffs,
                                openedQiao, autoPrompt, mainDao, drawCap);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DaotuStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.guzhenren.daotu.client.DaotuClientHandler.receive(payload));
    }
}
