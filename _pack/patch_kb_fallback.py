"""让 audit_quests 在知识库文件缺失时**优雅降级**，而不是直接崩。

## 背景

`audit_quests` 的核心保证是「每条谕令的 `// KB x.y` 出处必须在知识库里真实存在」。
但知识库原文（`D:\\cxdownload\\蛊真人整合包知识库_AI版.txt`）已经不在那个位置了，
脚本直接 FileNotFoundError —— 审计一崩，其他 14 项也跟着失去意义。

## 降级策略（分三档，逐档记录在输出里）

1. 优先找仓内副本：`docs/知识库/*.txt`（推荐把原文放进这里，审计就自包含）；
2. 其次找原来的下载路径；
3. 都找不到 → **仍然校验结构与格式**（条数/四档/章节/门槛/每条必须带 `// KB x.y` 标注），
   只在输出里用 `WARN` 明确写「出处真实性本轮未校验」，不使整个审计失败。
"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', '_pack', 'audit_quests.py')
s = open(P, encoding='utf-8').read()

old_kb = "KB = r'D:\\cxdownload\\蛊真人整合包知识库_AI版.txt'"
new_kb = '''# 知识库原文：优先仓内副本，其次原下载路径
KB_CANDIDATES = [
    os.path.join(ROOT, 'docs', '知识库', '蛊真人整合包知识库_AI版.txt'),
    r'D:\\cxdownload\\蛊真人整合包知识库_AI版.txt',
]
KB = next((p for p in KB_CANDIDATES if os.path.isfile(p)), None)'''

old_load = """kb_text = open(KB, encoding='utf-8', errors='replace').read()
kb_sections = set(re.findall(r'^#{2,3}\\s+(\\d+(?:\\.\\d+)?)', kb_text, re.M))
print(f'知识库章节数: {len(kb_sections)}')"""
new_load = """if KB:
    kb_text = open(KB, encoding='utf-8', errors='replace').read()
    kb_sections = set(re.findall(r'^#{2,3}\\s+(\\d+(?:\\.\\d+)?)', kb_text, re.M))
    print(f'知识库章节数: {len(kb_sections)}（来源 {KB}）')
else:
    kb_sections = None
    print('WARN 未找到知识库原文：本轮只校验格式（每条须有 // KB x.y 标注），'
          '出处真实性未校验。把原文放到 docs/知识库/ 即可恢复强校验。')"""

s = s.replace(old_kb, new_kb)
s = s.replace(old_load, new_load)
s = s.replace("""    elif hit.group(1) not in kb_sections:""",
              """    elif kb_sections is not None and hit.group(1) not in kb_sections:""")

open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('audit_quests 已支持缺失降级')
