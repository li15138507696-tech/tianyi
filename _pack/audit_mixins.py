"""Mixin 配置审计（1.0.9）—— 抓那个「模组装不上」的致命坑。

## 规则

Mixin 的 `package` 字段不是「这些 mixin 在哪」，而是**声明一个命名空间**：
该包下的类从此只能被当作 mixin 加载，**任何直接引用都会抛**

    IllegalClassLoadError: <类名> is in a defined mixin package <包>.***** owned by <配置>.json
    and cannot be referenced directly

1.0.8 之前我们的配置写的是 `"package": "net.guzhenren.tianyi"` —— 整个根包（含 `@Mod` 主类
`TianyiMod`、94 个普通类）都被圈进去，模组在容器初始化阶段就 FATAL，
并且连带后面 RuOK 的配置注册没跑，表现成「RuOK 崩了」的假象。

## 本脚本断言

1. 每个 `package` 下的**所有** Java 类都必须是某个配置里列出的 mixin；
2. 任何带 `@Mod` 的类都不得落在 mixin 包里；
3. 配置里列出的 mixin 类必须真实存在；
4. 不得残留已废弃的合并配置（`tianyi.mixins.json`）。
"""
import json
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意'
RES = os.path.join(ROOT, 'src', 'main', 'resources')
SRC = os.path.join(ROOT, 'src', 'main', 'java')
DEPRECATED = {'tianyi.mixins.json'}

problems = []

# ---------------------------------------------------------------- 收集配置
configs = {}
for name in sorted(os.listdir(RES)):
    if not name.endswith('.mixins.json'):
        continue
    cfg = json.load(open(os.path.join(RES, name), encoding='utf-8'))
    pkg = cfg.get('package', '')
    entries = []
    for key in ('mixins', 'client', 'server'):
        entries += cfg.get(key, []) or []
    configs[name] = (pkg, entries)
    print(f'{name}: package={pkg}  mixins={len(entries)}')

# 2.0.0 起允许零 mixin 配置：模组不再注入本体任何类
if not configs:
    print('零 mixin 配置（2.0.0 设计如此）')

for dep in DEPRECATED:
    if os.path.isfile(os.path.join(RES, dep)):
        problems.append(f'残留已废弃的 {dep}（会与拆分后的配置重复/冲突）')

# ---------------------------------------------------------------- 全量类清单
all_classes = {}
for base, _, fs in os.walk(SRC):
    for f in fs:
        if not f.endswith('.java'):
            continue
        rel = os.path.relpath(os.path.join(base, f), SRC).replace('\\', '/')
        fq = rel[:-5].replace('/', '.')
        all_classes[fq] = os.path.join(base, f)

# 所有配置里声明的 mixin 全名
declared = set()
for name, (pkg, entries) in configs.items():
    for e in entries:
        fq = pkg + '.' + e.replace('/', '.')
        declared.add(fq)
        if fq not in all_classes:
            problems.append(f'{name} 列了不存在的 mixin 类：{fq}')

# ---------------------------------------------------------------- 规则 1/2
for name, (pkg, _entries) in configs.items():
    prefix = pkg + '.'
    inside = [c for c in all_classes if c.startswith(prefix)]
    strays = sorted(c for c in inside if c not in declared)
    if strays:
        problems.append(
            f'{name} 的 package「{pkg}」下有 {len(strays)} 个非 mixin 类会被 Mixin 拒绝直接加载：'
            + ', '.join(strays[:5]) + ('…' if len(strays) > 5 else ''))
    # @Mod 类绝不能在里面
    for c in inside:
        text = open(all_classes[c], encoding='utf-8').read()
        if re.search(r'@Mod\s*\(', text):
            problems.append(f'{c} 带 @Mod 却在 mixin 包「{pkg}」内 —— 会 IllegalClassLoadError')

# ---------------------------------------------------------------- 打印结论
total = sum(len(e) for _, e in configs.values())
if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print(f'Mixin 配置 OK（{len(configs)} 个配置 / {total} 个 mixin / 包内无普通类 / 无 @Mod 混入）')
