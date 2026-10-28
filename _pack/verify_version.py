"""版本一致性：version.properties 是唯一来源，其余出现版本号的地方必须与它一致。"""
import os, re, sys

ROOT = r'D:\daima\mcmod\天意'


def props_version():
    with open(os.path.join(ROOT, 'version.properties'), encoding='utf-8') as f:
        for line in f:
            m = re.match(r'\s*version\s*=\s*(\S+)', line)
            if m:
                return m.group(1)
    raise SystemExit('version.properties 里没有 version=…')


def toml_version():
    p = os.path.join(ROOT, 'src', 'main', 'resources', 'META-INF', 'neoforge.mods.toml')
    raw = open(p, 'rb').read()
    if raw[:3] == b'\xef\xbb\xbf':
        raise SystemExit('mods.toml 带 BOM')
    m = re.search(r'^\s*version\s*=\s*"([^"]+)"', raw.decode('utf-8'), re.M)
    if not m:
        raise SystemExit('mods.toml 里没有 version = "…"')
    return m.group(1)


def doc_versions():
    """文档里的**当前版本声明**，只认带锚点的行（产物 / 版本 / 依据 / 使用（）。

    版本历史（例如「1.0.8 的改动」「删掉 tianyi-1.0.8.jar」）不算声明，
    否则每发一版都会因为历史行而被判「不一致」。
    """
    out = []
    anchors = ('产物', '版本', '依据', '使用（')
    docs = os.path.join(ROOT, 'docs')
    if not os.path.isdir(docs):
        return out
    for fn in sorted(os.listdir(docs)):
        if not fn.endswith('.md'):
            continue
        # 历史版本文档（文件名以 x.y.z- 开头）记录的是「那一版发了什么」，
        # 里面的 tianyi-x.y.z.jar 是事实而非当前版本，不参与一致性检查。
        if re.match(r'^\d+\.\d+\.\d+-', fn):
            continue
        text = open(os.path.join(docs, fn), encoding='utf-8').read()
        for line in text.split('\n'):
            if not any(a in line for a in anchors):
                continue
            for m in re.finditer(r'tianyi-(\d+\.\d+\.\d+)', line):
                out.append((fn, m.group(1)))
    return out


ver = props_version()
print('version.properties:', ver)

tv = toml_version()
print('neoforge.mods.toml :', tv, 'OK' if tv == ver else 'MISMATCH')

bad = [] if tv == ver else ['neoforge.mods.toml']

for fn, dv in doc_versions():
    status = 'OK' if dv == ver else 'MISMATCH'
    print(f'docs/{fn}: {dv} {status}')
    if dv != ver:
        bad.append(f'docs/{fn}')

jar = os.path.join(ROOT, 'build', 'libs', f'tianyi-{ver}.jar')
print('jar                :', 'OK' if os.path.isfile(jar) else 'MISSING（未打包）')

if bad:
    raise SystemExit('版本不一致：' + ', '.join(bad))
print('版本一致 OK')
