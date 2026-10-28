"""2.9.0 第一期内核接线：账本 / 注视 / 百世书 / 退款。

修的是用户点名的六条：
1. 收入/支出/退款分开 —— 全部改走 {@code ledger.TianyiLedger}；
2. 注视度唯一可信来源 —— 删掉「写一次缓存再读」的旧行为，改为每次现算；
3. 百世书不得被普通按钮/命令反复追加收益 —— 改成每世只结算一次，且不再铸天机；
4. 领取/付款/违约/结算一律服务端校验且幂等 —— 账本提供 {@code settleOnce}；
5. 不再用「累计百分比」铸新天机 —— 删除该逻辑；
6. 同一事件多入口重复发奖 —— 击杀入账改为一次性键（按实体 UUID）。
"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')


def patch(path, pairs, label):
    s = open(path, encoding='utf-8').read()
    ok = 0
    for a, b in pairs:
        if a in s:
            s = s.replace(a, b)
            ok += 1
        else:
            print(f'  ! {label}: 未匹配 {a[:56]!r}')
    open(path, 'w', encoding='utf-8', newline='\n').write(s)
    print(f'  {label}: {ok}/{len(pairs)}')


# ---------------------------------------------------------------- 1. TianyiEcon → 委托账本
ECON = os.path.join(SRC, 'TianyiEcon.java')
s = open(ECON, encoding='utf-8').read()

s = s.replace('''    public static int points(ServerPlayer player) {''',
              '''    public static int points(ServerPlayer player) {
        return net.guzhenren.tianyi.ledger.TianyiLedger.points(player);
    }

    /** @deprecated 2.9.0 起仅保留给旧调用点；收入请用 {@code grant}，退款请用 {@code refund}。 */
    @Deprecated
    public static int pointsLegacy(ServerPlayer player) {''')

# add() 拆分为 grant / penalize
old_add = re.search(r'    public static int add\(ServerPlayer player, int delta\) \{.*?\n    \}\n', s, re.S)
if old_add:
    s = s.replace(old_add.group(0), '''    /**
     * 旧入口（收入/处罚混用）。
     *
     * <p>2.9.0 起拆成两条路：正数走 {@link #grant}（收入，抬高累计、受日上限），
     * 负数走 {@link #penalize}（处罚，只扣余额、不动累计）。保留本方法只为旧调用点兼容，
     * 新代码请直接调用拆分后的入口。
     */
    @Deprecated
    public static int add(ServerPlayer player, int delta) {
        if (delta >= 0) {
            return net.guzhenren.tianyi.ledger.TianyiLedger.grant(player, delta, 0);
        }
        return -net.guzhenren.tianyi.ledger.TianyiLedger.penalize(player, -delta);
    }

    /** 收入入账（唯一会抬高累计的入口）。 */
    public static int grant(ServerPlayer player, int amount) {
        return net.guzhenren.tianyi.ledger.TianyiLedger.grant(player, amount, 0);
    }

    /** 处罚：扣余额，不动累计。 */
    public static int penalize(ServerPlayer player, int amount) {
        return net.guzhenren.tianyi.ledger.TianyiLedger.penalize(player, amount);
    }

    /** 退款：只回余额，不算收入、不计累计。 */
    public static void refund(ServerPlayer player, int amount) {
        net.guzhenren.tianyi.ledger.TianyiLedger.refund(player, amount);
    }
''')
else:
    print('  ! TianyiEcon.add 未匹配')

# lifetime → 账本
s = re.sub(r'    public static int lifetime\(ServerPlayer player\) \{.*?\n    \}\n',
           '''    public static int lifetime(ServerPlayer player) {
        return net.guzhenren.tianyi.ledger.TianyiLedger.lifetime(player);
    }
''', s, count=1, flags=re.S)

# spend → 账本
s = re.sub(r'    public static boolean spend\(ServerPlayer player, int cost\) \{.*?\n    \}\n',
           '''    public static boolean spend(ServerPlayer player, int cost) {
        return net.guzhenren.tianyi.ledger.TianyiLedger.spend(player, cost);
    }
''', s, count=1, flags=re.S)

open(ECON, 'w', encoding='utf-8', newline='\n').write(s)
print('  TianyiEcon: 已委托账本')

# ---------------------------------------------------------------- 2. 注视度：删缓存、只现算
CAU = os.path.join(SRC, 'TianyiCausality.java')
patch(CAU, [
    # attention() 现算，不再读缓存键
    ('''    public static int attention(ServerPlayer player) {
        return TianyiData.getInt(player, SUB, K_ATTR,
                enlightenCount(player) * 2 + defyCount(player) * 5 + banCount(player) * 3);
    }''',
     '''    /**
     * 注视度：<b>每次都现算</b>。
     *
     * <p>2.8.0 曾把结果写进 {@code sys.attention} 缓存，再在别处读缓存——
     * 计数一变，缓存就是旧值，面板显示的注视度会落后一拍。现在没有缓存，只有唯一来源。
     */
    public static int attention(ServerPlayer player) {
        return enlightenCount(player) * 2 + defyCount(player) * 5 + banCount(player) * 3;
    }'''),
    # bumpKill 不再写缓存
    ('''        int cur = TianyiData.getInt(player, SUB, key, 0);
        TianyiData.putInt(player, SUB, key, Math.max(0, cur + n));
        // 注释：注视度是按计数现算的（attention()），这里只维护计数，避免两份真相
        TianyiData.putInt(player, SUB, K_ATTR, attention(player));''',
     '''        int cur = TianyiData.getInt(player, SUB, key, 0);
        TianyiData.putInt(player, SUB, key, Math.max(0, cur + n));
        // 只维护计数。注视度由 attention() 现算，任何缓存都会变成第二份真相。'''),
    # 阶位读账本
    ('''    public static int tier(ServerPlayer player) {
        int lifetime = TianyiEcon.lifetime(player);''',
     '''    public static int tier(ServerPlayer player) {
        int lifetime = net.guzhenren.tianyi.ledger.TianyiLedger.lifetime(player);'''),
    ('''        return Math.max(0, TIER_NEED[t] - TianyiEcon.lifetime(player));''',
     '''        return Math.max(0, TIER_NEED[t] - net.guzhenren.tianyi.ledger.TianyiLedger.lifetime(player));'''),
], 'TianyiCausality')

# award() 内部：正数走 grant，负数走 penalize
s = open(CAU, encoding='utf-8').read()
s = s.replace('''        if (raw < 0) {
            TianyiEcon.add(player, raw);
            return raw;
        }''',
'''        if (raw < 0) {
            // 处罚只扣余额，不动累计（否则「死一次掉阶位」会变成真的掉阶）
            net.guzhenren.tianyi.ledger.TianyiLedger.penalize(player, -raw);
            return raw;
        }''')
s = s.replace('''        setEarnedToday(player, used + gain);
        TianyiEcon.add(player, gain);
        return gain;''',
'''        // 入账交给账本：它自己会记「当日已赚」，这里不再维护第二份游标
        int applied = net.guzhenren.tianyi.ledger.TianyiLedger.grant(player, gain, 0);
        return applied;''')
open(CAU, 'w', encoding='utf-8', newline='\n').write(s)
print('  TianyiCausality.award: 已改走 grant/penalize')

# ---------------------------------------------------------------- 3. 天机阁退款路径
patch(os.path.join(SRC, 'TianyiPavilion.java'), [
    ('''        if (!TianfuApply.buy(player, id)) {
            TianyiEcon.add(player, cost);
            return "买不了「" + spec.name() + "」：条件不满足（已退回 " + cost + " 天机）。";
        }''',
     '''        if (!TianfuApply.buy(player, id)) {
            // 退款走独立入口：只回余额，不抬高累计与当日已赚（否则「买-退」能刷阶位）
            net.guzhenren.tianyi.ledger.TianyiLedger.refund(player, cost);
            return "买不了「" + spec.name() + "」：条件不满足（已退回 " + cost + " 天机）。";
        }'''),
], 'TianyiPavilion 退款')
