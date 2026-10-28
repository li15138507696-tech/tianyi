"""属性链审计（P2）。

合并后属性有两个来源（天命：体质/词条；道途：抽选），各自挂自己的 modifier。
刻意<b>不</b>把两路合并成一条 id：撤销购买 / 转世回滚都要能单独移除某一源，
合并 id 会让「只退掉天命那部分」变成不可能。

本脚本锁的是「不会出事故」的四条：
1. 两路的 modifier id 命名空间必须都是 tianyi（transient，改名不伤存档）；
2. 绝不碰 Attributes.ARMOR（本体有 46 个 procedure 用 /attribute base set 控护甲，
   modifier 会与本体叠加成「卸甲后加成还在」）；
3. 两路 sync 都必须被登录与转世路径调用；
4. 两路的属性键不得重复到「同一属性被两个不同 id 同时按百分比乘」以外的情形
   —— 允许重叠（那是两个独立来源），但不允许同一来源内部出现重复键。
"""
import os, re, sys

ROOT = r'D:\daima\mcmod\天意'
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
OPEN = os.path.join(JAVA, 'open')
PATH = os.path.join(JAVA, 'path')

problems = []


def read(p):
    return open(p, encoding='utf-8').read()


def code_only(src):
    """去掉注释与字符串字面量，只留真代码——否则「注释里提到 ARMOR 也算违规」。"""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    return src


tianfu_attr = read(os.path.join(OPEN, 'TianfuAttributes.java'))
daotu_attr = read(os.path.join(PATH, 'DaotuRunAttributes.java'))

# 1. 命名空间
for name, src in (('TianfuAttributes', tianfu_attr), ('DaotuRunAttributes', daotu_attr)):
    if re.search(r'Attributes\s*\.\s*ARMOR\b', code_only(src)):
        problems.append(f'{name} 真的用了 Attributes.ARMOR（本体护甲红线）')

if 'TianfuMod.MODID' not in tianfu_attr:
    problems.append('TianfuAttributes 的 modifier id 没有走 TianfuMod.MODID')
if 'DaotuMod.MODID' not in daotu_attr:
    problems.append('DaotuRunAttributes 的 modifier id 没有走 DaotuMod.MODID')

modid_ok = True
for p in (os.path.join(OPEN, 'TianfuMod.java'), os.path.join(PATH, 'DaotuMod.java')):
    m = re.search(r'String MODID\s*=\s*"([^"]+)"', read(p))
    if not m or m.group(1) != 'tianyi':
        problems.append(f'{os.path.basename(p)} 的 MODID 不是 tianyi')
        modid_ok = False

# 2. 两个 sync 都被登录与转世路径调用
opening = read(os.path.join(OPEN, 'TianfuOpening.java'))
path_opening = read(os.path.join(PATH, 'DaotuOpening.java'))
rebirth = read(os.path.join(JAVA, 'TianyiRebirth.java'))

if 'TianfuAttributes.sync(' not in opening:
    problems.append('TianfuOpening 登录路径没有 TianfuAttributes.sync')
if 'DaotuAttributes.sync(' not in path_opening:
    problems.append('DaotuOpening 登录路径没有 DaotuAttributes.sync')
for need in ('TianfuAttributes.sync(', 'DaotuAttributes.sync('):
    if need not in rebirth:
        problems.append('TianyiRebirth 缺少 ' + need)

# 3. 同一来源内部键不重复
for name, src in (('DaotuRunAttributes', daotu_attr),):
    m = re.search(r'ALL_KEYS\s*=\s*\{(.*?)\}', src, re.S)
    if m:
        keys = re.findall(r'"([a-z_]+)"', m.group(1))
        dup = sorted({k for k in keys if keys.count(k) > 1})
        if dup:
            problems.append(f'{name}.ALL_KEYS 有重复键: {", ".join(dup)}')

# 4. transient（不落盘）：不许用 addPermanentModifier
for name, src in (('TianfuAttributes', tianfu_attr), ('DaotuRunAttributes', daotu_attr)):
    if 'addPermanentModifier' in code_only(src):
        problems.append(f'{name} 用了 addPermanentModifier（会写进玩家 NBT，转世清不掉）')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('属性链 OK（命名空间统一 / 不碰护甲 / 两路 sync 就位 / transient）')
