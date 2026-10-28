import zipfile, struct, re

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

D50 = struct.pack('>d', 50.0)
D100 = struct.pack('>d', 100.0)

# candidate: references LianGuJinDu, has 50.0, but NOT 100.0 -> likely the "mid-progress" logic
cand = [n for n, d in blob.items() if b'LianGuJinDu' in d and D50 in d and D100 not in d]
print(f'LianGuJinDu + 50.0, no 100.0: {len(cand)}')
for c in sorted(cand):
    print('  ', c)

print()
# also: names containing LianZhi / JiaGuCai / TouLiao / LianGu
names = [n for n in blob if re.search(r'LianZhi|JiaGuCai|TouLiao|TianJia', n)]
print(f'name-matched: {len(names)}')
for n in sorted(names)[:40]:
    print('  ', n, '50=' + ('Y' if D50 in blob[n] else 'N'), '100=' + ('Y' if D100 in blob[n] else 'N'))
