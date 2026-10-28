"""把 guzhenren_library（外层 10.20 包）+ 内层本体里我们关心的类签名 dump 成参考文本。

为什么需要：铁律 2 —— API 签名必须来自参考资料，不能凭记忆写。
本脚本产出 _pack/refs/*.javap.txt，供实现期 read_file 查阅。
用法：python _pack/dump_refs.py
"""
import os
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUTER = r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar'
INNER = os.path.join(ROOT, '_pack', 'libs', 'guzhenren-0.1-neoforge-1.21.1.jar')
JDK = r'C:\Users\ADMIN\.jdks\temurin-21\bin'
JAVAP = os.path.join(JDK, 'javap.exe')
REFS = os.path.join(ROOT, '_pack', 'refs')

# 需要的类：按主题分组，方便实现时定位
GROUPS = {
    'faction': [
        'com.guzhenrenlibrary.faction.FactionBrain',
        'com.guzhenrenlibrary.faction.FactionTerritory',
        'com.guzhenrenlibrary.faction.FactionTerritory$State',
        'com.guzhenrenlibrary.faction.FactionTerritory$Standing',
        'com.guzhenrenlibrary.faction.FactionTerritory$Posting',
        'com.guzhenrenlibrary.faction.FactionTerritory$Application',
        'com.guzhenrenlibrary.faction.journey.FactionJourney',
        'com.guzhenrenlibrary.faction.journey.FactionJourney$Phase',
        'com.guzhenrenlibrary.faction.journey.FactionJourneys',
        'com.guzhenrenlibrary.faction.journey.JourneyMaterializer',
    ],
    'npc': [
        'com.guzhenrenlibrary.npc.life.Resident',
        'com.guzhenrenlibrary.npc.life.Resident$State',
        'com.guzhenrenlibrary.npc.life.ResidentService',
        'com.guzhenrenlibrary.npc.life.ResidentData',
        'com.guzhenrenlibrary.npc.life.ResidentRegistry',
        'com.guzhenrenlibrary.npc.life.ResidentRules',
        'com.guzhenrenlibrary.npc.NpcTrade',
        'com.guzhenrenlibrary.npc.NpcTrade$Offer',
        'com.guzhenrenlibrary.npc.NpcStock',
    ],
    'identity': [
        'com.guzhenrenlibrary.identity.IdentityManager',
    ],
    'quest': [
        'com.guzhenrenlibrary.quest.QuestLog',
        'com.guzhenrenlibrary.quest.QuestLedger',
        'com.guzhenrenlibrary.quest.QuestLedgerData',
    ],
}


def jar_classes(jar):
    with zipfile.ZipFile(jar) as z:
        return set(x[:-6].replace('/', '.') for x in z.namelist() if x.endswith('.class'))


def main():
    if not os.path.isfile(JAVAP):
        sys.exit('missing javap: ' + JAVAP)
    outer_cls = jar_classes(OUTER)
    inner_cls = jar_classes(INNER)
    os.makedirs(REFS, exist_ok=True)
    cp = OUTER + ';' + INNER
    index = []
    for group, names in GROUPS.items():
        lines = []
        for name in names:
            where = 'outer' if name in outer_cls else ('inner' if name in inner_cls else None)
            if where is None:
                lines.append('### %s\n  !! 不存在于两个 jar !!\n' % name)
                index.append('%-8s %-64s MISSING' % (group, name))
                continue
            r = subprocess.run([JAVAP, '-p', '-cp', cp, name],
                               capture_output=True, text=True,
                               encoding='utf-8', errors='replace')
            body = (r.stdout or '') + (r.stderr or '')
            lines.append('### %s   [%s]\n%s\n' % (name, where, body.rstrip()))
            index.append('%-8s %-64s %s' % (group, name, where))
        path = os.path.join(REFS, group + '.javap.txt')
        with open(path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines))
        print('wrote', path, len('\n'.join(lines)))
    with open(os.path.join(REFS, 'INDEX.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(index) + '\n')
    print('\n'.join(index))


if __name__ == '__main__':
    main()
