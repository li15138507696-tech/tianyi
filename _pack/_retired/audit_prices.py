"""价目表审计（P3）。

神之商店的价目分三层：本体标签价 → VANILLA 手工表 → 规则兜底。
写错一个 id 的后果是「那件物品永远不上架」——属于最难发现的静默 bug，所以静态校验：

1. {@code ShopPrices.VANILLA} 里的每个路径必须是 MC 1.21.1 真实存在的物品
   （对照 {@code _pack/data/mc1211_item_ids.txt}，1333 条，取自 1.21.1 Items 静态字段）；
2. {@code ShopPrices.CUSTOM} 里的每个路径必须在本体里有对应物品模型
   （对照本体 jar 的 {@code assets/guzhenren/models/item/<path>.json}）；
3. 本模组自己注册的物品 id 必须与 ModItems 一致。
"""
import os, re, sys, zipfile

ROOT = r'D:\daima\mcmod\天意'
SHOP = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'shop')
PRICES = os.path.join(SHOP, 'shop', 'ShopPrices.java')
IDS = os.path.join(ROOT, '_pack', 'data', 'mc1211_item_ids.txt')
GUZHENREN = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'

problems = []
src = open(PRICES, encoding='utf-8').read()


def between(start_marker, end_marker=None):
    """取 start_marker 之后、end_marker 之前的源码片段（找不到 end 就取到文件末尾）。"""
    i = src.find(start_marker)
    if i < 0:
        return ''
    i += len(start_marker)
    if end_marker:
        j = src.find(end_marker, i)
        if j > 0:
            return src[i:j]
    return src[i:]


vanilla_ids = {l.strip() for l in open(IDS, encoding='utf-8') if l.strip()}
if len(vanilla_ids) < 1000:
    problems.append(f'物品 id 清单异常，只有 {len(vanilla_ids)} 条')

# VANILLA 由 buildVanilla() 里的 new String[]{...} 数组填充
vanilla_block = between('buildVanilla() {', 'CUSTOM = Map.ofEntries(')
vanilla_entries = re.findall(r'"([a-z0-9_]+)"', vanilla_block)
if not vanilla_entries:
    problems.append('没解析到 VANILLA 价目条目（正则或结构变了？）')
bad_vanilla = sorted({e for e in vanilla_entries if e not in vanilla_ids})
print(f'VANILLA 条目 {len(vanilla_entries)}，未知 id {len(bad_vanilla)}')
if bad_vanilla:
    problems.append('VANILLA 里有 1.21.1 不存在的物品 id: ' + ', '.join(bad_vanilla[:20]))

custom_block = between('CUSTOM = Map.ofEntries(', '\n    );')
custom_entries = re.findall(r'Map\.entry\("([a-z0-9_]+)"', custom_block)
if not custom_entries:
    problems.append('没解析到 CUSTOM 价目条目')

# 运道附属（yundao）是可选软依赖：它的蛊材/蛊虫不在本体 jar 里，
# 只能按命名前缀认出来，无法本地校验 —— 单独统计，不算失败。
SOFT_PREFIX = ('yundao_', 'gucai_')
with zipfile.ZipFile(GUZHENREN) as z:
    names = set(z.namelist())
missing_custom = []
soft_custom = []
for e in sorted(set(custom_entries)):
    if e.startswith(SOFT_PREFIX):
        soft_custom.append(e)
    elif f'assets/guzhenren/models/item/{e}.json' not in names:
        missing_custom.append(e)
print(f'CUSTOM 条目 {len(set(custom_entries))}（其中运道软依赖 {len(soft_custom)} 条不校验），'
      f'本体无模型 {len(missing_custom)}')
if missing_custom:
    problems.append('CUSTOM 里本体没有对应物品: ' + ', '.join(missing_custom[:20]))

items = open(os.path.join(SHOP, 'item', 'ModItems.java'), encoding='utf-8').read()
for want in ('"yuanshengu"', '"yuanshengu_gu_fang"'):
    if want not in items:
        problems.append('ModItems 缺注册 ' + want)
if 'createItems(ShenShangMod.MODID)' not in items:
    problems.append('ModItems 的命名空间没有走 ShenShangMod.MODID（应为 shenshang）')

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print('价目表 OK（原版 id 全存在 / 本体自定义价全有物品 / 物品命名空间未改）')
