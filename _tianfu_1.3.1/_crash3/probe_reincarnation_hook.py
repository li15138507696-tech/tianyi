"""精确核对转世钩子：JingJieBaoCunProcedure 在各版本里有哪些 execute 重载。

tianfu 的 mixin 注入描述符是：
  execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;
          Lnet/minecraft/world/entity/Entity;)V
如果某个版本没有这个三参重载（比如改成两参或改了类型），
require=0 会让 mixin 静默跳过 → 转世不再重置天赋（点数/已购/难度跨世残留）。
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
OWNER = 'net.guzhenren.procedures.JingJieBaoCunProcedure'
WANT = ('net.neoforged.bus.api.Event',
        'net.minecraft.world.level.LevelAccessor',
        'net.minecraft.world.entity.Entity')

for ver, jar in BASES.items():
    r = subprocess.run([JAVAP, '-p', '-classpath', jar, OWNER],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    print('=== %s ===' % ver)
    if r.returncode != 0:
        print('  !! 类不存在')
        continue
    found = False
    for ln in txt.splitlines():
        s = ln.strip()
        if 'execute(' in s:
            print('   ', s)
            if all(t in s for t in WANT):
                found = True
    print('    三参 execute(Event, LevelAccessor, Entity):',
          'OK' if found else '!! 缺失 -> mixin 会静默跳过')
    print()
