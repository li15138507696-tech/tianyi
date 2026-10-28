"""Probe base mod for identity/faction/lineage systems usable by a new addon."""
import io
import json
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'

sys.stdout.reconfigure(encoding='utf-8')

z = zipfile.ZipFile(JAR)

# 1. lang keys containing faction / identity words
lang = z.read('assets/guzhenren/lang/en_us.json').decode('utf-8')
data = json.loads(lang)
print('lang keys total:', len(data))

words = ['身份', '势力', '出身', '流派', '道痕', '转数', '境界', '蛊师', '家族', '宗门', '散修']
for w in words:
    hits = [(k, v) for k, v in data.items() if w in v or w in k]
    print(f'--- {w}: {len(hits)} ---')
    for k, v in hits[:8]:
        print('   ', k, '=', v)
