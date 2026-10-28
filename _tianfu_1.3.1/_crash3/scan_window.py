import zipfile, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

D50 = struct.pack('>d', 50.0)
D70 = struct.pack('>d', 70.0)
D100 = struct.pack('>d', 100.0)

# How many craft procedures have the [50,70] feeding window?
n_win = 0
n_100 = 0
tot = 0
for n, d in blob.items():
    if b'LianGuJinDu' not in d:
        continue
    tot += 1
    if D100 in d:
        n_100 += 1
    if D50 in d and D70 in d:
        n_win += 1

print(f'classes referencing LianGuJinDu: {tot}')
print(f'  with 100.0 (completion): {n_100}')
print(f'  with BOTH 50.0 and 70.0 (feeding window): {n_win}')

# Does the window procedure also REQUIRE the window (i.e. must be inside to feed)?
# Check one more craft proc to see if 50/70 pair always brackets a feed branch.
print()
sample = [n for n, d in blob.items() if b'LianGuJinDu' in d and D50 in d and D70 in d and D100 in d]
print('sample with all three:', len(sample))
for s in sorted(sample)[:8]:
    print('  ', s)
