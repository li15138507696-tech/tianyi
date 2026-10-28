"""离线编译：从启动器 profile 拼 classpath，javac 编译 src/main/java 到 _pack/javac_out。

与 tianfu/_crash3/javac_check.py 同款，只改了工程根与输出目录。
每次编译前清空输出目录——javac 只写不删，源码改名后旧 .class 会被打进 jar。
"""
import glob, json, os, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MC_DIR = os.environ.get('TIANYI_MINECRAFT_DIR', r'D:/game/mcmod/1/.minecraft')
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, 'reconfigure'):
        _stream.reconfigure(encoding='utf-8', errors='replace')
LIBS = os.path.join(MC_DIR, 'libraries')
# 编译依据：用户指定的 10.20 本体包内提取的真实本体 jar（见 _pack/libs/README.txt）
# 优先用环境变量（一键构建 --ephemeral 会传临时路径），否则用工程内缓存副本
GUZHENREN = os.environ.get('TIANYI_UPSTREAM_JAR') or os.path.join(
    os.path.dirname(os.path.abspath(__file__)), 'libs',
    'guzhenren-0.1-neoforge-1.21.1.jar')
# 可选前置：Numen 的 API jar（从 numen-neoforge-*.jar 的 META-INF/jarjar 里抽出来的）。
# 只用于编译：运行期由 Numen 自己以 JiJ 提供，玩家没装就不加载我们的 numen 桥。
NUMEN_API = os.path.join(ROOT, '_pack', 'libs', 'numen_api-neoforge-1.21.1-0.1.4.1.jar')
# 外层 guzhenren_library（势力/NPC/任务/身份）API：由 extract_library_api.py 从
# D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar 抽出 com/guzhenrenlibrary/** 生成。
# 运行期由整合包里的 guzhenren_library 本体提供，我们在 neoforge.mods.toml 里声明依赖。
LIBRARY_API = os.path.join(ROOT, '_pack', 'libs', 'guzhenren_library-api.jar')
SRC = os.path.join(ROOT, 'src', 'main', 'java')
OUT = os.path.join(ROOT, '_pack', 'javac_out')
JAVAC = os.environ.get('TIANYI_JAVAC', r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe')

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
else:
    sys.exit('missing guzhenren jar: ' + GUZHENREN)
if os.path.isfile(NUMEN_API):
    cp.append(NUMEN_API)
    print('numen_api: OK（可选前置，编译期）')
else:
    print('numen_api: 缺失（AI-Numen 桥会编译失败，把 numen 的 api jar 放到 _pack/libs/）')
if os.path.isfile(LIBRARY_API):
    cp.append(LIBRARY_API)
    print('library_api: OK（guzhenren_library 势力/NPC/任务/身份）')
else:
    sys.exit('missing library api jar: ' + LIBRARY_API
             + '\n  跑 python _pack/extract_library_api.py 重新生成')
print('runtime jars:', n_rt, '+ guzhenren')

sources = []
for root, _, files in os.walk(SRC):
    for fn in files:
        if fn.endswith('.java'):
            sources.append(os.path.join(root, fn))
print('sources:', len(sources))

os.makedirs(OUT, exist_ok=True)
for _fn in os.listdir(OUT):
    if _fn == 'args.txt':
        continue
    _p = os.path.join(OUT, _fn)
    if os.path.isdir(_p):
        shutil.rmtree(_p)
    else:
        os.remove(_p)

def javac_arg(value):
    # javac argument files use their own quoting, independent of shell quoting.
    return '"' + value.replace('\\', '/').replace('"', '\\"') + '"'


argfile = os.path.join(OUT, 'args.txt')
with open(argfile, 'w', encoding='utf-8') as fp:
    fp.write('-encoding\nUTF-8\n--release\n21\n-proc:none\n-d\n%s\n-cp\n%s\n' %
             (javac_arg(OUT), javac_arg(os.pathsep.join(cp))))
    fp.write('\n'.join(javac_arg(source) for source in sources) + '\n')

r = subprocess.run([JAVAC, '@' + argfile], capture_output=True, text=True, encoding='utf-8', errors='replace')
print('javac exit:', r.returncode)
out = (r.stdout or '') + (r.stderr or '')
print(out[-8000:])
sys.exit(r.returncode)
