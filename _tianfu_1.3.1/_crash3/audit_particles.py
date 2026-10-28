import json, os, struct, zlib

base = 'src/main/resources/assets/tianfu'
pdir = os.path.join(base, 'particles')
tdir = os.path.join(base, 'textures', 'particle')

# registered ids
import re
src = open('src/main/java/net/guzhenren/tianfu/init/TianfuParticleTypes.java', encoding='utf-8').read()
ids = re.findall(r'register\(\s*"(\w+)"', src)
print('registered ids:', ids)

def png_size(path):
    with open(path, 'rb') as f:
        head = f.read(33)
    if head[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    w, h = struct.unpack('>II', head[16:24])
    return w, h

for pid in ids:
    pj = os.path.join(pdir, pid + '.json')
    png = os.path.join(tdir, pid + '.png')
    mc = png + '.mcmeta'
    problems = []
    if not os.path.exists(pj):
        problems.append('MISSING particles json')
    else:
        try:
            j = json.load(open(pj, encoding='utf-8'))
            tex = j.get('textures', [])
            if not tex:
                problems.append('json has empty textures')
            else:
                want = f'tianfu:{pid}'
                if tex[0] != want:
                    problems.append(f'texture[0]={tex[0]} expected {want}')
        except Exception as e:
            problems.append(f'json parse error: {e}')
    if not os.path.exists(png):
        problems.append('MISSING png')
    else:
        sz = png_size(png)
        if sz is None:
            problems.append('png not valid')
        else:
            problems.append(f'png {sz[0]}x{sz[1]}')
    if os.path.exists(mc):
        try:
            m = json.load(open(mc, encoding='utf-8'))
            fr = m.get('animation', {}).get('frames')
            problems.append(f'mcmeta frames={len(fr) if isinstance(fr, list) else fr}')
        except Exception as e:
            problems.append(f'mcmeta parse error: {e}')
    else:
        problems.append('no mcmeta')
    print(f'  {pid}: ' + ' | '.join(problems))
