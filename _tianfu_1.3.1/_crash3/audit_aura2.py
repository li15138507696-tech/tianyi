import re, io

p = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuVfx.java'
t = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuTalents.java'

vfx = io.open(p, encoding='utf-8').read()
tal = io.open(t, encoding='utf-8').read()

real = set(re.findall(r'\bt\("([^"]+)"', tal))
ids = re.findall(r'AURAS\.put\("([^"]+)"', vfx)

bad = [i for i in ids if i not in real]
good = [i for i in ids if i in real]

print('real talent ids:', len(real))
print('explicit aura entries:', len(ids))
print('valid:', len(good))
print('BAD (must be empty):', bad if bad else 'none')

# 各品质覆盖情况
print()
print('=== 神/极 覆盖检查 ===')
import collections
qmap = {}
for m in re.finditer(r'\bt\("([^"]+)",\s*"[^"]+",\s*"([^"]+)"', tal):
    qmap[m.group(1)] = m.group(2)

for q in ['极', '神']:
    total = [i for i, qq in qmap.items() if qq == q]
    covered = [i for i in total if i in ids]
    print(f'  {q}: {len(covered)}/{len(total)}')
    for i in total:
        mark = 'OK ' if i in ids else 'MISS'
        print(f'     {mark} {i}')

print()
print('=== 天（前若干） ===')
tian = [i for i, qq in qmap.items() if qq == '天']
cov = [i for i in tian if i in ids]
print(f'  天: {len(cov)}/{len(tian)} 有专属，其余走档位回退')
