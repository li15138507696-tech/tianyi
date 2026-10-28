"""Check currency fields and keybind conflicts before designing the addon."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== base keybinds ===')
for k, v in sorted(lang.items()):
    if k.startswith('key.'):
        print('  ', k, '=', v)

print()
print('=== yuanshishuliang users ===')
names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]
for n in names:
    if b'yuanshishuliang' in z.read(n):
        print('  ', n.split('/')[-1])

print()
print('=== 元石 lang ===')
for k, v in sorted(lang.items()):
    if '元石' in v and len(v) < 30:
        print('  ', k, '=', v)
