"""Measure EVERY spec description width to size the wrap fix."""
import re

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'


def norm(p):
    return re.sub(r'\s+', ' ', open(p, encoding='utf-8').read())


pool = norm(BASE + r'\TianfuPool.java')
tal = norm(BASE + r'\TianfuTalents.java')

rows = []
rows += [(m[0], m[1], '凡体', m[3]) for m in re.findall(r'body\("([^"]*)", "([^"]*)", (\d+), "([^"]*)"', pool)]
rows += [(m[0], m[1], '十绝体', m[2]) for m in re.findall(r'sijue\((\d+), "([^"]*)", "([^"]*)"', pool)]
rows += [(m[0], m[1], '机缘', m[4]) for m in re.findall(r'new Spec\("([^"]*)", "([^"]*)", Kind\.JIYUAN, "([^"]*)", (\d+), \d+,\s*"([^"]*)"', pool)]
rows += [(m[0], m[1], '词条', m[4]) for m in re.findall(r'entry\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', pool)]
rows += [(m[0], m[1], '词条', m[4]) for m in re.findall(r't\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', tal)]


def w(s):
    return sum(9 if ord(c) > 0x2E80 else 6 for c in s)


AVAIL = 316 - 88
print(f'单行可用宽 = {AVAIL}px')
print()
over = sorted(((w(d), i, n, k, d) for i, n, k, d in rows), key=lambda x: -x[0])
print(f'全部 spec 描述 {len(rows)} 条；超宽 {len([x for x in over if x[0] > AVAIL])} 条')
print()
for ww, i, n, k, d in over[:12]:
    need = (ww + AVAIL - 1) // AVAIL
    print(f'  {ww:4}px  需 {need} 行  [{k}] {n}  {d}')

print()
print('结论：最长', over[0][0], 'px → 两行（2×%d=%d）足够' % (AVAIL, 2 * AVAIL))
