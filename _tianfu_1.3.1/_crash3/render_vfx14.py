# -*- coding: utf-8 -*-
"""14 条道复合特效的离线可视化。

每个形态的坐标公式逐帧算出来，画成俯视图 + 侧视图 + 单帧，
人眼判断几何是否对（冲击波是否成环、符文阵是否在脚下、
锁链是否缠绕、星辉是否垂落、毒雾是否贴地）。

用法：python _crash3/render_vfx14.py
"""
import math
import os
import re

from PIL import Image, ImageDraw

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
VFX = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren',
                   'tianfu', 'TianfuVfx.java')
OUT = os.path.join(ROOT, '_crash3')
TWO_PI = math.pi * 2


def consts():
    src = open(VFX, encoding='utf-8').read()
    c = {}
    c['RADIUS'] = float(re.search(r'RADIUS = ([\d.]+)', src).group(1))
    c['DENSITY'] = int(re.search(r'DENSITY = (\d+)', src).group(1))
    c['PERIOD'] = int(re.search(r'PERIOD = (\d+)', src).group(1))
    return c


def _lcg(seed):
    s = [seed * 1103515245 + 12345]

    def nxt():
        s[0] = (s[0] * 1103515245 + 12345) & 0x7FFFFFFF
        return s[0] / 0x7FFFFFFF
    return nxt


def shockwave(C, tick, n, speed, seed=0):
    """复刻 shockwave()：脉冲环。"""
    t = (tick % 20) / 20.0
    r = 0.3 + t * 1.9
    cnt = max(2, int(n * (1.0 - t * 0.6)))
    rng = _lcg(seed + tick)
    pts = []
    for i in range(cnt):
        a = (i / cnt) * TWO_PI + rng() * 0.15
        pts.append((math.cos(a) * r, 0.08, math.sin(a) * r, 0, 0.01, 0))
    return pts


def rune_circle(C, tick, n, seed=0):
    """复刻 runeCircle()：双层反向旋转。"""
    base = -(tick % 60) * (TWO_PI / 60.0)
    pts = []
    for ring in range(2):
        rr = 0.55 + ring * 0.45
        cnt = max(4, n // 2)
        for i in range(cnt):
            a = base * (1.0 if ring == 0 else -1.3) + i * (TWO_PI / cnt)
            pts.append((math.cos(a) * rr, 0.03, math.sin(a) * rr, 0, 0, 0))
    return pts


def chain(C, tick, n, speed, seed=0):
    """复刻 chain()：3.5 圈缠绕。"""
    base = (tick % 36) * (TWO_PI / 36.0)
    pts = []
    for i in range(n):
        t = i / n
        a = base + t * TWO_PI * 2.0
        h = 0.15 + t * 1.57
        r = C['RADIUS'] * (0.78 + math.sin(a) * 0.22)
        pts.append((math.cos(a) * r, h, math.sin(a) * r, 0, speed * 0.3, 0))
    return pts


def float_orbit(C, tick, n, speed, seed=0):
    """复刻 floatOrbit()：多层悬浮。"""
    base = (tick % 48) * (TWO_PI / 48.0)
    pts = []
    for i in range(n):
        h = 0.35 + ((i * 37) % 100) / 100.0 * 1.15
        rr = C['RADIUS'] * (0.9 + ((i * 53) % 100) / 100.0 * 0.7)
        a = base + i * (TWO_PI / n)
        pts.append((math.cos(a) * rr, h, math.sin(a) * rr, 0, 0.005, 0))
    return pts


def starfall(C, tick, n, speed, seed=0):
    """复刻 starfall()：高处垂落。"""
    rng = _lcg(seed + tick)
    pts = []
    for i in range(n):
        a = rng() * TWO_PI
        rr = C['RADIUS'] * (0.4 + rng() * 1.1)
        h = 0.2 + rng() * 2.2
        pts.append((math.cos(a) * rr, h, math.sin(a) * rr, 0, -0.02, 0))
    return pts


def mist(C, tick, n, speed, seed=0):
    """复刻 mist()：贴地扩散。"""
    t = (tick % 40) / 40.0
    r = 0.4 + t * 1.6
    rng = _lcg(seed + tick)
    pts = []
    for i in range(n):
        a = rng() * TWO_PI
        rr = r * (0.7 + rng() * 0.5)
        h = 0.05 + rng() * 0.45
        pts.append((math.cos(a) * rr, h, math.sin(a) * rr, 0, 0.002, 0))
    return pts


# 14 条道：(名字, 形态, 密度, 速度, 颜色)
ROADS = [
    ('lidao',   'SHOCKWAVE',   16, 0.04, (220, 70, 50)),
    ('qidao',   'ORBIT',       14, 0.03, (170, 230, 255)),
    ('xuedao',  'CHAIN',       20, 0.03, (170, 30, 40)),
    ('gudao',   'COLUMN',      12, 0.02, (230, 225, 210)),
    ('yandao',  'FLAME',       16, 0.035, (255, 130, 40)),
    ('shuidao', 'FLOAT_ORBIT', 14, 0.02, (80, 160, 240)),
    ('mudao',   'CHAIN',       20, 0.025, (90, 200, 90)),
    ('tudao',   'SHOCKWAVE',   12, 0.03, (200, 170, 90)),
    ('jindao',  'FLOAT_ORBIT', 16, 0.04, (240, 200, 70)),
    ('leidao',  'ORBIT',       16, 0.05, (170, 110, 240)),
    ('hundao',  'ORBIT',       14, 0.02, (110, 200, 140)),
    ('xindao',  'CHAIN',       20, 0.02, (240, 210, 130)),
    ('xingdao', 'STARFALL',    16, 0.02, (200, 210, 255)),
    ('dudao',   'MIST',        14, 0.015, (120, 200, 80)),
]

# 新形态函数映射
SHAPES = {
    'SHOCKWAVE': shockwave,
    'RUNE_CIRCLE': rune_circle,
    'CHAIN': chain,
    'FLOAT_ORBIT': float_orbit,
    'STARFALL': starfall,
    'MIST': mist,
}


def orbit(C, tick, n, speed, seed=0):
    """复刻 orbit()：单层竖直圆环。"""
    angle = (tick % 40) * (TWO_PI / 40.0)
    pts = []
    for i in range(n):
        a = angle + i * (TWO_PI / n)
        h = 0.6 + math.sin(a * 2.0) * 0.35
        pts.append((math.cos(a) * C['RADIUS'], h, math.sin(a) * C['RADIUS'], 0, speed * 0.5, 0))
    return pts


def column(C, tick, n, speed, seed=0):
    """复刻 column()：随机柱体。"""
    rng = _lcg(seed + tick)
    pts = []
    for i in range(n):
        h = rng() * 1.2
        ox = (rng() - 0.5) * 0.6
        oz = (rng() - 0.5) * 0.6
        pts.append((ox, h, oz, 0, speed, 0))
    return pts


def flame(C, tick, n, speed, seed=0):
    """复刻 flame()：螺旋上升。"""
    base = (tick % 24) * (TWO_PI / 24.0)
    pts = []
    for i in range(n):
        t = i / n
        a = base + t * TWO_PI * 2.0
        h = 0.05 + t * 1.70
        r = C['RADIUS'] * (0.70 + t * 0.40)
        pts.append((math.cos(a) * r, h, math.sin(a) * r, 0, speed, 0))
    return pts


SHAPES['ORBIT'] = orbit
SHAPES['COLUMN'] = column
SHAPES['FLAME'] = flame


def render(C, name, shape, n, speed, color, ticks):
    """三合一图：俯视 / 侧视 / 单帧，横向拼起来。"""
    W, H = 240, 300
    img = Image.new('RGB', (W * 3, H), (14, 13, 12))
    d = ImageDraw.Draw(img)
    fn = SHAPES[shape]
    scale = 85

    # ---- 面板 1：俯视（叠加多帧）----
    cx, cy = W // 2, H // 2 + 10
    for rr in (0.55, 1.0, 1.6):
        R = rr * scale
        d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(45, 42, 36))
    d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=(190, 175, 120))
    total = 0
    for tick in ticks:
        for (px, py, pz, *_r) in fn(C, tick, n, speed, 0):
            x, y = cx + px * scale, cy + pz * scale
            b = 0.4 + min(1.0, py / 2.0) * 0.6
            col = tuple(int(c * b) for c in color)
            d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=col)
            total += 1
    d.text((6, 6), '%s %s  TOP  %d pts' % (name, shape, total), fill=(215, 205, 185))

    # ---- 面板 2：侧视（叠加多帧）----
    ox = W
    cx2, base_y = ox + W // 2, H - 50
    d.line([(ox, base_y), (ox + W, base_y)], fill=(55, 50, 42))
    d.rectangle([cx2 - 12, base_y - 1.8 * scale, cx2 + 12, base_y],
                outline=(65, 60, 50))
    for tick in ticks:
        for (px, py, pz, *_r) in fn(C, tick, n, speed, 0):
            x = cx2 + px * scale
            y = base_y - py * scale
            b = 0.4 + min(1.0, py / 2.0) * 0.6
            col = tuple(int(c * b) for c in color)
            d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=col)
    d.text((ox + 6, 6), '%s SIDE' % name, fill=(215, 205, 185))

    # ---- 面板 3：单帧 ----
    ox2 = W * 2
    cx3, base_y2 = ox2 + W // 2, H - 50
    d.line([(ox2, base_y2), (ox2 + W, base_y2)], fill=(55, 50, 42))
    d.rectangle([cx3 - 12, base_y2 - 1.8 * scale, cx3 + 12, base_y2],
                outline=(65, 60, 50))
    pts = fn(C, 0, n, speed, 0)
    for (px, py, pz, *_r) in pts:
        x = cx3 + px * scale
        y = base_y2 - py * scale
        d.ellipse([x - 2.5, y - 2.5, x + 2.5, y + 2.5], fill=color)
    d.text((ox2 + 6, 6), '%s FRAME  %d pts' % (name, len(pts)), fill=(215, 205, 185))

    p = os.path.join(OUT, 'vfx14_%s.png' % name)
    img.save(p)
    return p, total, len(pts)


def main():
    C = consts()
    print('常量:', C)
    print()
    ticks = list(range(0, 40))
    print('%-10s %-12s %8s %8s' % ('road', 'shape', 'total', 'per-frame'))
    print('-' * 44)
    for (name, shape, n, speed, color) in ROADS:
        p, total, pf = render(C, name, shape, n, speed, color, ticks)
        print('%-10s %-12s %8d %8d   -> %s' % (name, shape, total, pf,
                                               os.path.basename(p)))
    print()
    print('saved %d images to %s' % (len(ROADS), OUT))


if __name__ == '__main__':
    main()
