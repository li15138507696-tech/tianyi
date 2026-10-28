"""Extract exact liupai field list + 原著 proper nouns for origins, and check clamps."""
import json
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')

liupai = sorted(set(m.decode() for m in re.findall(rb'liupai_[a-z0-9]+|Liupai_[a-z0-9]+', pv)))
print('=== liupai fields: %d ===' % len(liupai))
for i in range(0, len(liupai), 6):
    print('  ', '  '.join(liupai[i:i + 6]))

daohen = sorted(set(m.decode() for m in re.findall(rb'daohen_[a-z0-9]+|dahen_[a-z0-9]+', pv)))
print()
print('=== daohen/dahen fields: %d ===' % len(daohen))
for i in range(0, len(daohen), 6):
    print('  ', '  '.join(daohen[i:i + 6]))

print()
print('=== 原著 proper nouns (家族/寨/门/宗/谷/派) in lang ===')
names = set()
for v in lang.values():
    for m in re.finditer(r'([\u4e00-\u9fff]{2,3}(?:家族|家寨|寨|准宗|宗门|谷|派))', v):
        names.add(m.group(1))
print('  ', '、'.join(sorted(names)[:60]))
