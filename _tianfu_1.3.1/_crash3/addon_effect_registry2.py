import zipfile, os, struct, re

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
addons = {
    '兼修': 'guzhenren_兼修附属0.7.jar',
    '魂道': '魂道附属10.19.jar',
    '六合一': '20261020-2036_六合一大合集_v1.0.0.jar',
}


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
            return {}
        i += 1
    return cp


for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    print('=' * 66)
    print(label)
    # any class referencing DeferredRegister<MobEffect> or MobEffect registry
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        data = z.read(n)
        if b'DeferredRegister' in data and b'MobEffect' in data:
            cp = parse_cp(data)
            ids = sorted({v[1] for v in cp.values()
                          if v[0] == 'Utf8' and re.fullmatch(r'[a-z0-9_]{3,40}', v[1])
                          and '_' in v[1]})
            print(f'  {n}')
            for x in ids:
                print(f'       {x}')
    z.close()
    print()
