"""语义核对：UikongqiaoquedingProcedure.execute 在 9.8 / 10.4 / 10.20 里到底做什么。

tianfu 的设计假设（见 TianfuOpening 注释）：
  - 9.8：收尾结算【就在】queding 里（9.8 没有 guanbi 这个类）
  - 10.x：queding 是薄壳，先转调 guanbi、再 closeContainer
两份都靠 mixin 注入 queding 的 TAIL 拿到「收尾完成」这一刻。

本脚本验证：
  1. queding 是否转调 guanbi（决定 9.8 与 10.x 是否走同一入口）
  2. queding 是否 closeContainer
  3. 是否有提前 return（会让 TAIL 注入落不到正常路径上）
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
    txt = r.stdout or ''
    print('=' * 66)
    print(ver)
    print('=' * 66)
    if r.returncode != 0:
        print('  !! 类不存在')
        continue

    body = txt
    calls_guanbi = 'UikongqiaoguanbiProcedure.execute' in body
    calls_close = 'closeContainer' in body
    # 数 return 指令（areturn/return）
    returns = len(re.findall(r'^\s+\d+:\s+(?:return|areturn)\s*$', body, re.M))
    # 找 ifnonnull / ifeq 这类提前返回守卫
    guards = re.findall(r'^\s+\d+:\s+(?:ifnonnull|ifeq|ifne|ifnull)\s+\d+\s*$', body, re.M)

    print('  转调 guanbi      :', '是' if calls_guanbi else '否（收尾在本方法内）')
    print('  调用 closeContainer:', '是' if calls_close else '否')
    print('  return 指令数     :', returns, '（1 = 只有末尾返回，TAIL 稳定命中）')
    print('  条件跳转数        :', len(guards))
    print()

    # 打印 execute 前 40 行，人工看一眼结构
    lines = txt.splitlines()
    start = next((i for i, ln in enumerate(lines) if ' execute(' in ln), None)
    if start is not None:
        print('  --- execute 前 34 行 ---')
        for ln in lines[start:start + 34]:
            print('   ', ln.rstrip())
    print()
