# -*- coding: utf-8 -*-
"""核对候选装饰实体的注册字段名与动画名。"""
import json, os, re, subprocess, zipfile

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = os.path.join(ROOT, r'_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = os.path.join(ROOT, r'_crash3\entity_fields.txt')

r = subprocess.run([JAVAP, '-p', '-classpath', JAR,
                    'net.guzhenren.init.GuzhenrenModEntities'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
open(OUT, 'w', encoding='utf-8').write(r.stdout + '\n--- stderr ---\n' + r.stderr)
print('javap exit', r.returncode, 'lines', len(r.stdout.splitlines()))

# 候选：关键词 → 体质
cands = ['yaoteng', 'citeng', 'duteng', 'baigui', 'guifu', 'bingxue', 'kuangfeng',
         'jingyue', 'canyue', 'xueyi', 'tudici', 'yaogu', 'gumu', 'shu']
z = zipfile.ZipFile(JAR)
names = z.namelist()
print()
for c in cands:
    hits = [n for n in names if 'animations/' in n and c in n.lower()]
    for h in sorted(hits)[:3]:
        try:
            d = json.loads(z.read(h).decode('utf-8'))
            keys = list(d.get('animations', {}).keys())
        except Exception as e:
            keys = ['ERR ' + str(e)]
        print('%-42s %s' % (os.path.basename(h), keys))
