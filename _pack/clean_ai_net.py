"""按大括号配对删除 TianyiNet 里残留的 backendOf（正则搞不定的嵌套块）。"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi',
                 'TianyiNet.java')
lines = open(P, encoding='utf-8').read().split('\n')

start = None
for i, line in enumerate(lines):
    if '只取 endpoint 的 host 给面板显示' in line or '当前生效的 AI 后端' in line:
        start = i
        break

if start is None:
    print('没找到残留块，可能已清理')
else:
    # 找到方法签名行，再按括号配对定位结束
    sig = None
    for i in range(start, min(start + 6, len(lines))):
        if 'backendOf(' in lines[i]:
            sig = i
            break
    if sig is None:
        raise SystemExit('找不到 backendOf 方法签名')

    depth = 0
    end = None
    for i in range(sig, len(lines)):
        depth += lines[i].count('{') - lines[i].count('}')
        if depth == 0 and '{' in lines[sig] or (i > sig and depth == 0 and '}' in lines[i]):
            end = i
            break
    if end is None:
        raise SystemExit('找不到方法结尾')

    # 连同上方注释与下方空行一起删
    del lines[start:end + 1]
    while start < len(lines) and lines[start].strip() == '':
        del lines[start]
    open(P, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
    print(f'已删除第 {start + 1} 行起的 AI 后端起方法（含注释）')
