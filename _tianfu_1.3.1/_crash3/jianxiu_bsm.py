import zipfile, os, struct

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, 'guzhenren_兼修附属0.7.jar')
z = zipfile.ZipFile(p)
data = z.read('cn/blockforge/generated/minecraftjsontoml16x/IceContent.class')
z.close()

# --- parse constant pool with MethodHandle / InvokeDynamic support ---
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
    elif tag == 15:  # MethodHandle
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

# InvokeDynamic entries -> bootstrap index
indys = {}
for idx, v in cp.items():
    if v[0] == 18:
        bsm, nt = v[1]
        name, desc = nat(nt)
        indys[idx] = (bsm, name, desc)

# find the InvokeDynamic used right after ldc_w "ice_path_boost"
target_indy = None
p2 = 0
while p2 < len(data) - 3:
    op = data[p2]
    if op == 0x13:  # ldc_w
        idx = struct.unpack('>H', data[p2+1:p2+3])[0]
        if utf(idx) == 'ice_path_boost':
            # scan forward for invokedynamic (0xba)
            q = p2 + 3
            while q < len(data) - 5:
                if data[q] == 0xBA:
                    target_indy = struct.unpack('>H', data[q+1:q+3])[0]
                    break
                q += 1
            break
    p2 += 1

print('ice_path_boost invokedynamic idx:', target_indy)
if target_indy and target_indy in indys:
    bsm, name, desc = indys[target_indy]
    print('  bootstrap index:', bsm, 'name:', name, 'desc:', desc)

# BootstrapMethods table
pos2 = pos
# skip fields/methods to reach class attributes
# simpler: find BootstrapMethods attribute by name
bm_idx = None
for idx, v in cp.items():
    if v[0] == 'Utf8' and v[1] == 'BootstrapMethods':
        bm_idx = idx
        break
print('BootstrapMethods cp idx:', bm_idx)
