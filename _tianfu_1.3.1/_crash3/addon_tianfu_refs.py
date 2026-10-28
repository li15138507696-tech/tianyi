import zipfile, os, struct, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
addons = {
    '兼修': 'guzhenren_兼修附属0.7.jar',
    '魂道': '魂道附属10.19.jar',
    '蛊虫使用记录': '蛊虫使用记录-1.0.1.jar',
    '流派熟练度查看': '蛊真人流派熟练度查看.jar',
    '六合一大合集': '20261020-2036_六合一大合集_v1.0.0.jar',
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


def refs_into_tianfu(data):
    cp = parse_cp(data)
    if not cp:
        return []
    out = []
    for i, v in cp.items():
        if v[0] in (9, 10, 11):
            ci, nti = v[1]
            cv = cp.get(ci)
            if not cv or cv[0] != 7:
                continue
            owner = utf(cp, cv[1])
            if not owner or not owner.startswith('net/guzhenren/tianfu'):
                continue
            nat = cp.get(nti)
            if not nat:
                continue
            name = utf(cp, nat[1][0])
            desc = utf(cp, nat[1][1])
            out.append(f'{owner}.{name}{desc}')
    return out


for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    hits = collections.defaultdict(set)
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        for r in refs_into_tianfu(z.read(n)):
            hits[n].add(r)
    z.close()
    print(f'===== {label}')
    if not hits:
        print('  (no reference into net.guzhenren.tianfu)')
    for n in sorted(hits):
        print(f'  {n}')
        for r in sorted(hits[n]):
            print(f'      {r}')
    print()
