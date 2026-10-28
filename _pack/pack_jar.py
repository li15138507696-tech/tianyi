"""打包：javac 输出 + 资源 → build/libs/tianyi-<version>.jar，并做结构与 BOM 校验。"""
import os, re, shutil, zipfile

ROOT = r'D:\daima\mcmod\天意'
CLASSES = os.path.join(ROOT, '_pack', 'javac_out')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
TOML_REL = os.path.join('META-INF', 'neoforge.mods.toml')


def strip_bom(path):
    """TOML 解析器不接受 BOM：开头的 U+FEFF 会让 modLoader 变成非法裸键，整包被 FML 判为无效。"""
    with open(path, 'rb') as f:
        b = f.read()
    if b[:3] == b'\xef\xbb\xbf':
        with open(path, 'wb') as f:
            f.write(b[3:])
        print('  去掉 BOM:', os.path.relpath(path, ROOT))
        return True
    return False


def validate_toml():
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
        if mods['modId'] != 'tianyi':
            raise SystemExit(f"mods.toml 的 modId 是 {mods['modId']}，应为 tianyi")
    return text


def read_version():
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
                         f'\n  跑 _pack/bump_version.py 或手工同步后再打包。')
    return ver


VERSION = read_version()
OUT = os.path.join(ROOT, 'build', 'libs', f'tianyi-{VERSION}.jar')

staging = os.path.join(ROOT, '_pack', 'jar_stage')
if os.path.isdir(staging):
    shutil.rmtree(staging)
os.makedirs(staging)

for root, _, files in os.walk(CLASSES):
    for fn in files:
        if fn.endswith('.class'):
            src = os.path.join(root, fn)
            rel = os.path.relpath(src, CLASSES)
            dst = os.path.join(staging, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)

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

# MANIFEST.MF：NeoForge 不强制，但保留 Implementation-Version 便于人工核对与 verify_jar 校验。
#
# 踩过的坑（3.7.0-review B12）：以前用 newline='\r\n' 打开、字符串里又手写 '\r\n'，
# 结果每行变成 '\r\r\n'，java.util.jar.JarFile.getManifest() 直接抛
# "invalid manifest format"。现在改成：**二进制写**，换行只由这里决定。
os.makedirs(os.path.join(staging, 'META-INF'), exist_ok=True)
_mf_lines = [
    'Manifest-Version: 1.0',
    'Specification-Title: tianyi',
    'Specification-Vendor: tianyi',
    'Specification-Version: 1',
    'Implementation-Title: tianyi',
    f'Implementation-Version: {VERSION}',
    'Implementation-Vendor: tianyi',
    '',  # 清单必须以空行结束
]
with open(os.path.join(staging, 'META-INF', 'MANIFEST.MF'), 'wb') as mf:
    mf.write('\r\n'.join(_mf_lines).encode('utf-8'))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
if os.path.isfile(OUT):
    os.remove(OUT)

with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(staging):
        for fn in files:
            full = os.path.join(root, fn)
            rel = os.path.relpath(full, staging).replace(os.sep, '/')
            z.write(full, rel)

print('jar written:', OUT, os.path.getsize(OUT), 'bytes')

# ---- 打包后自检 ----
with zipfile.ZipFile(OUT) as z:
    names = set(z.namelist())
    toml_bytes = z.read('META-INF/neoforge.mods.toml')
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

probes = [
    # 入口与接线
    'net/guzhenren/tianyi/TianyiMod.class',
    'net/guzhenren/tianyi/TianyiEvents.class',
    'net/guzhenren/tianyi/TianyiCommands.class',
    'net/guzhenren/tianyi/legacy/LegacyItems.class',
    # 纯逻辑核心（will/）：必须真的进包，不能只在源码里
    'net/guzhenren/tianyi/will/WillConfig.class',
    'net/guzhenren/tianyi/will/Knowledge.class',
    'net/guzhenren/tianyi/will/Assessment.class',
    'net/guzhenren/tianyi/will/Intent.class',
    'net/guzhenren/tianyi/will/WillCore.class',
    'net/guzhenren/tianyi/will/Ploy.class',
    'net/guzhenren/tianyi/will/Opportunity.class',
    'net/guzhenren/tianyi/will/Calamity.class',
    'net/guzhenren/tianyi/will/Immortal.class',
    'net/guzhenren/tianyi/will/Destiny.class',
    'net/guzhenren/tianyi/will/Carrier.class',
    'net/guzhenren/tianyi/will/Counter.class',
    'net/guzhenren/tianyi/will/History.class',
    # 世界接线
    'net/guzhenren/tianyi/world/WillState.class',
    'net/guzhenren/tianyi/world/WillServer.class',
    'net/guzhenren/tianyi/world/Observation.class',
    'net/guzhenren/tianyi/world/WorldExec.class',
    'net/guzhenren/tianyi/world/SnapshotService.class',
    'net/guzhenren/tianyi/world/LegacyRetire.class',

    # 元数据与资源
    'META-INF/neoforge.mods.toml',
]
# 旧商店的价目数据已随商店删除，这两个资源不再要求存在（留着也不影响运行）
optional = [
]
missing = 0
for probe in probes:
    ok = probe in names
    missing += 0 if ok else 1
    print(('OK  ' if ok else 'MISS'), probe)
if missing:
    raise SystemExit(f'{missing} 个必需条目缺失')
print('OUT =', OUT)
