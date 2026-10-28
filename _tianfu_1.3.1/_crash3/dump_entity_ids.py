"""导出三个版本 GuzhenrenModEntities 的全部 String 常量（= 实体注册名），并存盘比对。

MCreator 写法：
    ldc "xxx"
    invokestatic register(String, EntityType$Builder)
所以注册名就是类常量池里的 String 字面量（"guzhenren" 是命名空间，要排除）。
"""
import subprocess
import re
import os

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
OUT = os.path.join(ROOT, '_crash3', 'basever')
BASES = {
    '9.8': ROOT + r'\_crash3\basever\guzhenren-9.8.jar',
    '10.4': ROOT + r'\_crash3\basever\guzhenren-10.4.jar',
    '10.20': ROOT + r'\_crash3\basever\guzhenren-10.20.jar',
}

sets = {}
for ver, jar in BASES.items():
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar,
                        'net.guzhenren.init.GuzhenrenModEntities'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()
    strs = {m.group(1) for m in
            (re.search(r'//\s*String\s+(\S+)', ln) for ln in lines) if m}
    strs.discard('guzhenren')
    sets[ver] = strs
    p = os.path.join(OUT, 'entities-%s.txt' % ver)
    open(p, 'w', encoding='utf-8').write('\n'.join(sorted(strs)))
    print('%-6s %4d 个实体注册名  -> %s' % (ver, len(strs), os.path.basename(p)))

print()
for ver, s in sets.items():
    j = sorted(x for x in s if 'jiang' in x.lower())
    print('%s 含 jiang: %s' % (ver, j if j else '（无）'))

print()
print('=== 9.8 有、10.4 没有（共 %d）===' % len(sets['9.8'] - sets['10.4']))
for x in sorted(sets['9.8'] - sets['10.4']):
    print('   ', x)
print()
print('=== 10.4 有、9.8 没有（共 %d）===' % len(sets['10.4'] - sets['9.8']))
for x in sorted(sets['10.4'] - sets['9.8']):
    print('   ', x)
