# -*- coding: utf-8 -*-
"""检查候选实体的 tick / 出生 procedure 是否含伤害性调用。"""
import os, re, subprocess

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = os.path.join(ROOT, r'_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = os.path.join(ROOT, r'_crash3\proc_safety.txt')

procs = [
 'YaotengushitiShiTiYouXiKeGengXinShiProcedure',
 'BingXueHuiKanTeXiaoDangShiTiGengXinKeShiProcedure',
 'JingyueshitiZaiShiTiKeGengXinShiProcedure',
 'XueyingliuhuatexiaoShiTiYouXiKeGengXinShiProcedure',
 'BaiguiyexingshitiShiTiChuShiShengChengShiProcedure',
 'JingyueshitiShiTiChuShiShengChengShiProcedure',
 'DaoGuangJianYingShiTiChuShiShengChengShiProcedure',
]
danger = ['hurt', 'setHealth', 'teleportTo', 'addEffect', 'explode',
          'damageSources', 'push(', 'setSecondsOnFire', 'removeEffect',
          'setDeltaMovement']

lines = []
for p in procs:
    fq = 'net.guzhenren.procedures.' + p
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout
    hits = []
    for d in danger:
        for ln in txt.splitlines():
            if d in ln and 'invoke' in ln:
                hits.append(ln.strip())
    lines.append('### %s  (exit %d)' % (p, r.returncode))
    lines.append('  danger: ' + ('\n           '.join(sorted(set(hits))) if hits else 'none'))
    # 粒度：调用了哪些 execute 外的自定义方法？跳过
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
print('\n'.join(lines)[:4000])
