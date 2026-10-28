import zipfile, os, struct

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '20261020-2036_六合一大合集_v1.0.0.jar')
z = zipfile.ZipFile(p)

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
            if tag == 5:
                cp[i] = ('Long', struct.unpack('>q', b[pos:pos+8])[0])
            else:
                cp[i] = ('Double', struct.unpack('>d', b[pos:pos+8])[0])
            pos += 8; i += 1
        else:
            return {}
        i += 1
    return cp

for n in z.namelist():
    if not n.endswith('.class'):
        continue
    if 'LianGu' not in n and 'GuFang' not in n and 'Yundao' not in n and 'mixin' not in n:
        continue
    cp = parse_cp(z.read(n))
    dbl = sorted({round(v[1], 4) for v in cp.values() if v[0] == 'Double'})
    if not dbl:
        continue
    interesting = [d for d in dbl if 40 <= d <= 80 or d in (100.0, 1.0, 0.0, 617.0, 4.0, 30.0)]
    if interesting:
        print(f'{n}')
        print(f'    doubles: {interesting}')
z.close()
