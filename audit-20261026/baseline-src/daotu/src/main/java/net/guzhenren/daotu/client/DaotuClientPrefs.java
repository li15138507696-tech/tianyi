package net.guzhenren.daotu.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

/**
 * 客户端自己的「自动弹窗」偏好。
 *
 * <p><b>为什么客户端也要存一份</b>：弹窗是服务端发 {@code open = true} 决定的，
 * 但这个值一旦没写进玩家存档（或设置包在途中丢了），服务端就会以为玩家还想要弹窗，
 * 玩家却在界面上明明看到「关」。表现就是「关了自动弹窗，攒满还是弹」。
 *
 * <p>所以客户端自己留一份偏好当<b>否决权</b>：服务端发来的强弹窗，如果本地记的是「关」，
 * 就不开。服务端那份值仍然保留——它决定聊天栏提示与别人眼里你的设置。
 *
 * <p>存在 {@code config/daotu-client.json}，重进游戏也记得。
 */
public final class DaotuClientPrefs {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("daotu-client.json");

    /** null = 还没读过文件。 */
    private static Boolean cached;

    /** 玩家是否在本地显式设置过（文件存在或本会话点过开关）。 */
    private static boolean explicit;

    private DaotuClientPrefs() {
    }

    public static boolean autoPrompt() {
        if (cached == null) {
            cached = read();
        }
        return cached;
    }

    /**
     * 本地没设置过时，采用服务端的值（换客户端 / 重装后保持与服务端一致）。
     *
     * <p>只影响显示与默认值，不写文件——否则「没设置过」会变成「设置过」。
     */
    public static void adoptIfUnset(boolean serverValue) {
        if (cached == null) {
            cached = read();
        }
        if (!explicit) {
            cached = serverValue;
        }
    }

    public static void setAutoPrompt(boolean on) {
        if (cached != null && cached == on && explicit) {
            return;
        }
        cached = on;
        explicit = true;
        Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(tmp, "{\"autoPrompt\":" + on + "}\n", StandardCharsets.UTF_8);
            try {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // 写不了就算了：本次会话内仍然生效
        }
    }

    private static boolean read() {
        try {
            if (!Files.isRegularFile(FILE)) {
                explicit = false;
                return true;
            }
            explicit = true;
            String s = Files.readString(FILE, StandardCharsets.UTF_8).replace(" ", "");
            return !s.contains("\"autoPrompt\":false");
        } catch (Exception e) {
            // 文件损坏：不当作「玩家设置过」，让服务端值兜底
            explicit = false;
            return true;
        }
    }
}
