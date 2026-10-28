import zipfile, re

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# Who calls LianGu4Procedure? (is it a tick handler or a click handler?)
for target in [b'LianGu4Procedure', b'LianGu2Procedure', b'LianGu3Procedure']:
    refs = [n for n, d in blob.items()
            if target in d and not n.encode().endswith(target + b'.class')]
    print(f'{target.decode()}: referenced by {len(refs)}')
    for r in sorted(refs)[:10]:
        print('   ', r)
    print()
