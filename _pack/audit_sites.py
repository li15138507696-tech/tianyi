"""有限资源点（3.11）静态边界审计。

3.10.2 时期这里断言过「FiniteSiteService 不得出现在 TianyiMod/TianyiEvents」——
那是“功能未接线”的临时守卫，3.11 已经过时。现在反过来：
**服务必须真的接上线，而危险行为必须仍然不存在**。

本脚本只做源码级静态审计，不能证明世界行为：
真实区块/容器/守护兽/事件顺序只能在游戏里看到（见 docs 与 run_site_service.py 的边界说明）。
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/net/guzhenren/tianyi'

NO_NPC = ('FactionBrain', 'FactionTerritory', 'FactionJourney', 'JourneyMaterializer',
          'ResidentService', 'ResidentRegistry', 'IdentityManager', 'QuestLog', 'QuestLedger',
          'NpcStock', 'NpcTrade', 'com.guzhenrenlibrary')
NO_WORLD_WRITE = ('setBlock(', 'setItem(', 'addFreshEntity(', 'BlockSnapshot', 'EventHooks',
                  'spawnAtLocation(', 'dropCustomDeathLoot')
NO_REWARD = ('give(', 'addItem(', 'spawnAtLocation(', 'setItem(', 'addFreshEntity(',
             'ItemStack', 'BuiltInRegistries')
NO_FORCE_LOAD = ('setChunkForced', 'forceLoad', 'ChunkStatus', 'getChunk(')


def source(path):
    return (JAVA / path).read_text(encoding='utf-8-sig')


def has_all(text, needles):
    return all(n in text for n in needles)


def strip_comments(text):
    """去掉注释再做“依赖了某类”的判断。

    踩过的坑：FiniteSiteService 的类注释里写着「不调用外层 com.guzhenrenlibrary.*」，
    结果被 NO_NPC 当成真的引用了本体 NPC 层，审计误报 FAIL。
    判断依赖只能看代码，不能看注释里的自述。
    """
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    return text


def lacks_all(text, needles):
    return all(n not in text for n in needles)


def main():
    checks = []
    events = source('TianyiEvents.java')
    mod = source('TianyiMod.java')
    service = source('world/FiniteSiteService.java')
    evidence = source('world/SiteEvidence.java')
    placement = source('world/SitePlacement.java')
    protection = source('world/SiteProtection.java')
    settings = source('world/SiteSettings.java')
    budget = source('resource/SiteBudget.java')
    notice = source('journal/SiteNotice.java')
    state = source('world/FiniteSiteState.java')
    journal = source('world/JournalStore.java')
    catalog = source('resource/SiteCatalog.java')
    terrain = source('resource/SiteTerrain.java')

    # ---- 3.10.2 仍然有效的老边界 ----------------------------------------
    encounter = source('world/EncounterSettings.java')
    checks.append(('legacy output and pressure default off', all(
        re.search(r'\.define\("' + key + r'",\s*false\)', encounter)
        for key in ('legacyAutomaticResources', 'legacyAutomaticPressure'))))
    sites = source('world/WorldSites.java')
    for name in ('produce', 'deliver'):
        checks.append((name + ' guarded at boundary', bool(re.search(
            r'public static int ' + name + r'\([^\n]+\)\s*\{\s*if \(!EncounterSettings\.legacyResources\(\)\) return 0;', sites))))
    server = source('world/WillServer.java')
    kill = server.split('public void onPlayerKill(', 1)[1].split('public void onPlayerDeath(', 1)[0]
    pickup = server.split('public void onItemPickedUp(', 1)[1].split('private void thunder(', 1)[0]
    checks.append(('hunting does not damage destiny or imply defiance',
                   'destiny().damage' not in kill and 'noteDefiance(' not in kill))
    checks.append(('pickup does not cause thunder or expose bait',
                   'thunder(player)' not in pickup and 'site.reveal()' not in pickup))
    state_src = source('world/EncounterState.java')
    checks.append(('quarantine preserves original NBT', 'return preserved.copy()' in state_src
                   and 's.preserved = tag.copy()' in state_src and 'putIfAbsent' in state_src))
    net = source('net/JournalNet.java')
    checks.append(('final journal sync is queued and flushed',
                   'PENDING.add(player.getUUID())' in net and 'JournalNet.tick(event.getServer())' in events
                   and 'PENDING.remove(id)' in net and 'JournalNet.clear()' in events))

    # ---- 3.11 服务确实接线（取代过时的“不得出现”断言）------------------
    checks.append(('finite site service is wired into the server tick and lifecycle', has_all(
        events, ('FiniteSiteService.started();', 'FiniteSiteService.tick(event.getServer());',
                 'FiniteSiteService.stopping(event.getServer());'))))
    checks.append(('finite site service observes breaks, container use and guardian death', has_all(
        events, ('FiniteSiteService.onBlockBroken(player, level, event.getPos());',
                 'FiniteSiteService.onBlockUsed(player, event.getPos());',
                 'FiniteSiteService.onGuardianDeath(event.getEntity());'))))
    checks.append(('site settings spec is registered with its own config file',
                   'SiteSettings.SPEC' in mod and 'SiteSettings.CONFIG_FILE' in mod
                   and 'registerConfig' in mod))

    # ---- 默认关闭，但启用后确实会工作（不是“永远不生成”）---------------
    checks.append(('every finite site template defaults to off', all(
        re.search(r'\.define\("' + key + r'",\s*false\)', settings)
        for key in ('enabled', 'plantEnabled', 'oreEnabled', 'guardedEnabled'))))
    checks.append(('service gates on the config and can actually generate once enabled', has_all(
        service, ('SiteSettings.active()', 'SitePlacement.candidate(', 'SitePlacement.materialize(',
                  'ledger.reserve(', 'SitePlacement.verified('))))
    checks.append(('runtime verification failure skips the round instead of disabling forever',
                   'report("verify:' in service and 'continue;' in service))

    # ---- 地形门控（3.11.x 扩展）：先找地形，再找材料 ----------------------
    # 15 个「流派专属地形」是真实 guzhenren 群系 id；静态 id 不等于实机生成。
    TERRAINS = ('cangmulinhai', 'chiyanjintu', 'guidaohuangdi', 'huishangzetan', 'juqiling',
                'kongyupingyuan', 'kuguxueling', 'leize', 'lianxuangudi', 'liuniansenjing',
                'misihuanlin', 'suijinqiulin', 'wanrenqianshan', 'wantanjiang', 'wuguihuangdi')
    checks.append(('all fifteen real guzhenren terrain biomes are declared',
                   all(('guzhenren:' + t) in terrain for t in TERRAINS)))
    checks.append(('terrain gate is pure, whitelist based and offline testable', has_all(
        terrain, ('public static boolean biomeAllowed(', 'public static String guardianFor(',
                  'ALL_GUZHENREN_BIOMES', 'GUARDIAN_BY_BIOME', 'PLANT_BIOMES'))))
    checks.append(('terrain gate is enforced per physical slot, not globally', has_all(
        placement, ('SiteTerrain.biomeAllowed(r.candidate.biomeCandidates(), biomeId(level,pos))',))))
    checks.append(('template is chosen from the candidate terrain first', has_all(
        service, ('SitePlacement.biomeId(level, origin)', 'SiteCatalog.templatesForBiome(biomeId)'))))
    # 「地形不匹配」分支必须是 continue（换下一个候选），不能是 return（整个玩法停摆）。
    terrain_branch = (service.split('if (candidate == null) {', 1)[1][:600]
                      if 'if (candidate == null) {' in service else 'return;')
    checks.append(('terrain mismatch skips this candidate only, never the whole template',
                   'continue;' in terrain_branch and 'return;' not in terrain_branch))
    checks.append(('guardian is chosen by terrain with a reviewed fallback', has_all(
        service, ('SiteTerrain.guardianFor(terrain, candidate.guardianCandidates())',))
        and has_all(terrain, ('FALLBACK_GUARDIANS', '"guzhenren:hao_dian_lang"',
                              '"guzhenren:hu"', '"guzhenren:huoyanxiong"', '"guzhenren:yao_ya_quan"'))))
    # 六个守护兽与四种矿石方块的真实类名都已从锁定内层 jar 逐一核实
    # （net/guzhenren/entity/*Entity.class、net/guzhenren/block/*Block.class）。
    # 断言要求它们逐个出现在校验表里：新增一种资源却不补类名，这里就会红。
    checks.append(('all six guardians map to a bytecode-reviewed class', all(
        c in placement for c in (
            'net.guzhenren.entity.HuiLangEntity', 'net.guzhenren.entity.TuLangEntity',
            'net.guzhenren.entity.HaoDianLangEntity', 'net.guzhenren.entity.HuEntity',
            'net.guzhenren.entity.HuoyanxiongEntity', 'net.guzhenren.entity.YaoYaQuanEntity'))))
    checks.append(('all four ore blocks map to a bytecode-reviewed class', all(
        c in placement for c in (
            'net.guzhenren.block.XuanjinBlock', 'net.guzhenren.block.FangkuaiyuanshiyuankuangBlock',
            'net.guzhenren.block.ChilianjinyuankuangBlock', 'net.guzhenren.block.WenDingShiKuangBlock'))))
    checks.append(('unknown ids are rejected instead of silently substituted',
                   placement.count('default -> null;') >= 2
                   and 'BuiltInRegistries.ENTITY_TYPE.getKey(' in placement))

    # ---- 三种真实矿石露头：真实方块 + 真实掉落表，永不恢复 ----------------
    checks.append(('three real ore outcrop templates carry their real block and loot item', has_all(
        catalog, ('tianyi:yuanshi_outcrop', 'guzhenren:fangkuaiyuanshiyuankuang', 'guzhenren:gucaiyuanshi',
                  'tianyi:chilian_outcrop', 'guzhenren:chilianjinyuankuang',
                  'tianyi:wending_outcrop', 'guzhenren:wen_ding_shi_kuang', 'guzhenren:wen_ding_shi'))))
    checks.append(('outcrop block may differ from its loot item, without treating the block as a container',
                   'blockFor(' in placement
                   and 'singleItemLoot(level, blockId, selection.material())' in placement))
    checks.append(('new terrain ore templates default to off', all(
        re.search(r'\.define\("' + key + r'",\s*false\)', settings)
        for key in ('yuanshiOutcropEnabled', 'chilianOutcropEnabled', 'wendingOutcropEnabled'))))
    checks.append(('per-template switch is additive to the kind switch', has_all(
        settings, ('public static boolean templateEnabled(String template', 'TEMPLATE_FLAGS'))))
    checks.append(('guardian AI stays the mod\'s own: no targeting, tether or teleport',
                   lacks_all(service, ('setTarget(', 'setLastHurtByMob('))
                   and lacks_all(placement, ('setTarget(', 'setLastHurtByMob('))))

    # ---- 预算与上限 ------------------------------------------------------
    checks.append(('budget constants are the single source of truth', has_all(
        budget, ('CHECK_INTERVAL_TICKS = 200', 'CANDIDATES_PER_CHECK = 4',
                 'PLACEMENTS_PER_CHECK = 1', 'REGION_CHUNKS = 8', 'GLOBAL_LIMIT = 64',
                 'GROWTH_TICKS = 72000L')) and has_all(
        settings, ('SiteBudget.CANDIDATES_PER_CHECK', 'SiteBudget.PLACEMENTS_PER_CHECK',
                   'SiteBudget.REGION_CHUNKS', 'SiteBudget.GLOBAL_LIMIT'))))
    checks.append(('service honours the per-check, region and global budgets', has_all(
        service, ('SiteBudget.due(', 'SiteBudget.hasRoom(', 'SiteBudget.regionAvailable(',
                  'SiteBudget.placementsLeft(', 'SiteBudget.units(', 'ledger.regions()'))))
    checks.append(('generation never force-loads chunks', lacks_all(service, NO_FORCE_LOAD)
                   and lacks_all(protection, NO_FORCE_LOAD) and 'level.hasChunk(' in service))

    # ---- 保护：普通原版环境是 ALLOW，不是全世界 UNKNOWN ------------------
    checks.append(('ordinary natural overworld is ALLOW, not globally UNKNOWN', has_all(
        protection, ('new Decision(Verdict.ALLOW, "natural_overworld")', 'if (claims != null)',
                     'adminAllowsUnknown(dim', 'deny("not_overworld")'))))
    checks.append(('protection never silently disables itself', 'SiteSettings.enabled()' not in protection))

    # ---- 容器/方块是唯一来源，服务不自己写世界 --------------------------
    checks.append(('service writes no blocks, items or entities directly',
                   lacks_all(service, NO_WORLD_WRITE) and has_all(
                       service, ('SitePlacement.observe(', 'SitePlacement.selection(',
                                 'SitePlacement.growthConditions(', 'SitePlacement.guardianStatus('))))
    checks.append(('unobserved decrease is reconciled, never replenished', has_all(
        service, ('observeRemaining(', 'destroyCarrier(', 'observation.remaining() < 0'))))
    checks.append(('ledger quarantine stops delivery instead of replaying', has_all(
        service, ('ledger.mutationReady()', 'current.quarantine()'))))

    # ---- 守护：只有匹配 UUID 的死亡算确认，不补生、不给奖励 -------------
    checks.append(('guardian death is confirmed only by matching UUID', has_all(
        service, ('matchesGuardianDeath(entity.getUUID())', 'SitePlacement.MARKER',
                  'guardianStatus(level, current)'))))
    checks.append(('service never spawns or rewards anything', lacks_all(service, NO_REWARD)
                   and lacks_all(evidence, NO_REWARD)))
    checks.append(('no kill reward is added for guardians', 'guardianSlain' in evidence
                   and 'give(' not in evidence and 'addItem(' not in evidence))

    # ---- 见闻：只写本人条目，复用既有同步，不新增 payload --------------
    checks.append(('evidence writes personal entries through the existing journal store',
                   has_all(evidence, ('JournalStore.note(', 'SiteNotice.witnessed(',
                                      'SitePlacement.clue('))
                   and 'JournalNet.sync(player)' in evidence))
    checks.append(('evidence adds no new payload or protocol', lacks_all(
        evidence, ('JournalSyncPayload', 'PacketDistributor', 'CustomPacketPayload'))))
    checks.append(('journal store exposes a single generic note() feed', 'public static boolean note(' in journal))

    # ---- 不依赖 NPC / 势力 / 任务 / 追捕 --------------------------------
    for label, text in (('service', service), ('evidence', evidence), ('placement', placement),
                        ('protection', protection), ('settings', settings), ('budget', budget),
                        ('notice', notice), ('terrain', terrain)):
        checks.append(('finite site ' + label + ' stays NPC independent',
                       lacks_all(strip_comments(text), NO_NPC)))

    # ---- 纯逻辑包仍然纯净 ------------------------------------------------
    for file in (JAVA / 'resource').glob('*.java'):
        checks.append((file.name + ' remains pure and NPC independent',
                       not re.search(r'^import\s+(net\.(minecraft|neoforged)|com\.guzhenrenlibrary)',
                                     file.read_text(encoding='utf-8-sig'), re.M)))
    checks.append(('journal notices remain pure and offline testable',
                   not re.search(r'^import\s+(net\.(minecraft|neoforged)|com\.guzhenrenlibrary)',
                                 notice, re.M)))

    # ---- 账本仍然是 B 的模型，C 没偷偷重写 ------------------------------
    checks.append(('finite ledger schema is unchanged', has_all(
        state, ('SCHEMA_VERSION = 1', 'tianyi_finite_sites', 'MAX_SITES = 64'))))

    for label, ok in checks:
        print(('PASS ' if ok else 'FAIL ') + label)
    print('Static wiring audit only: real chunks, containers, guardians, protection plugins,')
    print('AI, loot and rendering are NOT certified here and remain unverified in-game.')
    return 0 if all(ok for _, ok in checks) else 1


if __name__ == '__main__':
    sys.exit(main())
