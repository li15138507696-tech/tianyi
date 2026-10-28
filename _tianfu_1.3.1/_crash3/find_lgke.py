import zipfile, re, io

jar = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    data = z.read(n)
    if b'lg_ke' in data:
        hits.append(n)
print('classes referencing lg_ke:', len(hits))
for h in hits:
    print(' ', h)
