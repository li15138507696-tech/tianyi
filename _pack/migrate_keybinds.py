"""1.0.13 迁移：把我的按键 id 从旧附属命名空间搬出来。

## 背景

合并后三个旧附属（daotu / tianfu / shenshang）的**独立 jar 若还留在 mods 里**，
它们会和本模组注册同一个 KeyMapping translation key，NeoForge 在建表时抛

    IllegalStateException: Duplicate key key_key.daotu.view
    (attempted merging values key.keyboard.h and key.keyboard.v)

直接让游戏连启动画面都到不了（ModLauncher 阶段）。

根本修法有两个：
1. 删掉旧 jar（用户侧）；
2. 本模组不再复用旧附属的 key id（我们侧）—— 这样旧 jar 残留也不会撞。

本脚本做第 2 件：把 `key.daotu.view` → `key.tianyi.path_view`，
`key.shenshang.open` → `key.tianyi.shop_open`，分类统一到 `key.categories.tianyi`。
"""
import glob
import json
import os

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java')
RES = os.path.join(ROOT, 'src', 'main', 'resources')

RENAMES = {
    'key.daotu.view': 'key.tianyi.path_view',
    'key.shenshang.open': 'key.tianyi.shop_open',
    'key.categories.shenshang': 'key.categories.tianyi',
    'key.categories.daotu': 'key.categories.tianyi',
}

# 1. Java 源码里的字面量
changed_java = 0
for p in glob.glob(os.path.join(SRC, '**', '*.java'), recursive=True):
    text = open(p, encoding='utf-8').read()
    new = text
    for old, rep in RENAMES.items():
        new = new.replace(f'"{old}"', f'"{rep}"')
    if new != text:
        open(p, 'w', encoding='utf-8', newline='\n').write(new)
        changed_java += 1
        print('  java:', os.path.relpath(p, ROOT))

# 2. lang（按 JSON 键改名，值不动）
changed_lang = 0
for p in glob.glob(os.path.join(RES, 'assets', '**', 'lang', '*.json'), recursive=True):
    try:
        data = json.load(open(p, encoding='utf-8'))
    except Exception as e:
        print('  跳过（解析失败）', p, e)
        continue
    new = {}
    touched = False
    for k, v in data.items():
        nk = RENAMES.get(k, k)
        if nk != k:
            touched = True
        new[nk] = v
    if touched:
        with open(p, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(new, f, ensure_ascii=False, indent=2)
            f.write('\n')
        changed_lang += 1
        print('  lang:', os.path.relpath(p, ROOT))

print(f'完成：{changed_java} 个 java 文件、{changed_lang} 个 lang 文件')
