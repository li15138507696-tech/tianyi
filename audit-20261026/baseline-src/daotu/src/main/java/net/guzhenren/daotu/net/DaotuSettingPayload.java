package net.guzhenren.daotu.net;

import net.guzhenren.daotu.DaotuMod;
import net.guzhenren.daotu.DaotuNet;
import net.guzhenren.daotu.DaotuRun;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：玩家在道途界面页脚切换「自动弹窗」。
 *
 * <p><b>为什么走网络而不是纯客户端</b>：弹窗是服务端 {@link DaotuNet#promptChoice}
 * 决定的。若只改客户端标志，服务端照样发 {@code open = true}，开关就没意义。
 * 所以真正生效的值存在玩家数据里（{@link DaotuRun#autoPrompt}），
 * 客户端只负责把点击结果报上来。
 *
 * <p>服务端处理完会回推一次状态包，界面上的开关因此始终显示<b>服务端真实值</b>，
 * 不用信任客户端本地的乐观翻转。
 */
public record DaotuSettingPayload(boolean autoPrompt) implements CustomPacketPayload {

    public static final Type<DaotuSettingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DaotuMod.MODID, "setting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DaotuSettingPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.autoPrompt()),
                    buf -> new DaotuSettingPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DaotuSettingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                DaotuRun.setAutoPrompt(sp, payload.autoPrompt());
                // 回推：让界面显示服务端真实值（也顺带刷新其他字段）
                DaotuNet.sendState(sp);
                // 给玩家一条明确回执：看不到这条就说明设置包没到服务端
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        payload.autoPrompt()
                                ? "§6[道途] §e自动弹窗已开启"
                                : "§6[道途] §7自动弹窗已关闭（攒满只在聊天栏提示）"), false);
                DaotuMod.LOGGER.info("[道途] {} 自动弹窗 -> {}", sp.getScoreboardName(),
                        payload.autoPrompt());
            }
        });
    }
}
