import zipfile, os, struct, subprocess, tempfile, shutil

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, '魂道附属10.19.jar')
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

out = subprocess.run([javap, '-c', '-p', '-classpath', p,
                      'net.hundao.addon.recipe.GuFangNativeCraft'],
                     capture_output=True, text=True, encoding='utf-8', errors='replace')
lines = out.stdout.splitlines()

# find every putfield of GuFang and print 6 lines before it
for i, ln in enumerate(lines):
    if 'putfield' in ln and 'PlayerVariables.GuFang' in ln:
        print('>>>', ln.strip())
        for j in range(max(0, i-6), i):
            print('     ', lines[j].strip())
        print()
