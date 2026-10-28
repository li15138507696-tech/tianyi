import zipfile, os, struct, io, json

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
z = zipfile.ZipFile(os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar'))
inner = z.read('META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar')
z.close()
zi = zipfile.ZipFile(io.BytesIO(inner))

# base mod zh_cn lang -> effect display names
for key in ['assets/guzhenren/lang/zh_cn.json']:
    if key in zi.namelist():
        d = json.loads(zi.read(key).decode('utf-8-sig'))
        effs = {k: v for k, v in d.items() if k.startswith('effect.guzhenren.')}
        print(f'=== {key} : {len(effs)} effects')
        for k, v in sorted(effs.items()):
            print(f'   {k[20:]:38s} {v}')

# effect registration class
reg = [n for n in zi.namelist() if 'MobEffect' in n and n.endswith('.class') and n.count('/') <= 3]
print()
print('candidate registries:')
for n in reg[:20]:
    print('   ', n)
zi.close()
