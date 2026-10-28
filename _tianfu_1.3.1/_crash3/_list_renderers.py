# -*- coding: utf-8 -*-
import zipfile
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\renderers.txt'
z = zipfile.ZipFile(r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar')
names = [n for n in z.namelist() if n.endswith('Renderer.class')]
lines = ['%d renderers' % len(names)]
for kw in ['leiya', 'ying', 'feng', 'bingzhui', 'duteng', 'citeng', 'tudici', 'xueying', 'meng', 'zhoudao', 'jingyue', 'baigui', 'yaoteng']:
    lines.append('## ' + kw)
    lines += ['  ' + n for n in names if kw in n.lower()][:8]
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
