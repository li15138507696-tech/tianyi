import zipfile, os, json, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
addons = {
    '兼修': 'guzhenren_兼修附属0.7.jar',
    '魂道': '魂道附属10.19.jar',
    '蛊虫使用记录': '蛊虫使用记录-1.0.1.jar',
    '流派熟练度查看': '蛊真人流派熟练度查看.jar',
    '六合一大合集': '20261020-2036_六合一大合集_v1.0.0.jar',
}
mine = {'JingJieBaoCunProcedure', 'UikongqiaoguanbiProcedure',
        'UishuxingshijuetiProcedure', 'YizhuanrendaoxiwangguItem'}

for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    print(f'===== {label}')
    found = False
    for n in z.namelist():
        if n.endswith('.mixins.json') or n.endswith('.mixin.json'):
            try:
                cfg = json.loads(z.read(n).decode('utf-8', 'replace'))
            except Exception as e:
                print('  parse fail', n, e); continue
            mixins = cfg.get('mixins', []) + cfg.get('client', []) + cfg.get('server', [])
            print(f'  {n}  ({len(mixins)} mixins)')
            for m in mixins:
                mark = ''
                for t in mine:
                    if t.lower() in m.lower():
                        mark = '   <<< OVERLAP WITH TIANFU'
                print(f'     {m}{mark}')
                found = True
    if not found:
        print('  (no mixin config)')
    z.close()
    print()
