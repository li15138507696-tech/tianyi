# -*- coding: utf-8 -*-
import zipfile
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\shiqiao_users.txt'
z = zipfile.ZipFile(JAR)
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    data = z.read(n)
    if b'shiqiao' in data:
        hits.append((n, data.count(b'shiqiao')))
lines = ['%d classes reference shiqiao' % len(hits)]
for n, c in sorted(hits):
    lines.append('%s  x%d' % (n, c))
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
