"""蛊兽名录 + 原生属性审计（3.10.1）。

三重核对，任何一环对不上就失败：

1. **名录 ↔ 本体 jar**：名录里每个 id 必须存在，中文名必须与本体 lang 表一致，
   名字里带「百兽王/千兽王/万兽王/兽皇」的阶数必须分别是 2/3/4/5。
2. **BeastStats ↔ 名录**：属性表必须与名录**一一对应**（不多不少）。
3. **BeastStats ↔ 本体字节码**：生命/护甲/攻击必须与实体类
   `createAttributes()` 里的常量一致 —— 防止有人手改生成表。

第 3 步用**一次 javap 调用**批量反汇编所有实体类（89 次单独调用太慢）。
"""
import json
import os
import re
import subprocess
import sys
import zipfile

ROOT = r'D:\daima\mcmod\天意'
PKG = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi')
CATALOG = os.path.join(PKG, 'will', 'BeastCatalog.java')
STATS = os.path.join(PKG, 'encounter', 'BeastStats.java')
JAR = os.path.join(ROOT, '_pack', 'libs', 'guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'

TIER_WORD = {'百兽王': 2, '千兽王': 3, '万兽王': 4, '兽皇': 5}
PUNCT = str.maketrans('', '', '·.-—_,、 ')

problems = []


def norm(s):
    return str(s).translate(PUNCT)


# ---------------------------------------------------------------- 1. 名录 ↔ jar
cat_src = open(CATALOG, encoding='utf-8').read()
entries = {}
for m in re.finditer(
        r'add\(\s*(\d)\s*,\s*Dao\.([A-Z_]+)\s*,\s*"([a-z0-9_]+)"\s*,\s*"([^"]+)"\s*\)', cat_src):
    tier, dao, path, cn = int(m.group(1)), m.group(2), m.group(3), m.group(4)
    eid = 'guzhenren:' + path
    if eid in entries:
        problems.append(f'{eid} 在名录里重复')
    entries[eid] = (tier, dao, cn)

print(f'名录：{len(entries)} 条')
if len(entries) < 80:
    problems.append(f'名录只有 {len(entries)} 条，少于 80')

m = re.search(r'int MAX_TIER\s*=\s*(\d+)', cat_src)
if not m or int(m.group(1)) != 5:
    problems.append('BeastCatalog.MAX_TIER 不是 5')

if not os.path.isfile(JAR):
    print('FAIL 找不到本体 jar：' + JAR)
    sys.exit(1)

with zipfile.ZipFile(JAR) as z:
    lang = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8-sig'))
    names = {k[len('entity.guzhenren.'):]: v for k, v in lang.items()
             if k.startswith('entity.guzhenren.')}
    entity_classes = [n[:-6].replace('/', '.') for n in z.namelist()
                      if n.startswith('net/guzhenren/entity/') and n.endswith('.class')
                      and '$' not in n]

for eid, (tier, dao, cn) in sorted(entries.items()):
    short = eid.split(':', 1)[1]
    if short not in names:
        problems.append(f'{eid} 在本体 jar 里不存在')
        continue
    jar_name = str(names[short])
    if norm(jar_name) != norm(cn):
        problems.append(f'{eid}：名录写「{cn}」，本体叫「{jar_name}」')
    for word, want in TIER_WORD.items():
        if word in jar_name and tier != want:
            problems.append(f'{eid}「{jar_name}」含「{word}」应为 {want} 阶，写成 {tier} 阶')

for t in range(1, 6):
    n = sum(1 for v in entries.values() if v[0] == t)
    print(f'  {t} 阶：{n} 只')
    if n == 0:
        problems.append(f'{t} 阶没有任何实体')

# ---------------------------------------------------------------- 2. 属性表 ↔ 名录
stats_src = open(STATS, encoding='utf-8').read()
rows = {}
for m in re.finditer(
        r'new Row\("([^"]+)",\s*"([^"]+)",\s*(\d),\s*"([A-Z_]+)",\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)\)',
        stats_src):
    eid = m.group(1)
    rows[eid] = (m.group(2), int(m.group(3)), m.group(4),
                 float(m.group(5)), float(m.group(6)), float(m.group(7)))

print(f'属性表：{len(rows)} 条')
if set(rows) != set(entries):
    only_cat = set(entries) - set(rows)
    only_stats = set(rows) - set(entries)
    if only_cat:
        problems.append(f'名录有、属性表没有：{sorted(only_cat)[:5]}')
    if only_stats:
        problems.append(f'属性表有、名录没有：{sorted(only_stats)[:5]}')

for eid, (name, tier, dao, hp, armor, atk) in rows.items():
    if eid not in entries:
        continue
    ctier, cdao, cname = entries[eid]
    if tier != ctier or dao != cdao or norm(name) != norm(cname):
        problems.append(f'{eid} 属性表与名录不一致（阶/道/名）')

# ---------------------------------------------------------------- 3. 属性表 ↔ 本体字节码
# 把每个 id 映射到实体类（与 gen_beast_stats.py 同一套规则）
lower_classes = {c.rsplit('.', 1)[-1].lower(): c for c in entity_classes}
need = {}
for eid in rows:
    short = eid.split(':', 1)[1]
    key = short.replace('_', '').lower() + 'entity'
    cls = lower_classes.get(key)
    if cls is None:
        problems.append(f'{eid} 找不到实体类（{key}）')
        continue
    need[cls] = eid

if need:
    # 一次 javap 批量反汇编
    cmd = [JAVAP, '-c', '-p', '-cp', JAR] + sorted(need)
    r = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        print('FAIL javap 失败：', r.stderr[:300])
        sys.exit(1)
    # 按类切段
    chunks = re.split(r'\n(?=(?:public |final |abstract )?class |Compiled from )', r.stdout)
    by_class = {}
    for chunk in chunks:
        m = re.search(r'class (?:net\.guzhenren\.entity\.)?(\w+)', chunk)
        if m:
            by_class[m.group(1)] = chunk

    def grab(text, attr):
        m = re.search(r'Attributes\.' + attr + r'\b(.*?)(?:invokevirtual.*?\badd\b)', text, re.S)
        if not m:
            return None
        seg = m.group(1)
        num = re.search(r'//\s*(?:double|float|int|long)\s+(-?[\d.]+)', seg)
        if num:
            return float(num.group(1))
        if 'dconst_0' in seg or 'iconst_0' in seg or 'fconst_0' in seg:
            return 0.0
        return None

    mismatch = 0
    for cls, eid in sorted(need.items()):
        simple = cls.rsplit('.', 1)[-1]
        chunk = by_class.get(simple)
        if chunk is None:
            problems.append(f'{eid}：javap 输出里找不到 {simple} 的段')
            continue
        hp, armor, atk = grab(chunk, 'MAX_HEALTH'), grab(chunk, 'ARMOR'), grab(chunk, 'ATTACK_DAMAGE')
        want = rows[eid]
        if (hp, armor, atk) != (want[3], want[4], want[5]):
            problems.append(
                f'{eid} 属性与本体字节码不符：代码 {want[3]}/{want[4]}/{want[5]}，'
                f'本体 {hp}/{armor}/{atk}')
            mismatch += 1
    print(f'  属性复核：{len(need)} 只，不符 {mismatch} 只')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)

print('蛊兽名录 OK（名录↔本体 / 属性表↔名录 / 属性表↔字节码 三重一致）')
