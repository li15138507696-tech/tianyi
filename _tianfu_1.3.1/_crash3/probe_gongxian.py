"""Check: do the attribute screens actually render shili/shenfen? And what is GongXianZhi?"""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== all labels in u_ishuhishuxingjiemian_2 (the screen that shows 势力) ===')
for k, v in sorted(lang.items()):
    if k.startswith('gui.guzhenren.u_ishuhishuxingjiemian_2.label'):
        print('  ', k.split('.')[-1], '=', v)

print()
print('=== GongXianZhi refs ===')
names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]
hits = [n for n in names if b'GongXianZhi' in z.read(n)]
print(len(hits), 'classes')
for n in hits[:25]:
    print('   ', n.split('/')[-1])

print()
print('=== lang for GongXianZhi / 贡献 ===')
for k, v in sorted(lang.items()):
    if '贡献' in v:
        print('  ', k, '=', v)
