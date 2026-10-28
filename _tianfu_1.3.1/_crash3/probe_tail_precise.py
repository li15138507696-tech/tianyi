"""精确统计 execute 方法体内的 return 数量（只切这一个方法）。

上一版把 execute 后面的辅助方法（getEntityScore / lambda）的 return 也算进去了，
导致 9.8 误报 4 个。这里按「方法声明行 → 下一个方法声明行」精确切片。

TAIL 注入只落在方法内最后一个 return 上，所以：
  - 若只有一个 return（末尾），TAIL 稳定命中
  - 若有多个且正常路径走靠前的那个，TAIL 不执行
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
FQCN = 'net.guzhenren.procedures.UikongqiaoquedingProcedure'

METHOD_DECL = re.compile(r'^\s{2}(?:public|protected|private|static|final|abstract).*\(.*\).*;\s*$')

for ver, jar in BASES.items():
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar, FQCN],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()

    # 找 execute 声明
    idx = None
    for i, ln in enumerate(lines):
        if re.match(r'^\s{2}.*\bexecute\(', ln):
            idx = i
            break
    if idx is None:
        print('%-6s !! 找不到 execute' % ver)
        continue

    # 切到下一个方法声明（或类结束）
    end = len(lines)
    for j in range(idx + 1, len(lines)):
        if METHOD_DECL.match(lines[j]) or lines[j].strip() == '}':
            end = j
            break

    body = lines[idx:end]
    rets = []
    for ln in body:
        m = re.match(r'\s+(\d+):\s+(?:return|areturn)\s*$', ln)
        if m:
            rets.append(int(m.group(1)))

    print('=' * 60)
    print('%s  execute 方法体 %d 行' % (ver, len(body)))
    print('  return 偏移:', rets)
    if len(rets) == 1:
        print('  结论: 单 return，TAIL 稳定命中 OK')
    else:
        print('  结论: %d 个 return，TAIL 只命中最后一个 (%d)' % (len(rets), max(rets)))
        # 打印每个 return 前 6 行，判断是否正常路径
        for roff in rets:
            k = next((i for i, ln in enumerate(body)
                      if re.match(r'\s+%d:\s+(?:return|areturn)' % roff, ln)), None)
            if k is None:
                continue
            print('    --- return %d 前 6 行 ---' % roff)
            for ln in body[max(0, k - 6):k + 1]:
                print('      ', ln.rstrip())
    print()
