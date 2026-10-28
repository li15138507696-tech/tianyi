"""2.9.1：`audit_insight` 的入口检查从「指瑕/演算」改为「推演」。

旧版要求面板与命令里出现 `improvise`/`simulate`（指瑕/演算）。
四页重做后，这两项被合并进「推演」页（`divine`），服务端入口是 `TianyiFate.divinate`。
只读纪律（不许写本体、不许猜 NBT、不许改实体）保持不变。
"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', '_pack', 'audit_insight.py')
s = open(P, encoding='utf-8').read()

old_panel = """if 'improvise' not in panel or 'simulate' not in panel:
    problems.append('面板没有指瑕/演算入口')"""
new_panel = """if 'divine' not in panel:
    problems.append('面板没有推演入口（四页界面里的「推演」页）')"""

old_cmd = """if 'TianyiInsight.improvise' not in cmds or 'TianyiInsight.simulate' not in cmds:
    problems.append('命令没有指瑕/演算入口')"""
new_cmd = """if 'TianyiFate.divinate' not in cmds:
    problems.append('命令没有推演入口（/tianyi divine）')"""

n = 0
for a, b in ((old_panel, new_panel), (old_cmd, new_cmd)):
    if a in s:
        s = s.replace(a, b)
        n += 1
    else:
        print('  ! 未匹配:', a.splitlines()[0])

open(P, 'w', encoding='utf-8', newline='\n').write(s)
print(f'  audit_insight: {n}/2 处入口检查已改为推演')
