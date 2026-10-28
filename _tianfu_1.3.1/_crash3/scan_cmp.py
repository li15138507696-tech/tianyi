import zipfile, re

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
names = [i.filename for i in z.infolist() if i.filename.endswith('.class')]
blob = {}
for n in names:
    blob[n] = z.read(n)
z.close()

# Which classes read LianGuJinDu and compare with dcmpl/dcmpg (a threshold check)?
cands = []
for n, d in blob.items():
    if b'LianGuJinDu' not in d:
        continue
    if b'dcmpl' in d or b'dcmpg' in d:
        cands.append(n)
print(f'readers with double compare: {len(cands)}')
for c in cands[:60]:
    print('  ', c)
