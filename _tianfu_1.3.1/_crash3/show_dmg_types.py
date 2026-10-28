import zipfile, json

base = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(base)
d = json.loads(z.read('assets/guzhenren/lang/zh_cn.json').decode('utf-8')) if 'assets/guzhenren/lang/zh_cn.json' in z.namelist() else json.loads(z.read('assets/guzhenren/lang/en_us.json').decode('utf-8'))

# 找伤害类型
print('=== 伤害类型 (damage_type) ===')
for k, v in d.items():
    if k.startswith('death.attack') or 'damage_type' in k:
        print(f'  {k} = {v}')
