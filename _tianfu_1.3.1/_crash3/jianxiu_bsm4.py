import subprocess, re, os

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, 'guzhenren_兼修附属0.7.jar')
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

out = subprocess.run([javap, '-v', '-p', '-classpath', p,
                      'cn.blockforge.generated.minecraftjsontoml16x.IceContent'],
                     capture_output=True, text=True, encoding='utf-8', errors='replace')
lines = out.stdout.splitlines()

start = next(i for i, l in enumerate(lines) if 'BootstrapMethods:' in l)

# parse blocks: "  N: #x REF_..." then lines until next "  N:"
entries = {}
cur = None
for l in lines[start+1:]:
    m = re.match(r'^\s{2}(\d+): #(\d+) ', l)
    if m:
        cur = int(m.group(1))
        entries[cur] = []
    elif cur is not None:
        entries[cur].append(l.strip())

print('total bootstrap entries:', len(entries))
for k in (43, 44, 45):
    if k in entries:
        print(f'--- bsm #{k} ---')
        for x in entries[k]:
            print('    ', x)
