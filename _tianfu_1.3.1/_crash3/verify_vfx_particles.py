"""核对 TianfuVfxEntity 新实现引用的粒子类型是否真实存在于本体。

我重写时按实体名推测了几个粒子常量名（如 XUEYINGLIUHUATEXIAO / KUANGFENGLIZI），
这些必须逐个核对——猜错就编译不过，或者更糟：编译过了但运行时解析不到粒子。

方法：从本体 jar 的 GuzhenrenModParticleTypes.class 用 javap 列出全部字段。
"""
import subprocess
import re
import os

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'
TARGET = 'net.guzhenren.init.GuzhenrenModParticleTypes'

r = subprocess.run([JAVAP, '-p', '-classpath', JAR, TARGET],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
txt = r.stdout or ''
fields = set(re.findall(r'\b([A-Z][A-Z0-9_]+)\s*;', txt))

print(f'本体共 {len(fields)} 个粒子常量')
print()

# 新代码里引用的粒子
USED = {
    'SWORD_SHADOW': ['TianfuParticleTypes.JIANQI（我方注册）'],
    'FLYING_SWORD': ['TianfuParticleTypes.JIANQI', 'DUJINLIZI'],
    'TORNADO': ['KUANGFENGLIZI', 'ParticleTypes.CLOUD（原版）'],
    'QUAKE': ['QIDAOLIZILIQI', 'FEISHA'],
    'VINE': ['QITENGJIANYI', 'QINGFENLUNGULIZI'],
    'GHOST': ['TianfuParticleTypes.HUNHUO（我方注册）', 'GUIYAN_2'],
    'MOON': ['XINGXIU', 'XINGXIU_2'],
    'BLOOD_FLOWER': ['XUEYINGLIUHUATEXIAO'],
}

print('=== 新实现引用的本体粒子核对 ===')
missing = []
for shape, ps in USED.items():
    for p in ps:
        if '我方注册' in p or '原版' in p:
            print(f'  [--] {shape:14} {p}   （非本体，跳过）')
            continue
        if p in fields:
            print(f'  [OK] {shape:14} {p}')
        else:
            print(f'  [MISSING] {shape:14} {p}')
            missing.append((shape, p))

print()
if missing:
    print('!! 缺失的粒子常量:', missing)
    print()
    print('=== 本体里名字相近的候选 ===')
    for shape, p in missing:
        # 用前 4 个字母模糊找
        key = p[:4]
        cand = sorted(f for f in fields if key in f)
        print(f'  {p} -> {cand[:12]}')
else:
    print('全部存在')
