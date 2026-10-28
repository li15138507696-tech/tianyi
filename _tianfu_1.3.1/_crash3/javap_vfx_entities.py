"""javap 反编译 8 个装饰实体，抓攻击 / 伤害相关调用。"""
import subprocess
import os
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\vfx_entity_javap.txt'

NAMES = [
    'Duochongjianyingshiti', 'Jianfeng', 'Dalongjuanfeng', 'Dizhentexiao',
    'Yaotengushiti', 'Baiguiyexingshiti', 'Jingyueshiti', 'Xueyingliuhuatexiao',
]

PATTERNS = [
    ('攻击/伤害', re.compile(r'hurt\(|doHurtTarget|mobAttack|performRangedAttack|'
                          r'dealDamage|DamageSource|attack\(|ShiTi.*Procedure|'
                          r'setLastHurt|thrownTo|shoot\(', re.I)),
    ('父类', re.compile(r'extends [\w.$]+')),
]

chunks = []
summary = []
for nm in NAMES:
    fqcn = 'net.guzhenren.entity.' + nm + 'Entity'
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fqcn],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    chunks.append('=' * 78)
    chunks.append(f'### {fqcn}   (javap rc={r.returncode}, {len(txt)} chars)')
    chunks.append('=' * 78)
    chunks.append(txt)

    hits = sorted({m.group(0) for m in PATTERNS[0][1].finditer(txt)})
    summary.append((nm, r.returncode, hits))

open(OUT, 'w', encoding='utf-8').write('\n'.join(chunks))
print('written', OUT, os.path.getsize(OUT), 'bytes')
print()
print('=== 各实体是否含攻击/伤害调用 ===')
for nm, rc, hits in summary:
    mark = 'YES' if hits else 'no '
    print(f'  [{mark}] {nm:28} rc={rc}  {hits[:6]}')
