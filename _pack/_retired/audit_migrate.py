"""迁移契约静态审计（P1）。

行为级迁移测试需要真实 Player/PersistentData，只能进游戏验；这里锁住「代码形状」，
防止后续改动把迁移逻辑改坏：

1. TianfuData 的三个根常量必须是 tianyi / open / tianfu；
2. 登录路径必须调用 migrateLegacy；
3. 克隆路径必须走 copyFrom（不能再用 put(ROOT, …) 直接写顶层）；
4. 源码里不得出现「把 ROOT 直接写到 PersistentData 顶层」的写法。
"""
import os, re, sys

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java')
OPEN = os.path.join(SRC, 'net', 'guzhenren', 'tianyi', 'open')

problems = []


def read(p):
    return open(p, encoding='utf-8').read()


data = read(os.path.join(OPEN, 'TianfuData.java'))
for const, want in (('PARENT', 'tianyi'), ('ROOT', 'open'), ('LEGACY_ROOT', 'tianfu')):
    m = re.search(r'String\s+' + const + r'\s*=\s*"([^"]+)"', data)
    if not m:
        problems.append(f'TianfuData 缺常量 {const}')
    elif m.group(1) != want:
        problems.append(f'TianfuData.{const} = "{m.group(1)}"，应为 "{want}"')

if 'migrateLegacy' not in data or 'copyFrom' not in data:
    problems.append('TianfuData 缺 migrateLegacy / copyFrom')

opening = read(os.path.join(OPEN, 'TianfuOpening.java'))
if 'TianfuData.migrateLegacy(player)' not in opening:
    problems.append('TianfuOpening.onLogin 没有调用 TianfuData.migrateLegacy')
if 'TianfuData.copyFrom(' not in opening:
    problems.append('TianfuOpening.onClone 没有走 TianfuData.copyFrom')

# 反例：任何地方把 ROOT 直接塞进 PersistentData 顶层
for base, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith('.java'):
            continue
        p = os.path.join(base, fn)
        t = read(p)
        if re.search(r'getPersistentData\(\)\s*\.\s*(put|remove)\(\s*(ROOT|TianfuData\.ROOT|"tianfu")', t):
            problems.append(f'{os.path.relpath(p, ROOT)} 直接把 ROOT 写到了 PersistentData 顶层')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('迁移契约 OK（常量 / 登录迁移 / 克隆复制 / 无顶层误写）')
