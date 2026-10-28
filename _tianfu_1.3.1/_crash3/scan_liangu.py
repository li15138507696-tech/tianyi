import zipfile, sys, re

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
needle = b'LianGuJinDu'
hits = []
z = zipfile.ZipFile(jar)
for info in z.infolist():
    if not info.filename.endswith('.class'):
        continue
    data = z.read(info)
    if needle in data:
        hits.append(info.filename)
z.close()

print(f'classes referencing LianGuJinDu: {len(hits)}')
for h in hits:
    print('  ', h)
