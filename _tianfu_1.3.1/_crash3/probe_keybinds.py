"""Check GLFW key codes used by installed addons, to pick a free key for the new addon."""
import re
import sys
import zipfile
import glob

MODS = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
sys.stdout.reconfigure(encoding='utf-8')

targets = ['tianfu-1.2.17.jar', '蛊真人流派熟练度查看.jar', '蛊虫使用记录-1.0.1.jar']
for t in targets:
    p = MODS + '\\' + t
    try:
        z = zipfile.ZipFile(p)
    except FileNotFoundError:
        print('missing', t)
        continue
    keys = set()
    for n in z.namelist():
        if n.endswith('.class'):
            b = z.read(n)
            # KeyMapping ctor: ldc keycode -> look for GLFW constants usage patterns
            # easier: find 'key.' lang-ish strings and any small int constants near KeyMapping
            if b'KeyMapping' in b:
                # dump printable strings
                i = 0
                strs = []
                while i < len(b) - 3:
                    if b[i] == 1:
                        ln = b[i + 1] * 256 + b[i + 2]
                        if 2 <= ln < 120 and i + 3 + ln <= len(b):
                            try:
                                s = b[i + 3:i + 3 + ln].decode('utf-8')
                                if s.isprintable():
                                    strs.append(s)
                            except UnicodeDecodeError:
                                pass
                        i += 1 + ln
                    else:
                        i += 1
                print(f'--- {t} :: {n.split("/")[-1]}')
                print('    ', [s for s in dict.fromkeys(strs) if len(s) < 60][:20])
    z.close()
