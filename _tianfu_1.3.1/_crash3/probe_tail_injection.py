"""核对 TAIL 注入是否落在「正常路径」上。

Mixin 的 @At("TAIL") 只注入到方法里【最后一个】 RETURN 指令之前。
如果方法有多个 return，且正常路径走的是靠前的那个，TAIL 钩子就永远不会执行
——表现为「开窍了但没弹天赋界面、也没 markApplied」，而完全不报错。

做法：列出每个版本 execute 里所有 return 的字节码偏移，
并打印方法末尾，确认最后一个 return 就是正常出口。
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

for ver, jar in BASES.items():
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', jar, FQCN],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()
    start = next((i for i, ln in enumerate(lines) if ' execute(' in ln), None)
    if start is None:
        print('%-6s !! 找不到 execute' % ver)
        continue
    body = lines[start:]
    # 收集 return 偏移
    rets = []
    for ln in body:
        m = re.match(r'\s+(\d+):\s+(?:return|areturn)\s*$', ln)
        if m:
            rets.append(int(m.group(1)))
    print('=' * 62)
    print('%s  execute 里的 return 偏移: %s' % (ver, rets))
    print('  最后一个 return =', max(rets) if rets else None,
          '（TAIL 注入点就在这里）')
    # 打印末尾 14 行
    print('  --- 方法末尾 14 行 ---')
    tail = [ln for ln in body if ln.strip()]
    for ln in tail[-14:]:
        print('   ', ln.rstrip())
    print()
