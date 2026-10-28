"""看清 9.8 的 queding 末尾那段 scoreboard.set(0) 到底改的是哪个 objective。

如果是 kaiqiao，那么 9.8 上「用希望蛊开窍」会被本体收尾立刻归零，
tianfu 的 hasOpenedQiao 永远 false → K 永远被门禁拦。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = ROOT + r'\_crash3\basever\guzhenren-9.8.jar'
FQCN = 'net.guzhenren.procedures.UikongqiaoquedingProcedure'

r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, FQCN],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
lines = (r.stdout or '').splitlines()
start = next(i for i, ln in enumerate(lines) if ' execute(' in ln)

# 收集带偏移的指令行
ins = []
for ln in lines[start:]:
    m = re.match(r'\s+(\d+):\s+(.*)$', ln)
    if m:
        ins.append((int(m.group(1)), m.group(2).rstrip()))

def show(lo, hi, label):
    print('--- %s (偏移 %d..%d) ---' % (label, lo, hi))
    for off, txt in ins:
        if lo <= off <= hi:
            print('  %4d: %s' % (off, txt))
    print()

# 1) 末尾 scoreboard 段：往前找最近的 ldc 字符串
show(1380, 1449, '9.8 queding 末尾 scoreboard 段')

# 2) 中间那个 return（1138）附近
show(1100, 1150, '9.8 queding 偏移 1138 的 return 附近')

# 3) 汇总：整个 execute 里出现的所有 ldc 字符串常量
print('--- execute 里出现的全部字符串常量 ---')
seen = []
for off, txt in ins:
    m = re.search(r'//\s*String\s+(.+)$', txt)
    if m:
        s = m.group(1)
        if s not in seen:
            seen.append(s)
            print('  %4d: %r' % (off, s))
