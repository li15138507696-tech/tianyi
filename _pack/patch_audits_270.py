"""2.7.0：审计口径调整——`open/` 回归（只放词条层），`path/` `shop/` 仍然禁止。"""
import os
import re

ROOT = r'D:\daima\mcmod\天意'

# 1) audit_ui：允许 open/，但要求它只含词条层且没有开窍 mixin
p = os.path.join(ROOT, '_pack', 'audit_ui.py')
s = open(p, encoding='utf-8').read()
s = s.replace("""# ---------------------------------------------------------------- 三系统不得复活
for gone in ('open', 'path', 'shop'):
    if os.path.isdir(os.path.join(SRC, gone)):
        problems.append(f'2.0.0 已删除 {gone}/ 子系统，但它又出现了')""",
"""# ---------------------------------------------------------------- 子系统边界（2.7.0 口径）
# open/ 在 2.7.0 回归，但只允许放「天命词条层」：不得有开窍注入、不得有客户端面板
for gone in ('path', 'shop'):
    if os.path.isdir(os.path.join(SRC, gone)):
        problems.append(f'{gone}/ 已在 2.0.0 删除，不得复活')
open_dir = os.path.join(SRC, 'open')
if os.path.isdir(open_dir):
    names = [f for b, _, fs in os.walk(open_dir) for f in fs]
    for bad in ('TianfuOpening.java',):
        if bad in names:
            problems.append(f'open/{bad} 是开窍注入，用户口径明确不要（应为空桩纯签名）')
    if any(n.endswith('ViewScreen.java') or n.endswith('Screen.java') for n in names):
        problems.append('open/ 里出现了独立面板——面板只许有一个 TianyiPanelScreen')
    if any('mixin' in n.lower() for n in names):
        problems.append('open/ 里出现了 mixin——2.7.0 不注入本体开窍流程')""")
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('audit_ui 口径已更新')

# 2) verify_jar：只禁 path/ shop/
p = os.path.join(ROOT, '_pack', 'verify_jar.py')
s = open(p, encoding='utf-8').read()
s = s.replace("for gone in ('net/guzhenren/tianyi/open/', 'net/guzhenren/tianyi/path/', 'net/guzhenren/tianyi/shop/'):",
              "for gone in ('net/guzhenren/tianyi/path/', 'net/guzhenren/tianyi/shop/'):")
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('verify_jar 口径已更新')

# 3) pack_jar：探针加上词条层与天机阁
p = os.path.join(ROOT, '_pack', 'pack_jar.py')
s = open(p, encoding='utf-8').read()
if 'TianyiPavilion' not in s:
    s = s.replace("    'net/guzhenren/tianyi/quest/TianyiQuests.class',",
                  "    'net/guzhenren/tianyi/quest/TianyiQuests.class',\n"
                  "    'net/guzhenren/tianyi/TianyiPavilion.class',\n"
                  "    'net/guzhenren/tianyi/open/TianfuPool.class',\n"
                  "    'net/guzhenren/tianyi/open/TianfuTalents.class',\n"
                  "    'net/guzhenren/tianyi/open/TianfuEvents.class',")
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('pack_jar 探针已加')
