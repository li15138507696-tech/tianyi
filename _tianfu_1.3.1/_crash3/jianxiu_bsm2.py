import zipfile, os, struct

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, 'guzhenren_兼修附属0.7.jar')
z = zipfile.ZipFile(p)
data = z.read('cn/blockforge/generated/minecraftjsontoml16x/IceContent.class')
z.close()

pos = 8
n = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
cp = {}
i = 1
while i < n:
    tag = data[pos]; pos += 1
    if tag == 1:
        ln = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        cp[i] = ('Utf8', data[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
    elif tag in (7, 8, 16, 19, 20):
        cp[i] = (tag, struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
    elif tag == 15:
        cp[i] = ('MH', data[pos], struct.unpack('>H', data[pos+1:pos+3])[0]); pos += 3
    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
        cp[i] = (tag, struct.unpack('>HH', data[pos:pos+4])); pos += 4
    elif tag in (5, 6):
        cp[i] = (tag, None); pos += 8; i += 1
    else:
        raise SystemExit('tag %d' % tag)
    i += 1


def utf(idx):
    v = cp.get(idx)
    return v[1] if v and v[0] == 'Utf8' else None


def nat(idx):
    v = cp.get(idx)
    if v and v[0] == 12:
        return utf(v[1][0]), utf(v[1][1])
    return None, None


def mh(idx):
    v = cp.get(idx)
    if v and v[0] == 'MH':
        ref = cp.get(v[2])
        if ref and ref[0] == 10:  # Methodref
            ci, nti = ref[1]
            cname = utf(cp[ci][1]) if cp.get(ci) and cp[ci][0] == 7 else None
            name, desc = nat(nti)
            return cname, name, desc
    return None, None, None


# InvokeDynamic -> bootstrap index
indys = {}
for idx, v in cp.items():
    if v[0] == 18:
        bsm, nti = v[1]
        name, desc = nat(nti)
        indys[idx] = (bsm, name, desc)

# ---- find all ldc of "ice_path_boost" and next invokedynamic ----
found = []
p2 = 0
while p2 < len(data) - 4:
    op = data[p2]
    if op in (0x12, 0x13, 0x14):  # ldc / ldc_w / ldc2_w
        if op == 0x12:
            idx = data[p2+1]; ln = 2
        else:
            idx = struct.unpack('>H', data[p2+1:p2+3])[0]; ln = 3
        if utf(idx) == 'ice_path_boost':
            q = p2 + ln
            while q < len(data) - 5:
                if data[q] == 0xBA:
                    found.append(struct.unpack('>H', data[q+1:q+3])[0])
                    break
                q += 1
        p2 += ln
    else:
        p2 += 1

print('invokedynamic after ice_path_boost:', found)
for fi in found:
    if fi in indys:
        bsm, name, desc = indys[fi]
        print(f'  indy #{fi} -> bsm #{bsm} name={name} desc={desc}')
        print('  resolved lambda:', mh(bsm))
