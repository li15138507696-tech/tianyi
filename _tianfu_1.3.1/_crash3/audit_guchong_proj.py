"""检查本体蛊虫实体是否继承 Projectile / ThrowableProjectile。"""
import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

SUPERS = [
    b'net/minecraft/world/entity/projectile/Projectile',
    b'net/minecraft/world/entity/projectile/ThrowableProjectile',
    b'net/minecraft/world/entity/projectile/AbstractArrow',
    b'net/minecraft/world/entity/projectile/AbstractThrownPotion',
]

ents = [n for n in z.namelist()
        if n.startswith('net/guzhenren/entity/') and n.endswith('.class') and '$' not in n]

counts = {s: [] for s in SUPERS}
for n in ents:
    d = z.read(n)
    for s in SUPERS:
        if s in d:
            counts[s].append(n)

print(f'entity classes: {len(ents)}')
for s, lst in counts.items():
    name = s.decode().split('/')[-1]
    print(f'  {name}: {len(lst)}')
    for n in lst[:6]:
        print(f'      {n}')
