import zipfile, os, struct, re

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


def utf(cp, i):
    v = cp.get(i)
    return v[1] if v and v[0] == 'Utf8' else None


# Effect class files: find those whose constructor calls MobEffectCategory
targets = [
    ('兼修', 'guzhenren_兼修附属0.7.jar', ['IcePathBoost', 'BingFengLiuNian']),
    ('魂道', '魂道附属10.19.jar', ['HundaoZengfu', 'Zengfu']),
    ('六合一', '20261020-2036_六合一大合集_v1.0.0.jar', ['YizhiQiankun', 'Yundao']),
]

for label, fn, pats in targets:
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    print('=' * 66)
    print(label)
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        base = n.rsplit('/', 1)[-1]
        if not any(pt.lower() in base.lower() for pt in pats):
            continue
        data = z.read(n)
        if b'MobEffectCategory' not in data:
            continue
        cp = parse_cp(data)
        # find strings that look like effect id, and category constants
        strs = [v[1] for v in cp.values() if v[0] == 'Utf8']
        cats = [s for s in strs if 'BENEFICIAL' in s or 'HARMFUL' in s or 'NEUTRAL' in s]
        ids = [s for s in strs if re.fullmatch(r'[a-z0-9_]{3,40}', s) and '_' in s]
        # decode bytecode for getstatic MobEffectCategory.X
        hits = set()
        for m in re.finditer(rb'\xb2..', data):
            pass
        print(f'  {n}')
        print(f'      ids: {ids[:8]}')
        print(f'      category strings: {cats[:4]}')
        # raw search for HARMFUL token presence
        if b'HARMFUL' in data:
            print('      >>> contains HARMFUL reference')
        if b'BENEFICIAL' in data:
            print('      >>> contains BENEFICIAL reference')
    z.close()
    print()
