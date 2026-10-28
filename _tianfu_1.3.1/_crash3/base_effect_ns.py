import zipfile, os, io, re

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
z = zipfile.ZipFile(os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar'))
inner = z.read('META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar')
z.close()
zi = zipfile.ZipFile(io.BytesIO(inner))

# The registry is created with a namespace string; find it near DeferredRegister.create
data = zi.read('net/guzhenren/init/GuzhenrenModMobEffects.class')

# parse CP
pos = 8
n = int.from_bytes(data[pos:pos+2], 'big'); pos += 2
cp = {}
i = 1
while i < n:
    tag = data[pos]; pos += 1
    if tag == 1:
        ln = int.from_bytes(data[pos:pos+2], 'big'); pos += 2
        cp[i] = ('Utf8', data[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
    elif tag in (7, 8, 16, 19, 20):
        cp[i] = (tag, int.from_bytes(data[pos:pos+2], 'big')); pos += 2
    elif tag == 15:
        cp[i] = (tag, data[pos], int.from_bytes(data[pos+1:pos+3], 'big')); pos += 3
    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
        cp[i] = (tag, (int.from_bytes(data[pos:pos+2], 'big'), int.from_bytes(data[pos+2:pos+4], 'big'))); pos += 4
    elif tag in (5, 6):
        cp[i] = (tag, None); pos += 8; i += 1
    else:
        raise SystemExit('tag %d' % tag)
    i += 1

# find strings that look like namespaces (no underscore, lowercase)
ns = sorted({v[1] for v in cp.values() if v[0] == 'Utf8'
             and re.fullmatch(r'[a-z][a-z0-9_]{1,24}', v[1])})
print('candidate namespace strings in GuzhenrenModMobEffects:')
for s in ns:
    print('   ', s)

# static initializer: find ldc strings just before DeferredRegister.create
print()
print('strings that look like mod id near register create:')
for idx, v in cp.items():
    if v[0] == 'Utf8' and re.fullmatch(r'guzhenren[a-z_]*', v[1]):
        print('   cp', idx, '=', v[1])
zi.close()
