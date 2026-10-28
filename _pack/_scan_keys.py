"""扫描 mods 目录：哪些 jar 里出现 daotu.view / tianfu / shenshang 的按键 key（只读）。"""
import glob
import os
import zipfile

MODS = r'D:\game\mcmod\蛊2\.minecraft\versions\转生到蛊界就要拿出真本事\mods'
NEEDLES = [b'key.daotu.view', b'key.shenshang.open', b'key.tianfu.view', b'key.tianyi.panel']

for p in sorted(glob.glob(os.path.join(MODS, '*.jar'))):
    try:
        z = zipfile.ZipFile(p)
    except Exception:
        continue
    hits = {}
    for n in z.namelist():
        try:
            data = z.read(n)
        except Exception:
            continue
        for needle in NEEDLES:
            if needle in data:
                hits.setdefault(needle.decode(), []).append(n)
        # 嵌套 jar
        if n.startswith('META-INF/jarjar/') and n.endswith('.jar'):
            try:
                import io
                z2 = zipfile.ZipFile(io.BytesIO(data))
                for n2 in z2.namelist():
                    try:
                        d2 = z2.read(n2)
                    except Exception:
                        continue
                    for needle in NEEDLES:
                        if needle in d2:
                            hits.setdefault(needle.decode(), []).append(n + '!' + n2)
            except Exception:
                pass
    if hits:
        print('==', os.path.basename(p))
        for k, v in hits.items():
            print('   ', k, '->', len(v), 'entry(s)', v[:3])
