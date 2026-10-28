import zipfile, struct

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)


def parse_class(data):
    """Return (this_name, super_name, interfaces) from a class file."""
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
        if tag == 1:  # Utf8
            (ln,) = struct.unpack_from('>H', data, pos)
            pos += 2
            pool[i] = data[pos:pos + ln].decode('utf-8', 'replace')
            pos += ln
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            pos += 2
        elif tag in (15,):  # MethodHandle
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):  # int/float/ref/nameandtype/dynamic
            pos += 4
        elif tag in (5, 6):  # long/double take two slots
            pos += 8
            i += 1
        else:
            return None
        i += 1
    pos += 2  # access flags
    (this_i,) = struct.unpack_from('>H', data, pos)
    pos += 2
    (super_i,) = struct.unpack_from('>H', data, pos)
    return pool.get(this_i), pool.get(super_i)


# 找名字含 Zhu0 / 宙 的实体，以及若干样本
targets = [n for n in z.namelist()
           if n.startswith('net/guzhenren/entity/') and n.endswith('.class')
           and '$' not in n]
print('total plain entity classes:', len(targets))

proj = 0
samples = []
for n in targets:
    r = parse_class(z.read(n))
    if not r:
        continue
    _, sup = r
    if sup and 'projectile' in sup.lower():
        proj += 1
        if len(samples) < 12:
            samples.append((n, sup))

print('extend projectile:', proj)
for n, s in samples:
    print(f'  {n}\n      super = {s}')
