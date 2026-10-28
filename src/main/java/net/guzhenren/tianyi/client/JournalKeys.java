package net.guzhenren.tianyi.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 见闻录按键：默认 J，玩家可自行改绑。
 *
 * <p>翻译键统一放在 {@code key.tianyi.*} 命名空间下 —— 历史上有过旧附属
 * 用同名键造成「开窗即崩（Duplicate key）」的事故，这里不再复用旧键名。
 */
public final class JournalKeys {
    private JournalKeys() {
    }

    /** 分类键：必须在 lang 里只定义一次。 */
    public static final String CATEGORY = "key.categories.tianyi";

    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            "key.tianyi.journal",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY);

    /** mod bus：注册按键。 */
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNAL);
    }
}
