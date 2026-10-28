"""词条表去重审计（P2）。

锁三件事：
1. 道途词条表<b>不再</b>是手抄副本（没有硬编码的 t("...") 数据行），而是投影 TianfuTalents；
2. 投影里引用的 TianfuTalents.Op 全部真实存在（枚举名拼错在 javac 阶段就会报，这里再防一次字符串化）；
3. 投影产出的 COMBAT 键 ⊆ DaotuCombatEffects 实际读取的键；ATTR 键 ⊆ DaotuRunAttributes.ALL_KEYS。
   否则会出现「抽到了但没效果」的死词条。
"""
import os, re, sys

ROOT = r'D:\daima\mcmod\天意'
PATH = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'path')
OPEN = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'open')


def read(p):
    return open(p, encoding='utf-8').read()


problems = []

talent = read(os.path.join(PATH, 'DaotuTalent.java'))

# 1. 不许再有人肉数据表
hard = re.findall(r'^\s*t\("', talent, re.M)
if hard:
    problems.append(f'DaotuTalent 里仍有 {len(hard)} 条硬编码词条数据（应为运行时投影）')
if 'TianfuTalents.all()' not in talent:
    problems.append('DaotuTalent 没有从 TianfuTalents.all() 投影')

# 2. 引用的 Op 必须存在于枚举
op_src = read(os.path.join(OPEN, 'TianfuTalents.java'))
m = re.search(r'enum Op\s*\{(.*?)\}', op_src, re.S)
if not m:
    problems.append('找不到 TianfuTalents.Op 枚举')
    enum_ops = set()
else:
    enum_ops = set(re.findall(r'\b([A-Z][A-Z0-9_]+)\b', m.group(1)))

used = set(re.findall(r'case ([A-Z][A-Z0-9_]+) ->', talent))
unknown = sorted(used - enum_ops)
if unknown:
    problems.append('投影引用了不存在的 Op: ' + ', '.join(unknown))

# 3. 键集合对齐
combat_src = read(os.path.join(PATH, 'DaotuCombatEffects.java'))
read_keys = set(re.findall(r'\bc_[a-z_]+\b', combat_src))
emitted_combat = set(re.findall(r'combat\("(c_[a-z_]+)"', talent))
dead = sorted(emitted_combat - read_keys)
if dead:
    problems.append('投影产出了没人读取的战斗键: ' + ', '.join(dead))

attr_src = read(os.path.join(PATH, 'DaotuRunAttributes.java'))
m = re.search(r'ALL_KEYS\s*=\s*\{(.*?)\}', attr_src, re.S)
attr_keys = set(re.findall(r'"([a-z_]+)"', m.group(1))) if m else set()
emitted_attr = set(re.findall(r'attr\("([a-z_]+)"', talent))
bad_attr = sorted(emitted_attr - attr_keys)
if bad_attr:
    problems.append('投影产出了 DaotuRunAttributes 不认识的属性键: ' + ', '.join(bad_attr))

print('投影 Op 引用:', len(used), '战斗键:', len(emitted_combat), '属性键:', len(emitted_attr))

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('词条表去重 OK（单一来源 / Op 合法 / 键全对齐）')
