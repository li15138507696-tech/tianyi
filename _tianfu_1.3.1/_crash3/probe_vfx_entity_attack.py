"""提取本体内层 jar，并检查「特效实体」是否真的会攻击 / 造成伤害。

用户反馈：某些特效（用作体质装饰的本体实体）会像蛊虫一样攻击并造成伤害。
这里反查这 8 个实体的类继承链与攻击相关方法。
"""
import zipfile
import os
import re

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
OUTER = os.path.join(MODS, 'guzhenrn蛊真人模组本体10.4.jar')
INNER = 'META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar'
DEST = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'

if not os.path.exists(DEST):
    with zipfile.ZipFile(OUTER) as z:
        data = z.read(INNER)
    open(DEST, 'wb').write(data)
    print('extracted', len(data), 'bytes ->', DEST)
else:
    print('cached', DEST, os.path.getsize(DEST), 'bytes')

# 用户提到的这 8 个装饰实体
NAMES = [
    'Duochongjianyingshiti', 'Jianfeng', 'Dalongjuanfeng', 'Dizhentexiao',
    'Yaotengushiti', 'Baiguiyexingshiti', 'Jingyueshiti', 'Xueyingliuhuatexiao',
]

with zipfile.ZipFile(DEST) as z:
    names = z.namelist()
    print()
    print('=== 找到的实体类 ===')
    for nm in NAMES:
        hits = [n for n in names
                if n.endswith('/' + nm + 'Entity.class') or n.endswith('/' + nm + '.class')]
        print(f'  {nm:28} {hits if hits else "（未找到）"}')
