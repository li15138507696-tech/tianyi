import zipfile, os

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
z = zipfile.ZipFile(os.path.join(dst, 'guzhenren_兼修附属0.7.jar'))
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    d = z.read(n)
    if b'MobEffectCategory' not in d:
        continue
    cats = [c.decode() for c in (b'BENEFICIAL', b'HARMFUL', b'NEUTRAL') if c in d]
    print(n, cats)
z.close()
