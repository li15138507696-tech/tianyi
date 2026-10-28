"""一次性迁移：把 ex-天命 / ex-道途 的 payload id 加上子模块前缀。

背景：三合一后三个子模块 MODID 都是 "tianyi"，
`ResourceLocation.fromNamespaceAndPath(TianfuMod.MODID, "state")` 得到 `tianyi:state`，
与核心 TianyiStatePayload 撞名 → RegisterPayloadHandlersEvent 抛
"Cannot register payload tianyi:state as it is already registered"。

迁移后：open → tianyi:open/<path>，path → tianyi:path/<path>。
用 TianfuMod.net("x") / DaotuMod.net("x") 统一构造，避免以后再手拼。
"""
import pathlib
import re

ROOT = pathlib.Path(r'D:\daima\mcmod\天意\src\main\java\net\guzhenren\tianyi')
JOBS = [
    ('open', 'TianfuMod'),
    ('path', 'DaotuMod'),
]

total = 0
for sub, mod in JOBS:
    pat = re.compile(
        r'ResourceLocation\.fromNamespaceAndPath\(\s*' + mod + r'\.MODID\s*,\s*("(?:[^"\\]|\\.)*")\s*\)')
    for p in sorted((ROOT / sub / 'net').glob('*.java')):
        text = p.read_text(encoding='utf-8')
        new = pat.sub(lambda m: f'{mod}.net({m.group(1)})', text)
        if new != text:
            p.write_text(new, encoding='utf-8', newline='\n')
            n = len(pat.findall(text))
            total += n
            print(f'  {sub}/net/{p.name}: {n} 处')
print(f'共改写 {total} 处 payload id')
