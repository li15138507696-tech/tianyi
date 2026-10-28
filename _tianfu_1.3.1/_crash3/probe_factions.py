"""List lore faction names + contribution sources in the base mod, to ground a new addon's design."""
import json
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

# NPC names in dialogue keys often reveal factions
pat = re.compile(r'^gui\.guzhenren\.renwuduihuajiemian[^.]*\.label_duihua\d+$')
names = set()
for k, v in lang.items():
    if pat.match(k):
        m = re.match(r'^([^：:]{1,8})[：:]', v)
        if m:
            names.add(m.group(1))
print('NPC speakers (%d):' % len(names))
print('  ', '、'.join(sorted(names)[:60]))

print()
print('=== 城 / 楼 / 门 / 派 / 宗 地名 ===')
places = set()
for v in lang.values():
    for m in re.finditer(r'([\u4e00-\u9fff]{2,4}(?:城|楼|门|派|宗|山|谷|寨|坊))', v):
        places.add(m.group(1))
print('  ', '、'.join(sorted(places)[:80]))

print()
print('=== 贡献 / 声望 相关 key ===')
for k, v in sorted(lang.items()):
    if any(w in v for w in ('贡献', '声望', '人气')):
        print('  ', k, '=', v[:50])
