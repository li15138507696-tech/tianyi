import zipfile, os, struct, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar')
z = zipfile.ZipFile(p)
inner = z.read('META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar')
z.close()

import io
zi = zipfile.ZipFile(io.BytesIO(inner))
classes = [n for n in zi.namelist() if n.endswith('.class')]

FIELDS = {'GuFang', 'LianGu', 'LianGuJinDu', 'lg_ke'}


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


writes = collections.defaultdict(set)
for n in classes:
    data = zi.read(n)
    cp = parse_cp(data)
    if not cp:
        continue
    # map fieldref idx -> name
    def fname(idx):
        v = cp.get(idx)
        if not v or v[0] != 9:
            return None
        nat = cp.get(v[1][1])
        if not nat:
            return None
        return utf(cp, nat[1][0])

    p2 = 0
    while p2 < len(data) - 3:
        op = data[p2]
        if op == 0xB5:
            idx = struct.unpack('>H', data[p2+1:p2+3])[0]
            nm = fname(idx)
            if nm in FIELDS:
                # look back a few bytes for dconst_0 / iconst_0
                prev = data[max(0, p2-3):p2]
                zero = (0x0E in prev) or (0x03 in prev)  # dconst_0 / iconst_0
                writes[nm].add((n, 'ZERO' if zero else 'other'))
            p2 += 3
        else:
            p2 += 1
zi.close()

for f in ['GuFang', 'LianGu']:
    print(f'--- writes to {f} in BASE mod (ZERO = writes 0/false) ---')
    for n, kind in sorted(writes.get(f, [])):
        print(f'   [{kind}] {n}')
    print()
