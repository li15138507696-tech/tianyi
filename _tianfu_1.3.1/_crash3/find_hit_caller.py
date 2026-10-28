"""蛊虫命中 procedure 的 attacker 实参从哪来？看调用点。"""
import zipfile, re

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

# 找调用 Shizhuo...Procedure.execute 的地方
target = b'ShizhuotanshewuDanSheWuJiZhongShiTiShiProcedure'
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    if target in z.read(n):
        hits.append(n)

print('callers of Shizhuo hit procedure:', len(hits))
for h in hits[:10]:
    print(' ', h)
