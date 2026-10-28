"""Confirm the base attribute screen slots for 势力/身份 and that shili/shenfen default to empty string."""
import json
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== u_ishuhishuxingjiemian (screen 1) all labels ===')
for k, v in sorted(lang.items()):
    if k.startswith('gui.guzhenren.u_ishuhishuxingjiemian.label'):
        print('  ', k.split('.')[-1], '=', v)

print()
print('=== which screens call UiShiliProcedure / UishenfenProcedure ===')
for n in z.namelist():
    if n.endswith('Screen.class'):
        b = z.read(n)
        a = b'UiShiliProcedure' in b
        c = b'UishenfenProcedure' in b
        if a or c:
            print('  ', n.split('/')[-1], 'shili' if a else '', 'shenfen' if c else '')

print()
print('=== PlayerVariables ctor: does it init shili/shenfen to ""? ===')
import re
pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
# ldc of empty string is 0x01 0x00
print('  byte pattern check: contains "shili" field ref and empty-string ldc ->',
      b'shili' in pv, pv.count(b'\x01\x00\x00'))
