import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

# 找同时出现 ZHOUDAOBUFF 与 addEffect 的类（施加宙侵的地方）
target = b'ZHOUDAOBUFF'
add = b'addEffect'
hits = []
for n in z.namelist():
    if not n.endswith('.class'):
        continue
    d = z.read(n)
    if target in d and add in d:
        hits.append(n)

print('classes: ZHOUDAOBUFF + addEffect:', len(hits))
for h in hits[:30]:
    print(' ', h)
