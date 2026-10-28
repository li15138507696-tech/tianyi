"""天机阁审计 + 价目表生成（1.0.1）。

做两件事：

1. **结构校验**（失败即退出码 1）
   - 天机阁不许引用 sijues() / jiyuans()（十绝体与机缘不上架）；
   - 不许把「负」品质条目放进商品表；
   - 价格上下限与品质底价的阶梯顺序正确（极>神>天>地>玄>黄）；
   - TianyiPrice.weight 必须覆盖 TianfuTalents.Op 的全部成员（漏一个 = 新效果免费上架）。

2. **价格复算并生成文档**
   从 `TianyiPrice.java` 解析权重表达式、从 `TianfuTalents.java` 解析每条词条的效果，
   用与 Java 完全相同的公式复算价格，输出分布与 `docs/天机阁价目表.md`。
   权重只有一个来源（Java 文件），本脚本不另写一份数字。

公式：price = clamp(round10((品质底价 × 0.6) + (效果强度 × 3.0)), 80, 4000)
"""
import math
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意'
OPEN = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'open')
BASE_DIR = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')

problems = []


def read(p):
    return open(p, encoding='utf-8').read()


def code_only(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    return src


# ---------------------------------------------------------------- 1. 结构校验
shop_src = code_only(read(os.path.join(BASE_DIR, 'TianyiTalentShop.java')))
if 'sijues()' in shop_src or 'jiyuans()' in shop_src:
    problems.append('天机阁引用了 sijues()/jiyuans() —— 十绝体与机缘不该上架')
if '"负".equals' not in shop_src:
    problems.append('天机阁没有过滤「负」品质词条')

price_src = read(os.path.join(BASE_DIR, 'TianyiPrice.java'))
m = re.search(r'BASE\s*=\s*Map\.of\((.*?)\);', price_src, re.S)
base_map = {}
if m:
    for q, v in re.findall(r'"([^"]+)"\s*,\s*(\d+)', m.group(1)):
        base_map[q] = int(v)
if not base_map:
    problems.append('解析不到品质底价表 BASE')

ladder = ['极', '神', '天', '地', '玄', '黄']
for a, b in zip(ladder, ladder[1:]):
    if base_map.get(a, 0) <= base_map.get(b, 0):
        problems.append(f'品质底价阶梯错误：{a}({base_map.get(a)}) 应高于 {b}({base_map.get(b)})')

MIN, MAX = 80, 4000
m = re.search(r'int MIN\s*=\s*(\d+)', price_src)
if m:
    MIN = int(m.group(1))
m = re.search(r'int MAX\s*=\s*(\d+)', price_src)
if m:
    MAX = int(m.group(1))

# ---------------------------------------------------------------- 2. 解析权重
weight_body = re.search(r'private static double weight\(TianfuTalents\.Fx f\)\s*\{(.*?)\n    \}',
                        price_src, re.S)
if not weight_body:
    problems.append('解析不到 weight() 方法体')
    weight_src = ''
else:
    weight_src = weight_body.group(1)


def to_lambda(expr):
    """把 Java 权重表达式翻译成 Python lambda（只支持本文件里出现的几种形状）。"""
    expr = expr.strip()
    m = re.fullmatch(r'pct\(a\)\s*\*\s*([\d.]+)', expr)
    if m:
        k = float(m.group(1))
        return lambda a, k=k: a * 100.0 * k
    m = re.fullmatch(r'a\s*/\s*([\d.]+)', expr)
    if m:
        k = float(m.group(1))
        return lambda a, k=k: a / k
    m = re.fullmatch(r'([\d.]+)\s*\+\s*Math\.abs\(a\)\s*\*\s*([\d.]+)', expr)
    if m:
        c, k = float(m.group(1)), float(m.group(2))
        return lambda a, c=c, k=k: c + abs(a) * k
    m = re.fullmatch(r'a\s*\*\s*([\d.]+)', expr)
    if m:
        k = float(m.group(1))
        return lambda a, k=k: a * k
    m = re.fullmatch(r'Math\.abs\(a\)\s*\*\s*([\d.]+)', expr)
    if m:
        k = float(m.group(1))
        return lambda a, k=k: abs(a) * k
    return None


weights = {}
for op, expr in re.findall(r'case ([A-Z][A-Z0-9_]*)\s*->\s*([^;]+);', weight_src):
    fn = to_lambda(expr)
    if fn is None:
        problems.append(f'权重表达式无法解析（请保持简单形状）：{op} -> {expr.strip()}')
    else:
        weights[op] = fn

talents_src = read(os.path.join(OPEN, 'TianfuTalents.java'))
op_enum = re.search(r'enum Op\s*\{(.*?)\}', talents_src, re.S)
enum_ops = set(re.findall(r'\b([A-Z][A-Z0-9_]+)\b', op_enum.group(1))) if op_enum else set()
missing = sorted(enum_ops - set(weights))
if missing:
    problems.append('weight() 漏了这些 Op（会变成免费效果）：' + ', '.join(missing))
print(f'权重覆盖：{len(weights)}/{len(enum_ops)} 个 Op')

# ---------------------------------------------------------------- 3. 解析词条
src = code_only(talents_src)


def balanced_calls(text, marker):
    """取出所有 `marker(...)` 的完整调用文本（按括号配平）。"""
    out = []
    i = 0
    while True:
        j = text.find(marker, i)
        if j < 0:
            break
        k = j + len(marker)
        depth = 1
        while k < len(text) and depth > 0:
            if text[k] == '(':
                depth += 1
            elif text[k] == ')':
                depth -= 1
            k += 1
        out.append(text[j:k])
        i = k
    return out


FX_PATTERNS = [
    (r'f\(Op\.([A-Z_]+),\s*(-?[\d.]+)\)', lambda g: (g[0], float(g[1]), 0.0)),
    (r'dao\("[a-z_]+",\s*(-?[\d.]+)\)', lambda g: ('DAOHEN', float(g[0]), 0.0)),
    (r'fld\("[a-z_]+",\s*(-?[\d.]+)\)', lambda g: ('FIELD_MULT', float(g[0]), 0.0)),
    (r'fldAdd\("[a-z_]+",\s*(-?[\d.]+)\)', lambda g: ('FIELD_ADD', float(g[0]), 0.0)),
    (r'mob\("[A-Za-z0-9_]+",\s*(-?[\d.]+)\)', lambda g: ('MOB_EFFECT', float(g[0]), 0.0)),
    (r'reflect\((-?[\d.]+),\s*(-?[\d.]+)\)', lambda g: ('REFLECT', float(g[0]), float(g[1]))),
    (r'onHit\("[A-Za-z0-9_]+",\s*(-?[\d.]+),\s*(-?[\d.]+)\)', lambda g: ('ON_HIT_EFFECT', float(g[0]), float(g[1]))),
]

talents = []
for call in balanced_calls(src, 't('):
    head = re.match(r't\(\s*"([^"]+)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*(-?\d+)\s*,\s*"([^"]*)"',
                    call)
    if not head:
        continue
    tid, name, quality, cost, desc = head.groups()
    fx = []
    for pat, conv in FX_PATTERNS:
        for g in re.findall(pat, call):
            fx.append(conv(g if isinstance(g, tuple) else (g,)))
    talents.append({'id': tid, 'name': name, 'quality': quality, 'fx': fx})

print('解析到词条：', len(talents))
if len(talents) < 100:
    problems.append(f'只解析到 {len(talents)} 条词条（应 >= 111）')


def round10(x):
    return int(math.floor(x / 10.0 + 0.5)) * 10


def price_of(t):
    power = 0.0
    for op, a, b in t['fx']:
        fn = weights.get(op)
        if fn:
            power += fn(a)
    base = base_map.get(t['quality'], 60)
    return max(MIN, min(MAX, round10(base * 0.6 + power * 3.0)))


def fallback_price(quality, cost):
    """内置 22 条：效果写死在代码里，没有 Fx 列表 → 用「天赋点成本」当强度信号。"""
    base = base_map.get(quality, 60)
    return max(MIN, min(MAX, round10(max(0, cost) * 90.0 + base * 0.6)))


# ---- 内置 22 条 + 20 种体质（来自 TianfuPool）----
pool_src = code_only(read(os.path.join(OPEN, 'TianfuPool.java')))
builtin = []
for m2 in re.finditer(
        r'entry\("([^"]+)",\s*"([^"]*)",\s*(GOD_QUALITY|"([^"]*)")\s*,\s*(-?\d+)\s*,', pool_src):
    q = '神' if m2.group(3) == 'GOD_QUALITY' else m2.group(4)
    builtin.append({'id': m2.group(1), 'name': m2.group(2), 'quality': q, 'cost': int(m2.group(5))})

bodies = re.findall(r'body\("([^"]+)",\s*"([^"]*)",\s*(\d+)', pool_src)
print(f'内置词条 {len(builtin)} 条 / 体质 {len(bodies)} 种')

shop_rows = []
for t in talents:
    if t['quality'] == '负':
        continue
    shop_rows.append((price_of(t), t['name'], t['quality'], '词条'))
for b in builtin:
    shop_rows.append((fallback_price(b['quality'], b['cost']), b['name'], b['quality'], '词条'))
shop_rows.sort(key=lambda x: -x[0])

body_price = max(MIN, min(MAX, round10(1500 + base_map.get('凡体', 120))))
print(f'商品合计：{len(shop_rows)} 条词条 + {len(bodies)} 种体质 = {len(shop_rows) + len(bodies)} 件')
print(f'体质统一价：{body_price}')

# ---------------------------------------------------------------- 4. 生成报告
priced = [(p, t) for p, n, q, k in shop_rows for t in [{'name': n, 'quality': q}]]
priced.sort(key=lambda x: -x[0])

print('\n品质分布（天机阁在售词条）：')
lines = []
for q in ladder:
    vals = [p for p, t in priced if t['quality'] == q]
    if not vals:
        continue
    print(f'  {q}: {len(vals):>3} 条  价 {min(vals)}–{max(vals)}  均 {sum(vals)//len(vals)}')
    lines.append(f'| {q} | {len(vals)} | {min(vals)} – {max(vals)} | {sum(vals)//len(vals)} |')

print('\n最贵的 12 条：')
top = []
for p, t in priced[:12]:
    print(f'  {p:>5}  {t["name"]}  ({t["quality"]})')
    top.append(f'| {t["name"]} | {t["quality"]} | {p} |')

neg = [t for t in talents if t['quality'] == '负']
print(f'\n负品质 {len(neg)} 条：不上架（天命里是花点数买罪受、返还点数的机制）')

doc = os.path.join(ROOT, 'docs', '天机阁价目表.md')
with open(doc, 'w', encoding='utf-8', newline='\n') as f:
    f.write('# 天机阁价目表（自动生成）\n\n')
    f.write('> 由 `_pack/audit_shop.py` 按 `TianyiPrice` 的权重 × `TianfuTalents` 的效果复算生成。\n')
    f.write('> **不要手改** —— 改价格请改 `TianyiPrice.java` 的权重或品质底价，然后重跑脚本。\n\n')
    f.write(f'**在售：{len(shop_rows)} 条词条 + {len(bodies)} 种凡体体质 = '
            f'{len(shop_rows) + len(bodies)} 件**\n\n')
    f.write('```\n词条：price = clamp( round10( 品质底价 × 0.6 + 效果强度 × 3.0 ), 80, 4000 )\n')
    f.write('效果强度 = Σ(每条 effect 的主数值 × 该 effect 的权重)\n')
    f.write('内置 22 条（效果写死在代码里、无数据表）：price = clamp( round10( 天赋点成本 × 90 + 品质底价 × 0.6 ) )\n')
    f.write(f'体质：price = round10( 1500 + 品质底价(凡体=120) ) = {body_price}\n```\n\n')
    f.write('## 品质分布（词条）\n\n')
    f.write('| 品质 | 条数 | 价格区间 | 均价 |\n|---|---|---|---|\n')
    f.write('\n'.join(lines) + '\n\n')
    f.write('## 最贵的 12 条\n\n| 词条 | 品质 | 天机点 |\n|---|---|---|\n')
    f.write('\n'.join(top) + '\n\n')
    f.write(f'## 体质\n\n{len(bodies)} 种凡体统一 **{body_price} 天机点**。\n')
    f.write('体质效果写死在代码里、没有可折算的数据表，故按固定底价给。\n\n')
    f.write('## 不上架\n\n')
    f.write('- **十绝体 / 机缘**：直接覆写本体 `tizhi / kongqiao / zuida_zhenyuan / qiyun_shangxian`，\n')
    f.write('  属于开局资质的领地，中途用天机点买会与本体开窍结算打架。\n')
    f.write(f'- **负品质 {len(neg)} 条**：天命里它们是「花点数买罪受、返还点数」的机制，\n')
    f.write('  花天机点买负面效果没有意义。\n')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('\n天机阁 OK（不含十绝体/机缘/负品质；权重全覆盖；价目表已生成）')
