# -*- coding: utf-8 -*-
import os, re, subprocess
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\wave2_proc.txt'
procs = ['LeiyatexiaoShiTiYouXiKeGengXinShiProcedure', 'CitengushitiShiTiYouXiKeGengXinShiProcedure']
lines = []
for p in procs:
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, 'net.guzhenren.procedures.' + p],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines.append('## ' + p)
    for ln in r.stdout.splitlines():
        if ('invoke' in ln and any(k in ln for k in
            ['hurt', 'setHealth', 'teleportTo', 'addEffect', 'getEntities', 'explode',
             'damageSources', 'setSecondsOnFire', 'getNearestPlayer', 'Player'])):
            lines.append('   ' + ln.strip())
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
