import zipfile, os, struct, io

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '魂道附属10.19.jar')

z = zipfile.ZipFile(p)
data = z.read('net/hundao/addon/recipe/GuFangNativeCraft.class')
z.close()

# dump constant pool entries that are doubles or relevant strings/ints
# Minimal class-file constant pool parser
def parse_cp(b):
    assert b[:4] == b'\xca\xfe\xba\xbe'
    pos = 8
    cp_count = struct.unpack('>H', b[pos:pos+2])[0]; pos += 2
    cp = {}
    i = 1
    while i < cp_count:
        tag = b[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack('>H', b[pos:pos+2])[0]; pos += 2
            cp[i] = ('Utf8', b[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack('>H', b[pos:pos+2])[0]); pos += 2
        elif tag in (15,):
            cp[i] = (tag, b[pos], struct.unpack('>H', b[pos+1:pos+3])[0]); pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            if tag == 3:
                cp[i] = ('Int', struct.unpack('>i', b[pos:pos+4])[0])
            elif tag == 4:
                cp[i] = ('Float', struct.unpack('>f', b[pos:pos+4])[0])
            else:
                cp[i] = (tag, struct.unpack('>HH', b[pos:pos+4]))
            pos += 4
        elif tag in (5, 6):
            if tag == 5:
                cp[i] = ('Long', struct.unpack('>q', b[pos:pos+8])[0])
            else:
                cp[i] = ('Double', struct.unpack('>d', b[pos:pos+8])[0])
            pos += 8
            i += 1
        else:
            raise SystemExit(f'unknown tag {tag} at {pos}')
        i += 1
    return cp

cp = parse_cp(data)
print('--- Double constants ---')
for k, v in cp.items():
    if v[0] == 'Double':
        print(f'  #{k} = {v[1]}')
print('--- Int constants (small) ---')
for k, v in cp.items():
    if v[0] == 'Int':
        print(f'  #{k} = {v[1]}')
