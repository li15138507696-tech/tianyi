"""精确判断装饰实体是否真的会攻击。

只认真正的攻击签名：
  - LivingEntity.hurt:(Lnet/minecraft/world/damagesource/DamageSource;F)Z
  - Mob.doHurtTarget / performRangedAttack / mobAttack
排除误报：getHurtSound / entity.generic.hurt 字符串 / causeFallDamage。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'

NAMES = [
    'Duochongjianyingshiti', 'Jianfeng', 'Dalongjuanfeng', 'Dizhentexiao',
    'Yaotengushiti', 'Baiguiyexingshiti', 'Jingyueshiti', 'Xueyingliuhuatexiao',
]

# 真正的攻击调用（方法名后面必须跟冒号，避免匹配 getHurtSound）
ATTACK = re.compile(
    r'//\s*(?:Interface)?Method\s+'
    r'(?:[\w/$]+\.)?'
    r'(hurt|doHurtTarget|performRangedAttack|mobAttack|setLastHurtMob|setLastHurtByMob)'
    r':')

INVOKE = re.compile(r'^\s+\d+:\s+invoke\w+\s+#\d+')
ALOAD = re.compile(r'^\s+\d+:\s+aload(_\d+|\s+\d+)')


def analyze(fqcn):
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fqcn],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()

    method = '?'
    out = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        # 方法签名行：以 public/protected/private 开头且含 (
        if re.match(r'^(public|protected|private|static|final|abstract)\b', s) and '(' in s:
            head = s.split('(')[0]
            method = head.split()[-1] if head.split() else '?'
            continue

        if not INVOKE.match(ln):
            continue
        if not ATTACK.search(ln):
            continue

        # 找接收者：往回看最近的 aload
        recv = '?'
        for j in range(i - 1, max(-1, i - 10), -1):
            if ALOAD.match(lines[j]):
                recv = lines[j].strip().split(':', 1)[-1].strip()
                break
        sig = ATTACK.search(ln).group(1)
        out.append((method, sig, recv))
    return out


print('=== 真实攻击调用（排除 getHurtSound 等误报）===')
total = 0
for nm in NAMES:
    fqcn = 'net.guzhenren.entity.' + nm + 'Entity'
    fs = analyze(fqcn)
    total += len(fs)
    flag = 'ATTACK' if fs else 'safe  '
    print()
    print(f'[{flag}] {nm}: {len(fs)} 处')
    for meth, sig, recv in fs[:8]:
        print(f'      {meth} -> {sig}   接收者 {recv}')
print()
print('攻击调用总数:', total)
