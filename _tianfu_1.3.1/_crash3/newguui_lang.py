import zipfile, os, json

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '20261020-2036_六合一大合集_v1.0.0.jar')
z = zipfile.ZipFile(p)

for ns in ['newguui', 'yundao', 'shenshang', 'gzrzhoudao']:
    key = f'assets/{ns}/lang/zh_cn.json'
    if key not in z.namelist():
        print(ns, 'no lang')
        continue
    d = json.loads(z.read(key).decode('utf-8-sig'))
    print('=' * 60)
    print(ns, 'keys', len(d))
    items = {k: v for k, v in d.items() if k.startswith('item.') and 'description' not in k}
    effs = {k: v for k, v in d.items() if k.startswith('effect.')}
    print(f'-- items ({len(items)}) --')
    for k, v in list(items.items())[:25]:
        print('   ', v)
    print(f'-- effects ({len(effs)}) --')
    for k, v in list(effs.items())[:20]:
        print('   ', v)
    print()
z.close()
