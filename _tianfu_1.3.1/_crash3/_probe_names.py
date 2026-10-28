# -*- coding: utf-8 -*-
import json, zipfile
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\entity_names.txt'
z = zipfile.ZipFile(JAR)
lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))
keys = [k for k in lang if k.startswith('entity.guzhenren.')]
lines = ['entity keys: %d' % len(keys)]
for w in ['yaoteng', 'baigui', 'jingyue', 'xueying', 'tudici', 'canyue',
          'duochong', 'jianfeng', 'longjuan', 'dizhen']:
    hits = [(k, lang[k]) for k in keys if w in k.lower()]
    lines.append('%s -> %s' % (w, hits[:6]))
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
