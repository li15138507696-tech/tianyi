"""M1 前置：统计删除 open/path/shop 后，哪些文件还在引用它们。"""
import os
import re

SRC = r'D:\daima\mcmod\天意\src\main\java\net\guzhenren\tianyi'
DEL = ('open', 'path', 'shop')

refs = {}
for sub in DEL:
    d = os.path.join(SRC, sub)
    n = sum(1 for b, _, fs in os.walk(d) for f in fs if f.endswith('.java'))
    lines = sum(len(open(os.path.join(b, f), encoding='utf-8').read().splitlines())
                for b, _, fs in os.walk(d) for f in fs if f.endswith('.java'))
    print(f'删除目录 {sub}/: {n} 文件 / {lines} 行')

print('\n仍引用它们的文件：')
for b, _, fs in os.walk(SRC):
    rel_dir = os.path.relpath(b, SRC).replace('\\', '/')
    if rel_dir.split('/')[0] in DEL:
        continue
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(b, f)
        text = open(p, encoding='utf-8').read()
        hits = set()
        for sub in DEL:
            if re.search(rf'\bnet\.guzhenren\.tianyi\.{sub}\b', text):
                hits.add(f'import {sub}.*')
            if re.search(rf'\b(Tianfu|Daotu|ShenShang|Shop|TianyiTalentShop|TianyiPrice)[A-Za-z]*\b', text):
                hits.add(f'{sub}-type')
        if hits:
            refs[os.path.relpath(p, SRC).replace('\\', '/')] = sorted(hits)

for k, v in sorted(refs.items()):
    print(f'  {k:<46} {", ".join(v)}')
print(f'\n共 {len(refs)} 个文件需要处理')
