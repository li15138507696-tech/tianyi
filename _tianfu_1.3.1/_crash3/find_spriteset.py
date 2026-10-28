import zipfile, os, sys

roots = [r'D:\game\mcmod\1\.minecraft\libraries',
         r'D:\game\mcmod\1\.minecraft\versions']
target = 'client/particle/SpriteSet'
hits = []
for root in roots:
    for dirpath, _, files in os.walk(root):
        for f in files:
            if not f.endswith('.jar'):
                continue
            p = os.path.join(dirpath, f)
            try:
                z = zipfile.ZipFile(p)
            except Exception:
                continue
            names = z.namelist()
            if any(target in n for n in names):
                hits.append(p)
            z.close()
print('hits:', len(hits))
for h in hits:
    print(' ', h)
