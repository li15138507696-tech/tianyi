"""Probe the 本命蛊 (BenMingGu) flow — the natural 'choose your path' moment that tianfu does NOT occupy."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]

print('=== classes touching BenMingGu ===')
for n in names:
    if b'BenMingGu' in z.read(n):
        print('  ', n.split('/')[-1])

print()
print('=== advancement cheng_jiu_ben_ming_gu ===')
for k, v in sorted(lang.items()):
    if 'ben_ming_gu' in k.lower() or 'benminggu' in k.lower():
        print('  ', k, '=', v)

print()
print('=== BenMingCommand strings ===')
b = z.read('net/guzhenren/command/BenMingCommand.class')
print('  size', len(b))
