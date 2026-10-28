"""从 9.8 / 10.4 / 10.20 抽出本体 class 集合并确认彼此不同。

发现：
  - 9.8 是【扁平 jar】（没有 META-INF/jarjar），直接用外层即可
  - 10.4 / 10.20 是 jar-in-jar，本体是 jarjar 里最大的那个
  - 内层文件名都叫 guzhenren-0.1-neoforge-1.21.1.jar，版本只能靠外层文件名标注
"""
import zipfile
import hashlib
import os

OUTDIR = r'D:\daima\mcmod\蛊真人附属\_crash3\basever'
os.makedirs(OUTDIR, exist_ok=True)

VERSIONS = {
    '9.8': r'D:\game\mcmod\蛊1\PCL\.minecraft\versions\蛊真人-升仙\mods\guzhenren蛊真人模组本体9.8.jar',
    '10.4': r'D:\game\mcmod\1\.minecraft\versions\1.21.1-NeoForge_21.1.263\mods\guzhenrn蛊真人模组本体10.4.jar',
    '10.20': r'D:\game\mcmod\蛊2\.minecraft\versions\转生到蛊界就要拿出真本事\mods\guzhenrn蛊真人模组本体10.20.jar',
}

result = {}
for ver, outer in VERSIONS.items():
    if not os.path.exists(outer):
        print('!! 外层不存在:', ver, outer)
        continue
    with zipfile.ZipFile(outer) as z:
        inner = [n for n in z.namelist()
                 if n.startswith('META-INF/jarjar/') and n.endswith('.jar')]
        if inner:
            target = max(inner, key=lambda n: z.getinfo(n).file_size)
            data = z.read(target)
            kind = 'jar-in-jar'
        else:
            data = open(outer, 'rb').read()
            target = '(扁平 jar 本体)'
            kind = 'flat'
    dest = os.path.join(OUTDIR, 'guzhenren-%s.jar' % ver)
    open(dest, 'wb').write(data)
    h = hashlib.sha256(data).hexdigest()
    result[ver] = (dest, len(data), h)
    print('%-6s %-10s %-46s %12d bytes  sha256 %s'
          % (ver, kind, target, len(data), h[:16]))

print()
print('=== 内容是否彼此不同 ===')
seen = {}
for v, (p, sz, h) in result.items():
    if h in seen:
        print('  !! %s 与 %s 内容完全相同' % (v, seen[h]))
    seen[h] = v
print('  不同内容数:', len(seen), '/', len(result))

print()
print('=== 各版本 class 数 ===')
for v, (p, sz, h) in result.items():
    with zipfile.ZipFile(p) as z:
        n = len([x for x in z.namelist() if x.endswith('.class')])
    print('  %-6s %d 个 class' % (v, n))
