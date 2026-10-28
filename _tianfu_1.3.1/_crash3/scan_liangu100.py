import zipfile, re

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
names = [i.filename for i in z.infolist() if i.filename.endswith('.class') and 'procedures' in i.filename]
z.close()

# find classes that reference BOTH LianGuJinDu and a >= 100 style compare is hard;
# instead report classes whose bytecode has LianGuJinDu and also ldc2_w 100.0
hits = []
z = zipfile.ZipFile(jar)
for n in names:
    d = z.read(n)
    if b'LianGuJinDu' in d and (b'100.0' in d or b'\x00\x00\x00\x00\x00\x00\x59\x40' in d):
        hits.append(n)
z.close()
print(f'LianGuJinDu + 100.0 candidates: {len(hits)}')
for h in hits[:40]:
    print('  ', h)
