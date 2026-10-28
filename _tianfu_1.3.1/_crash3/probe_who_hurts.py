"""精确判断：装饰实体是「发起攻击」还是「只是自己被伤害」。

对每个实体，找出 baseTick / aiStep / tick 等运行期方法里，
是否存在「以别的实体为接收者的 hurt 调用」或「Mob.doHurtTarget」。

javap -c 输出中，调用点形如：
    invokevirtual #123  // Method net/minecraft/world/entity/LivingEntity.hurt(...)
接收者由前一条 aload 决定。这里用窗口法：取调用前 12 条指令，
找最近的 aload_N 及其后注释，判断是否是 this 还是别人。
"""
import subprocess
import re
import os

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'

NAMES = [
    'Duochongjianyingshiti', 'Jianfeng', 'Dalongjuanfeng', 'Dizhentexiao',
    'Yaotengushiti', 'Baiguiyexingshiti', 'Jingyueshiti', 'Xueyingliuhuatexiao',
]

CALL = re.compile(r'invoke\w+\s+#\d+.*//\s*(?:Interface)?Method\s+(.+)')
ALOAD = re.compile(r'\baload(_\d+|\s+\d+)?\s*(?://\s*(.+))?')
METHOD_HEAD = re.compile(r'^\s{2}\S.*\(.*\);?\s*$')

ATTACK_SIGS = (
    'hurt(', 'doHurtTarget', 'mobAttack', 'performRangedAttack',
    'dealDamage', 'setLastHurtMob', 'setLastHurtByMob',
)


def analyze(fqcn):
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fqcn],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()

    cur_method = '?'
    findings = []
    for i, ln in enumerate(lines):
        m = METHOD_HEAD.match(ln)
        if m and ('(' in ln) and not ln.strip().startswith('//'):
            cur_method = ln.strip().split('(')[0].split()[-1]

        cm = CALL.search(ln)
        if not cm:
            continue
        sig = cm.group(1)
        if not any(a in sig for a in ATTACK_SIGS):
            continue

        # 往回找最近的 aload 与接收者提示
        recv = '?'
        for j in range(i - 1, max(-1, i - 14), -1):
            am = ALOAD.search(lines[j])
            if am:
                hint = (am.group(2) or '').strip()
                reg = am.group(1) or '0'
                reg = reg.strip()
                recv = f'aload_{reg} {hint}'.strip()
                break
        findings.append((cur_method, sig, recv, ln.strip()))
    return findings


print('=== 装饰实体：攻击类调用点及其接收者 ===')
for nm in NAMES:
    fqcn = 'net.guzhenren.entity.' + nm + 'Entity'
    fs = analyze(fqcn)
    print()
    print(f'--- {nm}  ({len(fs)} 处) ---')
    if not fs:
        print('    （无攻击类调用）')
        continue
    for meth, sig, recv, raw in fs[:12]:
        short = sig.split('//')[-1].strip()
        print(f'    [{meth}] {short}')
        print(f'        接收者: {recv}')
