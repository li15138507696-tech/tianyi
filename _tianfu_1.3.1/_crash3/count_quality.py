import re, os, collections

src = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
txt = open(os.path.join(src, 'TianfuTalents.java'), encoding='utf-8').read()
entries = re.findall(r'\bt\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(-?\d+)', txt)
c = collections.Counter(e[2] for e in entries)
print('extended entries:', len(entries))
for q in ['黄', '玄', '地', '天', '神', '极', '负']:
    print(f'   {q}: {c.get(q,0)}')
