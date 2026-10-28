"""判断 10.x 的 gu_xiu 与 9.8 的僵尸蛊修分别是什么类型，决定该进哪一档标签。

l2hostility 的挂载条件：
    type.is(l2hostility:whitelist)
    || (e instanceof Enemy && !type.is(l2hostility:blacklist))
所以：
  - 若实体继承 Monster/Enemy，本体不写白名单也会自动挂上 → 不需要我们标
  - 若是 TamableAnimal / PathfinderMob（本体妖兽的常态），必须进白名单
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

TARGETS = ['GuXiuEntity', 'JiangShiGuXiuEntity', 'JiangShiGuXiuSiZhuanEntity',
           'NanGuXiuEntity', 'ZhongZhouNanGuXiuEntity']


def super_of(jar, simple):
    # 先在 entity 包里按简单名找类
    import zipfile
    z = zipfile.ZipFile(jar)
    cands = [n for n in z.namelist()
             if n.startswith('net/guzhenren/entity/') and n.endswith('/' + simple + '.class')]
    if not cands:
        return None, None
    fq = cands[0][:-6].replace('/', '.')
    r = subprocess.run([JAVAP, '-p', '-classpath', jar, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    m = re.search(r'public class \S+ extends ([\w.$]+)', r.stdout or '')
    return fq, (m.group(1) if m else None)


for ver, jar in BASES.items():
    print('=== %s ===' % ver)
    for simple in TARGETS:
        fq, sup = super_of(jar, simple)
        if fq is None:
            print('  %-30s （不存在）' % simple)
        else:
            flag = ''
            if sup:
                if 'Monster' in sup or 'Enemy' in sup:
                    flag = '  <- Enemy，l2hostility 会自动挂'
                elif 'TamableAnimal' in sup or 'PathfinderMob' in sup or 'Animal' in sup:
                    flag = '  <- 非 Enemy，必须进白名单'
            print('  %-30s extends %s%s' % (simple, sup, flag))
    print()
