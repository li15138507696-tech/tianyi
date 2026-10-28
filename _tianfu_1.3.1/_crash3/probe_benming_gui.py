"""Confirm 本命蛊 setting GUI + its procedure chain, and check shili/shenfen screen label keys."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== uilianhuaguchong labels ===')
for k, v in sorted(lang.items()):
    if k.startswith('gui.guzhenren.uilianhuaguchong'):
        print('  ', k.split('.')[-1], '=', v[:70])

print()
print('=== GUI classes containing 本命/流派选择 ===')
for n in z.namelist():
    if n.endswith('Screen.class'):
        b = z.read(n)
        if b'BenMing' in b or b'benming' in b.lower():
            print('  ', n.split('/')[-1])

print()
print('=== attributes screen 2/3 labels (shili/shenfen slots) ===')
for pre in ('gui.guzhenren.u_ishuhishuxingjiemian_1.', 'gui.guzhenren.u_ishuhishuxingjiemian_2.'):
    print(' --', pre)
    for k, v in sorted(lang.items()):
        if k.startswith(pre) and 'label' in k:
            print('    ', k[len(pre):], '=', v[:40])
