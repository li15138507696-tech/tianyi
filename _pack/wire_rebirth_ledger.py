"""2.9.0 第一期（续）：百世书去刷、真实转世识别、击杀入账去重。"""
import os

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

# ---------------------------------------------------------------- 1. 转世信号（保守：无法确认就是未确认）
SIGNAL = '''package net.guzhenren.tianyi.ledger;

import net.guzhenren.tianyi.TianyiData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 本体「真实转世」信号识别（2.9.0）。
 *
 * <h2>为什么先做成"永远未确认"</h2>
 * 用户要求：普通死亡、玩家克隆、断线、转数下降**都不能**当成转世证据；
 * 无法可靠验证时保持待迁移并禁用自动切换，且**不许声称已验证**。
 *
 * <p>本体内寿尽重置的判定目前没有可依赖的公开接口（`docu/整合勘验报告.md` 里也标了未验证）。
 * 所以这里：
 * <ul>
 *   <li>{@link #isVerified} 一律返回 {@code false} —— 阶段一不迁移、不结算；</li>
 *   <li>{@link #notePending} 只写一个"待迁移"标记 + 观测快照键，供后续阶段与日志核对；</li>
 *   <li>所有依赖于"已转世"的收益路径（百世书结算、旧档切换）因此天然关闭。</li>
 * </ul>
 *
 * <p>后续接入真实信号时，只需改这一处：确认后返回 true 并给出本世标识，
 * 其余代码（{@code LifeBook}、面板、迁移）都按"已确认"分支工作。
 */
public final class TianyiRebirthSignal {
    private static final String SUB = "sys";
    private static final String K_PENDING = "rebirthPending";
    private static final String K_SEEN = "rebirthSeenZhuanshu";
    private static final String K_REASON = "rebirthReason";

    private TianyiRebirthSignal() {
    }

    /** 是否确认发生了「本体真实转世」。阶段一恒为 false。 */
    public static boolean isVerified(ServerPlayer player) {
        return false;
    }

    /**
     * 记录一次"疑似重置"的观测（只读快照，不结算、不发奖）。
     *
     * @param zhuanshu 观测到的转数（0 常出现在重置后，但单独出现不足以证明）
     * @param reason   判定来源说明（日志/面板展示用）
     */
    public static void notePending(ServerPlayer player, int zhuanshu, String reason) {
        TianyiData.putBoolean(player, SUB, K_PENDING, true);
        TianyiData.putInt(player, SUB, K_SEEN, zhuanshu);
        TianyiData.putString(player, SUB, K_REASON, reason);
    }

    public static boolean pending(ServerPlayer player) {
        return TianyiData.getBoolean(player, SUB, K_PENDING, false);
    }

    public static String reason(ServerPlayer player) {
        return TianyiData.getString(player, SUB, K_REASON, "");
    }

    public static void clearPending(ServerPlayer player) {
        TianyiData.putBoolean(player, SUB, K_PENDING, false);
        TianyiData.putString(player, SUB, K_REASON, "");
    }
}
'''
open(os.path.join(SRC, 'ledger', 'TianyiRebirthSignal.java'), 'w',
     encoding='utf-8', newline='\n').write(SIGNAL)
print('  + ledger/TianyiRebirthSignal.java（阶段一恒未确认）')

# ---------------------------------------------------------------- 2. 百世书：每世一次、不铸天机
LIFE = '''package net.guzhenren.tianyi;

import net.guzhenren.tianyi.ai.TianyiAiQuests;
import net.guzhenren.tianyi.ledger.TianyiLedger;
import net.guzhenren.tianyi.ledger.TianyiRebirthSignal;
import net.guzhenren.tianyi.quest.TianyiQuestState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 百世书：跨世结算（2.9.0 重写）。
 *
 * <h2>修掉的三件事</h2>
 * <ol>
 *   <li><b>不再被普通按钮/命令反复追加收益</b>：结算前先取 {@link TianyiLedger#settleOnce}
 *       的一次性标记，键里带本世编号——同一世无论点多少次、发多少包，只生效一次；</li>
 *   <li><b>不再按「累计天机百分比」铸新天机</b>：上一版按 30% 返还，等于每世凭空造币。
 *       现在<b>不产生任何天机</b>；</li>
 *   <li><b>不猜转世</b>：只有 {@link TianyiRebirthSignal#isVerified} 为真才结算；否则只落
 *       「待迁移」标记并拒绝，绝不自动切换旧档。</li>
 * </ol>
 *
 * <h2>继承什么</h2>
 * 阶段一只继承<b>系统侧</b>的东西：本世编号、天机峰值记录、垂青少年（有上限）。
 * 本体修为/道痕/资质/蛊虫一概不碰——那是本体的领地。
 */
public final class LifeBook {
    /** 旧版按比例返还的常量，已废弃：保留名字只为让旧文档与审计能对照。 */
    @Deprecated
    public static final int CARRY_PERCENT = 0;
    /** 垂青次数上限。 */
    public static final int MAX_BLESSINGS = 3;

    private static final String SUB = "sys";
    private static final String K_LIFE = "life";
    private static final String K_BEST = "bestTianji";
    private static final String K_BLESS = "blessings";

    private LifeBook() {
    }

    public static int life(ServerPlayer player) {
        return Math.max(1, TianyiData.getInt(player, SUB, K_LIFE, 1));
    }

    public static int bestTianji(ServerPlayer player) {
        return TianyiData.getInt(player, SUB, K_BEST, 0);
    }

    public static int blessings(ServerPlayer player) {
        return TianyiData.getInt(player, SUB, K_BLESS, 0);
    }

    /**
     * 结算一世。
     *
     * @return 给玩家看的一句话
     */
    public static String settle(ServerPlayer player) {
        if (!TianyiRebirthSignal.isVerified(player)) {
            TianyiRebirthSignal.notePending(player, 0, "本体转世信号未确认（阶段一）");
            String msg = "【百世书】尚未确认本世真的结束（普通死亡/克隆/断线都不算转世）。"
                    + "已记为「待迁移」，不做结算、不发天机。";
            player.displayClientMessage(Component.literal("§6[天意] §r" + msg), false);
            return msg;
        }

        int ended = life(player);
        if (!TianyiLedger.settleOnce(player, "life" + ended + ".settle")) {
            return "本世已结算过，不再重复。";
        }

        int best = Math.max(bestTianji(player), TianyiLedger.lifetime(player));
        TianyiData.putInt(player, SUB, K_LIFE, ended + 1);
        TianyiData.putInt(player, SUB, K_BEST, best);
        TianyiData.putInt(player, SUB, K_BLESS,
                Math.min(MAX_BLESSINGS, blessings(player) + 1));

        // 清本世进度（不动账本余额与累计：天机不是"这一世的东西"，是天意对玩家的记账）
        TianyiQuestState.wipeOnRebirth(player);
        TianyiAiQuests.wipeOnRebirth(player);
        TianyiRebirthSignal.clearPending(player);

        String msg = "【百世书】第 " + ended + " 世结算完成：峰值 " + best
                + " 天机，垂青 " + blessings(player) + "/" + MAX_BLESSINGS
                + "。只继承已验证的知识与系统权限，不继承修为、道痕、资质与蛊虫。";
        player.displayClientMessage(Component.literal("§6[天意] §r" + msg), false);
        TianyiNet.sendState(player);
        return msg;
    }
}
'''
open(os.path.join(SRC, 'LifeBook.java'), 'w', encoding='utf-8', newline='\n').write(LIFE)
print('  ~ LifeBook.java 重写（每世一次 / 不铸天机 / 需真实转世信号）')

# ---------------------------------------------------------------- 3. 击杀入账去重
EV = os.path.join(SRC, 'TianyiEvents.java')
s = open(EV, encoding='utf-8').read()
old = '''                    TianyiCausality.award(sp, TianyiCausality.beastSource(sp == null ? 1 : tier), 0);'''
if old not in s:
    old = '''                    TianyiCausality.award(sp, TianyiCausality.beastSource(tier), 0);'''
if old in s:
    s = s.replace(old, '''                    // 同一只实体只入账一次：多入口/多次信用（getKillCredit 反复）不会重复给
                    if (net.guzhenren.tianyi.ledger.TianyiLedger.settleOnce(sp,
                            "kill." + event.getEntity().getUUID())) {
                        TianyiCausality.award(sp, TianyiCausality.beastSource(tier), 0);
                    }''')
    open(EV, 'w', encoding='utf-8', newline='\n').write(s)
    print('  ~ TianyiEvents: 击杀入账按实体 UUID 去重')
else:
    print('  ! TianyiEvents: 击杀入账片段未匹配')

# ---------------------------------------------------------------- 4. 面板文案（不再提 30%）
P = os.path.join(SRC, 'client', 'TianyiPanelScreen.java')
s = open(P, encoding='utf-8').read()
old_txt = '''        drawWrapped(g, "一世终了，带走本世天机 " + LifeBook.CARRY_PERCENT
                + "%，并得一次垂青（上限 " + LifeBook.MAX_BLESSINGS + " 次）。", x, y, w, 2, T_BODY);'''
new_txt = '''        drawWrapped(g, "一世终了只得一次垂青（上限 " + LifeBook.MAX_BLESSINGS
                + " 次）。不再按比例铸天机——那是造币，不是继承。", x, y, w, 2, T_BODY);'''
if old_txt in s:
    s = s.replace(old_txt, new_txt)
    print('  ~ 面板：百世书文案改为"不铸天机"')
else:
    print('  ! 面板百世书文案未匹配')
open(P, 'w', encoding='utf-8', newline='\n').write(s)
