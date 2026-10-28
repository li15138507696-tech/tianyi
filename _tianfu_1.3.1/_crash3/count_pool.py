import re, os

src = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'

pool = open(os.path.join(src, 'TianfuPool.java'), encoding='utf-8').read()
tal = open(os.path.join(src, 'TianfuTalents.java'), encoding='utf-8').read()

bodies = re.findall(r'\bbody\(\s*(\d+)', pool)
entries_builtin = re.findall(r'\bentry\("([^"]+)"', pool)
sijues = re.findall(r'\bsijue\(\s*(\d+)', pool)
jiyuans = re.findall(r'\bjiyuan\("([^"]+)"', pool)
talents = re.findall(r'\bt\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(-?\d+)', tal)

print('BODIES:', len(bodies))
print('ENTRIES builtin:', len(entries_builtin))
print('SIJUES:', len(sijues), sijues)
print('JIYUANS:', len(jiyuans), jiyuans)
print('TALENTS:', len(talents))
print('---')
print('entries total =', len(entries_builtin) + len(talents))
print('pool total    =', len(bodies) + len(entries_builtin) + len(talents)
      + len(sijues) + len(jiyuans))
