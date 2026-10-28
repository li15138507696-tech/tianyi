import zipfile, os, json

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'

for label, fn, ns in [
    ('兼修', 'guzhenren_兼修附属0.7.jar', 'guzhenren_addon'),
    ('魂道', '魂道附属10.19.jar', 'hundao_addon'),
    ('六合一', '20261020-2036_六合一大合集_v1.0.0.jar', 'yundao'),
]:
    z = zipfile.ZipFile(os.path.join(dst, fn))
    key = f'assets/{ns}/lang/zh_cn.json'
    if key not in z.namelist():
        print(label, 'no lang at', key)
        z.close()
        continue
    d = json.loads(z.read(key).decode('utf-8-sig'))
    effs = {k: v for k, v in d.items() if k.startswith(f'effect.{ns}.')}
    print('=' * 60)
    print(label, f'({ns})  effects: {len(effs)}')
    for k, v in sorted(effs.items()):
        print(f'   {k[len("effect."+ns+"."):]:30s} {v}')
    z.close()
    print()
