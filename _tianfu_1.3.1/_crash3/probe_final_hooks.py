"""Final verification of the three 'free' hooks before designing."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]

print('=== 1. shili/shenfen: any putfield assignment anywhere? ===')
for f in (b'shili', b'shenfen'):
    # putfield of that exact field name
    hits = []
    for n in names:
        b = z.read(n)
        if f in b and b'Field' in b:
            hits.append(n.split('/')[-1])
    print(f.decode(), '->', hits)

print()
print('=== 2. GongXianZhi: writers (gameplay) ===')
gw = [n.split('/')[-1] for n in names if b'GongXianZhi' in z.read(n)]
print(len(gw), 'classes; sample:', gw[:8])

print()
print('=== 3. is GongXianZhi shown in any GUI lang? ===')
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
hit = [k for k, v in lang.items() if '贡献' in v or 'GongXian' in v]
print('lang hits:', hit[:10])

print()
print('=== 4. liupai field count recheck ===')
pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
import re
found = set(re.findall(rb'liupai_[a-z0-9]+|Liupai_[a-z0-9]+', pv))
print(len(found), 'liupai fields in PlayerVariables')
