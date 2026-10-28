"""Package the mod jar from javac output + resources, and verify VFX classes are inside."""
import os, re, shutil, subprocess, zipfile

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
CLASSES = os.path.join(ROOT, '_crash3', 'javac_out')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
JAR = r'C:\Users\ADMIN\.jdks\temurin-21\bin\jar.exe'
TOML_REL = os.path.join('META-INF', 'neoforge.mods.toml')


def strip_bom(path):
    """TOML 解析器不接受 BOM：开头的 U+FEFF 会让 `modLoader` 变成非法裸键，
    整包被 FML 判为 'not a valid mod file'（2026-10-20 实际炸过一次）。
    任何编辑器/脚本都可能带进 BOM，所以打包前一律剥掉。"""
    with open(path, 'rb') as f:
        b = f.read()
    if b[:3] == b'\xef\xbb\xbf':
        with open(path, 'wb') as f:
            f.write(b[3:])
        print('  去掉 BOM:', os.path.relpath(path, ROOT))
        return True
    return False


def validate_toml():
    """打包前校验：无 BOM、且能被真正的 TOML 解析器读出来。"""
    toml = os.path.join(RES, TOML_REL)
    strip_bom(toml)
    raw = open(toml, 'rb').read()
    if raw[:3] == b'\xef\xbb\xbf':
        raise SystemExit('mods.toml 仍有 BOM，FML 会拒绝加载')
    text = raw.decode('utf-8')
    try:
        import tomllib
        with open(toml, 'rb') as f:
            data = tomllib.load(f)
    except ModuleNotFoundError:
        # Python < 3.11 没有 tomllib，退化为结构检查
        data = None
        if not re.search(r'^\s*modLoader\s*=', text, re.M):
            raise SystemExit('mods.toml 缺少 modLoader（可能被 BOM 破坏）')
    except Exception as e:
        raise SystemExit(f'mods.toml 不是合法 TOML：{e}')
    if data is not None:
        if 'mods' not in data:
            raise SystemExit('mods.toml 缺少 [[mods]] 段')
        mods = data['mods'][0]
        for k in ('modId', 'version'):
            if k not in mods:
                raise SystemExit(f'mods.toml 的 [[mods]] 缺少 {k}')
    return text


def read_version():
    """版本号唯一来源 version.properties；mods.toml 与它不一致就直接报错，防止再次漂移。"""
    props = os.path.join(ROOT, 'version.properties')
    ver = None
    with open(props, encoding='utf-8') as f:
        for line in f:
            m = re.match(r'\s*version\s*=\s*(\S+)', line)
            if m:
                ver = m.group(1)
                break
    if not ver:
        raise SystemExit('version.properties 里没有 version=…')
    text = validate_toml()
    tm = re.search(r'^\s*version\s*=\s*"([^"]+)"', text, re.M)
    if not tm:
        raise SystemExit('neoforge.mods.toml 里没有 version = "…"')
    if tm.group(1) != ver:
        raise SystemExit(f'版本不一致：version.properties={ver} 但 mods.toml={tm.group(1)}'
                         f'\n  跑 _crash3/bump_version.py 或手工同步后再打包。')
    return ver


VERSION = read_version()
OUT = os.path.join(ROOT, 'build', 'libs', f'tianfu-{VERSION}.jar')

staging = os.path.join(ROOT, '_crash3', 'jar_stage')
if os.path.isdir(staging):
    shutil.rmtree(staging)
os.makedirs(staging)

# classes
for root, _, files in os.walk(CLASSES):
    for fn in files:
        if fn.endswith('.class'):
            src = os.path.join(root, fn)
            rel = os.path.relpath(src, CLASSES)
            dst = os.path.join(staging, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)

# resources (skip .java)
for root, _, files in os.walk(RES):
    for fn in files:
        src = os.path.join(root, fn)
        rel = os.path.relpath(src, RES)
        dst = os.path.join(staging, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)

n_cls = sum(len([f for f in fs if f.endswith('.class')]) for _, _, fs in os.walk(staging))
print('version:', VERSION)
print('staged classes:', n_cls)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
r = subprocess.run([JAR, '--create', '--file', OUT, '-C', staging, '.'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('jar exit:', r.returncode, r.stderr[-800:] if r.stderr else '')

# verify
with zipfile.ZipFile(OUT) as z:
    names = z.namelist()
    # 终检成品：FML 会拒绝带 BOM 的 toml，这里直接对 jar 内字节复查一次
    toml_bytes = z.read('META-INF/neoforge.mods.toml')
print('jar size:', os.path.getsize(OUT), 'entries:', len(names))
if toml_bytes[:3] == b'\xef\xbb\xbf':
    raise SystemExit('jar 内 mods.toml 带 BOM —— FML 会判为 not a valid mod file')
try:
    import tomllib
    jar_toml = tomllib.loads(toml_bytes.decode('utf-8'))
except ModuleNotFoundError:
    jar_toml = None
except Exception as e:
    raise SystemExit(f'jar 内 mods.toml 不是合法 TOML：{e}')
if jar_toml is not None:
    got = jar_toml['mods'][0]['version']
    if got != VERSION:
        raise SystemExit(f'jar 内版本 {got} != version.properties {VERSION}')
    print('jar 内 mods.toml: OK (无 BOM, 合法 TOML, version=%s)' % got)
for probe in ['net/guzhenren/tianfu/TianfuVfx.class',
              'net/guzhenren/tianfu/TianfuEffects.class',
              'net/guzhenren/tianfu/TianfuApply.class',
              'META-INF/neoforge.mods.toml',
              'tianfu.mixins.json',
              'assets/tianfu/lang/zh_cn.json']:
    print(('OK  ' if probe in names else 'MISS'), probe)
print('OUT =', OUT)
