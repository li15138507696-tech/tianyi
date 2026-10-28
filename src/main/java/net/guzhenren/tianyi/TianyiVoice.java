package net.guzhenren.tianyi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.guzhenren.init.GuzhenrenModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 天意播报：聊天 + 音效，带节流。
 *
 * <p>节流按「玩家 + 播报键」记 {@code gameTime}，同一键 {@value #THROTTLE_TICKS} tick 内不重复。
 * 目的不是省性能，而是防刷屏：击杀类播报一秒能触发好几次，玩家会直接关掉聊天栏。
 *
 * <p>音效只用本体确实存在的常量（{@code GuzhenrenModSounds} 共 140 个，已 javap 核对），
 * 不按中文名类推。
 */
public final class TianyiVoice {
    /** 同一播报键的最小间隔（5 秒）。 */
    private static final long THROTTLE_TICKS = 100L;

    private static final Map<UUID, Map<String, Long>> LAST = new HashMap<>();

    private TianyiVoice() {
    }

    /** 纯文字播报（受节流）。 */
    public static void say(ServerPlayer player, String key, String msg) {
        if (throttled(player, key)) {
            return;
        }
        player.displayClientMessage(Component.literal("§b[天意] §r" + msg), false);
    }

    /** 带音效的播报（不受节流：这类事件本来就低频，且玩家需要听到反馈）。 */
    public static void announce(ServerPlayer player, String msg, SoundEvent sound, float volume, float pitch) {
        player.displayClientMessage(Component.literal("§6[天意] §r" + msg), false);
        if (sound != null) {
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    /** 常用音效：蛊虫触动 4（开窍同款），用于「试炼完成」这类正反馈。 */
    public static SoundEvent chime() {
        return GuzhenrenModSounds.GUCHONG_CUIDONG_4.get();
    }

    /** 低沉一点的正反馈（用在摇命/兑换）。 */
    public static SoundEvent lowChime() {
        return GuzhenrenModSounds.GUCHONG_CUIDONG_2.get();
    }

    private static boolean throttled(ServerPlayer player, String key) {
        long now = player.level().getGameTime();
        Map<String, Long> m = LAST.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long last = m.get(key);
        if (last != null && now - last < THROTTLE_TICKS) {
            return true;
        }
        m.put(key, now);
        return false;
    }

    /** 下线清理，避免长期运行的服务端残留已离线玩家条目。 */
    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }
}
