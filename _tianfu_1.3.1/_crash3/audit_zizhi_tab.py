"""审计：十绝体/机缘 合并为「资质」分页后，是否还有残留的旧标签或旧文案。"""
import re
import sys

ROOT = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
files = [
    rf'{ROOT}\client\TianfuViewScreen.java',
    rf'{ROOT}\net\TianfuConfirmPayload.java',
    rf'{ROOT}\TianfuPool.java',
]

fails = []

# 1. 枚举里不能再有 SIJUE / JIYUAN 作为 Tab 成员
screen = open(files[0], encoding='utf-8').read()
m = re.search(r'private enum Tab \{(.*?)\n    \}', screen, re.S)
if not m:
    sys.exit('Tab enum not found')
members = re.findall(r'([A-Z_]+)\("', m.group(1))
print('Tab 成员:', members)
if 'SIJUE' in members or 'JIYUAN' in members:
    fails.append('Tab 仍有 SIJUE/JIYUAN 成员')
if 'ZIZHI' not in members:
    fails.append('Tab 缺 ZIZHI 成员')

# 2. 代码里不能残留 Tab.SIJUE / Tab.JIYUAN 引用
for f in files:
    src = open(f, encoding='utf-8').read()
    for bad in ('Tab.SIJUE', 'Tab.JIYUAN', 'case SIJUE ->', 'case JIYUAN ->'):
        if bad in src:
            fails.append(f'{f.split(chr(92))[-1]}: 残留 {bad}')

# 3. Kind.SIJUE / Kind.JIYUAN 必须仍然存在（数据层不变，只是 UI 合并）
pool = open(files[2], encoding='utf-8').read()
if 'enum Kind { BODY, ENTRY, SIJUE, JIYUAN }' not in pool:
    fails.append('TianfuPool.Kind 被误改（数据层不该动）')

# 4. 资质页必须同时列出 sijues() 与 jiyuans()
zizhi_block = re.search(r'case ZIZHI -> \{(.*?)\n                \}', screen, re.S)
if not zizhi_block:
    fails.append('找不到 case ZIZHI 分支')
else:
    blk = zizhi_block.group(1)
    if 'TianfuPool.sijues()' not in blk:
        fails.append('资质页未列出十绝体')
    if 'TianfuPool.jiyuans()' not in blk:
        fails.append('资质页未列出机缘')

# 5. 门禁仍是「二选一」（任一即可），不是「两者都要」
conf = open(files[1], encoding='utf-8').read()
if 'hasAnchor = true' not in conf:
    fails.append('服务端门禁逻辑被改动')

if fails:
    print()
    for f in fails:
        print('FAIL:', f)
    sys.exit(1)

print()
print('ALL OK')
