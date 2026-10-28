"""版本号递增：显式传 X.Y.Z（不要用 minor/major 这种相对模式，历史上有过 1.2.13 → 1.3.0 的坑）。

同步点：version.properties、META-INF/neoforge.mods.toml、docs/*.md 里出现的 tianyi-X.Y.Z。
"""
import os, re, sys

ROOT = r'D:\daima\mcmod\天意'

if len(sys.argv) != 2:
    raise SystemExit('用法: python _pack/bump_version.py 1.0.1')
new = sys.argv[1].strip()
if not re.fullmatch(r'\d+\.\d+\.\d+', new):
    raise SystemExit(f'版本号必须是 X.Y.Z：{new}')


def read_old():
    with open(os.path.join(ROOT, 'version.properties'), encoding='utf-8') as f:
        for line in f:
            m = re.match(r'\s*version\s*=\s*(\S+)', line)
            if m:
                return m.group(1)
    raise SystemExit('version.properties 里没有 version=…')


old = read_old()
print('old:', old, '-> new:', new)
changed = []

# 1. version.properties
p = os.path.join(ROOT, 'version.properties')
t = open(p, encoding='utf-8').read()
t = re.sub(r'(^\s*version\s*=\s*)\S+', r'\g<1>' + new, t, count=1, flags=re.M)
open(p, 'w', encoding='utf-8', newline='\n').write(t)
changed.append('version.properties')

# 2. mods.toml
p = os.path.join(ROOT, 'src', 'main', 'resources', 'META-INF', 'neoforge.mods.toml')
raw = open(p, 'rb').read()
bom = raw[:3] == b'\xef\xbb\xbf'
t = raw.decode('utf-8-sig')
t2 = re.sub(r'(^\s*version\s*=\s*")[^"]+(")', r'\g<1>' + new + r'\g<2>', t, count=1, flags=re.M)
data = (b'\xef\xbb\xbf' if bom else b'') + t2.encode('utf-8')
open(p, 'wb').write(data)
changed.append('META-INF/neoforge.mods.toml' + ('（原文件带 BOM，已保留）' if bom else ''))

# 3. docs
#
# 只改「当前版本声明行」，不动版本历史。
# 旧实现是全文 replace(f'tianyi-{old}', ...) —— 那会把变更记录里的
# 「删掉 tianyi-1.0.8.jar」一起改写成新版本，等于伪造历史；而且下一次 bump 后
# verify_version 又会把历史当成「不一致」来报。两条脚本现在共用同一组锚点：
# 行里出现 产物 / 版本 / 依据 / 使用（ 才算「当前版本声明」。
ANCHORS = ('产物', '版本', '依据', '使用（')
VER = r'tianyi-(\d+\.\d+\.\d+)'
docs = os.path.join(ROOT, 'docs')
if os.path.isdir(docs):
    for fn in sorted(os.listdir(docs)):
        if not fn.endswith('.md'):
            continue
        p = os.path.join(docs, fn)
        lines = open(p, encoding='utf-8').read().split('\n')
        touched = 0
        for i, line in enumerate(lines):
            if not any(a in line for a in ANCHORS):
                continue
            if not re.search(VER, line):
                continue
            newline = re.sub(VER, lambda m: f'tianyi-{new}', line)
            if newline != line:
                lines[i] = newline
                touched += 1
        if touched:
            open(p, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
            changed.append(f'docs/{fn}（{touched} 处声明）')

print('updated:', ', '.join(changed))
