"""按章节标题抽取知识库片段（每节前 N 行），用于设计任务与系统输入。"""
import re

KB = r'D:\cxdownload\蛊真人整合包知识库_AI版.txt'
WANT = ['## 6. ', '## 8. ', '## 12. ', '## 16. ', '## 17. ', '## 20. ', '### 15.4', '### 15.1', '## 13. ']
N = 14

lines = open(KB, encoding='utf-8').read().split('\n')
starts = [i for i, l in enumerate(lines) if l.startswith('## ') or l.startswith('### ')]

for want in WANT:
    idx = next((i for i in starts if lines[i].startswith(want)), None)
    if idx is None:
        print(f'\n!!! 未找到 {want}')
        continue
    print('\n' + '=' * 70)
    for l in lines[idx:idx + N]:
        print(l)
