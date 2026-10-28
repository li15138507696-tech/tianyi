"""检查蛊虫实体是否调用 setOwner 记录发射者。"""
import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

ents = [n for n in z.namelist()
        if n.startswith('net/guzhenren/entity/') and n.endswith('.class') and '$' not in n]

with_owner = 0
without = []
for n in ents:
    d = z.read(n)
    if b'AbstractArrow' not in d:
        continue
    if b'setOwner' in d:
        with_owner += 1
    else:
        without.append(n)

print(f'AbstractArrow entities: {with_owner + len(without)}')
print(f'  调用 setOwner: {with_owner}')
print(f'  未调用      : {len(without)}')
for n in without[:8]:
    print(f'      {n}')
