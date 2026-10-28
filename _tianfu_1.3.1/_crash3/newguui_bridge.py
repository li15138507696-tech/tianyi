import zipfile, os, struct, subprocess

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '20261020-2036_六合一大合集_v1.0.0.jar')
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

targets = [
    'net.guzhenren.ui.bridge.TianfuBridge',
    'net.guzhenren.ui.bridge.TianfuBridge$Talent',
    'net.guzhenren.ui.network.TianfuSyncPayload',
]

for t in targets:
    print('=' * 70)
    print(t)
    print('=' * 70)
    r = subprocess.run([javap, '-p', '-classpath', p, t],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    print(r.stdout[:3000])
    print()
