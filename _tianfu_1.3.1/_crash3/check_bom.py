"""Scan every text resource for a UTF-8 BOM (FML/TOML/resource loaders choke on it)."""
import os

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
RES = os.path.join(ROOT, 'src', 'main', 'resources')
EXTS = ('.toml', '.json', '.mcmeta', '.properties', '.xml', '.txt')

bad = []
scanned = 0
for root, _, files in os.walk(RES):
    for fn in files:
        if not fn.lower().endswith(EXTS):
            continue
        p = os.path.join(root, fn)
        scanned += 1
        with open(p, 'rb') as f:
            if f.read(3) == b'\xef\xbb\xbf':
                bad.append(os.path.relpath(p, ROOT))

print(f'扫描 {scanned} 个文本资源（{", ".join(EXTS)}）')
if bad:
    print('带 BOM 的文件：')
    for b in bad:
        print('  ', b)
else:
    print('无 BOM 文件  OK')

# 顺带校验所有 JSON 可解析
import json
jbad = []
for root, _, files in os.walk(RES):
    for fn in files:
        if fn.endswith('.json') or fn.endswith('.mcmeta'):
            p = os.path.join(root, fn)
            try:
                with open(p, encoding='utf-8') as f:
                    json.load(f)
            except Exception as e:
                jbad.append((os.path.relpath(p, ROOT), str(e)[:90]))
print()
if jbad:
    print('JSON 解析失败：')
    for p, e in jbad:
        print('  ', p, '->', e)
else:
    print('全部 JSON 可解析  OK')
