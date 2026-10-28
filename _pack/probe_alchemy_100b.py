"""精确定位：本体在什么地方用 `100.0` 判定炼蛊/推算完成。

只看含 `LianGuJinDu`（炼蛊进度）或 `tuisuan`（推算）字段的类，
打印 getfield 之后的 6 条指令，并标出比较分支，判断是否为「精确等于」。
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
names = z.namelist()

# 只看 procedures 与 gui 里跟炼蛊/推算有关的类
cands = [n for n in names if n.endswith('.class')
         and ('procedures/' in n or 'gui/' in n)
         and re.search(r'liangu|tuisuan|LianGu|TuiSuan', n, re.I)]
print('候选:', len(cands))

report = []
for c in cands:
    cls = c[:-6].replace('/', '.')
    out = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, cls],
                         capture_output=True, text=True,
                         encoding='utf-8', errors='replace').stdout
    lines = out.splitlines()
    for i, line in enumerate(lines):
        if 'LianGuJinDu' not in line and 'tuisuan_ke' not in line:
            continue
        window = [x.strip() for x in lines[i:i + 7]]
        text = ' | '.join(window)
        if '100.0' in text and re.search(r'dcmpl|dcmpg|ifne|ifeq|iflt|ifge', text):
            report.append((c, text))
            break

if report:
    print(f'\n发现 {len(report)} 处「读进度 → 与 100.0 比较」的判定，摘录 3 处：')
    for c, text in report[:3]:
        print('  ==', c)
        print('     ', text[:300])
    print('\n结论：本体确实存在「进度与 100.0 比较」的完成判定。')
    print('所以本模组的炼道研究只读进度、不写进度，永远不可能越过本体结算阈值。')
else:
    print('\n在上述候选类里没有找到「LianGuJinDu 与 100.0 直接比较」的证据。')
    print('结论：保持未知，继续不写进度、不假设完成阈值。')
