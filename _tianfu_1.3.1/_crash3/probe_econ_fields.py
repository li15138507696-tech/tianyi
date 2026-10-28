"""Design-critical numbers: GongXianZhi type/clamp, renqi clamp, and 5-tier feasibility."""
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)

pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')
print('=== field descriptors for economy fields ===')
# crude: find "GongXianZhi" and look backwards for descriptor
for name in (b'GongXianZhi', b'renqi', b'meilizhi', b'yuanshishuliang', b'shili', b'shenfen'):
    pos = 0
    while True:
        p = pv.find(name, pos)
        if p < 0:
            break
        seg = pv[max(0, p - 40):p + 40]
        # descriptors appear as Utf8 entries: D or Ljava/lang/String;
        near = [d for d in (b'D', b'Ljava/lang/String;') if d in seg]
        print(' ', name.decode(), 'at', p, '-> near:', [x.decode() for x in near])
        pos = p + 1

print()
print('=== does KE1 clamp GongXianZhi? ===')
ke1 = z.read('net/guzhenren/procedures/KE1Procedure.class')
print('  KE1 size', len(ke1), '| GongXianZhi in KE1:', b'GongXianZhi' in ke1)
for tok in (b'GongXianZhi', b'renqi', b'meilizhi', b'yuanshishuliang'):
    print('  ', tok.decode(), '->', tok in ke1)

print()
print('=== FengYuLou3Procedure (贡献 gameplay source) ===')
f = z.read('net/guzhenren/procedures/FengYuLou3Procedure.class')
i = 0
strs = []
while i < len(f) - 3:
    if f[i] == 1:
        ln = f[i + 1] * 256 + f[i + 2]
        if 2 <= ln < 200 and i + 3 + ln <= len(f):
            try:
                s = f[i + 3:i + 3 + ln].decode('utf-8')
                if s.isprintable():
                    strs.append(s)
            except UnicodeDecodeError:
                pass
        i += 1 + ln
    else:
        i += 1
print('  ', [s for s in dict.fromkeys(strs) if 'Gong' in s or 'Xian' in s or 'gong' in s][:10])
