import zipfile, os, re

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
base = os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar')

z = zipfile.ZipFile(base)
names = z.namelist()
print('entries:', len(names))

# mods.toml
for n in names:
    if n.endswith('mods.toml') or n.endswith('neoforge.mods.toml'):
        txt = z.read(n).decode('utf-8', 'replace')
        print('---', n)
        for line in txt.splitlines():
            if re.search(r'^\s*(modId|version|displayName|displayTest|type)\s*=', line):
                print('   ', line.strip())
        break

# check key classes my mod depends on
keys = [
    'net/guzhenren/procedures/LianGuguiDangGaiGUIDaKaiShiMeiKeFaShengProcedure.class',
    'net/guzhenren/procedures/LianGu2Procedure.class',
    'net/guzhenren/procedures/AnJianGuguFang2Procedure.class',
    'net/guzhenren/procedures/KaiShiLianZhiProcedure.class',
    'net/guzhenren/procedures/UikongqiaoguanbiProcedure.class',
    'net/guzhenren/procedures/UishuxingshijuetiProcedure.class',
    'net/guzhenren/procedures/JingJieBaoCunProcedure.class',
    'net/guzhenren/world/inventory/LianGuguiMenu.class',
    'net/guzhenren/network/GuzhenrenModVariables.class',
    'net/guzhenren/init/GuzhenrenModMobEffects.class',
    'net/guzhenren/init/GuzhenrenModItems.class',
    'net/guzhenren/init/GuzhenrenModGameRules.class',
]
print()
for k in keys:
    print(('  OK   ' if k in names else '  MISS ') + k)
z.close()
