"""Numen 接入审计（1.0.6）。

Numen 是**可选前置**。软依赖最容易翻车的地方是「没装却碰了它的类」——那会在类加载期
炸掉整个模组。所以本脚本锁死六条：

1. 所有 `com.dwinovo.numen.*` 的 import 只允许出现在 `ai/numen/` 下（收口）；
2. 判断「装没装」只能用 `TianyiNumenPresence`（不含 Numen 类型），不允许在别处直接碰桥；
3. 桥的调用点必须带 `TianyiNumenPresence.isLoaded()` 守卫（短路求值才不加载桥类）；
4. 工具 schema 的目标枚举必须与 `TianyiAiSnapshot.OBJECTIVES` 完全一致；
5. 工具**不得**接受任何奖励参数（点数/物品），奖励只能服务端按难度算；
6. `neoforge.mods.toml` 里 `numen_api` 必须声明为 optional（不能 required）。
"""
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意'
BASE = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
AI = os.path.join(BASE, 'ai')
NUMEN_DIR = os.path.join(AI, 'numen')
TOML = os.path.join(ROOT, 'src', 'main', 'resources', 'META-INF', 'neoforge.mods.toml')

problems = []


def read(p):
    return open(p, encoding='utf-8').read()


def code_only(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


# ---------------------------------------------------------------- 1. import 收口
offenders = []
for base, _, fs in os.walk(BASE):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(base, f)
        if os.path.dirname(p) == NUMEN_DIR:
            continue
        src = code_only(read(p))
        if re.search(r'import\s+com\.dwinovo\.numen', src):
            offenders.append(os.path.relpath(p, ROOT))
if offenders:
    problems.append('这些文件 import 了 Numen 类型（必须收在 ai/numen/ 下）：' + ', '.join(offenders))

bridge = os.path.join(NUMEN_DIR, 'TianyiNumenBridge.java')
if not os.path.isfile(bridge):
    problems.append('缺 ai/numen/TianyiNumenBridge.java')
    bridge_src = ''
else:
    bridge_src = code_only(read(bridge))

# ---------------------------------------------------------------- 2. 探针与守卫
presence = os.path.join(AI, 'TianyiNumenPresence.java')
if not os.path.isfile(presence):
    problems.append('缺 ai/TianyiNumenPresence.java（不含 Numen 类型的探针）')
else:
    psrc = code_only(read(presence))
    if 'com.dwinovo' in psrc:
        problems.append('TianyiNumenPresence 里出现了 Numen 类型（应保持纯净）')
    if 'isLoaded' not in psrc:
        problems.append('TianyiNumenPresence 没有 isLoaded()')

# 调用点：引用 TianyiNumenBridge 的地方必须同一行附近有 isLoaded() 守卫
for base, _, fs in os.walk(BASE):
    for f in fs:
        if not f.endswith('.java') or os.path.dirname(os.path.join(base, f)) == NUMEN_DIR:
            continue
        p = os.path.join(base, f)
        src = code_only(read(p))
        for m in re.finditer(r'TianyiNumenBridge\.(\w+)', src):
            start = max(0, m.start() - 400)
            window = src[start:m.end() + 200]
            if 'TianyiNumenPresence.isLoaded()' not in window:
                problems.append(f'{f}: 调用 TianyiNumenBridge.{m.group(1)} 附近没有 isLoaded() 守卫')

# ---------------------------------------------------------------- 3. 工具 schema 与白名单一致
snapshot_src = code_only(read(os.path.join(AI, 'TianyiAiSnapshot.java')))
m = re.search(r'OBJECTIVES\s*=\s*List\.of\(([^)]*)\)', snapshot_src, re.S)
objectives = re.findall(r'"([A-Z_]+)"', m.group(1)) if m else []
print('白名单：', objectives)

m = re.search(r'enumStr\("type",\s*"[^"]*"\s*,([^)]*)\)', bridge_src)
if not m:
    problems.append('桥里没解析到 tianyi_destine 的 type 枚举')
else:
    enum_vals = re.findall(r'"([A-Z_]+)"', m.group(1))
    print('工具枚举：', enum_vals)
    if sorted(enum_vals) != sorted(objectives):
        problems.append(f'工具枚举与白名单不一致：{enum_vals} vs {objectives}')

# ---------------------------------------------------------------- 4. 不接受奖励参数
for bad in ('points', 'reward', 'tianji', 'yuanshi', 'item'):
    if re.search(r'\.(optionalInteger|optionalString|string|integer|bool)\("' + bad + r'"', bridge_src):
        problems.append(f'tianyi_destine 接受了一个奖励相关参数：{bad}')

# ---------------------------------------------------------------- 5. 走同一个校验器
if 'TianyiAiOracle.applyDraft' not in bridge_src:
    problems.append('工具没有走 TianyiAiOracle.applyDraft（必须复用同一套校验）')
if 'TianyiAiValidator.validate' not in code_only(read(os.path.join(AI, 'TianyiAiOracle.java'))):
    problems.append('applyDraft 没有调用 TianyiAiValidator.validate')

# ---------------------------------------------------------------- 6. 依赖声明
toml = read(TOML)
mtoml = re.search(r'\[\[dependencies\.tianyi\]\]\s*\n\s*modId\s*=\s*"numen_api"\s*\n\s*type\s*=\s*"(\w+)"', toml)
if not mtoml:
    problems.append('mods.toml 没有声明 numen_api 依赖')
elif mtoml.group(1) != 'optional':
    problems.append(f'numen_api 依赖类型是 {mtoml.group(1)}，必须是 optional（它是可选前置）')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('Numen 接入 OK（引用收口 / 探针守卫 / schema 一致 / 不收奖励参数 / 复用校验器 / 可选依赖）')
