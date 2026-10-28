"""M1：删除 open/ path/ shop/ 三个子系统（116 文件 / 30,950 行）。

只删 Java 源码目录与它们的 mixin 配置；不碰：
- src/main/resources/assets/**（lang/纹理/拼音表，留着无害）
- data/**（标签追加，留着无害）
- 玩家数据键（tianyi/open|path|econ|…）——只停用，不删
- legacy/LegacyItems.java（两个旧物品的空壳注册）

删除前把清单写进 _pack/_deleted_m1.txt，便于事后查证。
"""
import os
import shutil

ROOT = r'D:\daima\mcmod\天意'
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
RES = os.path.join(ROOT, 'src', 'main', 'resources')

DIRS = ['open', 'path', 'shop']
FILES = ['TianyiPrice.java', 'TianyiTalentShop.java']
MIXINS = ['tianyi.open.mixins.json', 'tianyi.shop.mixins.json']

log = []
for d in DIRS:
    p = os.path.join(JAVA, d)
    if not os.path.isdir(p):
        log.append(f'目录不存在（已删？）: {d}')
        continue
    n = sum(1 for b, _, fs in os.walk(p) for f in fs if f.endswith('.java'))
    lines = sum(len(open(os.path.join(b, f), encoding='utf-8').read().splitlines())
                for b, _, fs in os.walk(p) for f in fs if f.endswith('.java'))
    shutil.rmtree(p)
    log.append(f'删除目录 {d}/: {n} 文件 / {lines} 行')

for f in FILES:
    p = os.path.join(JAVA, f)
    if os.path.isfile(p):
        lines = len(open(p, encoding='utf-8').read().splitlines())
        os.remove(p)
        log.append(f'删除文件 {f}: {lines} 行')
    else:
        log.append(f'文件不存在（已删？）: {f}')

for m in MIXINS:
    p = os.path.join(RES, m)
    if os.path.isfile(p):
        os.remove(p)
        log.append(f'删除 mixin 配置 {m}')
    else:
        log.append(f'mixin 配置不存在: {m}')

out = os.path.join(ROOT, '_pack', '_deleted_m1.txt')
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write('M1 删除清单\n' + '=' * 40 + '\n' + '\n'.join(log) + '\n')

for line in log:
    print(line)
print(f'\n清单已写入 {out}')
