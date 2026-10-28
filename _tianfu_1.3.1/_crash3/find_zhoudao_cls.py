import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    data = z.read(n)
    if b'zhoudaobuff' in data:
        hits.append(n)
print('classes referencing zhoudaobuff:', len(hits))
for h in hits:
    print(' ', h)
