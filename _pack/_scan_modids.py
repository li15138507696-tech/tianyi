"""列出 mods 目录里每个 jar 声明的 modId（只读，找残留的三个旧附属）。"""
import glob
import os
import re
import zipfile

MODS = r'D:\game\mcmod\蛊2\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
WANT = {'daotu', 'tianfu', 'shenshang', 'tianfu_addon', 'daotu_addon'}

rows = []
for p in sorted(glob.glob(os.path.join(MODS, '*.jar'))):
    try:
        z = zipfile.ZipFile(p)
    except Exception:
        continue
    toml = None
    for cand in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml', 'mcmod.info'):
        if cand in z.namelist():
            try:
                toml = z.read(cand).decode('utf-8-sig', 'replace')
            except Exception:
                toml = None
            break
    if not toml:
        continue
    ids = re.findall(r'^\s*modId\s*=\s*"([^"]+)"', toml, re.M)
    if not ids:
        ids = re.findall(r'"modid"\s*:\s*"([^"]+)"', toml, re.I)
    rows.append((os.path.basename(p), ids))

print('=== 命中三个旧附属的 jar ===')
found = []
for name, ids in rows:
    hit = [i for i in ids if i.lower() in WANT]
    if hit:
        found.append((name, hit))
        print(' ', name, '->', hit)
if not found:
    print('  （无）')
print(f'\n共扫描 {len(rows)} 个带元数据的 jar')
for name, ids in rows:
    if any(i.lower() in ('tianyi', 'guzhenren') for i in ids):
        print(' 相关:', name, '->', ids)
