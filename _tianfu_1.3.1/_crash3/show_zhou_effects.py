import zipfile, json, re

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
d = json.loads(z.read('assets/guzhenren/lang/zh_cn.json').decode('utf-8')) if 'assets/guzhenren/lang/zh_cn.json' in z.namelist() else json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== effect.guzhenren.zhoudao* ===')
for k, v in d.items():
    if k.startswith('effect.guzhenren.zhoudao'):
        print(f'  {k} = {v}')

print()
print('=== 所有 effect 里含 宙/时间 ===')
for k, v in d.items():
    if k.startswith('effect.') and ('宙' in v or '时间' in v):
        print(f'  {k} = {v}')
