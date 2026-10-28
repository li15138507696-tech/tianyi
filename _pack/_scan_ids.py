"""临时检查：全库字面量 ResourceLocation 是否跨文件重复。

三合一之后多个模块共用 `tianyi` 命名空间，任何「同名 id 注册两次」都会在注册期炸。
payload 已有专门审计（audit_payloads.py），这里再扫一遍**所有** `fromNamespaceAndPath("a","b")`
字面量，看还有没有别的撞车（attachment / SavedData / config spec / 声音 / 粒子 等）。
"""
import collections
import os
import re

SRC = r'D:\daima\mcmod\天意\src\main\java'
lit = re.compile(r'fromNamespaceAndPath\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')

hits = collections.defaultdict(set)
for base, _, fs in os.walk(SRC):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(base, f)
        text = open(p, encoding='utf-8').read()
        for m in lit.finditer(text):
            hits[f'{m.group(1)}:{m.group(2)}'].add(os.path.relpath(p, SRC).replace('\\', '/'))

print('字面量 id 总数:', len(hits))
dups = {k: v for k, v in hits.items() if len(v) > 1}
print('跨文件重复:', len(dups))
for k, v in sorted(dups.items()):
    print('  ', k, '<-', sorted(v))
