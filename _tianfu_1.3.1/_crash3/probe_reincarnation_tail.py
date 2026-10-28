"""核对转世钩子的 TAIL 可达性：JingJieBaoCunProcedure.execute(Event, LevelAccessor, Entity)。

tianfu 与 daotu 的 JingJieBaoCunProcedureMixin 都注入这个三参重载的 TAIL。
Mixin 的 @At("TAIL") 只注入到方法内【最后一个】return 之前：
若正常路径走靠前的 return，钩子永不执行 → 「寿元耗尽转世后天赋/道途不重置」，
而且完全不报错。所以必须逐版本数 return 并确认末尾那个可达。
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
FQCN = 'net.guzhenren.procedures.JingJieBaoCunProcedure'
METHOD_DECL = re.compile(
    r'^\s{2}(?:public|protected|private|static|final|abstract).*\(.*\).*;\s*$')

for ver, jar in BASES.items():
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar, FQCN],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()
    if r.returncode != 0:
        print('%-6s !! 类不存在' % ver)
        continue

    # 找到三参 execute 的声明行（含 Event / LevelAccessor / Entity）
    idx = None
    for i, ln in enumerate(lines):
        if re.match(r'^\s{2}.*\bexecute\(', ln) and 'Event' in ln and 'LevelAccessor' in ln:
            idx = i
            break
    print('=' * 70)
    if idx is None:
        print('%-6s !! 找不到三参 execute' % ver)
        continue

    end = len(lines)
    for j in range(idx + 1, len(lines)):
        if METHOD_DECL.match(lines[j]) or lines[j].strip() == '}':
            end = j
            break
    body = lines[idx:end]

    rets = [int(m.group(1)) for m in
            (re.match(r'\s+(\d+):\s+(?:return|areturn)\s*$', ln) for ln in body) if m]
    print('%s  方法体 %d 行   return 偏移: %s' % (ver, len(body), rets))
    print('  声明:', lines[idx].strip())
    if len(rets) == 1:
        print('  结论: 单 return，TAIL 稳定命中 OK')
    else:
        last = max(rets)
        print('  结论: %d 个 return，TAIL 只命中最后一个 (%d)' % (len(rets), last))
        # 打印每个 return 前 5 行，便于判断哪条是正常出口
        for roff in rets:
            k = next((i for i, ln in enumerate(body)
                      if re.match(r'\s+%d:\s+(?:return|areturn)' % roff, ln)), None)
            if k is None:
                continue
            print('    --- return %d 前 5 行 ---' % roff)
            for ln in body[max(0, k - 5):k + 1]:
                print('      ', ln.rstrip())
    print()
