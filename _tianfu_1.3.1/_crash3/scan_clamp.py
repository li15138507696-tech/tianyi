import zipfile, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# Find classes that WRITE LianGuJinDu with a clamp (Math.min / 100.0 guard).
# Look for classes containing LianGuJinDu AND a call to Math.min (java/lang/Math min) 
hits = []
for n, d in blob.items():
    if b'LianGuJinDu' not in d:
        continue
    if b'java/lang/Math' in d and (b'min' in d):
        hits.append(n)
print('LianGuJinDu + Math.min candidates:', len(hits))
for h in sorted(hits)[:40]:
    print('  ', h)
