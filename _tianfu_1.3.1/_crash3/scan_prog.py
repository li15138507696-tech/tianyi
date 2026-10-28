import zipfile, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# A class that ADDS to LianGuJinDu (getfield then dadd then putfield) AND clamps with Math.min(…,100)
# Look for classes containing LianGuJinDu and the exact double 100.0 constant AND Math.min
D100 = struct.pack('>d', 100.0)
prog = []
for n, d in blob.items():
    if b'LianGuJinDu' not in d:
        continue
    if D100 in d and b'java/lang/Math' in d:
        # candidate progress-advance with clamp
        prog.append(n)

print('candidates with clamp+100:', len(prog))
for p in sorted(prog)[:15]:
    print('  ', p)
