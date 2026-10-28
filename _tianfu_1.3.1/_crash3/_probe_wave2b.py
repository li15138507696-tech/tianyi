# -*- coding: utf-8 -*-
import json, os, re, subprocess, zipfile
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\wave2_detail.txt'
z = zipfile.ZipFile(JAR)
lines = []
for ent, anim in [('LeiyatexiaoEntity', 'leiya'), ('DutengushitiEntity', 'duteng')]:
    d = json.loads(z.read('assets/guzhenren/animations/%s.animation.json' % anim).decode('utf-8'))
    lens = {k: v.get('animation_length') for k, v in d.get('animations', {}).items()}
    lines.append('## %s anim=%s length=%s' % (ent, anim, lens))
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, 'net.guzhenren.entity.' + ent],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    for ln in r.stdout.splitlines():
        if 'Procedure' in ln and 'invoke' in ln:
            lines.append('   ' + ln.strip())
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
