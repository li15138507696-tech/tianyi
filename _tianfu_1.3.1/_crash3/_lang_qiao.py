# -*- coding: utf-8 -*-
import json, zipfile
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\lang_qiao.txt'
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
lines = []
for k, v in lang.items():
    if isinstance(v, str) and ('开窍' in v or '凡人' in v or '空窍' in v or '资质' in v):
        lines.append('%s = %s' % (k, v))
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
