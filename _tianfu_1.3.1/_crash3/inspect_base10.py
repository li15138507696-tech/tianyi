import zipfile, os, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
p = os.path.join(dst, 'guzhenrn蛊真人模组本体10.4.jar')

z = zipfile.ZipFile(p)
names = z.namelist()
pkgs = collections.Counter()
for n in names:
    if n.endswith('.class'):
        pkgs['/'.join(n.split('/')[:3])] += 1
print('top class packages:')
for k, v in pkgs.most_common(15):
    print(f'  {v:5d}  {k}')

print()
print('non-class top dirs:')
tops = collections.Counter(n.split('/')[0] for n in names)
for k, v in tops.most_common(10):
    print(f'  {v:5d}  {k}')

print()
print('any jar-in-jar?')
for n in names:
    if n.endswith('.jar'):
        print('   ', n, z.getinfo(n).file_size)
z.close()
