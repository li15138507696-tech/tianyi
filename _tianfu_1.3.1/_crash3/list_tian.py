import re, io

t = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuTalents.java'
p = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuVfx.java'
tal = io.open(t, encoding='utf-8').read()
vfx = io.open(p, encoding='utf-8').read()
ids = set(re.findall(r'AURAS\.put\("([^"]+)"', vfx))

for m in re.finditer(r'\bt\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)"', tal):
    tid, name, q = m.group(1), m.group(2), m.group(3)
    if q == '天':
        mark = 'HAS' if tid in ids else '---'
        print(f'  {mark}  {tid:24s} {name}')
