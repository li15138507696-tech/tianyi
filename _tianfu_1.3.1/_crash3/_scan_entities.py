# -*- coding: utf-8 -*-
"""列出本体可复用的装饰实体 + 每个实体的动画名（从 animation.json 反查）。"""
import json, os, re, zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\entity_scan.txt'

z = zipfile.ZipFile(JAR)
names = z.namelist()

# 1) 动画文件 → 动画名
anim = {}
for n in names:
    if n.startswith('assets/guzhenren/animations/') and n.endswith('.animation.json'):
        try:
            data = json.loads(z.read(n).decode('utf-8'))
            anim[os.path.basename(n)] = list(data.get('animations', {}).keys())
        except Exception as e:
            anim[os.path.basename(n)] = ['<parse error: %s>' % e]

# 2) 实体类里带 Model/Renderer 的（粗略：类名含 shiti / texiao）
ent = [n for n in names if n.startswith('net/guzhenren/entity/') and n.endswith('.class')
       and '$' not in n]
ent = [n for n in ent if ('shiti' in n.lower() or 'texiao' in n.lower())]
   
lines = []
lines.append('== 动画文件 (%d) ==' % len(anim))
for k in sorted(anim):
    lines.append('%-46s %s' % (k, anim[k]))

lines.append('')
lines.append('== 实体类含 shiti/texiao (%d) ==' % len(ent))
for n in sorted(ent):
    lines.append(n)

open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
print('wrote', OUT, len(lines))
