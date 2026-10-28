"""Cross-check: quality filter coverage, qiyun caps, and description render widths."""
import re, collections

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'


def norm(p):
    return re.sub(r'\s+', ' ', open(p, encoding='utf-8').read())


tal = norm(BASE + r'\TianfuTalents.java')
pool = norm(BASE + r'\TianfuPool.java')

talents = re.findall(r't\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', tal)
entries = re.findall(r'entry\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', pool)

QFILTERS = ["全部", "极", "神", "天", "地", "玄", "黄", "负"]

print('=== 词条品质分布 ===')
c = collections.Counter(r[2] for r in entries + talents)
for q, n in c.most_common():
    mark = 'OK ' if q in QFILTERS else '!! 不在 QFILTERS'
    print(f'  {q:3} x{n:<4} {mark}')
unknown = [q for q in c if q not in QFILTERS]
print('  未覆盖品质:', unknown or '(none)')
if unknown:
    print('  → 这些品质的词条只在「全部」筛选下可见：')
    for r in entries + talents:
        if r[2] in unknown:
            print(f'      {r[0]:24} {r[1]:10} {r[2]}  {r[4]}')

print()
print('=== 描述像素宽度估算（CJK 9px/字符, ASCII 6px） ===')


def w(s):
    return sum(9 if ord(ch) > 0x2E80 else 6 for ch in s)


# 词条行：desc 起于 left+82，右界 right；可用宽 = panelW - 82 - 6
PANEL = 316
AVAIL = PANEL - 82 - 6
print(f'  词条/凡体行可用宽 ≈ {AVAIL}px（panel {PANEL}, 起点 +82, 右边距 6）')
over = [(r, w(r[4])) for r in entries + talents if w(r[4]) > AVAIL]
print(f'  超宽（会被 fit() 截断）: {len(over)} 条')
for r, ww in sorted(over, key=lambda x: -x[1]):
    print(f'    {ww:4}px  {r[0]:24} {r[1]:10}  {r[4]}')

print()
print('=== 机缘行：desc 起于 left+82，但右侧有「+N 自由点」徽章 ===')
print(f'  可用宽同上 ≈ {AVAIL}px（徽章在 right 端，desc 仍按 -88 截断）')

print()
print('=== 名称宽度 vs desc 起点 left+82（名称可用 74px） ===')


def nm(r):
    return w(r[1])


for r in entries + talents:
    if nm(r) > 74:
        print(f'  {nm(r):3}px  {r[1]}')

print('  (none)' if not [r for r in entries + talents if nm(r) > 74] else '')
