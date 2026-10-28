# -*- coding: utf-8 -*-
"""剑势环 / 火焰环绕 的离线可视化。

静态代码看不出「密集到能看出剑势」是什么样，所以把 sword() 与 flame()
的坐标公式逐帧算出来，画成俯视图 + 侧视图，人眼判断密度与形态。

用法：python _crash3/render_vfx.py
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


def read_consts():
    """从 Java 源码读常量，避免模拟器与实现漂移。"""
    src = open(VFX, encoding='utf-8').read()
    c = {}
    m = re.search(r'RADIUS = ([\d.]+)', src)
    c['RADIUS'] = float(m.group(1))
    m = re.search(r'DENSITY = (\d+)', src)
    c['DENSITY'] = int(m.group(1))
    m = re.search(r'PERIOD = (\d+)', src)
    c['PERIOD'] = int(m.group(1))
    # 剑：密度
    m = re.search(r'JIANQI, Shape\.SWORD, ([\d.]+), (\d+), (\d+)\)', src)
    c['SWORD_SPEED'] = float(m.group(1))
    c['SWORD_DENSITY'] = int(m.group(3))
    # 火：密度
    m = re.search(r'SHAREN_YAN, Shape\.FLAME, ([\d.]+), (\d+), (\d+)\)', src)
    c['FLAME_SPEED'] = float(m.group(1))
    c['FLAME_DENSITY'] = int(m.group(3))
    return c


def sword_points(C, tick, rnd_seed=0):
    """复刻 sword()：返回 [(px, py, pz, vx, vy, vz)]。"""
    n = C['SWORD_DENSITY']
    base = (tick % 30) * (TWO_PI / 30.0)
    breathe = 0.675 + math.sin(tick * 0.15) * 0.125
    pts = []
    rng = _lcg(rnd_seed + tick)
    for i in range(n):
        a = base + i * (TWO_PI / n)
        h = 0.55 if i % 2 == 0 else 1.05
        r = breathe * (0.92 + rng() * 0.16)
        px = math.cos(a) * r
        pz = math.sin(a) * r
        vx = -math.sin(a) * C['SWORD_SPEED']
        vz = math.cos(a) * C['SWORD_SPEED']
        vy = C['SWORD_SPEED'] * 0.35
        pts.append((px, h, pz, vx, vy, vz))
    return pts


def flame_points(C, tick, rnd_seed=0):
    """复刻 flame()。"""
    n = C['FLAME_DENSITY']
    base = (tick % 24) * (TWO_PI / 24.0)
    pts = []
    for i in range(n):
        t = i / n
        a = base + t * TWO_PI * 2.0
        h = 0.05 + t * 1.70
        r = C['RADIUS'] * (0.70 + t * 0.40)
        px = math.cos(a) * r
        pz = math.sin(a) * r
        vx = -math.sin(a) * C['FLAME_SPEED'] * 0.4
        vz = math.cos(a) * C['FLAME_SPEED'] * 0.4
        pts.append((px, h, pz, vx, C['FLAME_SPEED'], vz))
    return pts


def _lcg(seed):
    """确定性伪随机（模拟 Java RandomSource 的不可预测性，只要可重现即可）。"""
    state = [seed * 1103515245 + 12345]

    def nxt():
        state[0] = (state[0] * 1103515245 + 12345) & 0x7FFFFFFF
        return state[0] / 0x7FFFFFFF
    return nxt


def draw_top(C, name, fn, ticks, color, scale=120, size=(360, 360)):
    """俯视图：把所有 tick 的粒子叠在一起，看环绕是否成圈。"""
    img = Image.new('RGB', size, (16, 14, 12))
    d = ImageDraw.Draw(img)
    cx, cy = size[0] // 2, size[1] // 2
    # 参考圆
    for rr in (0.55, 0.675, 0.80):
        R = rr * scale
        d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(50, 45, 38))
    # 玩家位置
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=(200, 180, 120))

    total = 0
    for tick in ticks:
        for (px, py, pz, vx, vy, vz) in fn(C, tick):
            # 俯视：X 轴向右，Z 轴向下
            x = cx + px * scale
            y = cy + pz * scale
            # 高度映射亮度
            b = 0.45 + min(1.0, py / 1.3) * 0.55
            col = tuple(int(c * b) for c in color)
            d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=col)
            total += 1
    d.text((8, 8), '%s TOP  ticks=%d  particles=%d' % (name, len(ticks), total),
           fill=(220, 210, 190))
    p = os.path.join(OUT, 'vfx_%s_top.png' % name)
    img.save(p)
    print('saved', p, 'particles', total)
    return total


def draw_side(C, name, fn, ticks, color, scale=120, size=(360, 360)):
    """侧视图：X 轴水平、Y 轴垂直，看高度分布与螺旋。"""
    img = Image.new('RGB', size, (16, 14, 12))
    d = ImageDraw.Draw(img)
    cx = size[0] // 2
    base_y = size[1] - 60
    # 地面
    d.line([(0, base_y), (size[0], base_y)], fill=(60, 55, 45))
    # 玩家轮廓（1.8 格高）
    d.rectangle([cx - 14, base_y - 1.8 * scale, cx + 14, base_y],
                outline=(70, 65, 55))

    total = 0
    for tick in ticks:
        for (px, py, pz, vx, vy, vz) in fn(C, tick):
            x = cx + px * scale
            y = base_y - py * scale
            b = 0.45 + min(1.0, py / 1.3) * 0.55
            col = tuple(int(c * b) for c in color)
            d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=col)
            total += 1
    d.text((8, 8), '%s SIDE  ticks=%d  particles=%d' % (name, len(ticks), total),
           fill=(220, 210, 190))
    p = os.path.join(OUT, 'vfx_%s_side.png' % name)
    img.save(p)
    print('saved', p, 'particles', total)
    return total


def draw_single_frame(C, name, fn, tick, color, scale=140, size=(360, 360)):
    """单帧：看这一 tick 到底有多少粒子、分布是否均匀。"""
    img = Image.new('RGB', size, (16, 14, 12))
    d = ImageDraw.Draw(img)
    cx = size[0] // 2
    base_y = size[1] - 60
    d.line([(0, base_y), (size[0], base_y)], fill=(60, 55, 45))
    d.rectangle([cx - 14, base_y - 1.8 * scale, cx + 14, base_y],
                outline=(70, 65, 55))
    pts = fn(C, tick)
    for (px, py, pz, vx, vy, vz) in pts:
        x = cx + px * scale
        y = base_y - py * scale
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=color)
        # 速度矢量
        d.line([(x, y), (x + vx * 400, y - vy * 400)], fill=(120, 110, 90))
    d.text((8, 8), '%s FRAME tick=%d  particles=%d' % (name, tick, len(pts)),
           fill=(220, 210, 190))
    p = os.path.join(OUT, 'vfx_%s_frame.png' % name)
    img.save(p)
    print('saved', p, 'particles', len(pts))


def main():
    C = read_consts()
    print('常量:', C)
    print()

    ticks = list(range(0, 60))   # 两圈
    sword_col = (216, 232, 240)
    flame_col = (255, 140, 60)

    n1 = draw_top(C, 'sword', sword_points, ticks, sword_col)
    draw_side(C, 'sword', sword_points, ticks, sword_col)
    draw_single_frame(C, 'sword', sword_points, 0, sword_col)

    print()
    n2 = draw_top(C, 'flame', flame_points, ticks, flame_col)
    draw_side(C, 'flame', flame_points, ticks, flame_col)
    draw_single_frame(C, 'flame', flame_points, 0, flame_col)

    print()
    print('剑：60 tick 共 %d 粒子（每 tick %d）' % (n1, n1 // 60))
    print('火：60 tick 共 %d 粒子（每 tick %d）' % (n2, n2 // 60))
    print()
    # 覆盖率估算：把俯视图分成 24 个扇区，看有多少扇区有粒子
    for nm, fn in [('sword', sword_points), ('flame', flame_points)]:
        sectors = set()
        for tick in ticks:
            for (px, py, pz, *_rest) in fn(C, tick):
                ang = math.atan2(pz, px)
                sectors.add(int((ang + math.pi) / TWO_PI * 24) % 24)
        print('%s 俯视扇区覆盖: %d/24' % (nm, len(sectors)))


if __name__ == '__main__':
    main()
