"""生成天意面板的 GUI 纹理（暗墨底 + 暗金纹样，修仙质感）。

## 为什么不再用纯色块

用户反馈「界面是糊的、不够华丽」。纯色 `fill` 的问题：
- 没有边框层次，1px 线在非整数缩放下会糊成灰；
- 无渐变/无噪点 → 大面积平色，屏幕缩放后像贴了张纸；
- 文字关阴影 → 暗底上的浅字糊成一团。

所以改成**真纹理**：暗墨底 + 竖向渐变 + 细微噪点 + 双层金线 + 云纹角饰。
全部 1x 像素绘制，MC 以最近邻放大，所以只要坐标对齐整数就不会糊。

## 配色（修仙感：墨 / 金 / 玉 / 朱）

    墨底  #14100E → #1F1915      金线  #C9A227 / #E8C86A      暗金 #6B4F1D
    玉    #7FB8A0                朱砂  #B03A2E
"""
import os
import random

from PIL import Image, ImageDraw

OUT = r'D:\daima\mcmod\天意\src\main\resources\assets\tianyi\textures\gui'

INK_TOP = (20, 16, 14, 255)      # #14100E
INK_BOT = (31, 25, 21, 255)      # #1F1915
GOLD = (201, 162, 39, 255)       # #C9A227
GOLD_HI = (232, 200, 106, 255)   # #E8C86A
GOLD_DK = (107, 79, 29, 255)     # #6B4F1D
LINE = (58, 45, 22, 255)         # #3A2D16
BLACK = (10, 7, 5, 255)
JADE = (127, 184, 160, 255)
CINNABAR = (176, 58, 46, 255)


def noise_fill(img, box, top, bottom, jitter=3):
    """竖向渐变 + 细微噪点（避免大面积平色缩放后发糊）。"""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        t = (y - y0) / h
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        d.line([(x0, y), (x1 - 1, y)], fill=(r, g, b, 255))
    rnd = random.Random(20261024)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if rnd.random() < 0.06:
                r, g, b, a = img.getpixel((x, y))
                k = rnd.randint(-jitter, jitter)
                img.putpixel((x, y), (max(0, min(255, r + k)),
                                      max(0, min(255, g + k)),
                                      max(0, min(255, b + k)), a))


def corner_cloud(d, cx, cy, sx, sy, color):
    """12x12 云纹角饰（用 1px 线勾，缩放开也是硬的）。"""
    pts = [(0, 6), (1, 4), (3, 2), (6, 1), (9, 2), (10, 4)]
    for (px, py) in pts:
        x = cx + (px if sx > 0 else -px)
        y = cy + (py if sy > 0 else -py)
        d.point((x, y), fill=color)
    # 卷勾
    for i in range(3):
        d.point((cx + sx * (2 + i), cy + sy * (5 + i)), fill=color)


def panel():
    """主面板 9-slice：边 8px，内部可拉伸。"""
    S = 64
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    noise_fill(img, (0, 0, S, S), INK_TOP, INK_BOT, jitter=2)
    # 四层边框：黑 → 暗金 → 金 → 内部线
    d.rectangle([0, 0, S - 1, S - 1], outline=BLACK, width=1)
    d.rectangle([1, 1, S - 2, S - 2], outline=GOLD_DK, width=1)
    d.rectangle([2, 2, S - 3, S - 3], outline=GOLD, width=1)
    d.rectangle([3, 3, S - 4, S - 4], outline=LINE, width=1)
    # 四角云纹
    for (cx, cy, sx, sy) in ((5, 5, 1, 1), (S - 6, 5, -1, 1), (5, S - 6, 1, -1), (S - 6, S - 6, -1, -1)):
        corner_cloud(d, cx, cy, sx, sy, GOLD_HI)
    img.save(os.path.join(OUT, 'panel.png'))


def header():
    """标题条：64x14，金线分隔。"""
    W, H = 64, 14
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    noise_fill(img, (0, 0, W, H), (58, 42, 16, 255), (36, 26, 12, 255), jitter=2)
    d.line([(0, H - 1), (W, H - 1)], fill=GOLD_DK)
    d.line([(0, H - 2), (W, H - 2)], fill=GOLD)
    img.save(os.path.join(OUT, 'header.png'))


def row(hover):
    """谕令行：64x18，底部发丝线；hover 加左侧金条与淡金叠加。"""
    W, H = 64, 18
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if hover:
        noise_fill(img, (0, 0, W, H), (46, 36, 18, 255), (36, 28, 14, 255), jitter=2)
        d.rectangle([0, 0, 1, H - 1], fill=GOLD)
    d.line([(0, H - 1), (W, H - 1)], fill=LINE)
    img.save(os.path.join(OUT, 'row_hover.png' if hover else 'row.png'))


def button(hover):
    """按钮：48x14，金色描边 + 内高光。"""
    W, H = 48, 14
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if hover:
        noise_fill(img, (0, 0, W, H), (72, 54, 20, 255), (48, 36, 14, 255), jitter=2)
        d.rectangle([0, 0, W - 1, H - 1], outline=GOLD_HI, width=1)
    else:
        noise_fill(img, (0, 0, W, H), (44, 34, 16, 255), (28, 22, 12, 255), jitter=2)
        d.rectangle([0, 0, W - 1, H - 1], outline=GOLD_DK, width=1)
    d.line([(1, 1), (W - 2, 1)], fill=GOLD)
    img.save(os.path.join(OUT, 'btn_hover.png' if hover else 'btn.png'))


def tab(active):
    """页签：40x13。"""
    W, H = 40, 13
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if active:
        noise_fill(img, (0, 0, W, H), (62, 46, 18, 255), (40, 30, 14, 255), jitter=2)
        d.line([(0, H - 1), (W, H - 1)], fill=GOLD_HI)
        d.line([(0, H - 2), (W, H - 2)], fill=GOLD)
    else:
        noise_fill(img, (0, 0, W, H), (30, 25, 16, 255), (22, 18, 12, 255), jitter=2)
        d.line([(0, H - 1), (W, H - 1)], fill=LINE)
    img.save(os.path.join(OUT, 'tab_on.png' if active else 'tab_off.png'))


def bars():
    """底槽与填充：64x6。"""
    W, H = 64, 6
    bg = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    noise_fill(bg, (0, 0, W, H), (18, 14, 12, 255), (12, 9, 8, 255), jitter=1)
    d.rectangle([0, 0, W - 1, H - 1], outline=LINE, width=1)
    bg.save(os.path.join(OUT, 'bar_bg.png'))

    fill = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fill)
    noise_fill(fill, (0, 0, W, H), GOLD_HI, GOLD, jitter=2)
    d.line([(0, 0), (W - 1, 0)], fill=(255, 232, 170, 255))
    d.line([(0, H - 1), (W - 1, H - 1)], fill=GOLD_DK)
    fill.save(os.path.join(OUT, 'bar_fill.png'))


def seal():
    """天意印：28x28，朱砂底 + 金框。"""
    S = 28
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    noise_fill(img, (0, 0, S, S), (150, 44, 36, 255), (110, 30, 24, 255), jitter=3)
    d.rectangle([0, 0, S - 1, S - 1], outline=GOLD, width=1)
    d.rectangle([1, 1, S - 2, S - 2], outline=BLACK, width=1)
    # 中间两笔：简化的「天」
    d.line([(7, 9), (20, 9)], fill=GOLD_HI)
    d.line([(13, 9), (13, 20)], fill=GOLD_HI)
    d.line([(9, 15), (18, 15)], fill=GOLD_HI)
    img.save(os.path.join(OUT, 'seal.png'))


def divider():
    """分隔装饰：48x5，中间菱形。"""
    W, H = 48, 5
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([(0, 2), (W // 2 - 4, 2)], fill=LINE)
    d.line([(W // 2 + 4, 2), (W - 1, 2)], fill=LINE)
    d.polygon([(W // 2, 0), (W // 2 + 3, 2), (W // 2, 4), (W // 2 - 3, 2)], outline=GOLD)
    img.save(os.path.join(OUT, 'divider.png'))


def main():
    os.makedirs(OUT, exist_ok=True)
    panel()
    header()
    row(False)
    row(True)
    button(False)
    button(True)
    tab(False)
    tab(True)
    bars()
    seal()
    divider()
    files = sorted(os.listdir(OUT))
    print(f'生成 {len(files)} 个文件 -> {OUT}')
    for f in files:
        p = os.path.join(OUT, f)
        print(f'  {f:<16} {os.path.getsize(p):>6} bytes')


if __name__ == '__main__':
    main()
