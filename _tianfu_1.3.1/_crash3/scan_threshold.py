import zipfile, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# constant pool stores double 100.0 as 8 bytes big-endian
D100 = struct.pack('>d', 100.0)
D110 = struct.pack('>d', 110.0)
D0   = struct.pack('>d', 0.0)
D1   = struct.pack('>d', 1.0)

hits = [n for n, d in blob.items() if b'LianGuJinDu' in d and (D100 in d or D110 in d)]
print('LianGuJinDu + (100.0 or 110.0) constant:', len(hits))
for h in sorted(hits):
    flags = []
    if D100 in blob[h]: flags.append('100.0')
    if D110 in blob[h]: flags.append('110.0')
    if D0 in blob[h]: flags.append('0.0')
    print(f'  {h}  {flags}')
