import re, os

src = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
txt = open(os.path.join(src, 'TianfuTalents.java'), encoding='utf-8').read()

# extract t("id", "name", "quality", cost, ...)
entries = re.findall(r'\bt\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(-?\d+)', txt)
print('total talent entries:', len(entries))

ids = [e[0] for e in entries]
dupes = {i for i in ids if ids.count(i) > 1}
print('duplicate ids:', dupes or 'none')

# quality -> cost consistency
qmap = {'黄': 1, '玄': 2, '地': 3, '天': 5, '神': 12, '极': 16, '负': -2}
bad = [(e[0], e[2], e[3]) for e in entries if qmap.get(e[2]) != int(e[3])]
print('quality/cost mismatches:', bad or 'none')

# new addon talents
new = [e for e in entries if e[0] in
       ('bingxue_daoti', 'hundao_ronghe', 'qiyun_yizhi', 'shen_liupai_jianji')]
print()
print('new entries:')
for e in new:
    print('   ', e)

# names collision with body names
names = [e[1] for e in entries]
nb = {n for n in names if names.count(n) > 1}
print()
print('duplicate display names:', nb or 'none')

# mob effect refs -> namespaces used
mobs = re.findall(r'mob\("([^"]+)",\s*(-?[\d.]+)\)', txt)
print()
print('mob effect refs:')
for m in mobs:
    print('   ', m)
