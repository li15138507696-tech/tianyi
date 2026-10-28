"""Inspect base mod GUI that displays shili/shenfen, and confirm nobody writes them."""
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)

# who writes shili / shenfen
writers = {'shili': [], 'shenfen': []}
for n in z.namelist():
    if not n.startswith('net/guzhenren/') or not n.endswith('.class'):
        continue
    b = z.read(n)
    for f in writers:
        if f.encode() in b and b'putString' in b:
            writers[f].append(n)
for f, v in writers.items():
    print(f'{f} writers ({len(v)}):')
    for n in v:
        print('   ', n)

print()
print('=== lang keys for u_ishuxingdaohen ===')
import json
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
for k, v in lang.items():
    if 'ishuxingdaohen' in k:
        print('  ', k, '=', v)
