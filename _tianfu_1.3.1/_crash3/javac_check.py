"""Build the offline classpath from the NeoForge profile, then javac-compile the mod."""
import glob, json, os, subprocess, sys, tempfile

MC_DIR = r'D:/game/mcmod/1/.minecraft'
LIBS = os.path.join(MC_DIR, 'libraries')
GUZHENREN = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
SRC = r'D:\daima\mcmod\天意\_tianfu_1.3.1\src\main\java'
OUT = r'D:\daima\mcmod\天意\_tianfu_1.3.1\_crash3\javac_out'
JAVAC = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe'

# 1. find the NeoForge profile (same rule as build.gradle)
best, best_count = None, -1
for ver_dir in sorted(glob.glob(os.path.join(MC_DIR, 'versions', '*'))):
    for f in glob.glob(os.path.join(ver_dir, '*.json')):
        try:
            parsed = json.load(open(f, encoding='utf-8'))
        except Exception:
            continue
        if not isinstance(parsed, dict):
            continue
        game_args = parsed.get('arguments', {}).get('game', [])
        strs = [a for a in game_args if isinstance(a, str)]
        if '--fml.neoForgeVersion' not in strs:
            continue
        count = len(parsed.get('libraries', []))
        if count > best_count:
            best, best_count = parsed, count

if best is None:
    sys.exit('no NeoForge profile found')

def arg(key):
    strs = [a for a in best['arguments']['game'] if isinstance(a, str)]
    return strs[strs.index(key) + 1]

neo = arg('--fml.neoForgeVersion')
mc = arg('--fml.mcVersion')
nfs = arg('--fml.neoFormVersion')
print(f'profile: neoforge={neo} mc={mc}-{nfs} libs={best_count}')

core = [
    os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-client.jar'),
    os.path.join(LIBS, f'net/minecraft/client/{mc}-{nfs}/client-{mc}-{nfs}-srg.jar'),
    os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-universal.jar'),
]
for c in core:
    if not os.path.isfile(c):
        sys.exit('missing core jar: ' + c)
print('core jars:', len(core))

cp = list(core)
n_rt = 0
for lib in best.get('libraries', []):
    p = lib.get('downloads', {}).get('artifact', {}).get('path')
    if p and os.path.isfile(os.path.join(LIBS, p)):
        cp.append(os.path.join(LIBS, p))
        n_rt += 1
if os.path.isfile(GUZHENREN):
    cp.append(GUZHENREN)
print('runtime jars:', n_rt, '+ guzhenren')

# 2. compile all mod sources
sources = []
for root, _, files in os.walk(SRC):
    for fn in files:
        if fn.endswith('.java'):
            sources.append(os.path.join(root, fn))
print('sources:', len(sources))

os.makedirs(OUT, exist_ok=True)
# javac 只写不删：源码删掉/改名后，旧 .class 会一直留在 javac_out 里并被 pack_jar 打进 jar
# （曾导致「类已改名，jar 里还留着引用旧本体类的那份」，在 9.8 上会 NoClassDefFoundError）。
# 每次编译前清空输出目录。
import shutil
for _fn in os.listdir(OUT):
    if _fn == 'args.txt':
        continue
    _p = os.path.join(OUT, _fn)
    if os.path.isdir(_p):
        shutil.rmtree(_p)
    else:
        os.remove(_p)

argfile = os.path.join(OUT, 'args.txt')
with open(argfile, 'w', encoding='utf-8') as fp:
    fp.write('-encoding\nUTF-8\n-proc:none\n-d\n%s\n-cp\n%s\n' % (OUT, ';'.join(cp)))
    fp.write('\n'.join(sources) + '\n')

r = subprocess.run([JAVAC, '@' + argfile], capture_output=True, text=True, encoding='utf-8', errors='replace')
print('javac exit:', r.returncode)
out = (r.stdout or '') + (r.stderr or '')
print(out[-6000:])
sys.exit(r.returncode)
