package net.guzhenren.daotu.net;

import net.guzhenren.daotu.DaotuMainDao;
import net.guzhenren.daotu.DaotuMod;
import net.guzhenren.daotu.DaotuNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：玩家在道途界面设定「主修流派」。
 *
 * <p><b>为什么必须走网络</b>：主修决定特殊奖励发什么（道痕 / 流派熟练度 / 主修蛊虫），
 * 这是服务端在 {@code DaotuSpecial#candidates} 里读的。只改客户端标志没有意义，
 * 所以真正生效的值存玩家数据（{@link DaotuMainDao}），客户端只负责报上来。
 *
 * <p><b>空串 = 清除主修</b>。此时特殊奖励只剩气运一项，
 * 与「未设定」的处理一致（见 {@link DaotuMainDao}）。
 *
 * <p>处理完回推一次状态包，界面上的「主修」按钮因此永远显示<b>服务端真实值</b>。
 */
public record DaotuMainDaoPayload(String dao) implements CustomPacketPayload {

    public static final Type<DaotuMainDaoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DaotuMod.MODID, "maindao"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DaotuMainDaoPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.dao()),
                    buf -> new DaotuMainDaoPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DaotuMainDaoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                String dao = payload.dao() == null ? "" : payload.dao();
                boolean ok;
                if (dao.isEmpty()) {
                    DaotuMainDao.clear(sp);
                    ok = true;
                } else {
                    // 键不在白名单里时拒绝，防止脏数据把特殊奖励带崩
                    ok = DaotuMainDao.set(sp, dao);
                    if (!ok) {
                        DaotuMod.LOGGER.warn("拒绝非法主修流派: {}", dao);
                    }
                }
                if (!ok) {
                    return;
                }
                // 回推：让界面显示服务端真实值
                DaotuNet.sendState(sp);
                sp.displayClientMessage(Component.literal(
                        dao.isEmpty()
                                ? "§6[道途] §7已清除主修流派"
                                : "§6[道途] §e主修流派已设为 §f" + DaotuMainDao.label(dao)),
                        false);
            }
        });
    }
}
