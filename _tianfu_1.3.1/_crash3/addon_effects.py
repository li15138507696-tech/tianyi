import zipfile, os, struct, collections

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


def utf(cp, i):
    v = cp.get(i)
    return v[1] if v and v[0] == 'Utf8' else None


for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    names = z.namelist()

    # find classes that look like effect registration
    eff_classes = [n for n in names if 'ffect' in n and n.endswith('.class')
                   and ('Registry' in n or 'Init' in n or n.count('/') <= 3)]
    print('=' * 66)
    print(label, '| candidate effect classes:', len(eff_classes))

    allstr = set()
    for n in names:
        if not n.endswith('.class'):
            continue
        cp = parse_cp(z.read(n))
        for k, v in cp.items():
            if v[0] == 'Utf8':
                s = v[1]
                # effect registry ids look like bare snake_case in a MobEffect holder
                allstr.add(s)

    # heuristic: collect strings that appear in classes containing 'MobEffect'
    eff_ids = collections.Counter()
    for n in names:
        if not n.endswith('.class'):
            continue
        data = z.read(n)
        if b'MobEffect' not in data:
            continue
        cp = parse_cp(data)
        for k, v in cp.items():
            if v[0] == 'Utf8':
                s = v[1]
                if len(s) < 40 and s and all(c.islower() or c.isdigit() or c == '_' for c in s) and '_' in s:
                    eff_ids[s] += 1
    print('  snake_case strings inside MobEffect-referencing classes:')
    for s, c in eff_ids.most_common(40):
        print(f'     {s}')
    z.close()
    print()
