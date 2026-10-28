"""核查希望蛊 mixin 的注入目标在 10.4 本体里是否还存在。

tianfu 的 K 键门禁读 scoreboard kaiqiao；kaiqiao 只由
YizhuanrendaoxiwangguItemMixin 拦截 YizhuanrendaoxiwangguItem.use() 时写入。
如果 10.4 里这个类/方法没了（或签名变了），mixin 静默不生效（require=0），
开窍就永远不会把 kaiqiao 置 1 → K 键永远提示「需以希望蛊开窍」。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'
FQCN = 'net.guzhenren.item.YizhuanrendaoxiwangguItem'

r = subprocess.run([JAVAP, '-p', '-classpath', JAR, FQCN],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
txt = r.stdout or ''
print('javap rc =', r.returncode)
if r.returncode != 0:
    print('!! 类不存在：', (r.stderr or '')[:400])
else:
    print('=== 类签名 ===')
    for ln in txt.splitlines():
        s = ln.strip()
        if s.startswith('public class') or s.startswith('class '):
            print(' ', s)
    print()
    print('=== 是否有 use 方法 ===')
    hits = [ln.strip() for ln in txt.splitlines() if ' use(' in ln or ' use (' in ln]
    if hits:
        for h in hits:
            print('  OK  ', h)
    else:
        print('  !! 没有 use 方法')
    print()
    print('=== 全部方法 ===')
    for ln in txt.splitlines():
        s = ln.strip()
        if re.match(r'^(public|protected|private)\b', s) and '(' in s:
            print('  ', s)

# 顺带看父类，判断 use 是否继承自 Item
print()
print('=== 10.4 里所有 *Xiwanggu* / *xiwanggu* 相关类 ===')
import zipfile
z = zipfile.ZipFile(JAR)
cand = [n for n in z.namelist() if 'xiwanggu' in n.lower() and n.endswith('.class')]
for n in sorted(cand):
    print('  ', n)
