# -*- coding: utf-8 -*-
import zipfile
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\qiao_classes.txt'
z = zipfile.ZipFile(JAR)
names = z.namelist()
lines = []
for kw in ['Uikongqiao', 'Kongqiao', 'Kaiqiao', 'ShiQiao', 'Shiqiao']:
    hits = [n for n in names if kw.lower() in n.lower() and n.endswith('.class')]
    lines.append('## ' + kw + ' (%d)' % len(hits))
    lines += ['  ' + h for h in hits[:20]]
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
