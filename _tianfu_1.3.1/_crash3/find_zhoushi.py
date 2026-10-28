import zipfile, io, os, json, sys

JARS = {
    'base': r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar',
    'addon': r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods\guzhenren_兼修附属0.7.jar',
    'hundao': r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods\魂道附属10.19.jar',
    'newguui': r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods\20261020-2036_六合一大合集_v1.0.0.jar',
}

KEYS = ['宙蚀', '宙烛', '蚀', '烛']

for tag, path in JARS.items():
    if not os.path.exists(path):
        print(f'== {tag}: MISSING {path}')
        continue
    z = zipfile.ZipFile(path)
    print(f'== {tag} ==')
    for n in z.namelist():
        if not n.endswith('.json'):
            continue
        if 'lang' not in n.lower():
            continue
        try:
            txt = z.read(n).decode('utf-8', 'ignore')
        except Exception:
            continue
        for k in KEYS:
            if k in txt:
                print(f'  [{k}] {n}')
                break
