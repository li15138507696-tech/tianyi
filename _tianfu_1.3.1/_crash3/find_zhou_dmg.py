import zipfile, re, io

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

# 找同时引用 ZHOUDAOBUFF 且调用 hurt/damage 的类
target = b'ZHOUDAOBUFF'
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    d = z.read(n)
    if target in d and (b'hurt' in d or b'damage' in d):
        hits.append((n, b'hurt' in d, b'damage' in d))

print('classes: ZHOUDAOBUFF + hurt/damage:', len(hits))
for n, h, dm in hits:
    print(f'  hurt={h} damage={dm}  {n}')
