"""Probe the base economy & progression resources a mid-game talent tree could spend."""
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]

fields = ['yuanshishuliang', 'GongXianZhi', 'daohen_zong', 'zhuanshu', 'jieduan',
          'jingli', 'niantou', 'shouyuan', 'qiyun', 'zuida_zhenyuan', 'zhenyuan',
          'hunpo', 'zuida_hunpo', 'sanzhao_CD', 'gushi_xiulian_dangqian']
for f in fields:
    hits = [n for n in names if f.encode() in z.read(n)]
    readers = len(hits)
    writers = [n for n in hits if b'putfield' in z.read(n)]
    print(f'{f:22s} refs={readers:4d}  (putfield-capable files={len(writers)})')
    for n in hits[:3]:
        print('      ', n.split('/')[-1])
