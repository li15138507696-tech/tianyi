package net.guzhenren.malice;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 开局选难度。
 *
 * <p>用聊天栏里的<b>可点击文字</b>而不是自建 GUI：不用注册网络包、不用客户端类，
 * 点一下就是把 {@code /guzhenren_malice difficulty <档位>} 执行一遍，多人服一样能用。
 *
 * <p>每张存档只弹一次（{@link DifficultyState#isChosen()}）。想再弹一次用
 * {@code /guzhenren_malice choose}。
 */
public final class MaliceWelcome {

    private MaliceWelcome() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        DifficultyState state = DifficultyState.get(server);
        if (state.isChosen()) {
            return;
        }
        sendChooser(player, state);
    }

    /** 把四档按钮推给玩家。 */
    public static void sendChooser(ServerPlayer player, DifficultyState state) {
        player.sendSystemMessage(Component.literal("—— 蛊真人 × 莱特兰恶意 ——").withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("请选择怪物难度（点一下即可，之后随时能用命令改）：")
                .withStyle(ChatFormatting.GRAY));

        Component line = Component.literal("");
        for (Difficulty d : Difficulty.values()) {
            Component button = Component.literal("  [" + d.cn() + " ×" + d.levelMul() + "]")
                    .withStyle(style -> style
                            .withColor(d.color())
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/guzhenren_malice difficulty " + d.name().toLowerCase(Locale.ROOT)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("怪物恶意等级 ×" + d.levelMul()
                                            + "（当前：" + state.current().cn() + "）"))));
            line = line.copy().append(button);
        }
        player.sendSystemMessage(line);
        player.sendSystemMessage(Component.literal("安逸=新手 ｜ 标准=默认 ｜ 凶险=老手 ｜ 噩梦=找虐。"
                + "命令：/guzhenren_malice difficulty <档位>").withStyle(ChatFormatting.DARK_GRAY));
    }
}
