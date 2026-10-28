"""Offline check of the wrapDesc algorithm: no desc may lose text, and line count must match rowHeight.

Replicates the Java exactly:
  width(char) = 9 if ord > 0x2E80 else 6
  DESC_X=82, DESC_PAD=6, ROW_H=20, LINE_H=10, HEADER_H=16, MAX_DESC_LINES=3
  avail = max(width('字'), panelW - DESC_X - DESC_PAD)
"""
import re

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
ROW_H, LINE_H, HEADER_H, MAXL = 20, 10, 16, 3
DESC_X, DESC_PAD = 82, 6


def norm(p):
    return re.sub(r'\s+', ' ', open(p, encoding='utf-8').read())


def cw(c):
    return 9 if ord(c) > 0x2E80 else 6


def wrap(desc, maxw):
    if not desc:
        return []
    out, n, start = [], len(desc), 0
    while start < n and len(out) < MAXL:
        if len(out) == MAXL - 1:
            out.append(desc[start:])
            break
        width, cut, lastBreak = 0, start, -1
        for i in range(start, n):
            w = cw(desc[i])
            if width + w > maxw:
                break
            width += w
            cut = i + 1
            if desc[i] in '、，； ）。':
                lastBreak = i + 1
        if cut == start:
            cut = start + 1
        elif lastBreak > start:
            cut = lastBreak
        out.append(desc[start:cut])
        start = cut
    return out


def fit(s, maxw):
    if sum(cw(c) for c in s) <= maxw:
        return s
    i = len(s)
    while i > 0 and sum(cw(c) for c in s[:i]) + cw('…') > maxw:
        i -= 1
    return s[:i] + '…'


# ---- gather every desc rendered as a spec row or option row
pool, tal, diff = norm(BASE + r'\TianfuPool.java'), norm(BASE + r'\TianfuTalents.java'), norm(BASE + r'\TianfuDifficulty.java')
descs = []
descs += [(m[1], m[3]) for m in re.findall(r'body\("([^"]*)", "([^"]*)", (\d+), "([^"]*)"', pool)]
descs += [(m[1], m[2]) for m in re.findall(r'sijue\((\d+), "([^"]*)", "([^"]*)"', pool)]
descs += [(m[1], m[4]) for m in re.findall(r'new Spec\("([^"]*)", "([^"]*)", Kind\.JIYUAN, "([^"]*)", (\d+), \d+,\s*"([^"]*)"', pool)]
descs += [(m[1], m[4]) for m in re.findall(r'entry\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', pool)]
descs += [(m[1], m[4]) for m in re.findall(r't\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', tal)]
descs += [(m[0], m[3]) for m in re.findall(r'new Option\("([^"]*)", "([^"]*)", (\d+), "([^"]*)"', diff)]

print(f'共 {len(descs)} 条会被渲染的描述')
print()

problems = []
for panelw in (316, 300, 260, 220, 200, 180, 160, 140, 136, 120):
    avail = max(cw('字'), panelw - DESC_X - DESC_PAD)
    worst_lines = 0
    lost = 0
    for name, d in descs:
        lines = wrap(d, avail)
        worst_lines = max(worst_lines, len(lines))
        # 末行经 fit 截断，检查拼接后是否保留全部文本（未截断时）
        joined = ''.join(lines)
        if joined != d:
            lost += 1
    print(f'  面板宽 {panelw:3} → 描述列 {avail:3}px  最多 {worst_lines} 行  '
          f'行高 {ROW_H + (worst_lines - 1) * LINE_H:3}px  丢字 {lost}')

print()
print('=== 逐条检查最窄可用宽度（面板 120 → 描述列 32px）下是否仍不溢出 ===')
avail = max(cw('字'), 120 - DESC_X - DESC_PAD)
print(f'  描述列 {avail}px（极窄兜底）')
bad = 0
for name, d in descs:
    lines = wrap(d, avail)
    for i, ln in enumerate(lines):
        rendered = fit(ln, avail) if i == len(lines) - 1 else ln
        if sum(cw(c) for c in rendered) > avail:
            bad += 1
            print(f'    OVERFLOW {name}: {rendered!r}')
print(f'  溢出 {bad} 条' + ('  ← 全部安全' if bad == 0 else ''))

print()
print('=== 标准宽度 316：原先被截断的 12 条现在是否完整显示 ===')
avail = 316 - DESC_X - DESC_PAD
print(f'  描述列 {avail}px')
shown = 0
for name, d in sorted(descs, key=lambda x: -sum(cw(c) for c in x[1]))[:14]:
    lines = wrap(d, avail)
    last = fit(lines[-1], avail)
    truncated = last.endswith('…')
    if not truncated:
        shown += 1
    print(f'  {"截断" if truncated else "完整"}  {len(lines)}行  {name:10} {d}')
print(f'\n  完整显示 {shown}/14')
