"""jar 结构校验：清单、mods.toml、mixin 配置、关键类与资源、无 BOM。"""
import json, os, re, sys, zipfile

ROOT = r'D:\daima\mcmod\天意'


def read_version():
    with open(os.path.join(ROOT, 'version.properties'), encoding='utf-8') as f:
        for line in f:
            m = re.match(r'\s*version\s*=\s*(\S+)', line)
            if m:
                return m.group(1)
    raise SystemExit('version.properties 里没有 version=…')


VER = read_version()
JAR = os.path.join(ROOT, 'build', 'libs', f'tianyi-{VER}.jar')
if not os.path.isfile(JAR):
    raise SystemExit('jar 不存在：' + JAR)

problems = []

with zipfile.ZipFile(JAR) as z:
    names = set(z.namelist())
    cls = [n for n in names if n.endswith('.class')]
    print('jar:', JAR)
    print('size:', os.path.getsize(JAR), 'bytes')
    print('entries:', len(names), 'classes:', len(cls))

    # 1. manifest
    mf_bytes = z.read('META-INF/MANIFEST.MF')
    mf = mf_bytes.decode('utf-8', 'replace')
    m = re.search(r'Implementation-Version:\s*(\S+)', mf)
    if not m:
        problems.append('MANIFEST 缺 Implementation-Version')
    elif m.group(1) != VER:
        problems.append(f'MANIFEST 版本 {m.group(1)} != {VER}')
    else:
        print('MANIFEST version: OK', m.group(1))

    # 1b. 清单格式（B12：曾经写成 \r\r\n，java.util.jar.JarFile 直接抛 invalid manifest format）
    if b'\r\r\n' in mf_bytes:
        problems.append('MANIFEST 含 \\r\\r\\n（非法行尾，JarFile.getManifest 会抛异常）')
    if not mf_bytes.rstrip(b'\r\n').endswith(b'Vendor: tianyi'):
        problems.append('MANIFEST 结尾异常（应以空行结束）')
    for i, line in enumerate(mf_bytes.split(b'\r\n'), 1):
        if line == b'':
            continue
        if b': ' not in line:
            problems.append(f'MANIFEST 第 {i} 行不是 "Key: value" 形式：{line[:40]!r}')
        if len(line) > 72:
            problems.append(f'MANIFEST 第 {i} 行超过 72 字节（需折行）')
    if b'\r\r\n' not in mf_bytes and not any(
            p.startswith('MANIFEST 第') for p in problems):
        print('MANIFEST format: OK（行尾 \\r\\n / 每行 Key: value / 无 \\r\\r\\n）')

    # 2. mods.toml
    raw = z.read('META-INF/neoforge.mods.toml')
    if raw[:3] == b'\xef\xbb\xbf':
        problems.append('mods.toml 带 BOM')
    try:
        import tomllib
        data = tomllib.loads(raw.decode('utf-8'))
        mods = data['mods'][0]
        if mods['modId'] != 'tianyi':
            problems.append(f"modId 是 {mods['modId']}，应为 tianyi")
        if mods['version'] != VER:
            problems.append(f"mods.toml 版本 {mods['version']} != {VER}")
        deps = [d['modId'] for d in data.get('dependencies', {}).get('tianyi', [])]
        for need in ('neoforge', 'minecraft', 'guzhenren'):
            if need not in deps:
                problems.append(f'mods.toml 缺少依赖 {need}')
        print('mods.toml: OK  deps =', deps)
    except ModuleNotFoundError:
        print('mods.toml: 跳过 TOML 解析（Python < 3.11）')
    except Exception as e:
        problems.append(f'mods.toml 解析失败：{e}')

    # 3. mixins：2.0.0 起本模组**没有任何 mixin**（三个子系统的 5 个都随包删除）
    mx_files = [n for n in names if n.endswith('.mixins.json')]
    if mx_files:
        problems.append('仍残留 mixin 配置（2.0.0 应为零）：' + ', '.join(mx_files))
    toml_text = z.read('META-INF/neoforge.mods.toml').decode('utf-8-sig')
    if '[[mixins]]' in toml_text:
        problems.append('mods.toml 仍声明 [[mixins]]（2.0.0 应为零）')
    mx_classes = [n for n in names if '/mixin/' in n and n.endswith('.class')]
    if mx_classes:
        problems.append(f'仍残留 {len(mx_classes)} 个 mixin 类')
    print('mixins: OK 0 个（2.0.0 不注入本体）')

    # 4. 关键类（3.0.0 骨架：入口 + 世界接线 + 纯逻辑核心）
    for probe in ('net/guzhenren/tianyi/TianyiMod.class',
                  'net/guzhenren/tianyi/TianyiEvents.class',
                  'net/guzhenren/tianyi/TianyiCommands.class',
                  'net/guzhenren/tianyi/legacy/LegacyItems.class',
                  'net/guzhenren/tianyi/will/WillCore.class',
                  'net/guzhenren/tianyi/will/Knowledge.class',
                  'net/guzhenren/tianyi/will/Assessment.class',
                  'net/guzhenren/tianyi/will/Calamity.class',
                  'net/guzhenren/tianyi/will/Immortal.class',
                  'net/guzhenren/tianyi/will/Destiny.class',
                  'net/guzhenren/tianyi/will/Carrier.class',
                  'net/guzhenren/tianyi/will/Counter.class',
                  'net/guzhenren/tianyi/will/History.class',
                  'net/guzhenren/tianyi/world/WillState.class',
                  'net/guzhenren/tianyi/world/WillServer.class',
                  'net/guzhenren/tianyi/world/SnapshotService.class',
                  # 3.1.0 起：本体桥接 / 世界事实 / 情报网络 / 布局步骤
                  'net/guzhenren/tianyi/bridge/GuzhenrenBridge.class',
                  'net/guzhenren/tianyi/fact/WorldFact.class',
                  'net/guzhenren/tianyi/fact/FactLedger.class',
                  'net/guzhenren/tianyi/fact/IntelNet.class',
                  'net/guzhenren/tianyi/action/ActResult.class',
                  'net/guzhenren/tianyi/action/ActionPlan.class',
                  'net/guzhenren/tianyi/action/ActionLedger.class',
                  'net/guzhenren/tianyi/action/InterventionAction.class',
                  'net/guzhenren/tianyi/action/WorldView.class',
                  'net/guzhenren/tianyi/world/IntelState.class',
                  'net/guzhenren/tianyi/world/FactSource.class',
                  'net/guzhenren/tianyi/world/LiveWorld.class',
                  'net/guzhenren/tianyi/action/impl/InvestigateAction.class',
                  'net/guzhenren/tianyi/action/impl/PursueAction.class'):
        if probe not in names:
            problems.append('缺类：' + probe)

    # 4c. 附属绝不能把本体打进自己的 jar（那是 108MB 的 jar-in-jar），
    #     但自家类就在 net/guzhenren/tianyi/ 下，必须先排除掉再判。
    OWN = 'net/guzhenren/tianyi/'
    for n in names:
        if n.startswith('META-INF/jarjar/') and n.endswith('.jar'):
            problems.append('附属里混进了本体或其它模组 jar：' + n)
        if n.startswith('com/guzhenrenlibrary/'):
            problems.append('附属里混进了外层扩展的类：' + n)
        if n.startswith('net/guzhenren/') and not n.startswith(OWN):
            problems.append('附属里混进了内层本体的类：' + n)

    # 4b. 已退场的旧玩法骨架不得复活
    #     例外：client/ 与 net/ 只为《窥天录》重新引入，所以按**类名白名单**放行，
    #     而不是整目录放行（旧面板/旧整包同步仍然进不来）。
    for gone in ('net/guzhenren/tianyi/path/', 'net/guzhenren/tianyi/shop/',
                 'net/guzhenren/tianyi/quest/', 'net/guzhenren/tianyi/open/',
                 'net/guzhenren/tianyi/contract/'):
        if any(n.startswith(gone) for n in names):
            problems.append('残留已退场子系统：' + gone)
    allowed_client = {
        'net/guzhenren/tianyi/client/JournalKeys',
        'net/guzhenren/tianyi/client/JournalScreen',
        'net/guzhenren/tianyi/client/JournalButton',
        'net/guzhenren/tianyi/client/JournalClientState',
        'net/guzhenren/tianyi/client/JournalClientEvents',
        'net/guzhenren/tianyi/client/JournalClientSetup',
        'net/guzhenren/tianyi/client/JournalClientHandler',
    }
    allowed_net = {
        'net/guzhenren/tianyi/net/JournalSyncPayload',
        'net/guzhenren/tianyi/net/JournalActionPayload',
        'net/guzhenren/tianyi/net/JournalNet',
        'net/guzhenren/tianyi/net/EncounterSyncPayload',
        'net/guzhenren/tianyi/net/EncounterActionPayload',
    }
    for n in names:
        if not n.endswith('.class'):
            continue
        stem = n[:-6].split('$')[0]
        if stem.startswith('net/guzhenren/tianyi/client/') and stem not in allowed_client:
            problems.append('客户端里出现非见闻录类（旧面板不得回潮）：' + n)
        if stem.startswith('net/guzhenren/tianyi/net/') and stem not in allowed_net:
            problems.append('net 里出现非见闻录包（旧整包同步不得回潮）：' + n)
    for gone in ('TianyiEcon', 'TianyiSign', 'TianyiLuck', 'TianyiPavilion', 'LifeBook',
                 'TianyiInsight', 'TianyiCausality', 'TianyiFate', 'TianyiBoard', 'TianyiNet'):
        if f'net/guzhenren/tianyi/{gone}.class' in names:
            problems.append(f'残留已退场类：{gone}')

    # 5. 资源
        if probe not in names:
            problems.append('缺资源：' + probe)

    # 6. 任何 json/toml 都不许带 BOM
    for n in names:
        if n.endswith(('.json', '.toml', '.mcmeta')):
            if z.read(n)[:3] == b'\xef\xbb\xbf':
                problems.append('带 BOM：' + n)

    # 7. 不该混进旧 modid 的 mixin 配置
    for n in names:
        if n in ('tianfu.mixins.json', 'daotu.mixins.json', 'shenshang.mixins.json'):
            problems.append('残留旧 mixin 配置：' + n)

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('ALL OK')
