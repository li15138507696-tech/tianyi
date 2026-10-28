import zipfile, json

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
d = json.loads(z.read('assets/guzhenren/lang/zh_cn.json').decode('utf-8')) if 'assets/guzhenren/lang/zh_cn.json' in z.namelist() else json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

print('=== 瞬现烛蛊 全部词条 ===')
for k, v in d.items():
    if 'shunxianzhu' in k.lower() or '瞬现烛' in v:
        print(f'  {k} = {v}')

print()
print('=== 火光烛天蛊 全部词条 ===')
for k, v in d.items():
    if 'zhu_tian' in k.lower() or '烛天' in v:
        print(f'  {k} = {v}')
