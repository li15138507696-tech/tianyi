"""清理：`key.categories.tianyi` 只保留一份，避免三份 lang 互相覆盖。"""
import glob
import json
import os

RES = r'D:\daima\mcmod\天意\src\main\resources'
KEEP = os.path.join(RES, 'assets', 'tianfu', 'lang', 'zh_cn.json')

for p in glob.glob(os.path.join(RES, 'assets', '**', 'lang', '*.json'), recursive=True):
    if os.path.normpath(p) == os.path.normpath(KEEP):
        continue
    try:
        data = json.load(open(p, encoding='utf-8'))
    except Exception:
        continue
    if 'key.categories.tianyi' in data:
        del data['key.categories.tianyi']
        with open(p, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
        print('  去掉重复分类:', os.path.relpath(p, RES))

# 确保保留的那份是「天意」
data = json.load(open(KEEP, encoding='utf-8'))
data['key.categories.tianyi'] = '天意'
with open(KEEP, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('  统一分类名: tianfu/lang/zh_cn.json -> 天意')
