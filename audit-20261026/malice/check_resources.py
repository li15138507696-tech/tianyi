"""Read-only resource checks against the exact requested jar and installed base."""
from pathlib import Path
from io import BytesIO
import hashlib
import json
import struct
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = Path(r'D:\daima\mcmod\蛊真人恶意')
TARGET = ROOT / 'build/libs/guzhenren_malice-1.1.0-mc1020.jar'
MODS = Path(r'D:\game\mcmod\蛊2\.minecraft\versions\转生到蛊界就要拿出真本事\mods')

def utf8_constants(blob):
    count = struct.unpack_from('>H', blob, 8)[0]
    pos, index, values = 10, 1, set()
    sizes = {3:4, 4:4, 5:8, 6:8, 7:2, 8:2, 9:4, 10:4, 11:4, 12:4, 15:3, 16:2, 17:4, 18:4, 19:2, 20:2}
    while index < count:
        tag = blob[pos]; pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', blob, pos)[0]; pos += 2
            values.add(blob[pos:pos+size].decode('utf-8', errors='replace')); pos += size
        else:
            pos += sizes[tag]
            if tag in (5, 6): index += 1
        index += 1
    return values

outer_path = MODS / 'guzhenrn蛊真人模组本体10.20.jar'
with zipfile.ZipFile(outer_path) as outer:
    candidates = [n for n in outer.namelist() if n.endswith('.jar') and 'guzhenren-0.1' in n]
    assert len(candidates) == 1, candidates
    inner = outer.read(candidates[0])
with zipfile.ZipFile(BytesIO(inner)) as base:
    ids = utf8_constants(base.read('net/guzhenren/init/GuzhenrenModEntities.class'))

rows = []
def record(label, condition, detail=''):
    rows.append(('PASS ' if condition else 'FAIL ') + label + (': ' + detail if detail else ''))
    assert condition, rows[-1]

with zipfile.ZipFile(TARGET) as jar:
    record('target variant', jar.read('malice_variant.txt').decode().strip() == '1020')
    tags = {}
    for tier in ('normal', 'elite', 'boss', 'exclude'):
        data = json.loads(jar.read('data/guzhenren/tags/entity_type/malice_' + tier + '.json'))
        tags[tier] = set(data['values'])
        absent = [item for item in tags[tier] if item.split(':', 1)[1] not in ids]
        record('registered ids ' + tier, not absent, str(len(tags[tier])) + ' entries; absent=' + repr(absent))
    all_tiered = tags['normal'] | tags['elite'] | tags['boss']
    record('tiers disjoint', len(all_tiered) == sum(len(tags[k]) for k in ('normal', 'elite', 'boss')))
    record('excluded entities disjoint', not all_tiered.intersection(tags['exclude']))
    whitelist = set(json.loads(jar.read('data/l2hostility/tags/entity_type/whitelist.json'))['values'])
    record('whitelist equals tier union', whitelist == all_tiered, str(len(whitelist)) + ' entries')
    for name in jar.namelist():
        if name.endswith('.json'):
            json.loads(jar.read(name))
    record('all packaged JSON valid', True)
    config = json.loads(jar.read('data/guzhenren/l2hostility_config/entity/guzhenren_malice.json'))
    record('three entity configs', len(config['list']) == 3)
    for item in config['list']:
        forbidden = {'l2hostility:adaptive', 'l2hostility:levitation'}
        record('forbidden traits ' + item['entities'], forbidden.issubset(item['blacklist'])
               and not forbidden.intersection(t['trait'] for t in item['traits']))
    classfiles = [n for n in jar.namelist() if n.endswith('.class')]
    record('nine compiled classes', len(classfiles) == 9)
    matching = [name for name in classfiles if (ROOT / '_build/javac_out' / name).read_bytes() == jar.read(name)]
    record('target classfiles match original javac output', len(matching) == len(classfiles))
rows.append('Target SHA256: ' + hashlib.sha256(TARGET.read_bytes()).hexdigest())
rows.append('Installed embedded base SHA256: ' + hashlib.sha256(inner).hexdigest())
(HERE / 'resource-checks.txt').write_text('\n'.join(rows) + '\n', encoding='utf-8')
print('\n'.join(rows))
