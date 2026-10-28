"""BOM 扫描：src 下所有 .java/.toml/.json/.mcmeta 不得以 U+FEFF 开头。

mods.toml 带 BOM 会让 NeoForge 直接把整包判为 'not a valid mod file'（tianfu 时代炸过一次）。
"""
import os, sys

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src')
EXTS = ('.java', '.toml', '.json', '.mcmeta', '.properties')

bad = []
for base, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith(EXTS):
            continue
        p = os.path.join(base, fn)
        with open(p, 'rb') as f:
            if f.read(3) == b'\xef\xbb\xbf':
                bad.append(os.path.relpath(p, ROOT))

if bad:
    print('\n'.join('BOM ' + b for b in bad))
    sys.exit(1)
print('无 BOM 文件 OK')
