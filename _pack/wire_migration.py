"""2.9.0：状态包补账本诊断字段 + 旧档迁移入口（幂等）。"""
import os

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

# ---------------------------------------------------------------- 1. 迁移门面
MIG = '''package net.guzhenren.tianyi.ledger;

import net.guzhenren.tianyi.TianyiData;
import net.guzhenren.tianyi.TianyiMod;
import net.minecraft.server.level.ServerPlayer;

/**
 * 旧档迁移（幂等，每次状态同步都可安全调用）。
 *
 * <h2>为什么放在状态同步里调用</h2>
 * 迁移必须"玩家一进游戏就完成"，但又不该在登录事件里做一堆 IO 猜测。
 * {@link TianyiLedgerCore#migrate} 自带 {@code schema} 版本闸门：已经迁过的玩家
 * 只多读两个整数就返回，成本可以忽略。
 *
 * <h2>迁移口径</h2>
 * <ul>
 *   <li>数值来源：2.8.0 的 {@code econ.tianji} / {@code econ.lifetime_tianji}；</li>
 *   <li>累计取新旧<b>较大值</b>，只抬高不压低；</li>
 *   <li><b>不删除旧键</b>，便于回滚与对账；不反向扣任何本体数值；</li>
 *   <li>永久词条的「已购列表」不属于账本，本阶段不动它——旧档的既有影响按用户口径保留。</li>
 * </ul>
 */
public final class TianyiMigration {
    private static final String LEGACY_POINTS = "tianji";
    private static final String LEGACY_LIFETIME = "lifetime_tianji";

    private TianyiMigration() {
    }

    /**
     * 确保该玩家的账本已迁移到当前结构版本。
     *
     * @return 是否本次真正执行了迁移
     */
    public static boolean ensure(ServerPlayer player) {
        if (TianyiLedger.schema(player) >= TianyiLedgerCore.SCHEMA) {
            return false;
        }
        int oldPoints = TianyiData.getInt(player, TianyiData.ECON, LEGACY_POINTS, 0);
        int oldLifetime = TianyiData.getInt(player, TianyiData.ECON, LEGACY_LIFETIME, 0);
        boolean done = TianyiLedger.migrateFromLegacy(player, oldPoints, oldLifetime);
        if (done) {
            TianyiMod.LOGGER.info("[天意] 账本迁移完成：旧余额 {} / 旧累计 {} → schema {}",
                    oldPoints, oldLifetime, TianyiLedgerCore.SCHEMA);
        }
        return done;
    }
}
'''
open(os.path.join(SRC, 'ledger', 'TianyiMigration.java'), 'w',
     encoding='utf-8', newline='\n').write(MIG)
print('  + ledger/TianyiMigration.java')

# ---------------------------------------------------------------- 2. 状态包：迁移 + 账本诊断
p = os.path.join(SRC, 'TianyiNet.java')
s = open(p, encoding='utf-8').read()
n = 0
if 'TianyiMigration.ensure' not in s:
    s = s.replace('''        TianyiQuestState.ensureFresh(player);''',
                  '''        TianyiQuestState.ensureFresh(player);
        // 旧档账本迁移（幂等：已迁过只读两个整数就返回）
        net.guzhenren.tianyi.ledger.TianyiMigration.ensure(player);''')
    n += 1
if 'ledgerOk' not in s:
    s = s.replace('''        t.putBoolean("freeSim", TianyiInsight.freeSimAvailable(player));''',
                  '''        t.putBoolean("freeSim", TianyiInsight.freeSimAvailable(player));
        // 账本诊断（只发给本人，不含任何密钥；面板「系统」页据此显示对账信息）
        t.putInt("earnedToday", net.guzhenren.tianyi.ledger.TianyiLedger.earnedToday(player));
        t.putInt("refundedTotal", net.guzhenren.tianyi.ledger.TianyiLedger.refundedTotal(player));
        t.putInt("penaltyTotal", net.guzhenren.tianyi.ledger.TianyiLedger.penaltyTotal(player));
        t.putBoolean("ledgerOk", net.guzhenren.tianyi.ledger.TianyiLedger.consistent(player));
        t.putBoolean("rebirthPending",
                net.guzhenren.tianyi.ledger.TianyiRebirthSignal.pending(player));''')
    n += 1
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print(f'  TianyiNet: {n} 处（迁移入口 / 账本诊断字段）')

# ---------------------------------------------------------------- 3. 面板：系统页显示对账
P = os.path.join(SRC, 'client', 'TianyiPanelScreen.java')
s = open(P, encoding='utf-8').read()
if 'refundedTotal' not in s:
    anchor = '''        y = stat(g, x, y, "注视", TianyiClientState.i("attention") + " · "
                + TianyiClientState.s("attentionLabel"));
        y += 5;'''
    add = '''        y = stat(g, x, y, "注视", TianyiClientState.i("attention") + " · "
                + TianyiClientState.s("attentionLabel"));
        // 账本对账：今日已赚 / 累计退款 / 累计处罚。退款与处罚都不许抬高累计，
        // 所以这三行同时是给玩家的凭据、也是出问题时的证据。
        y = stat(g, x, y, "今日", TianyiClientState.i("earnedToday") + " 天机");
        y = stat(g, x, y, "退款", TianyiClientState.i("refundedTotal") + " 天机（不计累计）");
        y = stat(g, x, y, "处罚", TianyiClientState.i("penaltyTotal") + " 天机（不计累计）");
        if (!TianyiClientState.b("ledgerOk")) {
            g.drawString(font, "§c账本数据异常，请把这一页截图给服主", x, y, 0xFFE0695A, true);
        }
        y += 5;'''
    if anchor in s:
        s = s.replace(anchor, add)
        open(P, 'w', encoding='utf-8', newline='\n').write(s)
        print('  ~ 面板系统页：加账本对账三行')
    else:
        print('  ! 面板锚点未匹配')
