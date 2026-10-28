import zipfile

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

target = b'AnJianGuguFang2Procedure'
refs = [n for n, d in blob.items() if target in d and not n.endswith('AnJianGuguFang2Procedure.class')]
print('classes referencing AnJianGuguFang2Procedure:', len(refs))
for r in sorted(refs):
    print('  ', r)

print()
# Who references any *GuFang2Procedure / GuFang3Procedure? To find the dispatcher.
import re
disp = {}
for n, d in blob.items():
    hits = re.findall(rb'[A-Za-z0-9]+GuGuFang[23]Procedure', d)
    if hits and not re.search(rb'GuGuFang[23]Procedure', n.encode()):
        disp[n] = len(hits)
print('possible dispatchers (reference many GuFang procedures):')
for n, c in sorted(disp.items(), key=lambda kv: -kv[1])[:12]:
    print(f'   {c:3d}  {n}')
