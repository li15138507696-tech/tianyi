"""查清 @EventBusSubscriber 的 bus 到底怎么决定——是否按事件类型自动推断。

背景：daotu 的 DaotuKeyMappings 用 @EventBusSubscriber(modid=..., value=Dist.CLIENT)
（不写 bus），源码注释声称「RegisterKeyMappingsEvent 属于 mod bus，自动推断即可」。
而注解 bus() 默认值是 Bus.GAME（已从注解定义确认）。
若加载器严格按声明挂 GAME 总线，那么 MOD 总线的 RegisterKeyMappingsEvent
永远送不到它 → M 键根本注册不上 → 道途界面无法用键打开。
"""
import zipfile
import subprocess
import re
import glob

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
LOADER = r'D:\game\mcmod\1\.minecraft\libraries\net\neoforged\fancymodloader\loader\4.0.45\loader-4.0.45.jar'

z = zipfile.ZipFile(LOADER)
names = z.namelist()

print('=== 与 EventBusSubscriber 处理相关的类 ===')
for n in names:
    if n.endswith('.class') and ('Subscriber' in n or 'EventBusSubscriber' in n):
        print('  ', n)

print()
print('=== 在 loader 里搜「按事件类型推断 bus」的痕迹 ===')
# 找 AutomaticEventSubscriber 的字节码，看它怎么选 bus
targets = [n for n in names if n.endswith('AutomaticEventSubscriber.class')]
if not targets:
    # 退一步：任何提到 EventBusSubscriber 的类
    targets = [n for n in names if n.endswith('.class')
               and b'EventBusSubscriber' in z.read(n)][:6]
    print('  （没有 AutomaticEventSubscriber，改用引用它的类）')

for t in targets:
    fq = t[:-6].replace('/', '.')
    print()
    print('### ', fq)
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', LOADER, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    # 只打印与 bus / IModBusEvent / getAnnotation 相关的片段
    lines = txt.splitlines()
    for i, ln in enumerate(lines):
        if re.search(r'bus|Bus|IModBusEvent|EventBusSubscriber', ln):
            print('   ', ln.rstrip())
