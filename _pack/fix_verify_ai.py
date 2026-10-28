"""verify_jar 探针：去掉 AI 类（2.10.0 已删除该模块）。"""
import os
import re

P = os.path.join(r'D:\daima\mcmod\天意', '_pack', 'verify_jar.py')
s = open(P, encoding='utf-8').read()

old = "                  'net/guzhenren/tianyi/ai/TianyiAiOracle.class'):"
new = "                  'net/guzhenren/tianyi/contract/TianyiContractCore.class'):"
if old in s:
    s = s.replace(old, new)
    print('  verify_jar: AI 探针 → 契约核心探针')
else:
    print('  ! 未找到 AI 探针行')

# 顺手确认没有其它 ai 探针
s = re.sub(r"\n\s*'net/guzhenren/tianyi/ai/[^']*',", '', s)
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('  verify_jar: 其余 ai 探针已清理')
