"""核查开窍收尾 procedure 在 10.4 里的真实签名与行为。

tianfu 依赖两点：
  A. TianfuOpening.onUseHopeGu 调 UikongqiaoquedingProcedure.execute(LevelAccessor, Entity)
  B. UikongqiaoquedingProcedureMixin 往 "execute" 的 TAIL 注入 (LevelAccessor, Entity) 回调

只要 10.4 里 execute 的重载变了（比如只剩 5 参版，或 2 参版变成空壳），
A 会编译通过但运行无效，B 会因回调签名不匹配被 require=0 静默跳过。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'

for FQCN in ('net.guzhenren.procedures.UikongqiaoquedingProcedure',
             'net.guzhenren.procedures.UikongqiaoguanbiProcedure'):
    print('=' * 70)
    print(FQCN)
    print('=' * 70)
    r = subprocess.run([JAVAP, '-p', '-classpath', JAR, FQCN],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        print('  !! 类不存在')
        print('  ', (r.stderr or '')[:300])
        print()
        continue
    for ln in (r.stdout or '').splitlines():
        s = ln.strip()
        if re.match(r'^(public|protected|private|static)\b', s) and '(' in s:
            print('  ', s)
        elif s.startswith('public class') or s.startswith('class '):
            print('  ', s)
    print()

# 谁调用 guanbi？谁调用 queding？
print('=' * 70)
print('10.4 里调用 UikongqiaoguanbiProcedure 的类')
print('=' * 70)
import zipfile
z = zipfile.ZipFile(JAR)
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    try:
        d = z.read(n)
    except Exception:
        continue
    if b'UikongqiaoguanbiProcedure' in d:
        hits.append(n)
for h in sorted(hits):
    print('  ', h)
