"""核对恶意的实体标签在 9.8 / 10.4 / 10.20 里是否都指得到真实实体。

恶意靠 data/guzhenren/tags/entity_type/malice_*.json 识别本体妖兽。
标签里写了某实体 id、但那一版本本体没有这个实体时，MC 会静默跳过该条
（不报错、不崩），结果就是「那档难度对这只怪不生效」——很难发现。

做法：从每个版本的本体 GuzhenrenModEntities 常量池里抽出注册名，
再和恶意标签里的 id 做交集/差集。
"""
import zipfile
import subprocess
import re
import os
import json

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
BASES = {
    '9.8': ROOT + r'\_crash3\basever\guzhenren-9.8.jar',
    '10.4': ROOT + r'\_crash3\basever\guzhenren-10.4.jar',
    '10.20': ROOT + r'\_crash3\basever\guzhenren-10.20.jar',
}
TAGS = r'D:\daima\mcmod\蛊真人恶意\src\main\resources\data\guzhenren\tags\entity_type'
L2 = r'D:\daima\mcmod\蛊真人恶意\src\main\resources\data\l2hostility\tags\entity_type'

# ---- 收集恶意标签里的 id ----
def ids_of(path):
    txt = open(path, encoding='utf-8').read()
    return sorted({m.split(':')[-1] for m in re.findall(r'"(guzhenren:[^"]+)"', txt)})

files = {}
if os.path.isdir(TAGS):
    for f in sorted(os.listdir(TAGS)):
        if f.endswith('.json'):
            files[f] = ids_of(os.path.join(TAGS, f))
if os.path.isdir(L2):
    for f in sorted(os.listdir(L2)):
        if f.endswith('.json'):
            files['l2:' + f] = ids_of(os.path.join(L2, f))

all_ids = set()
for v in files.values():
    all_ids |= set(v)
print('恶意标签里用到的本体实体 id 共 %d 个' % len(all_ids))
for k, v in files.items():
    print('  %-28s %d' % (k, len(v)))
print()

# ---- 每个版本的实体注册名 ----
def entity_ids(jar):
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar,
                        'net.guzhenren.init.GuzhenrenModEntities'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    if r.returncode != 0:
        return None
    # 注册名都在 register("xxx", ...) 前一条 ldc
    strs = set()
    lines = txt.splitlines()
    for i, ln in enumerate(lines):
        if 'register' in ln and 'DeferredRegister' in ln:
            for j in range(max(0, i - 3), i):
                m = re.search(r'//\s*String\s+(\S+)', lines[j])
                if m:
                    strs.add(m.group(1))
    if not strs:
        # 退路：全部字符串常量
        strs = {m.group(1) for m in
                (re.search(r'//\s*String\s+(\S+)', ln) for ln in lines) if m}
    return strs


print('=' * 74)
print('标签 id 在各版本的覆盖情况')
print('=' * 74)
missing_by_ver = {}
for ver, jar in BASES.items():
    reg = entity_ids(jar)
    if reg is None:
        print('%-6s !! GuzhenrenModEntities 不存在' % ver)
        continue
    miss = sorted(all_ids - reg)
    missing_by_ver[ver] = miss
    print('%-6s 实体注册名 %d 个 | 标签 id 缺失 %d 个' % (ver, len(reg), len(miss)))
    if miss:
        for m in miss[:20]:
            print('        缺:', m)
        if len(miss) > 20:
            print('        ... 还有 %d 个' % (len(miss) - 20))
    print()

# ---- 按标签文件细看（以最差版本为准）----
if missing_by_ver:
    worst = max(missing_by_ver, key=lambda v: len(missing_by_ver[v]))
    print('缺失最多的版本:', worst, '(%d 个)' % len(missing_by_ver[worst]))
    print()
    print('按文件统计该版本的缺失:')
    for name, ids in files.items():
        miss = sorted(set(ids) - (all_ids - set(missing_by_ver[worst])))
        if miss:
            print('  %-28s 缺 %d / %d' % (name, len(miss), len(ids)))
