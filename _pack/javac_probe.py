"""单文件/少量文件编译探针：不碰 _pack/javac_out，避免与全量编译互相踩。

用法：
    python _pack/javac_probe.py src/main/java/.../Foo.java [更多 .java]

输出目录 _pack/_probe_out（每次清空）。用途：改了单个类时先快速验证签名，
不用等全量编译。全量验证仍然要跑 javac_check.py。
"""
import glob
import json
import os
import shutil
import subprocess
import sys

ROOT = r'D:/daima/mcmod/天意'
MC_DIR = r'D:/game/mcmod/1/.minecraft'
LIBS = os.path.join(MC_DIR, 'libraries')
GUZHENREN = os.path.join(ROOT, '_pack', 'libs', 'guzhenren-0.1-neoforge-1.21.1.jar')
LIBRARY_API = os.path.join(ROOT, '_pack', 'libs', 'guzhenren_library-api.jar')
NUMEN_API = os.path.join(ROOT, '_pack', 'libs', 'numen_api-neoforge-1.21.1-0.1.4.1.jar')
OUT = os.path.join(ROOT, '_pack', '_probe_out')
JAVAC = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe'


def build_cp():
    best, best_count = None, -1
    for ver_dir in sorted(glob.glob(os.path.join(MC_DIR, 'versions', '*'))):
        for f in glob.glob(os.path.join(ver_dir, '*.json')):
            try:
                parsed = json.load(open(f, encoding='utf-8'))
            except Exception:
                continue
            if not isinstance(parsed, dict):
                continue
            strs = [a for a in parsed.get('arguments', {}).get('game', []) if isinstance(a, str)]
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

    neo, mc, nfs = arg('--fml.neoForgeVersion'), arg('--fml.mcVersion'), arg('--fml.neoFormVersion')
    cp = [
        os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-client.jar'),
        os.path.join(LIBS, f'net/minecraft/client/{mc}-{nfs}/client-{mc}-{nfs}-srg.jar'),
        os.path.join(LIBS, f'net/neoforged/neoforge/{neo}/neoforge-{neo}-universal.jar'),
    ]
    for lib in best.get('libraries', []):
        p = lib.get('downloads', {}).get('artifact', {}).get('path')
        full = os.path.join(LIBS, p) if p else None
        if full and os.path.isfile(full):
            cp.append(full)
    for extra in (GUZHENREN, LIBRARY_API, NUMEN_API):
        if os.path.isfile(extra):
            cp.append(extra)
    return cp


def main(argv):
    if not argv:
        sys.exit('usage: javac_probe.py <file.java> [...]')
    sources = [os.path.abspath(a) for a in argv]
    for s in sources:
        if not os.path.isfile(s):
            sys.exit('missing source: ' + s)
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    cp = build_cp()
    # 全量源码根也上 sourcepath，这样探针能解析本工程其它类
    srcroot = os.path.join(ROOT, 'src', 'main', 'java')
    argfile = os.path.join(OUT, 'args.txt')
    with open(argfile, 'w', encoding='utf-8') as fp:
        fp.write('-encoding\nUTF-8\n-proc:none\n-d\n%s\n-cp\n%s\n-sourcepath\n%s\n'
                 % (OUT, ';'.join(cp), srcroot))
        fp.write('\n'.join(sources) + '\n')
    r = subprocess.run([JAVAC, '@' + argfile], capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    print('javac exit:', r.returncode)
    print(((r.stdout or '') + (r.stderr or ''))[-8000:])
    return r.returncode


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
