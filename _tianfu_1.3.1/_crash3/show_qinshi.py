import zipfile, json, io, re

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
d = json.loads(z.read('assets/guzhenren/lang/zh_cn.json').decode('utf-8')) if 'assets/guzhenren/lang/zh_cn.json' in z.namelist() else json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== 含「侵蚀」的条目 ===')
n = 0
for k, v in d.items():
    if '侵蚀' in v:
        n += 1
        if n <= 40:
            print(f'  {k}\n     = {v}')
print('... total:', n)

print()
print('=== 含「烛」的条目 ===')
for k, v in d.items():
    if '烛' in v:
        print(f'  {k} = {v}')
