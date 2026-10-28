import re

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'


def n(p):
    return open(BASE + p, encoding='utf-8').read()


tal = n(r'\TianfuTalents.java')
eff = n(r'\TianfuEffects.java')
pool = n(r'\TianfuPool.java')
nat = n(r'\TianfuNative.java')
gui = n(r'\client\TianfuViewScreen.java')

print('=== 残留直接写 qiyun 的代码（应为 0）===')
hits = []
for f, t in (('Talents', tal), ('Effects', eff), ('Native', nat), ('Pool', pool)):
    for m in re.finditer(r'(v\.qiyun\s*[-+]?=|fldAdd\("qiyun"\s*,)', t):
        line = t[:m.start()].count('\n') + 1
        hits.append((f, line, t.splitlines()[line - 1].strip()[:72]))
if hits:
    for f, l, s in hits:
        print(f'  {f}:{l}  {s}')
else:
    print('  无')

print()
print('=== 上限写入是否都经兜底 ===')
print('  addCap 定义:', 'OK' if 'private static void addCap' in eff else 'MISSING')
print('  addCap 调用次数:', len(re.findall(r'addCap\(v,', eff)))
print('  tryAdd 兜底 qiyun_shangxian:', 'OK' if '"qiyun_shangxian".equals(field)' in tal else 'MISSING')
print('  裸写 v.qiyun_shangxian +=/-= :', len(re.findall(r'v\.qiyun_shangxian\s*[-+]=', eff)), '处')

print()
print('=== GUI 换行接线 ===')
checks = [
    ('ROW_H 常量', r'private static final int ROW_H'),
    ('LINE_H', r'private static final int LINE_H'),
    ('MAX_DESC_LINES', r'MAX_DESC_LINES ='),
    ('DESC_X', r'private static final int DESC_X'),
    ('wrapDesc', r'private List<String> wrapDesc'),
    ('rowHeight', r'private int rowHeight'),
    ('drawDesc', r'private void drawDesc'),
    ('wrapCache 字段', r'private final HashMap<String, List<String>> wrapCache'),
    ('HashMap import', r'import java\.util\.HashMap;'),
]
for k, pat in checks:
    print(f'  {k:16}', 'OK' if re.search(pat, gui) else 'MISSING')
print('  layout 用 rowHeight:', len(re.findall(r'rowHeight\(row\)', gui)), '处')
print('  drawDesc 调用:', len(re.findall(r'drawDesc\(g,', gui)), '处')
stale = [p for p in ('fit(spec.desc()', 'fit(option.desc()') if p in gui]
print('  残留单行 fit(desc):', ' / '.join(stale) if stale else '无')
print('  重复 wrapCache 字段:', 'YES-BAD' if len(re.findall(r'private final HashMap<String, List<String>> wrapCache', gui)) > 1 else '无')

print()
print('=== 气运 7 条最终数值 ===')
for m in re.finditer(r'entry\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', pool):
    if '气运' in m.group(5) or 'qiyun' in m.group(1):
        print(f'  {m.group(1):20} {m.group(2):10} {m.group(5)}')
for m in re.finditer(r't\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', tal):
    if '气运' in m.group(5):
        print(f'  {m.group(1):20} {m.group(2):10} {m.group(5)}')
