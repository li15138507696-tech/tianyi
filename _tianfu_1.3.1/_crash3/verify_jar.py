"""Full verification of the particle pipeline in the built jar."""
import os, re, zipfile

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'


def read_version():
    with open(os.path.join(ROOT, 'version.properties'), encoding='utf-8') as f:
        for line in f:
            m = re.match(r'\s*version\s*=\s*(\S+)', line)
            if m:
                return m.group(1)
    raise SystemExit('version.properties 里没有 version=…')


JAR = os.path.join(ROOT, 'build', 'libs', f'tianfu-{read_version()}.jar')
z = zipfile.ZipFile(JAR)
names = set(z.namelist())

PARTICLES = ['tianfu_zhenyuan_qi', 'tianfu_niantou_guang', 'tianfu_sharen_yan',
             'tianfu_hunhuo', 'tianfu_xueqi', 'tianfu_jianqi']

print('=== 三件套完整性 ===')
ok = True
for p in PARTICLES:
    trio = [
        f'net/guzhenren/tianfu/init/TianfuParticleTypes.class',
        f'assets/tianfu/particles/{p}.json',
        f'assets/tianfu/textures/particle/{p}.png',
        f'assets/tianfu/textures/particle/{p}.png.mcmeta',
    ]
    # 每个粒子只需要后三样（注册表类只有一个）
    need = trio[1:]
    for n in need:
        mark = 'OK  ' if n in names else 'MISS'
        if n not in names:
            ok = False
        print(f'  {mark} {n}')

print()
print('=== 客户端粒子类 ===')
for c in ['ZhenyuanQiParticle', 'NiantouGuangParticle', 'SharenYanParticle',
          'HunHuoParticle', 'XueQiParticle', 'JianQiParticle']:
    n = f'net/guzhenren/tianfu/client/particle/{c}.class'
    print(('OK  ' if n in names else 'MISS'), n)
    if n not in names:
        ok = False

print()
print('=== provider 注册类 ===')
n = 'net/guzhenren/tianfu/client/TianfuParticles.class'
print(('OK  ' if n in names else 'MISS'), n)
ok = ok and n in names

print()
print('=== particles/*.json 内容抽查 ===')
for p in PARTICLES[:3]:
    n = f'assets/tianfu/particles/{p}.json'
    if n in names:
        print(' ', n, '->', z.read(n).decode('utf-8').strip())

print()
print('=== mcmeta 内容抽查 ===')
n = 'assets/tianfu/textures/particle/tianfu_zhenyuan_qi.png.mcmeta'
if n in names:
    print(z.read(n).decode('utf-8'))

print()
print('=== 总条目 ===', len(names), 'jar bytes', os.path.getsize(JAR))
print('ALL OK' if ok else 'SOMETHING MISSING')
