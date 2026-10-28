import zipfile, os, struct, subprocess

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '20261020-2036_六合一大合集_v1.0.0.jar')
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

r = subprocess.run([javap, '-c', '-p', '-classpath', p,
                    'net.guzhenren.ui.bridge.TianfuBridge'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')

lines = r.stdout.splitlines()
# locate methods of interest and dump their body
want = ['confirmFinal', 'chooseDifficulty', 'buy(', 'revoke(', 'present()']
cur = None
buf = []
for ln in lines:
    stripped = ln.strip()
    if ln and not ln.startswith(' ') and not ln.startswith('\t') and '(' in stripped:
        cur = stripped
    if cur and any(w in cur for w in want):
        buf.append(ln)

text = '\n'.join(buf)
# print only lines mentioning tianfu calls
for ln in buf:
    s = ln.strip()
    if 'net/guzhenren/tianfu' in s or 'Method' in s and 'tianfu' in s:
        print(s)
print()
print('---- raw order of invokestatic into my mod ----')
for ln in buf:
    s = ln.strip()
    if 'invokestatic' in s and 'tianfu' in s:
        print(' ', s)
