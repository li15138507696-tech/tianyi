import zipfile, json, io

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
d = json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('total keys:', len(d))
for k, v in d.items():
    if '宙蚀' in v or '宙蚀' in k:
        print(f'  {k} = {v}')

print()
print('--- 含 宙 的所有条目 ---')
for k, v in d.items():
    if '宙' in v or 'zhou' in k.lower():
        print(f'  {k} = {v}')
