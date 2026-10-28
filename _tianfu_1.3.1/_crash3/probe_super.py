import zipfile, struct
from collections import Counter

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)


def parse_class(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None
    pos = 8
    (count,) = struct.unpack_from('>H', data, pos)
    pos += 2
    pool = {}
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            (ln,) = struct.unpack_from('>H', data, pos)
            pos += 2
            pool[i] = data[pos:pos + ln].decode('utf-8', 'replace')
            pos += ln
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        else:
            return None
        i += 1
    pos += 2
    (this_i,) = struct.unpack_from('>H', data, pos)
    pos += 2
    (super_i,) = struct.unpack_from('>H', data, pos)
    return pool.get(this_i), pool.get(super_i)


targets = [n for n in z.namelist()
           if n.startswith('net/guzhenren/entity/') and n.endswith('.class') and '$' not in n]

c = Counter()
for n in targets:
    r = parse_class(z.read(n))
    if r and r[1]:
        c[r[1].split('/')[-1]] += 1

print('super class distribution (top 15):')
for k, v in c.most_common(15):
    print(f'  {v:5d}  {k}')
