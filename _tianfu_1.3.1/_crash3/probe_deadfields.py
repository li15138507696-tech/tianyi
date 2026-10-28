"""Verify: shili/shenfen are dead fields (only base attribute GUI reads them) -> free hook for a new addon."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)

# 1. Is shili/shenfen serialized in PlayerVariables NBT?
pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
for f in (b'shili', b'shenfen'):
    print(f.decode(), 'in PlayerVariables:', f in pv)

# 2. Any non-UI writer?
for f in (b'shili', b'shenfen'):
    hits = [n for n in z.namelist()
            if n.startswith('net/guzhenren/') and n.endswith('.class')
            and f in z.read(n) and b'putString' in z.read(n)]
    print(f.decode(), 'putString writers:', hits)

# 3. Bootstrap recipe of the two display procedures
print()
print('=== lang keys of the attribute screens that render 势力/身份 ===')
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
for k, v in sorted(lang.items()):
    if 'u_ishuhishuxingjiemian' in k and ('shi_li' in k or 'shen' in k):
        print('  ', k, '=', v)

# 4. zhuanshu / jingjie thresholds visible in lang
print()
print('=== 转数 / 境界 wording ===')
for k, v in sorted(lang.items()):
    if 'u_ishuhishuxingjiemian' in k and ('jing_jie' in k):
        print('  ', k, '=', v)
