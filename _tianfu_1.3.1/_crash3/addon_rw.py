import zipfile, os, struct, subprocess, collections

dst = r'D:\game\mcmod\1\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
javap = r'C:\Program Files\Java\jdk-17\bin\javap.exe'

FIELDS = ['qiyun', 'qiyun_shangxian', 'tizhi', 'zhenyuan', 'zuida_zhenyuan',
          'niantou', 'niantou_rongliang', 'shouyuan', 'kongqiao',
          'zuida_hunpo', 'hunpochengzaijixian', 'zuida_jingli', 'niantou_zhida']

addons = {
    '兼修': 'guzhenren_兼修附属0.7.jar',
    '魂道': '魂道附属10.19.jar',
    '蛊虫使用记录': '蛊虫使用记录-1.0.1.jar',
    '六合一大合集': '20261020-2036_六合一大合集_v1.0.0.jar',
}

for label, fn in addons.items():
    p = os.path.join(dst, fn)
    z = zipfile.ZipFile(p)
    classes = [n for n in z.namelist() if n.endswith('.class')]
    z.close()

    writes = collections.defaultdict(list)
    reads = collections.defaultdict(list)

    for c in classes:
        fqcn = c[:-6].replace('/', '.')
        try:
            out = subprocess.run([javap, '-c', '-p', '-classpath', p, fqcn],
                                 capture_output=True, text=True, encoding='utf-8',
                                 errors='replace', timeout=60)
        except Exception:
            continue
        for ln in out.stdout.splitlines():
            s = ln.strip()
            if 'PlayerVariables.' not in s:
                continue
            for f in FIELDS:
                if f'PlayerVariables.{f}:' not in s:
                    continue
                if s.startswith('putfield'):
                    writes[f].append(fqcn)
                elif s.startswith('getfield'):
                    reads[f].append(fqcn)
    print(f'===== {label}')
    for f in FIELDS:
        w, r = writes.get(f, []), reads.get(f, [])
        if w or r:
            print(f'  {f:22s} W={len(w):3d}  R={len(r):3d}')
            for c in sorted(set(w))[:4]:
                print(f'      W {c}')
    print()
