"""看一眼 GuzhenrenModEntities.<clinit> 里真实的注册写法，再决定怎么提取注册名。"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = ROOT + r'\_crash3\basever\guzhenren-10.4.jar'

r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR,
                    'net.guzhenren.init.GuzhenrenModEntities'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
lines = (r.stdout or '').splitlines()
print('总行数:', len(lines))
print()

# 找所有含 register 的行
idx = [i for i, ln in enumerate(lines) if 'register' in ln.lower()]
print('含 register 的行数:', len(idx))
print()
print('=== 前 3 处 register 上下文 ===')
for k in idx[:3]:
    print('--- 行 %d ---' % k)
    for ln in lines[max(0, k - 8):k + 4]:
        print('   ', ln.rstrip())
    print()

print('=== 前 40 个 String 常量 ===')
cnt = 0
for ln in lines:
    m = re.search(r'//\s*String\s+(\S+)', ln)
    if m:
        print('   ', m.group(1))
        cnt += 1
        if cnt >= 40:
            break
