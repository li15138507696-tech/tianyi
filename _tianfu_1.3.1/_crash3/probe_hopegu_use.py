"""反编译本体希望蛊 use()，看开窍后到底写了哪些字段、开了什么界面。

目的：用户反馈「用希望蛊后天赋 mod 用不了」。
tianfu 的 K 键门禁 = scoreboard kaiqiao != 0。
若本体 use() 在 10.4 里改成不写 kaiqiao（或改了 objective 名），
tianfu 的 hasOpenedQiao 就永远 false → K 键永远被拦。
"""
import subprocess

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'
FQCN = 'net.guzhenren.item.YizhuanrendaoxiwangguItem'

r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, FQCN],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
txt = r.stdout or ''
out = r'D:\daima\mcmod\蛊真人附属\_crash3\hopegu_use.txt'
open(out, 'w', encoding='utf-8').write(txt)
print('written', out, len(txt), 'chars')

# 只打印 use 方法体
lines = txt.splitlines()
start = None
for i, ln in enumerate(lines):
    if 'InteractionResultHolder' in ln and ' use(' in ln:
        start = i
        break
if start is None:
    print('未找到 use 方法')
else:
    print()
    print('=== use() 方法体 ===')
    for ln in lines[start:]:
        print(ln.rstrip())
        if ln.strip() == '' and 'return' in ''.join(lines[start:start + 3]):
            pass
