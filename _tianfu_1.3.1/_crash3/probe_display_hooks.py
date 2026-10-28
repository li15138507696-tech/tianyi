"""Which base GUIs display shili / shenfen, and what labels do they use."""
import json
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

# which screens reference UiShiliProcedure / UishenfenProcedure
for proc in (b'UiShiliProcedure', b'UishenfenProcedure'):
    hits = [n for n in z.namelist()
            if n.startswith('net/guzhenren/') and n.endswith('.class') and proc in z.read(n)]
    print(proc.decode(), '->', hits)

print()
# labels on u_ishuxingdaohen
for prefix in ('gui.guzhenren.u_ishuxingdaohen.', 'gui.guzhenren.u_ishuhishuxingjiemian.'):
    print('===', prefix)
    for k, v in sorted(lang.items()):
        if k.startswith(prefix) and any(w in v for w in ('势力', '身份', '流派', '境界')):
            print('  ', k, '=', v)

print()
# does tianfu touch shili/shenfen?
TF = r'D:\daima\mcmod\蛊真人附属\build\libs\tianfu-1.2.17.jar'
zt = zipfile.ZipFile(TF)
for field in (b'shili', b'shenfen'):
    hits = [n for n in zt.namelist() if n.endswith('.class') and field in zt.read(n)]
    print('tianfu refs', field.decode(), ':', hits)
