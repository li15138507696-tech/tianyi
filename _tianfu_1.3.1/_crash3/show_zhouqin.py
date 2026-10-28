import zipfile, json

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
d = json.loads(z.read('assets/guzhenren/lang/zh_cn.json').decode('utf-8')) if 'assets/guzhenren/lang/zh_cn.json' in z.namelist() else json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== 含「宙侵」的条目 ===')
for k, v in d.items():
    if '宙侵' in v:
        print(f'  {k}\n     = {v}')

print()
print('=== 含「瞬现」的条目 ===')
for k, v in d.items():
    if '瞬现' in v:
        print(f'  {k}\n     = {v}')
