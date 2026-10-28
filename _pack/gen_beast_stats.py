"""从本体 jar 的字节码里抽取蛊兽的原生属性（生命/护甲/攻击）。

为什么要抽：奇遇系统需要「每只兽的原生属性」才能给玩家可用的提示，
而这些值只写在实体类的 `registerAttributes()` / `createAttributes()` 里
（`Attributes.MAX_HEALTH` / `ARMOR` / `ATTACK_DAMAGE`），没有 JSON 可读。

做法：对每个名录 id 找到 `net/guzhenren/entity/<Name>Entity.class`，
用 `javap -c -p` 反汇编，取 `getstatic Attributes.X` 之后、`invokevirtual add`
之前那个数值常量。

产出 `_pack/_beast_stats.json`，供生成 `encounter/BeastStats.java` 与审计核对。
"""
import json
import os
import re
import subprocess
import tempfile
import zipfile

ROOT = r'D:\daima\mcmod\天意'
JAR = os.path.join(ROOT, '_pack', 'libs', 'guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
CATALOG = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'will',
                       'BeastCatalog.java')
OUT = os.path.join(ROOT, '_pack', '_beast_stats.json')


def catalog_entries():
    src = open(CATALOG, encoding='utf-8').read()
    pat = r'add\(\s*(\d)\s*,\s*Dao\.([A-Z_]+)\s*,\s*"([a-z0-9_]+)"\s*,\s*"([^"]+)"\s*\)'
    return [(int(t), d, p, cn) for t, d, p, cn in re.findall(pat, src)]


def grab(out, attr):
    """取某个 Attributes.X 后面紧跟的数值常量。"""
    m = re.search(r'Attributes\.' + attr + r'\b(.*?)(?:invokevirtual.*?\badd\b)', out, re.S)
    if not m:
        return None
    seg = m.group(1)
    num = re.search(r'//\s*(?:double|float|int|long)\s+(-?[\d.]+)', seg)
    if num:
        return float(num.group(1))
    if 'dconst_0' in seg or 'iconst_0' in seg or 'fconst_0' in seg:
        return 0.0
    return None


def main():
    entries = catalog_entries()
    print('名录条目：', len(entries))
    z = zipfile.ZipFile(JAR)
    classes = [n for n in z.namelist()
               if n.startswith('net/guzhenren/entity/') and n.endswith('.class')
               and '$' not in n]
    tmp = tempfile.mkdtemp()

    def find(eid):
        norm = eid.replace('_', '').lower()
        for c in classes:
            if os.path.basename(c)[:-6].lower() == norm + 'entity':
                return c
        for c in classes:
            if os.path.basename(c)[:-6].lower().startswith(norm):
                return c
        return None

    rows, miss = [], []
    for tier, dao, eid, cn in entries:
        cls = find(eid)
        if cls is None:
            miss.append((eid, '找不到实体类'))
            continue
        probe = os.path.join(tmp, 'Probe.class')
        with open(probe, 'wb') as f:
            f.write(z.read(cls))
        r = subprocess.run([JAVAP, '-c', '-p', '-cp', tmp, 'Probe'],
                           capture_output=True, text=True, encoding='utf-8', errors='replace')
        hp = grab(r.stdout, 'MAX_HEALTH')
        armor = grab(r.stdout, 'ARMOR')
        atk = grab(r.stdout, 'ATTACK_DAMAGE')
        if None in (hp, armor, atk):
            miss.append((eid, f'属性缺项 hp={hp} armor={armor} atk={atk}'))
            continue
        rows.append({'tier': tier, 'dao': dao, 'id': 'guzhenren:' + eid, 'name': cn,
                     'hp': hp, 'armor': armor, 'atk': atk,
                     'cls': os.path.basename(cls)[:-6]})

    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
    print('抽取成功', len(rows), '失败', len(miss))
    for eid, why in miss:
        print('  MISS', eid, '->', why)
    if rows:
        print('示例：', rows[0])
    return 0 if not miss else 1


if __name__ == '__main__':
    raise SystemExit(main())
