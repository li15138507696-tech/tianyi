"""统计道途词条池规模与品质分布。"""
import re

src = open(r'D:\daima\mcmod\道途附属\src\main\java\net\guzhenren\daotu\DaotuTalent.java',
           encoding='utf-8').read()

# 形如 t("id", "名称", "品质", "描述", "摘要", "效果")
ids = re.findall(r't\("([a-z0-9_]+)"', src)
if not ids:
    ids = re.findall(r'entry\("([a-z0-9_]+)"', src)

print('词条条目数:', len(ids))
print('前 8:', ids[:8])

quals = re.findall(r't\("[a-z0-9_]+",\s*"[^"]*",\s*"([^"]*)"', src)
from collections import Counter
print()
print('品质分布:', dict(Counter(quals)))
print('总计:', len(quals))
