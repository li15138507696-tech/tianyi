import zipfile, os, struct, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '20261020-2036_六合一大合集_v1.0.0.jar')

z = zipfile.ZipFile(p)
data = z.read('net/guzhenren/ui/bridge/TianfuBridge.class')
z.close()


def parse_cp(b):
    pos = 8
    n = struct.unpack('>H', b[pos:pos+2])[0]; pos += 2
    cp = {}
    i = 1
    while i < n:
        tag = b[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack('>H', b[pos:pos+2])[0]; pos += 2
            cp[i] = ('Utf8', b[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack('>H', b[pos:pos+2])[0]); pos += 2
        elif tag == 15:
            cp[i] = (tag, b[pos], struct.unpack('>H', b[pos+1:pos+3])[0]); pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            cp[i] = (tag, struct.unpack('>HH', b[pos:pos+4])); pos += 4
        elif tag in (5, 6):
            cp[i] = (tag, None); pos += 8; i += 1
        else:
            raise SystemExit(f'tag {tag}')
        i += 1
    return cp


cp = parse_cp(data)

# resolve Class refs and NameAndType for Methodref/Fieldref
def utf(i):
    v = cp.get(i)
    return v[1] if v and v[0] == 'Utf8' else None

def cls(i):
    v = cp.get(i)
    if v and v[0] == 7:
        return utf(v[1])
    return None

refs = collections.defaultdict(set)
for i, v in cp.items():
    if v[0] in (9, 10, 11):
        ci, nti = v[1]
        owner = cls(ci)
        nat = cp.get(nti)
        if not owner or not nat:
            continue
        name = utf(nat[1][0])
        desc = utf(nat[1][1])
        if owner and owner.startswith('net/guzhenren/tianfu'):
            refs[owner].add(f'{name}{desc}')

print('TianfuBridge references these classes/members of MY mod:')
for owner in sorted(refs):
    print(f'\n  {owner}')
    for m in sorted(refs[owner]):
        print(f'      {m}')
