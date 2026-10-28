"""Extract the newguui mod-construction failure stack trace."""
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
start = None
for i, l in enumerate(lines):
    if 'Failed to create mod instance' in l:
        start = i
        break
out.append(f'newguui 失败起始行: {start}')
out.append('')
out.append('=== 该行起 60 行完整堆栈 ===')
for j in range(start, min(len(lines), start + 60)):
    out.append(f'{j} {lines[j][:250]}')

# 也找 CrashReport 里对该 mod 的描述
out.append('')
out.append('=== 崩溃报告里 newguui 相关 ===')
P2 = r'_crash5\crash-2026-10-20_21.24.34-client.txt'
raw2 = open(P2, 'rb').read()
for enc in ('utf-8', 'gbk', 'utf-16'):
    try:
        t2 = raw2.decode(enc)
        break
    except Exception:
        pass
for i, l in enumerate(t2.splitlines()):
    if 'newguui' in l.lower() or 'guzhenren.ui' in l or 'GuzhenrenUi' in l:
        out.append(f'{i} {l[:250]}')

open(r'_crash3\crash5_newguui.txt', 'w', encoding='utf-8').write('\n'.join(out))
print('written')
