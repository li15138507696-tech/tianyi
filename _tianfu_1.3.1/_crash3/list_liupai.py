import zipfile, os, struct, io

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
z = zipfile.ZipFile(os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar'))
inner = z.read('META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar')
z.close()
zi = zipfile.ZipFile(io.BytesIO(inner))
d = zi.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
zi.close()

pos = 8
n = struct.unpack('>H', d[pos:pos+2])[0]; pos += 2
strs = []
i = 1
while i < n:
    tag = d[pos]; pos += 1
    if tag == 1:
        ln = struct.unpack('>H', d[pos:pos+2])[0]; pos += 2
        strs.append(d[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
    elif tag in (7, 8, 16, 19, 20):
        pos += 2
    elif tag == 15:
        pos += 3
    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
        pos += 4
    elif tag in (5, 6):
        pos += 8; i += 1
    else:
        break
    i += 1

lp = sorted({s[7:] for s in strs if s.startswith('liupai_')})
dh = sorted({s[7:] for s in strs if s.startswith('daohen_')})
print(f'liupai_ ({len(lp)}):')
for x in lp:
    print('   ', x)
print()
print(f'daohen_ ({len(dh)}):')
for x in dh:
    print('   ', x)
