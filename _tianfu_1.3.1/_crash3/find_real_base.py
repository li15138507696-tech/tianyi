import zipfile, os, re

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'

cands = [
    'guzhenren_兼修附属0.7.jar',
    '魂道附属10.19.jar',
    '蛊虫使用记录-1.0.1.jar',
    '蛊真人流派熟练度查看.jar',
    '20261020-2036_六合一大合集_v1.0.0.jar',
    '自动喂食附属]autogufeed-1.4.0（适配金木水火土雷血版本）.jar',
]

for c in cands:
    p = os.path.join(dst, c)
    if not os.path.exists(p):
        print('MISSING:', c)
        continue
    z = zipfile.ZipFile(p)
    names = z.namelist()
    has_proc = any(n.startswith('net/guzhenren/procedures/') for n in names)
    has_vars = 'net/guzhenren/network/GuzhenrenModVariables.class' in names
    modid = None
    for n in names:
        if n.endswith('mods.toml'):
            txt = z.read(n).decode('utf-8', 'replace')
            m = re.search(r'^\s*modId\s*=\s*"([^"]+)"', txt, re.M)
            if m:
                modid = m.group(1)
                break
    print(f'{c}')
    print(f'    modId={modid}  entries={len(names)}  procedures={has_proc}  Variables={has_vars}')
    z.close()
