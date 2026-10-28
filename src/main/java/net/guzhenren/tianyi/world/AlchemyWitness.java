package net.guzhenren.tianyi.world;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.network.GuzhenrenModVariables.PlayerVariables;
import net.guzhenren.tianyi.will.AlchemyTracker;
import net.guzhenren.tianyi.will.IdentityCase;
import net.guzhenren.tianyi.will.Knowledge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * 炼蛊见证：把「本体不告诉我们炼出了什么」这件事，用<b>背包前后差异</b>补上。
 *
 * <h2>为什么要这么绕</h2>
 * 本体只暴露 {@code LianGuJinDu}（0~100）、{@code LianGu}（是否在炼）、{@code GuFang}，
 * <b>没有任何字段说这次炼出了什么</b>。而「炼蛊证据」的第三条判据要求可识别产物，
 * 所以只能在炼蛊开始前给背包拍个照，炼成后对比。
 *
 * <h2>B05 修复：差异必须干净</h2>
 * 单纯「增量最大的那项就是产物」会被污染 —— 同期捡到别的本体物品就能冒充。现在三道闸：
 * <ol>
 *   <li><b>会话绑定</b>：一次炼制 = 一个会话（基线 + 蛊方 + 期间拾取记录）。
 *       换蛊方、进度归零都会结束会话；跨会话的差异不算数。</li>
 *   <li><b>扣掉期间拾取</b>：{@link #notePickup} 收下的每一笔拾取都从差异里扣，
 *       所以「炼出 1 个 + 捡到 64 个」不会把 64 记成产物。</li>
 *   <li><b>只认唯一候选</b>：扣完之后必须<b>恰好剩一项</b>正增量才算数；
 *       多于一项说明差异不可信 —— 记一条待核实线索，不写 CRAFT 证据。</li>
 * </ol>
 */
public final class AlchemyWitness {
    private AlchemyWitness() {
    }

    /** 一次炼制会话。 */
    private static final class Session {
        final Map<String, Integer> baseline;
        final String fang;
        final Map<String, Integer> pickups = new LinkedHashMap<>();

        Session(Map<String, Integer> baseline, String fang) {
            this.baseline = baseline;
            this.fang = fang;
        }
    }

    /** 玩家 UUID → 当前炼制会话。 */
    private static final Map<String, Session> SESSIONS = new HashMap<>();

    /**
     * 记录一次拾取（由拾取事件调用）。
     *
     * <p>只在「这个人正在炼蛊」时有意义：这些数量会从炼成时的背包差异里扣掉，
     * 从而避免「捡来的东西被当成产物」。
     */
    public static void notePickup(String playerUuid, String itemId, int count) {
        if (playerUuid == null || itemId == null || itemId.indexOf(':') <= 0 || count <= 0) {
            return;
        }
        Session s = SESSIONS.get(playerUuid);
        if (s == null) {
            return; // 没在炼，不记
        }
        s.pickups.merge(itemId.toLowerCase(java.util.Locale.ROOT), count, Integer::sum);
    }

    /**
     * 观测一次某人的炼蛊状态。
     *
     * @return 本次是否被判定为「真的炼成了一次」
     */
    public static boolean observe(ServerPlayer player, Knowledge.Store knowledge,
                                  IdentityCase.Ledger identity, long now, Logger log) {
        String uuid = player.getUUID().toString();
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
        double progress = v.LianGuJinDu;
        String fang = fangOf(v);
        AlchemyTracker.Tracker tracker = AlchemyTracker.of(uuid);

        // ---- 进度不足：维护会话 ----
        if (progress < AlchemyTracker.COMPLETE) {
            if (progress <= 0.0) {
                // 这一轮结束（成功已记录 / 失败 / 取消）→ 会话结束
                SESSIONS.remove(uuid);
                tracker.observe(0.0, fang, "", 0);
                return false;
            }
            Session s = SESSIONS.get(uuid);
            if (s == null || !s.fang.equals(fang)) {
                // 新会话（第一次炼，或换了蛊方）：重新拍基线
                SESSIONS.put(uuid, new Session(snapshot(player), fang));
            }
            tracker.observe(progress, fang, "", 0);
            return false;
        }

        // ---- 到满值：必须有会话，且差异必须干净 ----
        Session session = SESSIONS.get(uuid);
        if (session == null) {
            tracker.observe(progress, fang, "", 0);
            return false; // 没有会话（例如上线时进度已是 100）→ 不认
        }
        if (!session.fang.equals(fang)) {
            SESSIONS.remove(uuid);
            tracker.observe(progress, fang, "", 0);
            return false; // 换方，旧会话作废
        }

        Map<String, Integer> grown = AlchemyTracker.diff(session.baseline, snapshot(player));
        // 扣掉期间捡到的
        for (Map.Entry<String, Integer> e : session.pickups.entrySet()) {
            Integer g = grown.get(e.getKey());
            if (g == null) {
                continue;
            }
            int net = g - e.getValue();
            if (net > 0) {
                grown.put(e.getKey(), net);
            } else {
                grown.remove(e.getKey());
            }
        }

        if (grown.size() != 1) {
            // 0 项：没认出产物；≥2 项：差异被污染，不可信。两种都不写 CRAFT 证据。
            if (log != null && log.isDebugEnabled()) {
                log.debug("[天意] 炼蛊差异不可用（{} 项候选）：{}",
                        grown.size(), player.getGameProfile().getName());
            }
            SESSIONS.remove(uuid);
            tracker.observe(progress, fang, "", 0);
            return false;
        }

        var top = AlchemyTracker.topProduct(grown);
        if (top == null) {
            SESSIONS.remove(uuid);
            tracker.observe(progress, fang, "", 0);
            return false;
        }

        var result = tracker.observe(progress, fang, top.getKey(), top.getValue());
        SESSIONS.remove(uuid);
        if (!result.completed()) {
            if (log != null && log.isDebugEnabled() && !"stale-100".equals(result.why())) {
                log.debug("[天意] 炼蛊未成证据：{}（{}）",
                        player.getGameProfile().getName(), result.why());
            }
            return false;
        }

        String dim = player.level().dimension().location().toString();
        long pos = player.blockPosition().asLong();
        // ---- 只写「玩家自己的尝试记录」，不当作已确认的炼成 ----
        // 理由：本体没有产物接口，唯一的线索是背包差分；而差分会被同期拾取/交易/取出污染。
        // 所以 CRAFT 事实与身份案的手艺痕迹**都不再**由差分产生 ——
        // 天意不会因为一个不可靠的推断就掌握「这人炼成了什么」。
        if (JournalStore.noteAlchemyAttempt(player, result.productId(), result.count(), now)) {
            net.guzhenren.tianyi.net.JournalNet.sync(player);
        }
        if (log != null) {
            log.info("[天意] 炼蛊尝试（结果未核实）：{} 疑似产物 {} ×{}（{}）",
                    player.getGameProfile().getName(), result.productId(),
                    result.count(), result.why());
        }
        return true;
    }

    /** 本体物品的背包计数（只数 guzhenren 命名空间）。 */
    public static Map<String, Integer> snapshot(ServerPlayer player) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ItemStack st : player.getInventory().items) {
            if (st == null || st.isEmpty()) {
                continue;
            }
            var key = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (key == null || !"guzhenren".equals(key.getNamespace())) {
                continue;
            }
            out.merge(key.toString(), st.getCount(), Integer::sum);
        }
        return out;
    }

    /** 蛊方标识（本体只有一个数值字段，没有名称）。 */
    private static String fangOf(PlayerVariables v) {
        return v.GuFang > 0 ? "fang:" + (long) v.GuFang : "";
    }

    /** 下线清理（避免长期运行残留会话）。 */
    public static void forget(String uuid) {
        SESSIONS.remove(uuid);
        AlchemyTracker.forget(uuid);
    }

    /** 诊断：当前有多少人处在「正在炼」的会话中。 */
    public static int baselines() {
        return SESSIONS.size();
    }
}
