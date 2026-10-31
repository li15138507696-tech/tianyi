"""3.14.1 性能回归的静态审计：热路径不许重新长出「每 tick 全量遍历」。

本脚本只做源码级检查，**不能**证明耗时。它守的是「结构性约束」：

  1. 服务端 tick 入口（TianyiEvents.onServerTick / WillServer.onServerTick）里
     不许出现 getAllLevels / getEntitiesOfClass / players() / getBlockState / getBlockEntity
     —— 每 tick 全量扫描一旦被加回来，没有编译错误，只有服务端卡顿。
  2. 已登记的节流必须还在：有限资源点 200 刻、见闻录 10 刻、决策周期走 WillConfig。
  3. 3.14.1 的几处改法不许被无声回退：
     - SitePlacement.matchesOwned 不许再用每槽位 new BlockPos 的 Stream 过滤；
     - SiteProtection 的球壳扫描必须先判 EntityBlock 再取方块实体；
     - Observation 的反射字段必须走缓存；
     - SiteEvidence 不许每写一条就现算全体 key 总数；
     - EncounterService 的每秒轮询必须走内容去重（syncIfChanged）。

真实 tick 耗时、方块/方块实体读取成本、网络包体积：只能在游戏里用 spark 或实机观测。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/net/guzhenren/tianyi'


def source(path):
    return (JAVA / path).read_text(encoding='utf-8-sig')


def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return re.sub(r'//[^\n]*', '', text)


def method_body(text, signature):
    """粗取一个方法的函数体：从签名后的第一个 '{' 起做花括号配对。"""
    i = text.find(signature)
    if i < 0:
        return None
    j = text.find('{', i)
    if j < 0:
        return None
    depth = 0
    for k in range(j, len(text)):
        if text[k] == '{':
            depth += 1
        elif text[k] == '}':
            depth -= 1
            if depth == 0:
                return text[j:k + 1]
    return None


def main():
    checks = []

    def check(name, ok, detail=''):
        checks.append((name, bool(ok), detail))

    events = strip_comments(source('TianyiEvents.java'))
    server = strip_comments(source('world/WillServer.java'))
    encounter = strip_comments(source('world/EncounterService.java'))
    finite = strip_comments(source('world/FiniteSiteService.java'))
    net = strip_comments(source('net/JournalNet.java'))
    placement = strip_comments(source('world/SitePlacement.java'))
    protection = strip_comments(source('world/SiteProtection.java'))
    evidence = strip_comments(source('world/SiteEvidence.java'))
    observation = strip_comments(source('world/Observation.java'))

    # ---- 1. tick 入口不得做全量世界扫描 ------------------------------------
    heavy = ('getAllLevels', 'getEntitiesOfClass', '.players()', 'getBlockState',
             'getBlockEntity', 'getEntities(')
    tick = method_body(events, 'static void onServerTick')
    check('TianyiEvents.onServerTick 存在', tick is not None)
    if tick:
        bad = [n for n in heavy if n in tick]
        check('TianyiEvents.onServerTick 里没有全量世界扫描', not bad, ','.join(bad))

    will_tick = method_body(server, 'public void onServerTick')
    check('WillServer.onServerTick 存在', will_tick is not None)
    if will_tick:
        bad = [n for n in heavy if n in will_tick]
        check('WillServer.onServerTick 里没有全量世界扫描', not bad, ','.join(bad))
        check('WillServer.onServerTick 仍然只做计数与周期分派',
              'counter++' in will_tick and 'counter %' in will_tick)

    # ---- 2. 节流仍在 ------------------------------------------------------
    check('有限资源点仍然走 SiteBudget.due（200 刻）',
          'SiteBudget.due(' in finite)
    check('见闻录仍然有 SYNC_MIN_GAP 限流', 'SYNC_MIN_GAP' in net)
    gap = re.search(r'SYNC_MIN_GAP\s*=\s*(\d+)L', net)
    check('见闻录限流没有被改快（≥10 刻）', gap is not None and int(gap.group(1)) >= 10,
          gap.group(1) if gap else 'missing')
    check('决策周期仍然来自 WillConfig/WillForce，不写在 tick 里',
          'force.decisionPeriod()' in will_tick or 'decisionPeriod()' in will_tick)
    check('布局推进仍然按 ACTIVE_JOBS_PER_TICK 限流',
          'ACTIVE_JOBS_PER_TICK' in server)
    check('有限资源点每轮候选数仍然经 SiteBudget 判定',
          'SiteBudget.CANDIDATES_PER_CHECK' in finite)

    # ---- 3. 3.14.1 的改法不许回退 ------------------------------------------
    mo = method_body(placement, 'static boolean matchesOwned')
    check('SitePlacement.matchesOwned 存在', mo is not None)
    if mo:
        check('matchesOwned 不再为每个槽位 new BlockPos + Stream',
              '.stream()' not in mo and 'pos(s).equals' not in mo)
        check('matchesOwned 仍然按 materialized + x/y/z 定位槽位',
              'materialized()' in mo and 'p.getX()' in mo)

    bo = method_body(protection, 'private static boolean benignBlockEntity')
    check('SiteProtection.benignBlockEntity 存在', bo is not None)
    if bo:
        check('球壳扫描先判 EntityBlock 再取方块实体',
              'EntityBlock' in bo and bo.index('EntityBlock') < bo.index('getBlockEntity'))
    check('球壳半径仍然来自 SiteSettings.AVOIDANCE_RADIUS',
          'SiteSettings.AVOIDANCE_RADIUS' in protection)
    check('球壳扫描仍然先确认区块已加载（未加载不读地形）',
          'unloaded_safety_margin' in protection)

    check('Observation 的反射字段走缓存', 'FIELDS.get(' in observation and
          'PlayerVariables.class.getField(name)' in observation)
    check('SiteEvidence 不再现算全体 key 总数',
          'mapToInt(Set::size)' not in evidence and 'writtenKeys' in evidence)
    check('EncounterService 的每秒轮询走内容去重',
          'syncIfChanged(' in encounter and 'LAST_SENT' in encounter)
    check('EncounterService 的显式路径仍然无条件发包（sync 仍然存在）',
          'private static void syncIfChanged' in encounter
          and 'public static void sync(ServerPlayer p)' in encounter)

    # ---- 4. 输出 ----------------------------------------------------------
    failed = 0
    for name, ok, detail in checks:
        if ok:
            print('  PASS', name)
        else:
            failed += 1
            print('  FAIL', name, ('  [' + detail + ']') if detail else '')
    print()
    if failed:
        print(f'FAIL audit_perf：{failed} 项未通过')
        print('边界：静态结构审计，不证明真实耗时；实机请用 spark/服务端 tick 时间对照。')
        return 1
    print(f'audit_perf：{len(checks)} 项全部通过')
    print('边界：静态结构审计，不证明真实耗时；实机请用 spark/服务端 tick 时间对照。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
