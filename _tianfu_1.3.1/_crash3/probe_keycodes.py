"""Decode KeyMapping constructor keycodes from every installed jar to find a free key."""
import struct
import sys
import zipfile
import os

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
BASE = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')

GLFW = {
    32: 'SPACE', 39: 'APOSTROPHE', 44: 'COMMA', 45: 'MINUS', 46: 'PERIOD', 47: 'SLASH',
    48: '0', 49: '1', 50: '2', 51: '3', 52: '4', 53: '5', 54: '6', 55: '7', 56: '8', 57: '9',
    59: 'SEMICOLON', 61: 'EQUAL', 65: 'A', 66: 'B', 67: 'C', 68: 'D', 69: 'E', 70: 'F',
    71: 'G', 72: 'H', 73: 'I', 74: 'J', 75: 'K', 76: 'L', 77: 'M', 78: 'N', 79: 'O',
    80: 'P', 81: 'Q', 82: 'R', 83: 'S', 84: 'T', 85: 'U', 86: 'V', 87: 'W', 88: 'X',
    89: 'Y', 90: 'Z', 91: 'LEFT_BRACKET', 92: 'BACKSLASH', 93: 'RIGHT_BRACKET',
    96: 'GRAVE_ACCENT', 257: 'ENTER', 258: 'TAB', 259: 'BACKSPACE', 260: 'INSERT',
    261: 'DELETE', 262: 'RIGHT', 263: 'LEFT', 264: 'DOWN', 265: 'UP', 266: 'PAGE_UP',
    267: 'PAGE_DOWN', 268: 'HOME', 269: 'END', 280: 'CAPS_LOCK', 340: 'LEFT_SHIFT',
    341: 'LEFT_CONTROL', 342: 'LEFT_ALT', 344: 'RIGHT_SHIFT', 345: 'RIGHT_CONTROL',
    346: 'RIGHT_ALT',
}


def utf8_pool(data):
    """Return dict idx -> utf8 str for a class file."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return {}
    i = 10
    count = struct.unpack('>H', data[8:10])[0]
    pool = {}
    idx = 1
    while idx < count and i < len(data):
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            pool[idx] = data[i + 3:i + 3 + ln].decode('utf-8', 'replace')
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
    return pool


def scan(jar_path, label):
    found = []
    try:
        z = zipfile.ZipFile(jar_path)
    except Exception as e:
        print('  skip', label, e)
        return found
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        data = z.read(n)
        if b'KeyMapping' not in data:
            continue
        pool = utf8_pool(data)
        # find string constants that look like key.<ns>.<name>
        keynames = [s for s in pool.values() if s.startswith('key.') and len(s) < 60]
        # find small ints used as keycode: look for GLFW class ref
        # crude: scan for "org/lwjgl/glfw/GLFW" then int constants are fieldrefs, not literals.
        # Instead: many mods use literal ints. Detect bipush/sipush/ldc int right before KeyMapping.<init>
        codes = []
        for k, s in pool.items():
            if s.startswith('key.'):
                codes.append(s)
        found.append((label, n.split('/')[-1], sorted(set(keynames))))
    z.close()
    return found


print('=== key.* constants per jar ===')
for jar in sorted(os.listdir(MODS)):
    if not jar.endswith('.jar'):
        continue
    p = os.path.join(MODS, jar)
    res = scan(p, jar)
    for label, cls, keys in res:
        if keys:
            print(f'  [{label}] {cls}: {keys}')

print()
print('=== base jar ===')
for label, cls, keys in scan(BASE, 'guzhenren'):
    if keys:
        print(f'  {cls}: {keys}')
