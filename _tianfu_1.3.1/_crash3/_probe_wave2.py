# -*- coding: utf-8 -*-
"""第二批选型：实体 -> renderer 动画文件（类名去掉 Entity 后缀再匹配）。"""
import os, re, subprocess, zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\entity_wave2.txt'
z = zipfile.ZipFile(JAR)
names = z.namelist()
ents = [n for n in names if n.startswith('net/guzhenren/entity/') and n.endswith('.class') and '$' not in n]

def info(cls):
    short = cls.split('/')[-1][:-6]
    base = short.replace('Entity', '')
    rend = [n for n in names if n.endswith('Renderer.class') and base.lower() in n.lower()]
    if not rend:
        return None, None
    fq = rend[0][:-6].replace('/', '.')
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    m = re.search(r'String (guzhenren:animations/[^"]+)', r.stdout)
    g = re.search(r'String (guzhenren:geo/[^"]+)', r.stdout)
    return (m.group(1) if m else None), (g.group(1) if g else None)

lines = []
for kw in ['zhoudao', 'leiya', 'ying2', 'jianying2', 'bingzhui', 'fengshang',
           'feng', 'xue', 'meng', 'duteng', 'citeng', 'tudici']:
    hits = [n for n in ents if kw in n.lower()]
    if not hits:
        continue
    lines.append('## ' + kw)
    for h in hits[:6]:
        a, g = info(h)
        lines.append('  %-42s anim=%s' % (h.split('/')[-1], a))
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
