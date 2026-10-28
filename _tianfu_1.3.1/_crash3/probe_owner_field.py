"""蛊虫实体如何记录发射者？查各类里的 owner/player 字段与方法。"""
import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

sample = 'net/guzhenren/entity/BaDaoZhan1Entity.class'
d = z.read(sample)

# 抽出常量池里的字符串（可能是字段名/方法名）
import re
strs = re.findall(rb'[\x20-\x7e]{4,40}', d)
interesting = [s.decode() for s in strs if any(
    k in s.lower() for k in (b'owner', b'shooter', b'caster', b'player', b'getdata', b'entitydata'))]
print('interesting strings:')
for s in sorted(set(interesting))[:30]:
    print('  ', s)

# 也看方法名
methods = re.findall(rb'get[A-Z]\w+|set[A-Z]\w+', d)
print()
print('getter/setter names:')
for m in sorted(set(methods))[:40]:
    print('  ', m.decode())
