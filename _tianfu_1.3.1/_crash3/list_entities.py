import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
names = [n for n in z.namelist() if n.startswith('net/guzhenren/entity/') and n.endswith('.class')]
print('entity classes:', len(names))
for n in names[:40]:
    print(' ', n)
