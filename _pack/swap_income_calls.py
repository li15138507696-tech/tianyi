"""2.9.0：把所有「收入」调用点从 add() 改成 grant()，并新增账本审计。

调换点（都是收入，不是退款/处罚）：
- TianyiLuck（摇命奖励 10/40）
- TianyiSign（签到奖励）
- TianyiQuestEngine（谕令/试炼领取）
- TianyiAiQuests（AI 降谕领取）

`TianyiEcon.spend(...)` 保持不动（支出语义没变，内部已委托账本）。
之后任何 `TianyiEcon.add(` 出现在账本之外都会让审计失败——它是旧入口，
新增代码不该再用。
"""
import os

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

SWAP = [
    ('TianyiLuck.java', 'TianyiEcon.add(player, 10);', 'TianyiEcon.grant(player, 10);'),
    ('TianyiLuck.java', 'TianyiEcon.add(player, 40);', 'TianyiEcon.grant(player, 40);'),
    ('TianyiSign.java', 'TianyiEcon.add(player, gain);', 'TianyiEcon.grant(player, gain);'),
    ('TianyiQuestEngine.java', 'TianyiEcon.add(player, q.points());',
     'TianyiEcon.grant(player, q.points());'),
    ('ai/TianyiAiQuests.java', 'TianyiEcon.add(player, q.points());',
     'TianyiEcon.grant(player, q.points());'),
]

for rel, a, b in SWAP:
    p = os.path.join(SRC, rel.replace('/', os.sep))
    s = open(p, encoding='utf-8').read()
    if a in s:
        s = s.replace(a, b)
        open(p, 'w', encoding='utf-8', newline='\n').write(s)
        print(f'  ~ {rel}: {a} → {b}')
    else:
        print(f'  ! {rel}: 未匹配 {a}')
