# -*- coding: utf-8 -*-
"""实体装饰层审计（TianfuVfxEntity）。

复用本体实体的风险点只有三个，本脚本逐个查：
  1. 会不会伤到人 / 给玩家加 buff（生成路径的副作用）；
  2. 会不会在世界里残留孤儿实体（寿命回收）；
  3. 会不会自己追人乱走（AI 未关）。
"""
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
SRC = ROOT + r'\src\main\java\net\guzhenren\tianfu'
VFXE = os.path.join(SRC, 'TianfuVfxEntity.java')
EVENTS = os.path.join(SRC, 'TianfuEvents.java')
EFFECTS = os.path.join(SRC, 'TianfuEffects.java')

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


def body_of(src):
    """剥掉注释，只留代码。"""
    return '\n'.join(l for l in src.split('\n')
                     if not l.strip().startswith(('*', '//', '/*')))


def main():
    print('=' * 68)

    if not os.path.exists(VFXE):
        bad('TianfuVfxEntity.java 不存在')
        return 1
    ok('TianfuVfxEntity.java 存在')

    src = read(VFXE)
    body = body_of(src)

    # ---- 1. 生成路径必须绕过 finalizeSpawn ----
    if 'EntityType.create' in body or 'type.create(' in body:
        ok('用 EntityType.create(level) 生成（绕过 finalizeSpawn）')
    else:
        bad('未用 EntityType.create —— 可能触发 finalizeSpawn 副作用')

    # 绝不能出现 MobSpawnType 相关调用
    for danger in ['MobSpawnType', 'finalizeSpawn', 'spawnMob', 'addWithSpawnType']:
        if danger in body:
            bad('出现 %s —— 会触发本体的生成 procedure 副作用' % danger)
    ok('未出现 MobSpawnType / finalizeSpawn / spawnMob')

    # ---- 2. 必须设的四道保险 ----
    for call, why in [
        ('setInvulnerable(true)', '打不死'),
        ('setNoGravity(true)', '不坠落'),
        ('setSilent(true)', '不发声'),
        ('setNoAi(true)', '不乱走 / 不追人'),
    ]:
        if call in body:
            ok('设了 %s（%s）' % (call, why))
        else:
            bad('缺 %s —— %s' % (call, why))

    # ---- 3. 寿命回收 ----
    if 'discard()' in body:
        ok('用 discard() 回收实体')
    else:
        bad('没有 discard() —— 实体会永久残留')

    m = re.search(r'LIFE_TICKS = (\d+)', src)
    if m:
        ok('寿命常量 LIFE_TICKS = %s' % m.group(1))
    else:
        bad('缺 LIFE_TICKS')

    # 寿命必须按动画长度分配，不能一刀切
    if 'lifeOf' in body and 'lifeOf(tizhi)' in body:
        ok('寿命按体质分配（lifeOf），匹配动画长度')
    else:
        bad('寿命一刀切 —— 非循环动画会被毙在半路')

    # LIFE_TICKS 必须真的被用来判断超龄
    if 'tickCount - born >= lifeOf(tizhi)' in body:
        ok('寿命真的被用于判断超龄')
    else:
        bad('寿命定义了但没用于回收')

    # 刷新必须是「播完再换」，不能定时毙动画
    if 'LIVE.containsKey(id)' in body:
        ok('刷新策略是「播完再换」（不等动画播完就换会看到闪烁的雕塑）')
    else:
        bad('刷新策略不是「播完再换」 —— 非循环动画会被毙在半路')

    # 不该再有定时刷新残留
    if 'RESPAWN_TICKS' in body:
        bad('仍有 RESPAWN_TICKS 定时刷新 —— 会毙掉播到一半的动画')
    else:
        ok('无定时刷新残留')

    # ---- 4. 清理钩子 ----
    ev = read(EVENTS)
    for hook, why in [
        ('PlayerLoggedOutEvent', '下线清理'),
        ('PlayerChangedDimensionEvent', '换维度清理'),
    ]:
        if hook in ev:
            ok('挂了 %s（%s）' % (hook, why))
        else:
            bad('没挂 %s —— %s' % (hook, why))
    if 'TianfuVfxEntity.forget' in ev:
        ok('事件里调了 forget() 清理')
    else:
        bad('事件里没调 forget()')

    # 死亡也要清
    m = re.search(r'onDeath\(LivingDeathEvent event\)(.*?)\n    \}', ev, re.S)
    if m and 'TianfuVfxEntity.forget' in m.group(1):
        ok('死亡时清理装饰实体')
    else:
        bad('死亡时未清理 —— 实体会留在死亡点')

    # ---- 5. 接入调用点 ----
    ef = read(EFFECTS)
    if 'TianfuVfxEntity.tick' in ef:
        ok('TianfuEffects 已调 TianfuVfxEntity.tick')
    else:
        bad('TianfuEffects 未接入实体层')

    # ---- 6. 实体类型必须来自本体注册表 ----
    types = re.findall(r'GuzhenrenModEntities\.(\w+)\.get\(\)', body)
    if types:
        ok('引用了 %d 种本体实体: %s' % (len(types), types))
    else:
        bad('没有引用任何本体实体')
    for t in types:
        if not t.isupper():
            bad('实体字段名 %s 不是大写常量' % t)

    # ---- 7. 必须处理 create 返回 null ----
    if 'e == null' in body or '== null' in body:
        ok('处理了 create() 返回 null 的情况')
    else:
        bad('没判 null —— EntityType.create 可能返回 null')

    # ---- 8. 不能在客户端跑 ----
    if 'isClientSide' in body:
        ok('有客户端判断')
    else:
        warn('没显式判 isClientSide（依赖调用方只在服务端调）')

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
