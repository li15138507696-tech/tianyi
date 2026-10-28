# -*- coding: utf-8 -*-
"""查候选实体 renderer 引用的 geo / 贴图 / 动画基准名。"""
import os, re, subprocess, zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\entity_renderers.txt'
z = zipfile.ZipFile(JAR)
names = z.namelist()

lines = []
for cls in ['Yaotengushiti', 'Baiguiyexingshiti', 'Jingyueshiti', 'Xueyingliuhuatexiao']:
    cand = [n for n in names if n.endswith('Renderer.class') and cls.lower() in n.lower()]
    cand += [n for n in names if n.endswith('Model.class') and cls.lower() in n.lower()]
    lines.append('## ' + cls + ' -> ' + str(cand))
    for c in cand:
        fq = c[:-6].replace('/', '.')
        r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fq],
                           capture_output=True, text=True, encoding='utf-8', errors='replace')
        for ln in r.stdout.splitlines():
            if '// String' in ln and ('.geo' in ln or 'textures' in ln or 'animation' in ln.lower()):
                lines.append('   ' + ln.strip())
    lines.append('')

open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
