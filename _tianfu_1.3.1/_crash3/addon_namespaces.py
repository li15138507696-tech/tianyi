import zipfile, os, struct, re, json

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'


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


checks = [
    ('六合一', '20261020-2036_六合一大合集_v1.0.0.jar',
     'net/guzhenren/ui/yundao/YundaoMobEffects.class'),
    ('魂道', '魂道附属10.19.jar',
     'net/hundao/addon/registry/ModMobEffects.class'),
    ('兼修', 'guzhenren_兼修附属0.7.jar',
     'cn/blockforge/generated/minecraftjsontoml16x/IceContent.class'),
]

for label, fn, cls in checks:
    z = zipfile.ZipFile(os.path.join(dst, fn))
    cp = parse_cp(z.read(cls))
    # find MODID-like strings (namespaces) - print all short lowercase strings
    ns = sorted({v[1] for v in cp.values()
                 if v[0] == 'Utf8' and re.fullmatch(r'[a-z][a-z0-9_]{1,24}', v[1])
                 and '_' not in v[1]})
    print(f'{label} ({cls})')
    print('   namespace candidates:', ns)
    # also print mods.toml modId
    for n in z.namelist():
        if n.endswith('mods.toml'):
            txt = z.read(n).decode('utf-8', 'replace')
            m = re.search(r'^\s*modId\s*=\s*"([^"]+)"', txt, re.M)
            if m:
                print('   mods.toml modId:', m.group(1))
            break
    z.close()
    print()
