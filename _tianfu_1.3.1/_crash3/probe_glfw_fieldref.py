"""Extract GLFW fieldref names (GLFW_KEY_*) from constant pools of KeyMapping classes."""
import os
import struct
import sys
import zipfile

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
BASE = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')


def parse_pool(data):
    """Return list of (tag, payload) with utf8 resolved."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    i = 10
    count = struct.unpack('>H', data[8:10])[0]
    entries = {}
    idx = 1
    while idx < count and i < len(data):
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            try:
                entries[idx] = ('utf8', data[i + 3:i + 3 + ln].decode('utf-8'))
            except UnicodeDecodeError:
                entries[idx] = ('utf8', '')
            i += 3 + ln
        elif tag == 7:
            entries[idx] = ('class', struct.unpack('>H', data[i + 1:i + 3])[0])
            i += 3
        elif tag == 8:
            entries[idx] = ('string', struct.unpack('>H', data[i + 1:i + 3])[0])
            i += 3
        elif tag == 9:
            entries[idx] = ('fieldref', struct.unpack('>HH', data[i + 1:i + 5]))
            i += 5
        elif tag == 10:
            entries[idx] = ('methodref', struct.unpack('>HH', data[i + 1:i + 5]))
            i += 5
        elif tag == 11:
            entries[idx] = ('imethodref', struct.unpack('>HH', data[i + 1:i + 5]))
            i += 5
        elif tag == 12:
            entries[idx] = ('nat', struct.unpack('>HH', data[i + 1:i + 5]))
            i += 5
        elif tag in (3, 4):
            entries[idx] = ('int', struct.unpack('>i', data[i + 1:i + 5])[0])
            i += 5
        elif tag in (5, 6):
            entries[idx] = ('long', None)
            i += 9
            idx += 1
        elif tag in (15, 16, 17, 18, 19, 20):
            i += 4 if tag == 15 else 3
        else:
            break
        idx += 1
    return entries


def resolve(entries, idx):
    e = entries.get(idx)
    if not e:
        return None
    if e[0] == 'utf8':
        return e[1]
    if e[0] in ('class', 'string'):
        return resolve(entries, e[1])
    return None


def glfw_keys(data):
    entries = parse_pool(data)
    out = set()
    for tag, payload in entries.values():
        if tag != 'fieldref':
            continue
        cls = resolve(entries, payload[0])
        nat = entries.get(payload[1])
        if not nat:
            continue
        name = resolve(entries, nat[1][0])
        if cls and 'glfw/GLFW' in cls and name and name.startswith('GLFW_KEY_'):
            out.add(name.replace('GLFW_KEY_', ''))
    return out


def scan(jar_path, label, acc):
    try:
        z = zipfile.ZipFile(jar_path)
    except Exception:
        return
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        data = z.read(n)
        if b'glfw/GLFW' not in data:
            continue
        keys = glfw_keys(data)
        if keys:
            acc.append((label, n.split('/')[-1], sorted(keys)))
    z.close()


acc = []
for jar in sorted(os.listdir(MODS)):
    if jar.endswith('.jar'):
        scan(os.path.join(MODS, jar), jar, acc)
scan(BASE, 'guzhenren本体', acc)

used = {}
print('=== GLFW keys per class ===')
for label, cls, keys in acc:
    print(f'  {label[:30]:32s} {cls:28s} {",".join(keys)}')
    for k in keys:
        used.setdefault(k, []).append(f'{label[:20]}/{cls}')

print()
print('=== OCCUPIED KEY UNION (%d) ===' % len(used))
print(' ', ' '.join(sorted(used)))
