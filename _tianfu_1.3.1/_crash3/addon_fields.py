import zipfile, os, re, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'

addons = {
    '兼修': 'guzhenren_兼修附属0.7.jar',
    '魂道': '魂道附属10.19.jar',
    '蛊虫使用记录': '蛊虫使用记录-1.0.1.jar',
    '流派熟练度查看': '蛊真人流派熟练度查看.jar',
    '六合一大合集': '20261020-2036_六合一大合集_v1.0.0.jar',
}

# fields my mod reads/writes on PlayerVariables
FIELDS = [
    'zhenyuan','zuida_zhenyuan','niantou','niantou_rongliang','tizhi','kongqiao',
    'qiyun','qiyun_shangxian','shouyuan','zuida_hunpo','hunpochengzaijixian',
    'zuida_jingli','niantou_zhida','LianGu','LianGuJinDu','LianGuChengGongLv',
    'LianGuBenMingGu','lg_ke','kslg_ke','GuFang','zhuanshu',
    'zhoudao_shijian','zhoudao_shijian_baifenbi','zhoudao_cd','zhoudao_cd_baifenbi',
    'dahen_zhoudao','mingge',
]

print(f'{"addon":14s} {"entries":>7s}  shared-field hits (count)')
print('-' * 78)
for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    hits = collections.Counter()
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        d = z.read(n)
        for f in FIELDS:
            if f.encode() in d:
                hits[f] += 1
    z.close()
    top = ', '.join(f'{f}({c})' for f, c in hits.most_common(8))
    print(f'{label:14s} {len(z.namelist()):7d}  {top}')
