"""调试并正确提取本体实体注册名。

上一版对不上：9.8 报 700、10.4 报 657，但差集却是 0——说明提取走了不同分支。
MCreator 的写法是：
    ldc "name"
    ... (invokedynamic / 构造)
    invokevirtual DeferredRegister.register(String, Function)
所以注册名是 register 调用【之前】最近的 ldc String。
这里改成：逐条扫描 clinit，遇到 register 调用就回看 12 条指令找 ldc String。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
BASES = {
    '9.8': ROOT + r'\_crash3\basever\guzhenren-9.8.jar',
    '10.4': ROOT + r'\_crash3\basever\guzhenren-10.4.jar',
    '10.20': ROOT + r'\_crash3\basever\guzhenren-10.20.jar',
}


def entity_ids(jar, verbose=False):
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar,
                        'net.guzhenren.init.GuzhenrenModEntities'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        return None, 0
    lines = (r.stdout or '').splitlines()

    # 定位 clinit 区间
    start = None
    for i, ln in enumerate(lines):
        if 'static {};' in ln or 'static {}' in ln:
            start = i
            break
    body = lines[start:] if start is not None else lines

    ids = set()
    for i, ln in enumerate(body):
        if re.search(r'//\s*(?:Interface)?Method\s+.*DeferredRegister\.register:', ln):
            for j in range(i - 1, max(-1, i - 12), -1):
                m = re.search(r'//\s*String\s+(\S+)', body[j])
                if m:
                    ids.add(m.group(1))
                    break
    if verbose:
        print('   clinit 起始行:', start, ' 提取到 id:', len(ids))
    return ids, len(body)


per_ver = {}
for v, p in BASES.items():
    ids, blen = entity_ids(p, verbose=True)
    per_ver[v] = ids or set()
    print('%s: %d 个实体注册名（clinit %d 行）' % (v, len(per_ver[v]), blen))

print()
for v in per_ver:
    j = sorted(x for x in per_ver[v] if 'jiang' in x.lower())
    print('%s 含 jiang 的: %s' % (v, j))

print()
print('=== 9.8 有、10.4 没有 ===')
d1 = sorted(per_ver['9.8'] - per_ver['10.4'])
print('  %d 个: %s' % (len(d1), d1[:40]))
print('=== 10.4 有、9.8 没有 ===')
d2 = sorted(per_ver['10.4'] - per_ver['9.8'])
print('  %d 个: %s' % (len(d2), d2[:40]))
