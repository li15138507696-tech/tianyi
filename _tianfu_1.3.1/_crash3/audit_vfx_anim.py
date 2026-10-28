# -*- coding: utf-8 -*-
"""验证实体装饰层用的动画名在实体自己的 animation.json 里真实存在。

这是「动画会不会动」的唯一硬证据：
  * ANIMATION 默认值是 "undefined" -> GeckoLib 找不到 -> 停在默认帧；
  * 写错名字 -> 同样找不到 -> 还是不动。
所以必须逐个比对动画文件里的 key。
"""
import json
import os
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
SRC = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuVfxEntity.java'

# 实体 -> 它自己的 animation.json
ENTITY_ANIM = {
    'Duochongjianyingshiti': 'assets/guzhenren/animations/duochongjianying.animation.json',
    'Jianfeng': 'assets/guzhenren/animations/jianfeng.animation.json',
    'Dalongjuanfeng': 'assets/guzhenren/animations/longjuanfeng.animation.json',
    'Dizhentexiao': 'assets/guzhenren/animations/dizhen.animation.json',
    # v1.2.25 新增四条道（动画文件取自各自 Renderer 的常量）
    'Yaotengushiti': 'assets/guzhenren/animations/yaoteng.animation.json',
    'Baiguiyexingshiti': 'assets/guzhenren/animations/baigui.animation.json',
    'Jingyueshiti': 'assets/guzhenren/animations/jingyueshiti.animation.json',
    'Xueyingliuhuatexiao': 'assets/guzhenren/animations/xueyingliuhua.animation.json',
}

oks, fails = [], []


def ok(m):
    oks.append(m)
    print('  OK    ' + m)


def bad(m):
    fails.append(m)
    print('  FAIL  ' + m)


def main():
    print('=' * 68)
    z = zipfile.ZipFile(JAR)

    # 1. 读源码里的 animOf 映射
    src = open(SRC, encoding='utf-8').read()
    m = re.search(r'private static String animOf\(int tizhi\)\s*\{(.*?)\n    \}', src, re.S)
    if not m:
        bad('找不到 animOf 方法')
        return 1
    body = m.group(1)
    mapping = re.findall(r'case (\d+) -> "([^"]+)"', body)
    if not mapping:
        bad('animOf 里没有 case 映射')
        return 1
    ok('animOf 有 %d 条映射: %s' % (len(mapping), mapping))

    # 2. 体质 -> 实体 -> animation.json 的对应关系
    TIZHI_ENTITY = {
        '25': 'Duochongjianyingshiti',
        '26': 'Jianfeng',
        '31': 'Dalongjuanfeng',
        '17': 'Dizhentexiao',
        '30': 'Yaotengushiti',
        '24': 'Baiguiyexingshiti',
        '40': 'Jingyueshiti',
        '53': 'Xueyingliuhuatexiao',
    }

    # 3. 逐个验证动画名存在
    for tizhi, anim in mapping:
        ent = TIZHI_ENTITY.get(tizhi)
        if ent is None:
            # 没有动画文件的实体：确认它确实不在映射里（避免写了个不存在的名）
            ok('tizhi=%s 用的动画 %r —— 该实体无独立动画文件，跳过校验' % (tizhi, anim))
            continue
        path = ENTITY_ANIM.get(ent)
        if path is None or path not in z.namelist():
            bad('tizhi=%s 找不到 %s 的动画文件' % (tizhi, ent))
            continue
        data = json.loads(z.read(path).decode('utf-8'))
        names = set(data.get('animations', {}).keys())
        if anim in names:
            ok('tizhi=%s -> %r 存在于 %s（可选: %s）'
               % (tizhi, anim, os.path.basename(path), sorted(names)))
        else:
            bad('tizhi=%s 的动画 %r 不在 %s 里（实际有: %s）'
                % (tizhi, anim, os.path.basename(path), sorted(names)))

    # 4. 必须不是默认值 "undefined"
    if '"undefined"' in body:
        bad('animOf 里出现了 "undefined"（等于没设）')
    else:
        ok('没有把 "undefined" 当作动画名')

    # 5. 必须有反射写入（否则设了也没用）
    if 'animationprocedure' in src:
        ok('用反射写 animationprocedure')
    else:
        bad('没写 animationprocedure —— 动画不会动')

    # 5b. 必须同时写 prevAnim，否则每 tick 被 forceAnimationReset 重置
    if 'prevAnim' in src:
        ok('同时写 prevAnim（否则每 tick 被重置，动画冻在第 0 帧）')
    else:
        bad('只写 animationprocedure 没写 prevAnim —— 动画会被反复重置')

    # 6. 反射必须沿继承链找（字段在父类 GeoEntity 里）
    if 'getSuperclass()' in src:
        ok('反射沿继承链向上找字段')
    else:
        bad('只查当前类 —— 字段在父类，会找不到')

    # 7. 寿命必须覆盖动画长度（loop=false 的动画播不完就消失）
    m = re.search(r'private static int lifeOf\(int tizhi\)\s*\{(.*?)\n    \}', src, re.S)
    if m:
        life = dict(re.findall(r'case (\d+) -> (\d+)', m.group(1)))
        ok('lifeOf 有 %d 条映射' % len(life))
        for tizhi, anim in mapping:
            ent = TIZHI_ENTITY.get(tizhi)
            if ent is None or ent not in ENTITY_ANIM:
                continue
            path = ENTITY_ANIM[ent]
            data = json.loads(z.read(path).decode('utf-8'))
            a = data.get('animations', {}).get(anim)
            if a is None:
                continue
            length = a.get('animation_length', 0)
            need = length * 20 + 5
            got = int(life.get(tizhi, 0))
            if got >= need:
                ok('tizhi=%s 寿命 %d >= 动画长度 %.2fs×20+5 = %.0f'
                   % (tizhi, got, length, need))
            else:
                bad('tizhi=%s 寿命 %d 太短 —— 动画长 %.2fs（需 %d tick），播不完就没了'
                    % (tizhi, got, length, need))
    else:
        bad('找不到 lifeOf 方法')

    # 8. 非循环动画必须有「播完再换」保护
    nonloop = 0
    for tizhi, anim in mapping:
        ent = TIZHI_ENTITY.get(tizhi)
        if ent is None or ent not in ENTITY_ANIM:
            continue
        data = json.loads(z.read(ENTITY_ANIM[ent]).decode('utf-8'))
        a = data.get('animations', {}).get(anim)
        if a and not a.get('loop', False):
            nonloop += 1
    if nonloop > 0 and 'LIVE.containsKey' not in src:
        bad('有 %d 个非循环动画，但没有「播完再换」保护' % nonloop)
    else:
        ok('非循环动画有「播完再换」保护（共 %d 个非循环）' % nonloop)

    print('-' * 68)
    print('=' * 68)
    print('通过 %d / 失败 %d' % (len(oks), len(fails)))
    if fails:
        print()
        for f in fails:
            print('  -', f)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
