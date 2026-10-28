"""补充 dump：桥接层还需要的一批类型（FactionDef / Persona / 玩家变量 / JourneyKind 等）。"""
import os
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUTER = r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar'
INNER = os.path.join(ROOT, '_pack', 'libs', 'guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
REFS = os.path.join(ROOT, '_pack', 'refs')

NAMES = [
    'com.guzhenrenlibrary.faction.FactionDef',
    'com.guzhenrenlibrary.faction.FactionStance',
    'com.guzhenrenlibrary.faction.FactionRank',
    'com.guzhenrenlibrary.faction.FactionChronicle',
    'com.guzhenrenlibrary.faction.journey.JourneyKind',
    'com.guzhenrenlibrary.faction.team.FactionTeam',
    'com.guzhenrenlibrary.npc.NpcPersona',
    'com.guzhenrenlibrary.npc.NpcIdentityType',
    'com.guzhenrenlibrary.npc.combat.NpcInventory',
    'com.guzhenrenlibrary.identity.PlayerIdentity',
    'com.guzhenrenlibrary.identity.IdentityBook',
    'com.guzhenrenlibrary.terrain.TerrainKind',
    'net.guzhenren.network.GuzhenrenModVariables',
]


def classes_of(jar):
    with zipfile.ZipFile(jar) as z:
        return set(x[:-6].replace('/', '.') for x in z.namelist() if x.endswith('.class'))


def main():
    outer = classes_of(OUTER)
    inner = classes_of(INNER)
    cp = OUTER + ';' + INNER
    out = []
    for name in NAMES:
        where = 'outer' if name in outer else ('inner' if name in inner else None)
        if where is None:
            out.append('### %s\n  !! 不存在 !!\n' % name)
            print('MISSING', name)
            continue
        r = subprocess.run([JAVAP, '-p', '-cp', cp, name], capture_output=True,
                           text=True, encoding='utf-8', errors='replace')
        out.append('### %s   [%s]\n%s\n' % (name, where, ((r.stdout or '') + (r.stderr or '')).rstrip()))
        print('OK', where, name)
    path = os.path.join(REFS, 'extra.javap.txt')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out))
    print('wrote', path)


if __name__ == '__main__':
    sys.exit(main())
