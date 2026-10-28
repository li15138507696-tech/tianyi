"""Decode KeyMapping.<init> keycode arguments by parsing method Code attributes.

GLFW_KEY_* are compile-time constants, so javac inlines them as bipush/sipush/ldc ints.
We locate `invokespecial ... KeyMapping.<init>:(Ljava/lang/String;ILjava/lang/String;)V`
and walk backwards to recover the int operand.
"""
import os
import struct
import sys
import zipfile

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
BASE = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')


def parse(data):
    """Return (cp, methods) where methods = list of (name, desc, code_bytes)."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None, []
    i = 10
    count = struct.unpack('>H', data[8:10])[0]
    cp = {}
    idx = 1
    while idx < count and i < len(data):
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            cp[idx] = ('utf8', data[i + 3:i + 3 + ln].decode('utf-8', 'replace'))
            i += 3 + ln
        elif tag == 7:
            cp[idx] = ('class', struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
        elif tag == 8:
            cp[idx] = ('string', struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
        elif tag in (9, 10, 11):
            cp[idx] = ({9: 'field', 10: 'method', 11: 'imethod'}[tag],
                       struct.unpack('>HH', data[i + 1:i + 5])); i += 5
        elif tag == 12:
            cp[idx] = ('nat', struct.unpack('>HH', data[i + 1:i + 5])); i += 5
        elif tag in (3, 4):
            cp[idx] = ('int', struct.unpack('>i', data[i + 1:i + 5])[0]); i += 5
        elif tag in (5, 6):
            cp[idx] = ('long', None); i += 9; idx += 1
        elif tag == 15:
            cp[idx] = ('mh', (data[i + 1], struct.unpack('>H', data[i + 2:i + 4])[0])); i += 4
        elif tag == 16:
            cp[idx] = ('mt', struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
        elif tag in (17, 18):
            cp[idx] = ('dyn', struct.unpack('>HH', data[i + 1:i + 5])); i += 5
        elif tag in (19, 20):
            cp[idx] = ('mod', struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
        else:
            break
        idx += 1

    def res(k):
        e = cp.get(k)
        if not e:
            return None
        if e[0] == 'utf8':
            return e[1]
        if e[0] in ('class', 'string'):
            return res(e[1])
        return None

    # skip access, this, super
    i += 6
    icount = struct.unpack('>H', data[i:i + 2])[0]; i += 2 + 2 * icount
    fcount = struct.unpack('>H', data[i:i + 2])[0]; i += 2
    for _ in range(fcount):
        i += 6
        ac = struct.unpack('>H', data[i:i + 2])[0]; i += 2
        for _ in range(ac):
            i += 6
            ln = struct.unpack('>I', data[i:i + 4])[0]; i += 4 + ln
    mcount = struct.unpack('>H', data[i:i + 2])[0]; i += 2
    methods = []
    for _ in range(mcount):
        acc, nm, dm, ac = struct.unpack('>HHHH', data[i:i + 8])
        i += 8
        code = None
        for _ in range(ac):
            anm, aln = struct.unpack('>HI', data[i:i + 6])
            i += 6
            body = data[i:i + aln]
            i += aln
            if res(anm) == 'Code':
                mx, ml, cl = struct.unpack('>HHI', body[:8])
                code = body[8:8 + cl]
        methods.append((res(nm), res(dm), code))
    return (cp, res), methods


def keycodes(data):
    parsed = parse(data)
    if not parsed[0]:
        return []
    (cp, res), methods = parsed
    out = []
    for name, desc, code in methods:
        if not code:
            continue
        # linear scan with a tiny stack tracker
        pc = 0
        last_ints = []  # recent int pushes
        pending = None
        while pc < len(code):
            op = code[pc]
            if op == 0x10:      # bipush
                pending = struct.unpack('>b', code[pc + 1:pc + 2])[0]; pc += 2; continue
            if op == 0x11:      # sipush
                pending = struct.unpack('>h', code[pc + 1:pc + 3])[0]; pc += 3; continue
            if op == 0x12:      # ldc
                k = code[pc + 1]
                e = cp.get(k)
                if e and e[0] == 'int':
                    pending = e[1]
                pc += 2; continue
            if op == 0x13:      # ldc_w
                k = struct.unpack('>H', code[pc + 1:pc + 3])[0]
                e = cp.get(k)
                if e and e[0] == 'int':
                    pending = e[1]
                pc += 3; continue
            if op == 0xB7:      # invokespecial
                k = struct.unpack('>H', code[pc + 1:pc + 3])[0]
                e = cp.get(k)
                if e and e[0] == 'method':
                    cls = res(e[1][0])
                    nat = cp.get(e[1][1])
                    mname = res(nat[1][0]) if nat else None
                    mdesc = res(nat[1][1]) if nat else None
                    if mname == '<init>' and mdesc == '(Ljava/lang/String;ILjava/lang/String;)V':
                        out.append((name, pending))
                pc += 3; continue
            if op in (0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08):
                pending = op - 0x03
                pc += 1; continue
            # skip operands of other instructions
            pc += 1
            if op in (0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3A,
                      0xA9, 0xBC):
                pc += 1
            elif op in (0x84,):
                pc += 2
            elif op in (0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xBB, 0xBD, 0xC0,
                        0xC1, 0xC6, 0xC7):
                pc += 2
            elif op in (0xB9, 0xBA, 0xC8, 0xC9):
                pc += 4
            elif op in (0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA0, 0xA1, 0xA2,
                        0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xC6, 0xC7):
                pc += 2
            elif op == 0xC5:
                pc += 3
            elif op == 0xAA or op == 0xAB:
                break
    return out


def scan(path, label, acc):
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        return
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        data = z.read(n)
        if b'KeyMapping' not in data:
            continue
        ks = keycodes(data)
        if ks:
            acc.append((label, n.split('/')[-1], ks))
    z.close()


acc = []
for jar in sorted(os.listdir(MODS)):
    if jar.endswith('.jar'):
        scan(os.path.join(MODS, jar), jar, acc)
scan(BASE, 'guzhenren本体', acc)

print('=== KeyMapping keycodes ===')
used = set()
for label, cls, ks in acc:
    print(f'  {label[:30]:32s} {cls:28s} {ks}')
    for _, k in ks:
        if k is not None:
            used.add(k)
print()
print('=== OCCUPIED CODES ===', sorted(used))
