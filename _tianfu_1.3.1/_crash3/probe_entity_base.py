import zipfile, re

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

# 找 zhoudao_shijian_baifenbi 的默认值（PlayerVariables 构造/init）
d = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')

# 反编译不容易，先看 superclass of a few entity classes
samples = [
    'net/guzhenren/entity/AibieliEntity.class',
    'net/guzhenren/entity/BaiGuCheLunEntity.class',
    'net/guzhenren/entity/BaDaoZhan1Entity.class',
]
for s in samples:
    data = z.read(s)
    # parse constant pool for super class name
    # simpler: search for common base strings
    print(s, '->', b'GuchongBase' in data, b'Projectile' in data, b'Monster' in data, b'PathfinderMob' in data)
