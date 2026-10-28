"""查 10.4/10.20 里「僵尸蛊修」相关实体到底叫什么。

恶意标签引用了 jiang_shi_gu_xiu / jiangshiguxiusizhuan，这两个在 10.x 缺失。
若只是改名，就该把标签里的 id 换成新名（或两个都留，兼容 9.8 与 10.x）。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
BASES = {
    '9.8': ROOT + r'\_crash3\basever\guzhenren-9.8.jar',
    '10.4': ROOT + r'\_crash3\basever\guzhenren-10.4.jar',
    '10.20': ROOT + r'\_crash3\basever\guzhenren-10.20.jar',
}


def entity_ids(jar):
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar,
                        'net.guzhenren.init.GuzhenrenModEntities'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()
    strs = set()
    for i, ln in enumerate(lines):
        if 'register' in ln and 'DeferredRegister' in ln:
            for j in range(max(0, i - 3), i):
                m = re.search(r'//\s*String\s+(\S+)', lines[j])
                if m:
                    strs.add(m.group(1))
    return strs


per_ver = {v: entity_ids(p) for v, p in BASES.items()}

for v, s in per_ver.items():
    jiang = sorted(x for x in s if 'jiang' in x.lower() or 'zombie' in x.lower())
    print('=== %s 里含 jiang/zombie 的实体（%d）===' % (v, len(jiang)))
    for x in jiang:
        print('   ', x)
    print()

print('=== 差异：9.8 有、10.4 没有的实体 ===')
only98 = sorted(per_ver['9.8'] - per_ver['10.4'])
print('  共 %d 个' % len(only98))
for x in only98:
    print('   ', x)
print()
print('=== 10.4 有、9.8 没有的实体 ===')
only104 = sorted(per_ver['10.4'] - per_ver['9.8'])
print('  共 %d 个' % len(only104))
for x in only104:
    print('   ', x)
