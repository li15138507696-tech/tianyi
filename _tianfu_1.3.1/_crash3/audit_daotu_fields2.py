"""Verify the 8 流派 buff field names + exact daohen/liupai counts against GuzhenrenModMobEffects."""
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)

eff = z.read('net/guzhenren/init/GuzhenrenModMobEffects.class')
claimed = ['SHUIDAOZENGFU', 'YUDAOGUCHONGWEILIZENGFU', 'QIDAOGUCHONGWEILIZENGFU',
           'JIANDAOSHANGHAIZENGFU', 'LVDAOZENGFU', 'JINDAOZENGFU', 'YANDAOZENGFU',
           'LEIDAOZENGSHANG']
print('=== 8 claimed buffs exist in GuzhenrenModMobEffects ===')
for c in claimed:
    print('  ', 'OK  ' if c.encode() in eff else 'MISS', c)

pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
daohen = sorted(set(m.decode() for m in re.findall(rb'daohen_[a-z0-9]+', pv)))
dahen = sorted(set(m.decode() for m in re.findall(rb'dahen_[a-z0-9]+', pv)))
print()
print('=== exact counts ===')
print('  daohen_* :', len(daohen), daohen)
print('  dahen_*  :', len(dahen), dahen)
print('  total    :', len(daohen) + len(dahen))
print('  per-path (excl daohen_zong):', len(daohen) + len(dahen) - 1)

print()
print('=== liupai fields with NO matching daohen/dahen ===')
liupai = sorted(set(m.decode() for m in re.findall(rb'liupai_[a-z0-9]+|Liupai_[a-z0-9]+', pv)))
allpaths = set(daohen) | set(dahen)
for lp in liupai:
    base = lp.replace('liupai_', '').replace('Liupai_', '')
    cands = ['daohen_' + base, 'dahen_' + base]
    if not any(c in allpaths for c in cands):
        print('  ', lp, '-> candidates tried:', cands)

print()
print('=== daohen/dahen fields with NO matching liupai ===')
for d in sorted(allpaths):
    base = d.replace('daohen_', '').replace('dahen_', '')
    if base == 'zong':
        continue
    if 'liupai_' + base not in liupai and 'Liupai_' + base not in liupai:
        print('  ', d)
