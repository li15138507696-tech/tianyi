"""Probe how liupai_* (流派) and daohen_* (道痕) grow in the base mod."""
import json
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)

names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]

# who increments liupai_jiandao (a representative)
for field in (b'liupai_jiandao', b'daohen_jiandao', b'liupai_tudao'):
    hits = [n for n in names if field in z.read(n)]
    print(f'{field.decode()}: {len(hits)} classes')
    for n in hits[:6]:
        print('   ', n)

print()
# liupai fields that appear in procedures -> growth pattern
proc = [n for n in names if '/procedures/' in n]
lp = [n for n in proc if b'liupai_' in z.read(n)]
print('procedures touching liupai_:', len(lp))
for n in lp[:10]:
    print('   ', n)

print()
dh = [n for n in proc if b'daohen_' in z.read(n)]
print('procedures touching daohen_:', len(dh))
for n in dh[:10]:
    print('   ', n)
