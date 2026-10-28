"""Probe zhuanshu/jieduan progression + attribute modifier APIs available to a new addon."""
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
names = [n for n in z.namelist() if n.startswith('net/guzhenren/') and n.endswith('.class')]

print('=== commands (progression entry points) ===')
for n in names:
    if '/command/' in n:
        print('  ', n.split('/')[-1])

print()
print('=== KE1 / KE2 / KE3 (per-tick drivers) exist? ===')
for n in names:
    b = n.split('/')[-1]
    if b.startswith('KE'):
        print('  ', b)

print()
print('=== attributes registered by base ===')
init = [n for n in names if '/init/' in n]
for n in init[:20]:
    print('  ', n.split('/')[-1])
