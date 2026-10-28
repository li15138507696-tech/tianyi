import zipfile

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# getfield ref to LianGuJinDu means READ. Constant pool stores "LianGuJinDu" and fieldref
# distinguishing read vs write needs full parse; approximate: report classes that do NOT
# contain a putfield to it. We just list all and count putfield occurrences by looking for
# the name in the same constant-pool neighbourhood is unreliable -> instead list classes
# containing both 'LianGuJinDu' and 'd2i'/'ddiv' etc.
readers = [n for n, d in blob.items() if b'LianGuJinDu' in d]
print('total refs:', len(readers))
sub = [n for n in readers if 'menu' in n.lower() or 'gui' in n.lower() or 'screen' in n.lower() or 'client' in n.lower()]
print('menu/gui-ish:', len(sub))
for s in sub[:40]:
    print('  ', s)
