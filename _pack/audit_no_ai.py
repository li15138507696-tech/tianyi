"""AI 模块移除审计（2.10.0）。

用户要求删掉 AI 模块。删模块最容易出的事是"文件夹删了、引用还在"：
配置还在注册、状态包还在算、命令还能进、探针还在打包里点名字。
所以这里做**反向断言**：只要任何地方还提到 ai，就失败。

检查项：
1. 源码里不得存在 `net/guzhenren/tianyi/ai` 目录，也不得有任何 `tianyi.ai.` 引用；
2. `mods.toml` 不得再声明 `numen_api` 依赖；
3. 命令树里不得出现 `literal("ai")` / `setkey` / `providers`；
4. 状态包里不得再有 `put("ai"`；
5. `pack_jar.py` 的探针不得包含 ai 类；
6. `build.py` 的审计清单不得再引用已退役的 `audit_ai` / `audit_numen`。
"""
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
PACK = os.path.join(ROOT, '_pack')

problems = []

# 1. 源码
if os.path.isdir(os.path.join(SRC, 'ai')):
    problems.append('ai/ 目录仍然存在')

refs = []
for base, _, files in os.walk(SRC):
    for f in files:
        if not f.endswith('.java'):
            continue
        p = os.path.join(base, f)
        txt = open(p, encoding='utf-8').read()
        if 'tianyi.ai.' in txt or re.search(r'\bTianyiAi[A-Za-z]*\b', txt):
            refs.append(os.path.relpath(p, SRC))
if refs:
    problems.append('仍有 AI 引用：' + ', '.join(refs[:6]))

# 2. mods.toml
toml = open(os.path.join(ROOT, 'src', 'main', 'resources', 'META-INF', 'neoforge.mods.toml'),
            encoding='utf-8').read()
if 'numen_api' in toml:
    problems.append('mods.toml 仍声明 numen_api')

# 3. 命令
cmds = open(os.path.join(SRC, 'TianyiCommands.java'), encoding='utf-8').read()
for bad in ('literal("ai")', 'setkey', 'providers', 'TianyiAiKey'):
    if bad in cmds:
        problems.append(f'命令里仍出现 {bad}')

# 4. 状态包（3.0.0 起整条客户端同步链已随面板一起移除，文件不在也算通过）
net_path = os.path.join(SRC, 'TianyiNet.java')
if os.path.isfile(net_path):
    net = open(net_path, encoding='utf-8').read()
    if 'put("ai"' in net:
        problems.append('状态包仍在发送 ai 复合体')

# 5. 打包探针
pack = open(os.path.join(PACK, 'pack_jar.py'), encoding='utf-8').read()
if '/ai/' in pack:
    problems.append('pack_jar 探针仍包含 ai 类')

# 6. 构建清单
build = open(os.path.join(PACK, 'build.py'), encoding='utf-8').read()
for bad in ('audit_ai', 'audit_numen'):
    if bad in build:
        problems.append(f'build.py 仍在跑已退役的 {bad}')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('AI 模块移除 OK（无目录 / 无引用 / 无依赖 / 无命令 / 无状态字段 / 探针与审计已清理）')
