import zipfile

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

tgt = b'LianGuguiDangGaiGUIDaKaiShiMeiKeFaSheng1Procedure'
refs = [n for n, d in blob.items() if tgt in d and not n.encode().endswith(tgt + b'.class')]
print(f'{tgt.decode()} referenced by {len(refs)}:')
for r in sorted(refs):
    print('   ', r)
