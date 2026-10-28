"""Is 本命蛊 (BenMingGu) a real player-facing 'choose your path' moment? Ground Layer-2 anchor."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== advancement ben_ming_gu ===')
for k, v in sorted(lang.items()):
    if 'ben_ming_gu' in k.lower() or 'benminggu' in k.lower():
        print('  ', k, '=', v)

print()
print('=== items with BenMingGu in name (candidate 本命蛊) ===')
for n in z.namelist():
    b = n.split('/')[-1]
    if b.startswith('BenMing') or 'benming' in b.lower():
        print('  ', b)

print()
print('=== 本命 lang ===')
cnt = 0
for k, v in sorted(lang.items()):
    if '本命' in v and cnt < 15:
        print('  ', k, '=', v[:60])
        cnt += 1
