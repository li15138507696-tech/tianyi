"""找谁给蛊虫设置 owner：搜全 jar 里调用 setOwner 的类。"""
import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    if b'setOwner' in z.read(n):
        hits.append(n)

print('classes calling/referencing setOwner:', len(hits))
for h in hits[:25]:
    print(' ', h)
