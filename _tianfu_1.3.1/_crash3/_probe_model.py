# -*- coding: utf-8 -*-
import os, re, subprocess
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\model_anim.txt'
classes = ['LeiyatexiaoModel', 'DutengushitiModel', 'CitengushitiModel',
           'TudiciModel', 'FengshangshitiModel', 'CanfenggushitiModel',
           'YaotengushitiModel', 'BaiguiyexingshitiModel']
lines = []
for c in classes:
    fq = 'net.guzhenren.entity.model.' + c
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines.append('## %s (exit %d)' % (c, r.returncode))
    for ln in r.stdout.splitlines():
        if '// String' in ln:
            lines.append('   ' + ln.strip())
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
