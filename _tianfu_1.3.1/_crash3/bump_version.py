"""Bump the mod version in one shot.

Usage:
    python _crash3/bump_version.py            # patch +1   (1.2.5 -> 1.2.6)
    python _crash3/bump_version.py minor      # minor +1   (1.2.6 -> 1.3.0)
    python _crash3/bump_version.py major      # major +1   (1.3.0 -> 2.0.0)
    python _crash3/bump_version.py 1.4.2      # set explicitly
    python _crash3/bump_version.py --check    # only verify all sites agree

Touches (the only places a version lives):
  version.properties                      <- source of truth
  src/main/resources/META-INF/neoforge.mods.toml
  docs/天赋系统介绍文档.md, docs/宣传文档.md   (mentions of tianfu-X.Y.Z)
build.gradle and the _crash3 scripts read version.properties, so they need no edit.
"""
import os
import re
import sys

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
PROPS = os.path.join(ROOT, 'version.properties')
TOML = os.path.join(ROOT, 'src', 'main', 'resources', 'META-INF', 'neoforge.mods.toml')
DOCS = [os.path.join(ROOT, 'docs', '天赋系统介绍文档.md'),
        os.path.join(ROOT, 'docs', '宣传文档.md')]


def read_props():
    with open(PROPS, encoding='utf-8') as f:
        text = f.read()
    m = re.search(r'^\s*version\s*=\s*(\S+)\s*$', text, re.M)
    if not m:
        raise SystemExit('version.properties 里没有 version=…')
    return text, m.group(1)


def read_toml():
    with open(TOML, encoding='utf-8') as f:
        text = f.read()
    m = re.search(r'^(\s*version\s*=\s*")([^"]+)(")', text, re.M)
    if not m:
        raise SystemExit('neoforge.mods.toml 里没有 version = "…"')
    return text, m.group(2)


def doc_versions():
    """{path: set(versions)} for tianfu-X.Y.Z mentions in the docs."""
    out = {}
    for p in DOCS:
        if not os.path.isfile(p):
            continue
        with open(p, encoding='utf-8') as f:
            out[p] = set(re.findall(r'tianfu-(\d+\.\d+\.\d+)', f.read()))
    return out


def check():
    _, pv = read_props()
    _, tv = read_toml()
    dv = doc_versions()
    print(f'version.properties      : {pv}')
    print(f'neoforge.mods.toml      : {tv}  {"OK" if tv == pv else "<< 不一致"}')
    for p, vs in dv.items():
        tag = 'OK' if vs == {pv} else ('<< 不一致' if vs else '(无版本引用)')
        print(f'{os.path.basename(p):24}: {sorted(vs) or "-"}  {tag}')
    stale = [p for p, vs in dv.items() if vs and vs != {pv}]
    ok = tv == pv and not stale
    print('\n一致' if ok else '\n存在不一致，跑 `python _crash3/bump_version.py` 同步')
    return ok


def bump(target):
    ptext, old = read_props()
    if target is None:
        major, minor, patch = (int(x) for x in old.split('.'))
        new = f'{major}.{minor}.{patch + 1}'
    elif target == 'minor':
        major, minor, _ = (int(x) for x in old.split('.'))
        new = f'{major}.{minor + 1}.0'
    elif target == 'major':
        major, _, _ = (int(x) for x in old.split('.'))
        new = f'{major + 1}.0.0'
    else:
        if not re.fullmatch(r'\d+\.\d+\.\d+', target):
            raise SystemExit(f'版本号格式应为 X.Y.Z：{target!r}')
        new = target

    if new == old:
        print(f'版本未变：{old}')
        return

    # 1) version.properties
    with open(PROPS, 'w', encoding='utf-8', newline='\n') as f:
        f.write(re.sub(r'^(\s*version\s*=\s*)\S+\s*$', lambda m: m.group(1) + new, ptext, count=1, flags=re.M))

    # 2) mods.toml
    ttext, tv = read_toml()
    with open(TOML, 'w', encoding='utf-8', newline='\n') as f:
        f.write(re.sub(r'^(\s*version\s*=\s*")[^"]+(")', lambda m: m.group(1) + new + m.group(2),
                       ttext, count=1, flags=re.M))

    # 3) docs
    touched = []
    for p in DOCS:
        if not os.path.isfile(p):
            continue
        with open(p, encoding='utf-8') as f:
            text = f.read()
        fixed = re.sub(r'tianfu-\d+\.\d+\.\d+', f'tianfu-{new}', text)
        fixed = re.sub(r'(\*\*当前版本：)tianfu-\d+\.\d+\.\d+(\*\*)', rf'\g<1>tianfu-{new}\g<2>', fixed)
        if fixed != text:
            with open(p, 'w', encoding='utf-8', newline='\n') as f:
                f.write(fixed)
            touched.append(os.path.basename(p))

    print(f'版本 {old} → {new}')
    print(f'  已更新 version.properties, META-INF/neoforge.mods.toml'
          + (f', {", ".join(touched)}' if touched else ''))
    print('  build.gradle / _crash3 脚本自动跟随，无需改动。')


if __name__ == '__main__':
    arg = sys.argv[1] if len(sys.argv) > 1 else None
    if arg == '--check':
        sys.exit(0 if check() else 1)
    bump(arg)
    check()
