"""修复 FML 早期启动崩溃：options.txt 里重复的 key_key.* 行。

## 原因

合并版与旧独立附属同时在场时，两者注册了同一个按键 id（`key.daotu.view`），
游戏分别把 H 与 V 写进了 options.txt，于是文件里出现两行：

    key_key.daotu.view:key.keyboard.h
    key_key.daotu.view:key.keyboard.v

FML 的 `DisplayWindow.initialize` 在**开窗之前**就读 options.txt 并
`.collect(Collectors.toMap(...))` 建按键表 —— 同一个 key 出现两次直接抛
`IllegalStateException: Duplicate key key_key.daotu.view`，游戏连主菜单都到不了，
自然也没机会把 options.txt 重写干净（死循环）。

## 做法

1. 备份 options.txt；
2. 去掉 `key_key.daotu.view`（1.0.13 起已把按键改名到 `key.tianyi.*`，这两行是死的）；
3. 其它 `key_key.*` 若有重复，保留最后一次出现；
4. 立刻回读校验。
"""
import io
import os
import shutil
import time

VER = r'D:\game\mcmod\蛊2\.minecraft\versions\转生到蛊界就要拿出真本事'
OPT = os.path.join(VER, 'options.txt')
DEAD = 'key_key.daotu.view'

raw = open(OPT, 'rb').read()
text = raw.decode('utf-8')
nl = '\r\n' if '\r\n' in text else '\n'
lines = text.split(nl)

bak = OPT + '.bak-' + time.strftime('%Y%m%d-%H%M%S')
shutil.copy2(OPT, bak)
print('备份:', os.path.basename(bak))

dropped_dead = [l for l in lines if l.startswith(DEAD)]
kept = [l for l in lines if not l.startswith(DEAD)]
print(f'删除 {DEAD} 行 {len(dropped_dead)} 条: {dropped_dead}')

# 通用去重：同一个 key_key.* 保留最后一次
seen = {}
for i, l in enumerate(kept):
    if l.startswith('key_key.'):
        seen.setdefault(l.split(':', 1)[0], []).append(i)
dups = {k: v for k, v in seen.items() if len(v) > 1}
for k, idxs in dups.items():
    print(f'  {k} 重复 {len(idxs)} 次，保留最后一条')
    for i in idxs[:-1]:
        kept[i] = None
kept = [l for l in kept if l is not None]

open(OPT, 'w', encoding='utf-8', newline='').write(nl.join(kept))

# 回读校验
after = open(OPT, encoding='utf-8').read().split(nl)
keys = [l.split(':', 1)[0] for l in after if l.startswith('key_key.')]
dups2 = {k for k in keys if keys.count(k) > 1}
print('写回完成；剩余重复:', dups2 if dups2 else '无')
print(f'行数 {len(lines)} -> {len(after)}')
