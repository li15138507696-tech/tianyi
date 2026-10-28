import zipfile, os, struct, subprocess

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '20261020-2036_六合一大合集_v1.0.0.jar')
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

for cls in ['net.guzhenren.ui.yundao.YundaoRegistries',
            'net.guzhenren.ui.yundao.YundaoEvents',
            'net.guzhenren.ui.mixin.KaiShiLianZhiMixin']:
    print('=' * 66)
    print(cls)
    r = subprocess.run([javap, '-c', '-p', '-classpath', p, cls],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = r.stdout.splitlines()
    for i, ln in enumerate(lines):
        s = ln.strip()
        if 'PlayerVariables.tizhi' in s or 'PlayerVariables.kongqiao' in s \
                or 'PlayerVariables.LianGuJinDu' in s or 'PlayerVariables.lg_ke' in s \
                or 'PlayerVariables.GuFang' in s or 'PlayerVariables.qiyun' in s:
            print(f'  ...{s}')
    print()
