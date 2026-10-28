"""在指定本体 jar 里核实「炼蛊完成判定是否用精确 100.0」。

这是上一轮交付里明确标为「未验证」的一条。现在有权威 jar，可以直接看字节码常量，
不用猜：把含 `liangu` 的 class 逐个 javap，找 `100.0` 常量与 `dcmpg/dcmpl` 比较。
"""
import os
import re
import subprocess
import zipfile

JAR = r'D:\daima\mcmod\天意\_pack\libs\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = os.path.join(os.environ.get('JAVA_HOME', ''), 'bin', 'javap.exe')
if not os.path.isfile(JAVAP):
    JAVAP = 'javap'

z = zipfile.ZipFile(JAR)
cands = [n for n in z.namelist()
         if n.endswith('.class') and re.search(r'liangu|LianGu', n, re.I)]
print('候选 class:', len(cands))
for c in cands[:20]:
    print('   ', c)

hits = []
for c in cands:
    cls = c[:-6].replace('/', '.')
    try:
        out = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, cls],
                             capture_output=True, text=True,
                             encoding='utf-8', errors='replace').stdout
    except Exception as e:
        continue
    if '100.0' in out or '100.0d' in out:
        # 取 100.0 附近的比较指令做证据
        ctx = []
        lines = out.splitlines()
        for i, line in enumerate(lines):
            if '100.0' in line:
                ctx.append(' | '.join(x.strip() for x in lines[max(0, i - 2):i + 3]))
        hits.append((c, ctx[:3]))

print()
if hits:
    print('发现使用 100.0 的位置（前 3 处）:')
    for c, ctx in hits[:3]:
        print('  ==', c)
        for line in ctx:
            print('     ', line[:200])
else:
    print('在含 liangu 的类里没有发现 100.0 字面量常量。')
