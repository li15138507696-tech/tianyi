"""全面排查：8 个装饰实体（及其调用的所有 procedure）里，哪些会造成伤害。

对每个实体类，扫描其全部方法里 invokestatic 的 *Procedure.execute，
再对每个 procedure 反编译，找 Entity.hurt / LivingEntity.hurt / doHurtTarget 等。
打印伤害值与 DamageSource 类型，并判断接收者是 this 还是别的实体。
"""
import subprocess
import re
import os

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'

ENTITIES = {
    'Duochongjianyingshiti': 25,
    'Jianfeng': 26,
    'Dalongjuanfeng': 31,
    'Dizhentexiao': 17,
    'Yaotengushiti': 30,
    'Baiguiyexingshiti': 24,
    'Jingyueshiti': 40,
    'Xueyingliuhuatexiao': 53,
}

_cache = {}


def javap(fqcn):
    if fqcn in _cache:
        return _cache[fqcn]
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fqcn],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    _cache[fqcn] = txt
    return txt


HURT = re.compile(r'//\s*(?:Interface)?Method\s+(?:[\w/$]+\.)?'
                  r'(hurt|doHurtTarget|performRangedAttack|mobAttack):')


def find_procs(txt):
    return sorted(set(re.findall(r'([A-Za-z0-9_]+Procedure)\.execute', txt)))


def damage_in(txt):
    """返回 [(方法名, 伤害常量, DamageType, 接收者aload)]"""
    lines = txt.splitlines()
    out = []
    for i, ln in enumerate(lines):
        if not HURT.search(ln):
            continue
        # 找方法名
        meth = '?'
        for k in range(i, -1, -1):
            s = lines[k].strip()
            if re.match(r'^(public|protected|private)\b', s) and '(' in s:
                meth = s.split('(')[0].split()[-1]
                break
        # 找伤害常量（往后 6 条内的 ldc float / fconst）
        dmg = '?'
        dtype = '?'
        for k in range(max(0, i - 12), min(len(lines), i + 2)):
            m = re.search(r'//\s*float\s+([0-9.]+)f?', lines[k])
            if m:
                dmg = m.group(1)
            m2 = re.search(r'DamageTypes\.([A-Z_]+)', lines[k])
            if m2:
                dtype = m2.group(1)
        # 接收者：往前找 aload
        recv = '?'
        for k in range(i - 1, max(-1, i - 8), -1):
            m = re.match(r'^\s+\d+:\s+(aload(?:_\d+|\s+\d+))\s*(.*)', lines[k])
            if m:
                recv = m.group(1) + ' ' + m.group(2).strip()
                break
        out.append((meth, dmg, dtype, recv))
    return out


print('=' * 76)
print('装饰实体伤害排查（体质 → 实体 → 其调用的 procedure → 伤害）')
print('=' * 76)

dangerous = []
for ent, tizhi in ENTITIES.items():
    fq = 'net.guzhenren.entity.' + ent + 'Entity'
    txt = javap(fq)
    procs = find_procs(txt)
    print()
    print(f'【{ent}】 体质 {tizhi}')
    print(f'  调用 procedure: {procs if procs else "（无）"}')

    # 实体自身类里的伤害
    self_dmg = damage_in(txt)
    if self_dmg:
        for meth, dmg, dtype, recv in self_dmg:
            print(f'  !! 类内伤害  [{meth}] dmg={dmg} type={dtype} recv={recv}')

    for p in procs:
        ptxt = javap('net.guzhenren.procedures.' + p)
        ds = damage_in(ptxt)
        if ds:
            for meth, dmg, dtype, recv in ds:
                print(f'  !! 伤害 @ {p}')
                print(f'       dmg={dmg}  DamageType={dtype}  接收者={recv}  [{meth}]')
            dangerous.append((ent, tizhi, p, ds))
        else:
            print(f'     {p}: 无伤害')

print()
print('=' * 76)
print('结论：会造成伤害的装饰实体')
print('=' * 76)
for ent, tizhi, p, ds in dangerous:
    print(f'  体质 {tizhi} {ent}  ->  {p}  {ds}')
if not dangerous:
    print('  （无）')
