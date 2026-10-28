"""列出所有按键绑定的 translation key 与 lang 条目（只读诊断）。"""
import glob
import json
import os
import re

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, 'src', 'main', 'java')
RES = os.path.join(ROOT, 'src', 'main', 'resources')

print('=== 代码里的 KeyMapping translation key ===')
for p in glob.glob(os.path.join(SRC, '**', '*.java'), recursive=True):
    text = open(p, encoding='utf-8').read()
    if 'new KeyMapping(' not in text:
        continue
    print(' ', os.path.relpath(p, SRC))
    for m in re.finditer(r'new KeyMapping\(\s*\n?\s*"([^"]+)"', text):
        print('     translationKey =', m.group(1))
    for m in re.finditer(r'"(key\.[\w.]+)"', text):
        print('     literal       =', m.group(1))

print('\n=== lang 里的 key.* 条目 ===')
for p in glob.glob(os.path.join(RES, 'assets', '**', 'lang', '*.json'), recursive=True):
    try:
        d = json.load(open(p, encoding='utf-8'))
    except Exception as e:
        print(' ', os.path.relpath(p, RES), '解析失败', e)
        continue
    keys = [k for k in d if k.startswith('key.')]
    if keys:
        print(' ', os.path.relpath(p, RES))
        for k in keys:
            print('     ', k, '=', d[k])
