import zipfile, re

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# Find every class that WRITES LianGu (boolean) -> who sets true / false
print('=== classes writing LianGu:Z ===')
for n, d in sorted(blob.items()):
    if b'PlayerVariables.LianGu:Z' not in d:
        continue
    # crude: look for putfield of LianGu followed/preceded by iconst_0/iconst_1
    if b'putfield' not in d:
        continue
    seg = d
    sets = []
    for m in re.finditer(rb'Field net/guzhenren/network/GuzhenrenModVariables\$PlayerVariables\.LianGu:Z', seg):
        ctx = seg[max(0, m.start()-40):m.start()]
        if b'putfield' in ctx:
            sets.append('W')
        elif b'getfield' in ctx:
            sets.append('R')
    if 'W' in sets:
        print(f'  WRITE {n}')
