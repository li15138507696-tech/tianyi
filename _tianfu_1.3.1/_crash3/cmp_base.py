import zipfile, os, hashlib, io

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
outer = os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar')
ref = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'

z = zipfile.ZipFile(outer)
inner_name = 'META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar'
data = z.read(inner_name)
z.close()

print('embedded inner jar size:', len(data))
print('embedded sha256:', hashlib.sha256(data).hexdigest())

ref_size = os.path.getsize(ref)
with open(ref, 'rb') as f:
    ref_hash = hashlib.sha256(f.read()).hexdigest()
print('reference jar size  :', ref_size)
print('reference sha256    :', ref_hash)
print()
print('IDENTICAL' if len(data) == ref_size and hashlib.sha256(data).hexdigest() == ref_hash else 'DIFFERENT')

# compare class sets
zi = zipfile.ZipFile(io.BytesIO(data))
zi_names = set(zi.namelist())
zi.close()
zr = zipfile.ZipFile(ref)
zr_names = set(zr.namelist())
zr.close()
print()
print('inner entries:', len(zi_names), ' ref entries:', len(zr_names))
print('only in inner:', len(zi_names - zr_names))
print('only in ref  :', len(zr_names - zi_names))
for n in list(zi_names - zr_names)[:5]:
    print('  inner-only:', n)
for n in list(zr_names - zi_names)[:5]:
    print('  ref-only  :', n)
