"""Dump every ERROR/FATAL line with context, plus the window right before the crash."""
P = r'_crash5\latest.log'
raw = open(P, 'rb').read()
for enc in ('utf-8', 'gbk', 'utf-16'):
    try:
        t = raw.decode(enc)
        break
    except Exception:
        pass
lines = t.splitlines()

out = []
out.append(f'总行数 {len(lines)}')

# 所有 ERROR / FATAL 行（带时间戳）
out.append('')
out.append('=== 全部 ERROR / FATAL 行 ===')
for i, l in enumerate(lines):
    if '] [ERROR]' in l or '] [FATAL]' in l or '/ERROR]' in l or '/FATAL]' in l:
        out.append(f'{i} {l[:230]}')

# 找崩溃点
crash = None
for i, l in enumerate(lines):
    if 'Reported exception thrown' in l:
        crash = i
        break
out.append('')
out.append(f'=== 崩溃点 {crash}，前 25 行 ===')
if crash:
    for j in range(max(0, crash - 25), crash + 2):
        out.append(f'{j} {lines[j][:210]}')

# 21:24:30 - 21:24:34 之间的所有行
out.append('')
out.append('=== 21:24:30 ~ 21:24:35 全部行 ===')
for i, l in enumerate(lines):
    if '21:24:3' in l and len(l) > 20:
        out.append(f'{i} {l[:210]}')

open(r'_crash3\crash5_errors.txt', 'w', encoding='utf-8').write('\n'.join(out))
print('lines written:', len(out))
