import zipfile

j = r'D:\game\mcmod\1\.minecraft\libraries\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar'
z = zipfile.ZipFile(j)
names = [n for n in z.namelist() if 'particle' in n and ('Sprite' in n or 'sprite' in n)]
for n in names:
    print(n)
