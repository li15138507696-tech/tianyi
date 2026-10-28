"""Parse every talent/entry/body declaration and report description anomalies."""
import re, collections

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'


def norm(p):
    return re.sub(r'\s+', ' ', open(p, encoding='utf-8').read())


tal = norm(BASE + r'\TianfuTalents.java')
pool = norm(BASE + r'\TianfuPool.java')

talents = re.findall(r't\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', tal)
entries = re.findall(r'entry\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"', pool)
bodies = re.findall(r'body\("([^"]*)", "([^"]*)", (\d+), "([^"]*)"', pool)
sijues = re.findall(r'sijue\((\d+), "([^"]*)", "([^"]*)"', pool)
specs = re.findall(r'new Spec\("([^"]*)", "([^"]*)", Kind\.(\w+), "([^"]*)", (\d+), (\d+), "([^"]*)"', pool)

print(f'TianfuTalents.t()      : {len(talents)}')
print(f'TianfuPool.entry()     : {len(entries)}')
print(f'TianfuPool.body()      : {len(bodies)}')
print(f'TianfuPool.sijue()     : {len(sijues)}')
print(f'TianfuPool.new Spec()  : {len(specs)}')
print(f'  词条合计 = {len(entries)} + {len(talents)} = {len(entries) + len(talents)}')

print()
print('=== 描述为空 / 无中文 / 过短 ===')
bad = 0
for label, rows, di in (('t', talents, 4), ('entry', entries, 4),
                        ('body', bodies, 3), ('sijue', sijues, 2), ('Spec', specs, 6)):
    for r in rows:
        d = r[di]
        if len(d) <= 3 or not re.search(r'[\u4e00-\u9fff]', d):
            print(f'  {label:6} id={r[0]!r} desc={d!r}')
            bad += 1
print('  (none)' if not bad else f'  {bad} 条异常')

print()
print('=== 重复 id ===')
ids = ([r[0] for r in talents] + [r[0] for r in entries]
       + [r[0] for r in bodies] + [r[0] for r in specs])
dups = {k: v for k, v in collections.Counter(ids).items() if v > 1}
print(' ', dups or '(none)')

print()
print('=== 描述长度 ===')
allrows = talents + entries
lens = sorted(len(r[4]) for r in allrows)
print(f'  min={lens[0]}  max={lens[-1]}  median={lens[len(lens) // 2]}  n={len(lens)}')
over = [r for r in allrows if len(r[4]) > 30]
print(f'  >30 字符: {len(over)} 条')
for r in over:
    print(f'    [{len(r[4]):2}] {r[0]:22} {r[1]:8} {r[4]}')

print()
print('=== 气运类词条全清单 ===')
qy = [r for r in allrows if '气运' in r[4]]
for r in qy:
    print(f'  {r[0]:22} {r[1]:8} {r[2]:2} cost={r[3]:>3}  {r[4]}')
print(f'  共 {len(qy)} 条')
