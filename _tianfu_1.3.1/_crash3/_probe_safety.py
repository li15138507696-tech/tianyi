# -*- coding: utf-8 -*-
"""候选实体安全性 + 动画长度核查。"""
import json, os, re, subprocess, zipfile

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = os.path.join(ROOT, r'_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = os.path.join(ROOT, r'_crash3\entity_safety.txt')

cands = {
    'YaotengushitiEntity': 'yaoteng.animation.json',
    'BaiguiyexingshitiEntity': 'baigui.animation.json',
    'JingyueshitiEntity': 'jingyueshiti.animation.json',
    'XueyingliuhuatexiaoEntity': 'xueyingliuhua.animation.json',
    'CanyuexueyishitiEntity': 'canyuexueyitexiao.animation.json',
}

lines = []
z = zipfile.ZipFile(JAR)
for cls, animfile in cands.items():
    fq = 'net.guzhenren.entity.' + cls
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout
    risk = []
    for kw in ['Procedure', 'hurt(', 'teleportTo', 'setHealth', 'addEffect',
               'push(', 'damageSources', 'explode', 'setSecondsOnFire']:
        c = txt.count(kw)
        if c:
            risk.append('%s=%d' % (kw, c))
    # 动画长度
    try:
        d = json.loads(z.read('assets/guzhenren/animations/' + animfile).decode('utf-8'))
        anims = d.get('animations', {})
        lens = {k: v.get('animation_length') for k, v in anims.items()}
    except Exception as e:
        lens = {'ERR': str(e)}
    lines.append('### ' + cls + '  (exit %d, %d bytes)' % (r.returncode, len(txt)))
    lines.append('  anim: ' + str(lens))
    lines.append('  risk: ' + (', '.join(risk) if risk else 'none'))
    # 列出方法名，便于看有没有 aiStep/tick 覆写
    ms = re.findall(r'^\s{2}(?:public|protected|private).*?\b(\w+)\(', txt, re.M)
    lines.append('  methods: ' + ', '.join(sorted(set(ms))))
    lines.append('')

open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
print('wrote', OUT)
print('\n'.join(lines)[:3000])
