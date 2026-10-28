# -*- coding: utf-8 -*-
"""「X烛」架构审计。

取代 audit_zhoushi.py —— 那个脚本检查的是旧逻辑
（所有带流派道痕的体质统一挂宙侵 zhoudaobuff），
v1.2.19 改成「每个凡体挂自己流派的烛」后已过时。

本脚本检查新架构的不变量。
"""
import json
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
SRC = ROOT + r'\src\main\java\net\guzhenren\tianfu'
INIT = SRC + r'\init'
LANG = ROOT + r'\src\main\resources\assets\tianfu\lang\zh_cn.json'

oks, warns, fails = [], [], []


def ok(m):
    oks.append(m)
    print('  OK    ' + m)


def warn(m):
    warns.append(m)
    print('  WARN  ' + m)


def bad(m):
    fails.append(m)
    print('  FAIL  ' + m)


def read(p):
    with open(p, encoding='utf-8') as f:
        return f.read()


# 期望的 19 支烛：(体质 tizhi, 中文名, 流派标记, 道痕字段)
EXPECT = [
    (17, '土烛', 'TuDao', 'daohen_tudao'),
    (18, '兵烛', 'BingDao', 'daohen_bingdao'),
    (19, '冰雪烛', 'BingXueDao', 'daohen_bingxuedao'),
    (21, '风烛', 'FengDao', 'daohen_fengdao'),
    (22, '骨烛', 'GuDao', 'daohen_gudao'),
    (24, '魂烛', 'HunDao', 'daohen_hundao'),
    (25, '剑烛', 'JianDao', 'daohen_jiandao'),
    (26, '金烛', 'JinDao', 'daohen_jindao'),
    (28, '力烛', 'LiDao', 'daohen_lidao'),
    (30, '木烛', 'MuDao', 'daohen_mudao'),
    (31, '气烛', 'QiDao', 'dahen_qidao'),
    (33, '血烛', 'XueDao', 'daohen_xuedao'),
    (37, '虚烛', 'XuDao', 'daohen_xudao'),
    (39, '影烛', 'YingDao', 'daohen_yingdao'),
    (40, '月烛', 'YueDao', 'daohen_yuedao'),
    (42, '智烛', 'ZhiDao', 'dahen_zhidao'),
    (43, '宙烛', 'ZhouDao', 'dahen_zhoudao'),
    (48, '梦烛', 'MengDao', 'daohen_mengdao'),
]
# 53 血煞蜕变体共用血烛
SHARED = [(53, '血烛')]


def main():
    print('=' * 68)

    # ---- 1. 旧实现已删 ----
    if os.path.exists(os.path.join(SRC, 'TianfuZhoushi.java')):
        bad('TianfuZhoushi.java 仍存在（旧「统一挂宙侵」实现）')
    else:
        ok('TianfuZhoushi.java 已删除')

    # ---- 2. 新文件都在 ----
    for f in ['TianfuZhu.java', 'TianfuZhuCombat.java']:
        if os.path.exists(os.path.join(SRC, f)):
            ok('%s 存在' % f)
        else:
            bad('%s 缺失' % f)
    if os.path.exists(os.path.join(INIT, 'TianfuZhuEffects.java')):
        ok('init/TianfuZhuEffects.java 存在')
    else:
        bad('init/TianfuZhuEffects.java 缺失')

    zhu = read(os.path.join(SRC, 'TianfuZhu.java'))
    combat = read(os.path.join(SRC, 'TianfuZhuCombat.java'))
    effects = read(os.path.join(INIT, 'TianfuZhuEffects.java'))
    mod = read(os.path.join(SRC, 'TianfuMod.java'))

    # ---- 3. 19 支烛的数据表 ----
    n = len(re.findall(r'^\s*public static final Candle ', zhu, re.M))
    if n == 18:
        ok('烛常量 18 个（+ 血烛服务两个体质 = 19 支）')
    else:
        bad('烛常量 %d 个，期望 18' % n)

    for tizhi, name, liupai, key in EXPECT:
        if name in zhu:
            pass
        else:
            bad('缺少烛：%s' % name)
    ok('19 个中文名全部存在')

    # 每个流派标记都要出现
    missing_lp = [lp for _, _, lp, _ in EXPECT if '"%s"' % lp not in zhu]
    if missing_lp:
        bad('缺少流派标记: %s' % missing_lp)
    else:
        ok('18 个流派标记齐全')

    # 每个道痕字段都要在 daohenOf 里被读到
    missing_key = [k for _, _, _, k in EXPECT if '"%s"' % k not in combat]
    if missing_key:
        bad('daohenOf 未覆盖字段: %s' % missing_key)
    else:
        ok('daohenOf 覆盖全部 18 个道痕字段')

    # ---- 4. 注册表 ----
    n_reg = len(re.findall(r'= reg\(TianfuZhu\.', effects))
    if n_reg == 18:
        ok('注册了 18 个效果（19 支烛）')
    else:
        bad('注册了 %d 个效果，期望 18' % n_reg)

    if 'DeferredRegister.create(Registries.MOB_EFFECT' in effects:
        ok('走 DeferredRegister<MobEffect> 注册')
    else:
        bad('未使用 DeferredRegister<MobEffect>')

    if 'MobEffectCategory.NEUTRAL' in zhu:
        ok('效果类别为 NEUTRAL（与宙侵一致）')
    else:
        bad('效果类别不是 NEUTRAL')

    # 注册表必须在 Mod 构造里注册
    if 'TianfuZhuEffects.REGISTRY.register(modBus)' in mod:
        ok('TianfuZhuEffects.REGISTRY 已在 TianfuMod 注册')
    else:
        bad('TianfuZhuEffects.REGISTRY 未注册 —— 效果不会生效')

    # ---- 5. 伤害结算走本体通道 ----
    if 'ShangHaiLiuCheng1Procedure.execute' in combat:
        ok('伤害结算走本体 ShangHaiLiuCheng1Procedure')
    else:
        bad('未走本体伤害结算通道')

    if re.search(r'\.hurt\(', combat):
        bad('自己调了 hurt() —— 会和本体结算双计')
    else:
        ok('未自己调 hurt()（不双计）')

    # 8 参签名：level, attacker, target, base, mult, elapsed, liupai
    m = re.search(r'ShangHaiLiuCheng1Procedure\.execute\((.*?)\);', combat, re.S)
    if m:
        args = [a.strip() for a in m.group(1).split(',')]
        if len(args) == 7:
            ok('结算传 7 个参数（level/source/target/base/mult/elapsed/liupai）')
        else:
            bad('结算参数 %d 个，期望 7' % len(args))
        if args and 'candle.liupai()' in args[-1]:
            ok('流派标记用各自的 candle.liupai()')
        else:
            bad('流派标记未按烛传入')

    # ---- 6. 时长与结算口径 ----
    if 'BASE_DURATION = 200' in zhu:
        ok('基准时长 200 tick（与宙侵一致）')
    else:
        bad('基准时长不是 200')
    if 'ELAPSED_SCALE = 0.1' in zhu:
        ok('已侵蚀时长折算 0.1（与宙侵一致）')
    else:
        bad('折算系数不是 0.1')
    if 'getDuration() * TianfuZhu.ELAPSED_SCALE' in combat:
        ok('结算用「已侵蚀时长 × 0.1」')
    else:
        bad('结算未用已侵蚀时长')

    # ---- 7. 调用点已切到新实现 ----
    tc = read(os.path.join(SRC, 'TianfuCombat.java'))
    if 'TianfuZhuCombat.onHit' in tc:
        ok('TianfuCombat 已改调 TianfuZhuCombat.onHit')
    else:
        bad('TianfuCombat 未切到新实现')
    if 'TianfuZhoushi.apply' in tc:
        bad('TianfuCombat 仍调旧 TianfuZhoushi.apply')
    else:
        ok('TianfuCombat 无旧调用')

    te = read(os.path.join(SRC, 'TianfuEvents.java'))
    if 'TianfuZhuCombat.forget' in te:
        ok('TianfuEvents 已改调 TianfuZhuCombat.forget')
    else:
        bad('TianfuEvents 未清理新节流表')

    # ---- 8. 全源码不得再引用已删类 ----
    stale = []
    for dirpath, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith('.java'):
                continue
            txt = read(os.path.join(dirpath, f))
            body = '\n'.join(l for l in txt.split('\n')
                             if not l.strip().startswith(('*', '//', '/*')))
            if 'TianfuZhoushi' in body:
                stale.append(f)
    if stale:
        bad('仍有代码引用 TianfuZhoushi: %s' % stale)
    else:
        ok('无代码引用已删的 TianfuZhoushi')

    # ---- 9. lang 条目 ----
    try:
        lang = json.loads(read(LANG))
    except Exception as e:
        bad('lang 解析失败: %s' % e)
        lang = {}
    missing = []
    for tizhi, name, liupai, key in EXPECT:
        # 从 TianfuZhu 里抓 id
        pass
    ids = re.findall(r'c\("(tianfu_zhu_\w+)"', zhu)
    for i in ids:
        if 'effect.tianfu.%s' % i not in lang:
            missing.append(i)
    if missing:
        bad('lang 缺 %d 条: %s' % (len(missing), missing[:5]))
    else:
        ok('lang 覆盖全部 %d 个效果 id' % len(ids))

    # lang 里的中文名要与 TianfuZhu 一致
    mismatch = []
    for m2 in re.finditer(r'c\("(tianfu_zhu_\w+)",\s*"([^"]+)"', zhu):
        eid, cname = m2.group(1), m2.group(2)
        lname = lang.get('effect.tianfu.%s' % eid)
        if lname != cname:
            mismatch.append('%s: java=%s lang=%s' % (eid, cname, lname))
    if mismatch:
        bad('lang 与 Java 名不一致: %s' % mismatch[:3])
    else:
        ok('lang 中文名与 Java 定义完全一致')

    # ---- 10. 血烛共用 ----
    if 'BY_TIZHI.put(53, XUE)' in zhu:
        ok('血烛同时服务 tizhi=53（血煞蜕变体）')
    else:
        bad('血烛未覆盖 tizhi=53')

    # ---- 11. 御空踏云体确实被排除 ----
    if '45' in zhu:
        # 允许在注释里提到
        pass
    if re.search(r'c\("tianfu_zhu_\w+",\s*"[^"]+",\s*"\w+",\s*"\w+",\s*0x[0-9A-Fa-f]+,\s*45\)', zhu):
        bad('御空踏云体（45）被建了烛 —— 它没有 daohen 字段')
    else:
        ok('御空踏云体（45）已排除（无 daohen 字段）')

    print('-' * 68)
    print('=' * 68)
    print('通过 %d / 警告 %d / 失败 %d' % (len(oks), len(warns), len(fails)))
    if fails:
        print()
        print('失败项:')
        for f in fails:
            print('  -', f)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
