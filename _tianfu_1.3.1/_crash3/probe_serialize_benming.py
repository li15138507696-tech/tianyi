"""Verify shili/shenfen persistence + find where 本命蛊 choice is stored."""
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)

pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
print('=== PlayerVariables serialization of shili/shenfen ===')
for tok in (b'shili', b'shenfen'):
    print(' ', tok.decode(), 'occurrences:', pv.count(tok))
print('  putString refs:', pv.count(b'putString'))
print('  getString refs:', pv.count(b'getString'))
print('  "shili" putString adjacency:',
      pv.find(b'shili') >= 0, pv.find(b'shenfen') >= 0)

print()
print('=== 本命蛊: which PlayerVariables field? scan BenMingCommand ===')
cmd = z.read('net/guzhenren/command/BenMingCommand.class')
print('  BenMingCommand size', len(cmd))
i = 0
strs = []
while i < len(cmd) - 3:
    if cmd[i] == 1:
        ln = cmd[i + 1] * 256 + cmd[i + 2]
        if 2 <= ln < 200 and i + 3 + ln <= len(cmd):
            try:
                s = cmd[i + 3:i + 3 + ln].decode('utf-8')
                if s.isprintable():
                    strs.append(s)
            except UnicodeDecodeError:
                pass
        i += 1 + ln
    else:
        i += 1
print('  strings:', list(dict.fromkeys(strs))[:40])

print()
print('=== 本命蛊 candidate fields in PlayerVariables ===')
import re
for m in sorted(set(re.findall(rb'[A-Za-z_]*[Bb]en[Mm]ing[A-Za-z_]*', pv))):
    print('  ', m.decode())
