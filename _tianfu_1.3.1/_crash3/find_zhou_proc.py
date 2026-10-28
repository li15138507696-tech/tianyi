import zipfile

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)

# 找专门做「宙侵伤害结算」的 procedure：名字含 Zhou 且含 ShangHai / Damage
names = [n for n in z.namelist() if n.endswith('.class')]
cands = [n for n in names if 'Zhou' in n and ('ShangHai' in n or 'Shanghai' in n or 'Shang' in n)]
print('=== Zhou + Shang ===')
for c in cands:
    print(' ', c)

cands2 = [n for n in names if 'ZhouDao' in n or 'Zhoudao' in n]
print()
print('=== Zhoudao procedures ===')
for c in cands2:
    print(' ', c)
