"""兼容性审计：tianfu 的编译产物引用了哪些本体类，是否都存在于玩家实际运行的本体 jar。

为什么需要这个：编译通过 ≠ 运行不缺类。
只要有一个被引用的本体类在玩家那版里不存在，对应代码一被加载就
NoClassDefFoundError；如果它正好在客户端 tick 路径上，
表现就是「按 K 完全没反应」，而且不崩游戏（异常被事件总线吞掉只记日志）。

做法：javap -c -p 遍历 tianfu 每个 class，抽出常量池里
  // class net/guzhenren/...
  // Method net/guzhenren/...:...
  // Field net/guzhenren/...
再对本体 jar 的 class 清单核对类是否存在。
"""
import zipfile
import subprocess
import re
import os

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = os.path.join(ROOT, 'build', 'libs', 'tianfu-1.2.36.jar')
BASE = os.path.join(ROOT, '_crash3', 'guzhenren_inner.jar')

zb = zipfile.ZipFile(BASE)
base_classes = {n[:-6].replace('/', '.') for n in zb.namelist() if n.endswith('.class')}
print('本体 class 数:', len(base_classes))

zt = zipfile.ZipFile(JAR)
tf_classes = [n[:-6].replace('/', '.') for n in zt.namelist()
              if n.endswith('.class') and n.startswith('net/guzhenren/tianfu/')]
print('tianfu class 数:', len(tf_classes))
print()

# 类路径：本体 jar + tianfu jar + 原版/NeoForge 由 javap 自己忽略即可
cp = os.pathsep.join([JAR, BASE])

refs_class = set()
refs_member = {}   # (owner, name) -> set of usage

for fq in tf_classes:
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', cp, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    for m in re.finditer(r'//\s*class\s+(net/guzhenren/[\w/$]+)', txt):
        refs_class.add(m.group(1).replace('/', '.'))
    for m in re.finditer(r'//\s*(?:Interface)?Method\s+(net/guzhenren/[\w/$]+)\.([\w$<>]+):', txt):
        owner = m.group(1).replace('/', '.')
        refs_member.setdefault((owner, m.group(2)), set()).add(fq)
        refs_class.add(owner)
    for m in re.finditer(r'//\s*Field\s+(net/guzhenren/[\w/$]+)\.([\w$]+):', txt):
        owner = m.group(1).replace('/', '.')
        refs_member.setdefault((owner, m.group(2)), set()).add(fq)
        refs_class.add(owner)

print('引用到的本体类:', len(refs_class))
# 排除 tianfu 自己的类（包名同样是 net.guzhenren.*）
refs_class = {c for c in refs_class if not c.startswith('net.guzhenren.tianfu.')}
refs_member = {(o, n): u for (o, n), u in refs_member.items()
               if not o.startswith('net.guzhenren.tianfu.')}
print('其中本体类（排除 tianfu 自身）:', len(refs_class))
missing = sorted(c for c in refs_class if c not in base_classes)
print()
if missing:
    print('!! 本体 jar 里不存在的类（%d）:' % len(missing))
    for c in missing:
        print('   ', c)
else:
    print('所有被引用的本体类都存在 OK')

# 方法/字段存在性：只查本体里的类（用 javap 拉签名）
print()
print('=== 方法/字段存在性（抽样核对本体类）===')
owners = sorted({o for (o, _) in refs_member if o in base_classes})
bad_member = []
for o in owners:
    r = subprocess.run([JAVAP, '-p', '-classpath', BASE, o],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    sig_txt = r.stdout or ''
    if r.returncode != 0:
        continue
    names = set(re.findall(r'\b([\w$]+)\s*[;(=]', sig_txt))
    names |= set(re.findall(r'\b([\w$]+)\s*;', sig_txt))
    for (ow, nm), users in refs_member.items():
        if ow != o:
            continue
        if nm not in names:
            bad_member.append((o, nm, sorted(users)[:3]))

if bad_member:
    print('!! 可能缺失的成员（%d）:' % len(bad_member))
    for o, nm, users in bad_member[:40]:
        print('   %s.%s   <- %s' % (o, nm, users))
else:
    print('被引用成员全部存在 OK')
