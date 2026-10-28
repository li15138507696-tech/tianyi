"""Confirm the packaged jar's manifest version matches version.properties."""
import os
import re
import zipfile

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'

with open(os.path.join(ROOT, 'version.properties'), encoding='utf-8') as f:
    ver = re.search(r'^\s*version\s*=\s*(\S+)', f.read(), re.M).group(1)

jar = os.path.join(ROOT, 'build', 'libs', f'tianfu-{ver}.jar')
print('version.properties :', ver)
print('jar file           :', os.path.basename(jar), '存在' if os.path.isfile(jar) else '缺失')

with zipfile.ZipFile(jar) as z:
    toml = z.read('META-INF/neoforge.mods.toml').decode('utf-8')
    m = re.search(r'^\s*version\s*=\s*"([^"]+)"', toml, re.M)
    print('jar 内 mods.toml   :', m.group(1), 'OK' if m.group(1) == ver else '<< 不一致')
    print('jar 内 modId       :', re.search(r'modId\s*=\s*"([^"]+)"', toml).group(1))
    # 新类是否都在
    for c in ('TianfuViewScreen', 'TianfuEffects', 'TianfuTalents', 'TianfuNative'):
        p = f'net/guzhenren/tianfu/{c}.class' if c != 'TianfuViewScreen' else f'net/guzhenren/tianfu/client/{c}.class'
        print(f'  {c:18}', 'OK' if p in z.namelist() else 'MISS')

# build.gradle 是否已改为读 version.properties
with open(os.path.join(ROOT, 'build.gradle'), encoding='utf-8') as f:
    g = f.read()
print()
print("build.gradle 读 version.properties :", 'OK' if 'version.properties' in g else 'MISSING')
print("build.gradle 残留硬编码 1.2.x     :", 'YES-BAD' if re.search(r"version\s*=\s*'1\.2\.", g) else '无')
