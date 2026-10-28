import zipfile, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

D50 = struct.pack('>d', 50.0)
D100 = struct.pack('>d', 100.0)

hits = []
for n, d in blob.items():
    if b'LianGuJinDu' not in d:
        continue
    has50 = D50 in d
    has100 = D100 in d
    if has50:
        hits.append((n, has100))

print(f'classes with LianGuJinDu AND constant 50.0: {len(hits)}')
for n, h100 in sorted(hits):
    print(f'  100={"Y" if h100 else "N"}  {n}')
