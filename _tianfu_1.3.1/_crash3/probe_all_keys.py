"""Sweep every installed jar: javap KeyMapping classes, extract keycode literals."""
import os
import re
import subprocess
import sys
import zipfile

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
BASE = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
if not os.path.exists(JAVAP):
    JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

sys.stdout.reconfigure(encoding='utf-8')

GLFW = {
    32: 'SPACE', 39: "'", 44: ',', 45: '-', 46: '.', 47: '/', 59: ';', 61: '=',
    91: '[', 92: '\\', 93: ']', 96: '`', 257: 'ENTER', 258: 'TAB', 259: 'BACKSPACE',
    260: 'INSERT', 261: 'DELETE', 262: 'RIGHT', 263: 'LEFT', 264: 'DOWN', 265: 'UP',
    266: 'PGUP', 267: 'PGDN', 268: 'HOME', 269: 'END', 280: 'CAPS', 340: 'LSHIFT',
    341: 'LCTRL', 342: 'LALT', 344: 'RSHIFT', 345: 'RCTRL', 346: 'RALT',
}
for d in range(48, 58):
    GLFW[d] = str(d - 48)
for c in range(65, 91):
    GLFW[c] = chr(c)
for f in range(1, 13):
    GLFW[289 + f] = f'F{f}'


def label(code):
    return GLFW.get(code, f'#{code}')


def classes_with_keymapping(jar):
    out = []
    try:
        z = zipfile.ZipFile(jar)
    except Exception:
        return out
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        d = z.read(n)
        if b'KeyMapping' in d and b'key.' in d:
            out.append(n)
    z.close()
    return out


def keycodes(jar, entry):
    cls = entry[:-6].replace('/', '.')
    try:
        r = subprocess.run([JAVAP, '-p', '-c', '-cp', jar, cls],
                           capture_output=True, timeout=60)
    except Exception:
        return []
    txt = r.stdout.decode('utf-8', 'replace')
    if 'KeyMapping."<init>"' not in txt and 'KeyMapping.<init>' not in txt:
        # still may be static init with ctor calls
        if 'net/minecraft/client/KeyMapping' not in txt:
            return []
    codes = []
    for m in re.finditer(r'^\s+(\d+): (bipush|sipush|iconst_m1|iconst_[0-5])\s*(.*)$', txt, re.M):
        op, arg = m.group(2), m.group(3).strip()
        if op == 'bipush' or op == 'sipush':
            try:
                codes.append(int(arg))
            except ValueError:
                pass
        elif op == 'iconst_m1':
            codes.append(-1)
        else:
            codes.append(int(op.split('_')[1]))
    return codes


occupied = {}
targets = [(os.path.join(MODS, j), j) for j in sorted(os.listdir(MODS)) if j.endswith('.jar')]
targets.append((BASE, 'guzhenren(本体)'))

for path, name in targets:
    for entry in classes_with_keymapping(path):
        for c in keycodes(path, entry):
            if -1 <= c <= 400:
                occupied.setdefault(c, set()).add(f'{name[:22]}/{entry.split("/")[-1][:20]}')

print('=== OCCUPIED KEYCODES ===')
for c in sorted(occupied):
    print(f'  {label(c):>8s}  ({c:3d})  {sorted(occupied[c])}')

print()
print('=== FREE LETTERS ===')
free = [chr(c) for c in range(65, 91) if c not in occupied]
print(' ', ' '.join(free))
print()
print('=== FREE DIGITS ===')
print(' ', ' '.join(str(d - 48) for d in range(48, 58) if d not in occupied))
