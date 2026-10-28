import re, os

src = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'

pool = open(os.path.join(src, 'TianfuPool.java'), encoding='utf-8').read()
tal = open(os.path.join(src, 'TianfuTalents.java'), encoding='utf-8').read()

bodies = re.findall(r'body\(\s*"([^"]+)"', pool)
entries_builtin = re.findall(r'\bentry\("([^"]+)"', pool)
sijues = re.findall(r'\bsijue\(\s*(\d+)', pool)
jiyuans = re.findall(r'new Spec\("(jiyuan_[^"]+)"', pool)
talents = re.findall(r'\bt\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(-?\d+)', tal)

print('BODIES:', len(bodies), bodies)
print('SIJUES:', len(sijues), sijues)
print('JIYUANS:', len(jiyuans), jiyuans)
print('ENTRIES builtin:', len(entries_builtin))
print('TALENTS:', len(talents))
print('---')
print('entries total (builtin + talents) =', len(entries_builtin) + len(talents))
print('purchasable total =', len(bodies) + len(sijues) + len(jiyuans)
      + len(entries_builtin) + len(talents))
