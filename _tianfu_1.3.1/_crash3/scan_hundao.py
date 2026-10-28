import zipfile, os, re

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'

def scan(fn, needles, label):
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    print(f'===== {label} ({fn})')
    for n in sorted(z.namelist()):
        if not n.endswith('.class'):
            continue
        d = z.read(n)
        hit = [x for x in needles if x.encode() in d]
        if hit:
            print(f'  {n}')
            print(f'      {hit}')
    z.close()
    print()

scan('魂道附属10.19.jar', ['LianGuJinDu', 'LianGu', 'GuFang', 'lg_ke'], '魂道')
