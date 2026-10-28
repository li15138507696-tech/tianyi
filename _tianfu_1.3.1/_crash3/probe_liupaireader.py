"""Inspect the community liupai-reader addon: what field does it read for 流派熟练度?"""
import sys
import zipfile

JAR = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods\蛊真人流派熟练度查看.jar'
sys.stdout.reconfigure(encoding='utf-8')
z = zipfile.ZipFile(JAR)
for n in z.namelist():
    if n.endswith('.class'):
        b = z.read(n)
        # crude constant pool string scan
        out = []
        i = 0
        while i < len(b) - 3:
            if b[i] == 1:
                ln = b[i + 1] * 256 + b[i + 2]
                if 2 <= ln < 200 and i + 3 + ln <= len(b):
                    try:
                        s = b[i + 3:i + 3 + ln].decode('utf-8')
                    except UnicodeDecodeError:
                        i += 1
                        continue
                    if all(32 <= ord(c) < 0x3000 for c in s) and s.isprintable():
                        out.append(s)
                i += 1 + ln
            else:
                i += 1
        interesting = [s for s in dict.fromkeys(out)
                       if 'liupai' in s.lower() or 'daohen' in s.lower()
                       or '熟练' in s or '流派' in s]
        if interesting:
            print('===', n.split('/')[-1])
            for s in interesting[:25]:
                print('   ', s)
