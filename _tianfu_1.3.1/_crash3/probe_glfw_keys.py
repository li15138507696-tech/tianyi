"""Extract GLFW_KEY_* constants referenced by each KeyMapping-registering class, per jar."""
import os
import re
import struct
import sys
import zipfile

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
BASE = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')


def utf8_strings(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    i = 10
    count = struct.unpack('>H', data[8:10])[0]
    out = []
    idx = 1
    while idx < count and i < len(data):
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            try:
                out.append(data[i + 3:i + 3 + ln].decode('utf-8'))
            except UnicodeDecodeError:
                pass
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            i += 3
        elif tag == 15:
            i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            i += 5
        elif tag in (5, 6):
            i += 9
            idx += 1
        else:
            break
        idx += 1
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
        if b'KeyMapping' not in data and b'GLFW_KEY_' not in data:
            continue
        strs = utf8_strings(data)
        keys = sorted({s for s in strs if re.fullmatch(r'GLFW_KEY_[A-Z0-9_]+', s)})
        keyids = sorted({s for s in strs if s.startswith('key.') and len(s) < 60})
        if keys or keyids:
            acc.append((label, n.split('/')[-1], keys, keyids))
    z.close()


acc = []
for jar in sorted(os.listdir(MODS)):
    if jar.endswith('.jar'):
        scan(os.path.join(MODS, jar), jar, acc)
scan(BASE, 'guzhenren(本体)', acc)

print('=== GLFW keys used per KeyMapping class ===')
used = set()
for label, cls, keys, keyids in acc:
    if keys:
        print(f'  {label[:34]:36s} {cls:26s} {",".join(k.replace("GLFW_KEY_", "") for k in keys)}')
        used.update(k.replace('GLFW_KEY_', '') for k in keys)

print()
print('=== union of occupied keys ===')
print(' ', ' '.join(sorted(used)))

print()
print('=== classes with key.* but no GLFW literal (may use int or InputConstants) ===')
for label, cls, keys, keyids in acc:
    if not keys and keyids:
        print(f'  {label[:34]:36s} {cls:26s} {keyids}')
