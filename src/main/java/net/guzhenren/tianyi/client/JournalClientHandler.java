package net.guzhenren.tianyi.client;

import net.guzhenren.tianyi.net.JournalSyncPayload;
import net.guzhenren.tianyi.net.EncounterSyncPayload;

/**
 * 客户端收包入口：只做一件事 —— 把服务端给的那份存进状态。
 *
 * <p>单独成一个类，是为了让 {@code JournalNet}（双端）在独立服务器上
 * 不会因为引用客户端类型而加载失败。
 */
public final class JournalClientHandler {
    private JournalClientHandler() {
    }

    public static void onSync(JournalSyncPayload payload) {
        JournalClientState.accept(payload);
    }

    public static void onEncounterSync(EncounterSyncPayload payload) {
        JournalClientState.acceptEncounter(payload);
    }
}
