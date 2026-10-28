import re, os, collections

src = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
pool = open(os.path.join(src, 'TianfuPool.java'), encoding='utf-8').read()
tal = open(os.path.join(src, 'TianfuTalents.java'), encoding='utf-8').read()

# builtin entries: entry("id", "name", "quality", cost, "desc")
builtin = re.findall(r'\bentry\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(-?\d+)', pool)
talents = re.findall(r'\bt\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(-?\d+)', tal)

allq = collections.Counter(e[2] for e in builtin + talents)
print('builtin:', len(builtin), collections.Counter(e[2] for e in builtin))
print('talents:', len(talents), collections.Counter(e[2] for e in talents))
print()
print('combined by quality:')
order = ['极', '神', '天', '地', '玄', '黄', '负']
total = 0
for q in order:
    n = allq.get(q, 0)
    total += n
    print(f'   {q}: {n}')
print('   TOTAL:', total)

# other qualities not in order
extra = {q: c for q, c in allq.items() if q not in order}
if extra:
    print('   unexpected:', extra)
