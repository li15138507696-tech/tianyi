"""「零实体」契约审计（继承 tianfu 1.2.27 血案教训）。

本体那些 GeckoLib 装饰实体的 baseTick 里直接调 *Procedure.execute()，与 AI 无关：
`setNoAi(true)` 只关 Goal 选择，拦不住 baseTick；龙卷风实体会对 7.5 格内**所有**实体
（包括玩家自己）每 tick 造成 600 伤害。所以本模组一律只用粒子，永不生成实体做特效。

本脚本扫描天意系统新增代码（根包 + quest 包），禁止出现：
  EntityType.create / addFreshEntity / GuzhenrenModEntities / setNoAi / animationprocedure
"""
import os, re, sys

ROOT = r'D:\daima\mcmod\天意'
BASE = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

BANNED = [
    r'EntityType\s*\.\s*create',
    r'addFreshEntity',
    r'GuzhenrenModEntities',
    r'setNoAi',
    r'animationprocedure',
]

# 只审天意新增的代码（open/path/shop 是原样搬来的既有模块，另有各自的历史审计）
TARGETS = ['TianyiRebirth.java', 'TianyiNet.java', 'TianyiEvents.java', 'TianyiBoard.java',
           'TianyiLuck.java', 'TianyiSign.java', 'TianyiEcon.java', 'TianyiVoice.java',
           'TianyiData.java', 'TianyiMod.java']
QUEST_DIR = os.path.join(BASE, 'quest')
CLIENT_DIR = os.path.join(BASE, 'client')


def code_only(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    return src


problems = []
files = [os.path.join(BASE, f) for f in TARGETS]
for d in (QUEST_DIR, CLIENT_DIR):
    if os.path.isdir(d):
        files += [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.endswith('.java')]

for p in files:
    if not os.path.isfile(p):
        continue
    src = code_only(open(p, encoding='utf-8').read())
    for pat in BANNED:
        if re.search(pat, src):
            problems.append(f'{os.path.basename(p)} 命中禁用模式: {pat}')

print('扫描文件数:', len(files))
if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('零实体契约 OK（天意系统只用粒子/文本，不生成实体）')
