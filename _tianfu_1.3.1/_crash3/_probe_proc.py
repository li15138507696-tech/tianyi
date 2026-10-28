# -*- coding: utf-8 -*-
"""列出候选实体里 Procedure 的真实调用点。"""
import os, re, subprocess

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = os.path.join(ROOT, r'_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = os.path.join(ROOT, r'_crash3\entity_proc_calls.txt')

classes = ['YaotengushitiEntity', 'BaiguiyexingshitiEntity', 'JingyueshitiEntity',
           'XueyingliuhuatexiaoEntity', 'CanyuexueyishitiEntity',
           'DuochongjianyingshitiEntity', 'JianfengEntity',
           'DalongjuanfengEntity', 'DizhentexiaoEntity']

lines = []
for cls in classes:
    fq = 'net.guzhenren.entity.' + cls
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines.append('### ' + cls)
    cur = '?'
    for ln in r.stdout.splitlines():
        m = re.match(r'\s{2}(?:public|protected|private|[\w<>\[\], ]+)\s+(\w+)\(', ln)
        if m and 'invoke' not in ln:
            cur = m.group(1)
        if 'Procedure' in ln:
            lines.append('  [%s] %s' % (cur, ln.strip()))
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
print('\n'.join(lines)[:5000])
