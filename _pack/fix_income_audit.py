"""补完收入调用点替换 + 修账本审计的两处误报/漏报。"""

import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

# ---- 1. 剩下的两个收入调用点（路径写全）
SWAPS = [
    (os.path.join(SRC, 'quest', 'TianyiQuestEngine.java'),
     'TianyiEcon.add(player, q.points());', 'TianyiEcon.grant(player, q.points());'),
    (os.path.join(SRC, 'ai', 'TianyiAiQuests.java'),
     'TianyiEcon.add(player, q.points());', 'TianyiEcon.grant(player, q.points());'),
]
for p, a, b in SWAPS:
    if not os.path.isfile(p):
        print('  ! 找不到', p)
        continue
    s = open(p, encoding='utf-8').read()
    if a in s:
        open(p, 'w', encoding='utf-8', newline='\n').write(s.replace(a, b))
        print('  ~', os.path.basename(p), '收入 → grant')
    else:
        print('  ! 未匹配', os.path.basename(p))

# ---- 2. 审计：扫描前先剥掉注释，避免把 javadoc 里的说明文字当成调用
AUD = os.path.join(ROOT, '_pack', 'audit_ledger.py')
s = open(AUD, encoding='utf-8').read()

helper = '''
def strip_comments(text):
    """去掉 // 与 /* */ 注释：审计只该看真实调用，不该看说明文字。"""
    text = re.sub(r'/\\*.*?\\*/', '', text, flags=re.S)
    return re.sub(r'//[^\\n]*', '', text)

'''

if 'def strip_comments' not in s:
    s = s.replace('def read(rel):', helper + 'def read(rel):')
    s = s.replace("""        txt = open(p, encoding='utf-8').read()
        for m in re.finditer(r'TianyiEcon\\.add\\(', txt):""",
                  """        txt = strip_comments(open(p, encoding='utf-8').read())
        for m in re.finditer(r'TianyiEcon\\.add\\(', txt):""")
    open(AUD, 'w', encoding='utf-8', newline='\n').write(s)
    print('  ~ audit_ledger: 扫描前剥离注释')
else:
    print('  · audit_ledger 已带 strip_comments')
