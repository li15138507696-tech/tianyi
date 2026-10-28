"""从指定的 10.20 本体包里找出真正含代码的 jar（它是个 jarjar 壳）。

结论先行：`guzhenrn蛊真人模组本体10.20.jar` 的 modId 是 `guzhenren_library`，
只有 1386 个条目，**不含** `network/GuzhenrenModVariables` —— 它是壳，真正代码在嵌套 jar 里。
本脚本逐个探测嵌套 jar，找出含 `GuzhenrenModVariables` 的那一个。
"""
import io
import re
import zipfile

SHELL = r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar'
TARGET = 'net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class'

shell = zipfile.ZipFile(SHELL)
nested = [n for n in shell.namelist() if n.startswith('META-INF/jarjar/') and n.endswith('.jar')]
print('嵌套 jar:', len(nested))

found = []
for n in nested:
    data = shell.read(n)
    try:
        inner = zipfile.ZipFile(io.BytesIO(data))
    except Exception as e:
        print('  跳过', n, e)
        continue
    names = inner.namelist()
    has = TARGET in names
    meta = ''
    for m in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
        if m in names:
            text = inner.read(m).decode('utf-8', 'replace')
            mid = re.search(r'modId\s*=\s*"([^"]+)"', text)
            ver = re.search(r'^\s*version\s*=\s*"([^"]+)"', text, re.M)
            meta = f"modId={mid.group(1) if mid else '?'} version={ver.group(1) if ver else '?'}"
            break
    print(f'  {n.split("/")[-1]:<52} entries={len(names):<6} {"★含PlayerVariables" if has else ""} {meta}')
    if has:
        found.append((n, len(names)))

print()
if found:
    print('应作为编译依赖的是:', found[0][0])
else:
    print('!! 嵌套 jar 里也没有 —— 这个 10.20 包里不含代码本体')
