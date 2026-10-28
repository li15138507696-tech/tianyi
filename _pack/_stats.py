"""统计各子包规模与关键重复面（只读，用于融合计划的事实依据）。"""
import os
import re

ROOT = r'D:\daima\mcmod\天意\src\main\java\net\guzhenren\tianyi'

subs = ['(根)', 'open', 'path', 'shop', 'quest', 'ai', 'client', 'net']
print('包'.ljust(10), '文件'.rjust(5), '行数'.rjust(8))
total_f = total_l = 0
for sub in subs:
    d = ROOT if sub == '(根)' else os.path.join(ROOT, sub)
    if not os.path.isdir(d):
        continue
    files = [os.path.join(b, f) for b, _, fs in os.walk(d) for f in fs if f.endswith('.java')]
    if sub == '(根)':
        files = [f for f in files if os.path.dirname(f) == d]
    lines = sum(len(open(f, encoding='utf-8').read().splitlines()) for f in files)
    total_f += len(files)
    total_l += lines
    print(sub.ljust(10), str(len(files)).rjust(5), str(lines).rjust(8))
print('合计'.ljust(10), str(total_f).rjust(5), str(total_l).rjust(8))

print('\n=== 重复面：GUI / 按键 / 命令 / 数据根 / 货币 ===')
pat = {
    'Screen 类': re.compile(r'class (\w*(?:Screen|Panel)\b)'),
    '按键绑定': re.compile(r'KeyMapping|GLFW_KEY_([A-Z0-9]+)'),
    '命令根': re.compile(r'literal\("(tianyi|tianfu|daotu|shenshang)"\)'),
    '数据根': re.compile(r'(?:ROOT|PARENT)\s*=\s*"([a-z_]+)"'),
}
hits = {k: set() for k in pat}
for b, _, fs in os.walk(ROOT):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(b, f)
        text = open(p, encoding='utf-8').read()
        for k, rx in pat.items():
            for m in rx.finditer(text):
                val = next((g for g in (m.groups() or ()) if g), m.group(0))
                hits[k].add(val)
for k, v in hits.items():
    print(k, ':', sorted(v)[:14])

print('\n=== 词条 / 任务 / 商品 的文案来源 ===')
pool = os.path.join(ROOT, 'open', 'TianfuPool.java')
talents = os.path.join(ROOT, 'open', 'TianfuTalents.java')
quests = os.path.join(ROOT, 'quest', 'TianyiQuests.java')
for name, p in (('TianfuPool', pool), ('TianfuTalents', talents), ('TianyiQuests', quests)):
    text = open(p, encoding='utf-8').read()
    print(f'{name}: 中文字面量 {len(re.findall(r"[一-龥]{2,}", text))} 处, '
          f'lang key 引用 {len(re.findall(r"translatable|Translation", text))} 处')

panel = os.path.join(ROOT, 'client', 'TianyiPanelScreen.java')
text = open(panel, encoding='utf-8').read()
for probe in ('getString("desc")', 'o.desc()', 'drawWordWrap', 'font.split'):
    print(f'面板里 {probe}: {"有" if probe in text else "没有"}')
