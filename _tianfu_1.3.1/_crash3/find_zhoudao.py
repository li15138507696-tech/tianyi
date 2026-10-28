import zipfile, re, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
target = 'GuzhenrenModVariables'
names = [n for n in z.namelist() if target in n and n.endswith('.class')]
print('classes:', names)

# search constant pool strings + look for putfield of zhoudao_cd_baifenbi preceded by a dconst
for n in names:
    data = z.read(n)
    if b'zhoudao_cd_baifenbi' in data:
        print('  references zhoudao_cd_baifenbi:', n)
z.close()
