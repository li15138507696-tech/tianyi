"""从本体 GUI 纹理里提取配色（用于让新面板符合画风）。

模型看不了图，所以用程序化方式取「事实」：
- 尺寸（判断是 256x256 背景还是 9-slice 边框）
- 出现频率最高的若干颜色（主色调）
- 四角与四边中点的颜色（边框/底纹色）
"""
import collections
import glob
import os

from PIL import Image

DIR = os.path.join(os.environ['TEMP'], 'guitex')


def report(path):
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    px = list(im.getdata())
    cnt = collections.Counter(px)
    print(f'\n== {os.path.basename(path)}  {w}x{h}')
    print('   top colors:')
    for color, n in cnt.most_common(8):
        r, g, b, a = color
        print(f'     #{r:02X}{g:02X}{b:02X} a={a:3d}  {n:6d}px  {n / (w * h) * 100:5.1f}%')
    pts = {
        'corner(0,0)': (0, 0),
        'corner(w-1,0)': (w - 1, 0),
        'corner(0,h-1)': (0, h - 1),
        'corner(w-1,h-1)': (w - 1, h - 1),
        'edge top mid': (w // 2, 0),
        'edge left mid': (0, h // 2),
        'center': (w // 2, h // 2),
    }
    print('   samples:')
    for name, (x, y) in pts.items():
        r, g, b, a = im.getpixel((x, y))
        print(f'     {name:<18} #{r:02X}{g:02X}{b:02X} a={a}')


for p in sorted(glob.glob(os.path.join(DIR, '*.png'))):
    report(p)
