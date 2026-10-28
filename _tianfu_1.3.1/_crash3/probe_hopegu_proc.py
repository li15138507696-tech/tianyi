"""反编译本体希望蛊的开窍 procedure，找 kaiqiao 写入点。

若这里不写 kaiqiao（或写了别的 objective 名），
tianfu 的 hasOpenedQiao 永远 false → K 键永远提示「需以希望蛊开窍」。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'
FQCN = 'net.guzhenren.procedures.YizhuanrendaoxiwangguDangYouJiKongQiShiShiTiDeWeiZhiProcedure'

r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, FQCN],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
txt = r.stdout or ''
out = r'D:\daima\mcmod\蛊真人附属\_crash3\hopegu_proc.txt'
open(out, 'w', encoding='utf-8').write(txt)
print('written', out, len(txt), 'chars')
print()

# 1. 有没有 kaiqiao 字面量
print('=== 含 kaiqiao 的行 ===')
for ln in txt.splitlines():
    if 'kaiqiao' in ln.lower():
        print('  ', ln.strip())
print()

# 2. scoreboard 相关调用
print('=== scoreboard 相关调用 ===')
for ln in txt.splitlines():
    if 'scoreboard' in ln.lower() or 'ScoreAccess' in ln or 'Objective' in ln:
        print('  ', ln.strip())
print()

# 3. 调了哪些 procedure / 关键方法
print('=== 调用的 procedure 与 set 方法 ===')
for ln in txt.splitlines():
    m = re.search(r'//\s*(?:Interface)?Method\s+([\w/$]+\.[\w$]+):', ln)
    if m:
        sig = m.group(1)
        if ('Procedure.execute' in sig or '.set:' in sig
                or 'addObjective' in sig or 'getOrCreate' in sig
                or 'openMenu' in sig or 'displayClientMessage' in sig):
            print('  ', sig)
