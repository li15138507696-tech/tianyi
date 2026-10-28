import zipfile, os, struct, subprocess, re

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, 'guzhenren_兼修附属0.7.jar')
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

out = subprocess.run([javap, '-c', '-p', '-classpath', p,
                      'cn.blockforge.generated.minecraftjsontoml16x.IceContent'],
                     capture_output=True, text=True, encoding='utf-8', errors='replace')
lines = out.stdout.splitlines()

# map: line index of 'ice_path_boost' -> the invokedynamic #N that follows
idx_lines = [i for i, l in enumerate(lines) if 'ice_path_boost' in l]
print('lines mentioning ice_path_boost:', idx_lines)
for li in idx_lines:
    for j in range(li, min(li + 8, len(lines))):
        if 'invokedynamic' in lines[j]:
            print('   ', lines[li].strip())
            print('   ', lines[j].strip())
            break

# list every lambda that uses BENEFICIAL together with its method name
print()
print('--- lambdas with BENEFICIAL ---')
cur = None
for i, l in enumerate(lines):
    s = l.strip()
    if s.startswith('private static') and 'lambda$' in s:
        cur = s
    if 'MobEffectCategory.BENEFICIAL' in s and cur:
        print(f'   {cur}')
        cur = None
