"""从外层 10.20 本体包里抽出 guzhenren_library 的类，做成只用于编译的 API jar。

为什么要单独抽：外层 jar 里 99% 体积是 META-INF/jarjar 下的嵌套模组（含 106MB 的内层本体），
编译期只需要 com/guzhenrenlibrary/** 这一层类。抽出来体积从 108MB 降到约 1.5MB，
同时保留可追溯性（来源路径 + sha256 写进 _pack/libs/README.txt）。

用法：python _pack/extract_library_api.py
"""
import hashlib
import os
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUTER = r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar'
OUT = os.path.join(ROOT, '_pack', 'libs', 'guzhenren_library-api.jar')
KEEP_PREFIXES = ('com/guzhenrenlibrary/',)
KEEP_FILES = ('META-INF/neoforge.mods.toml',)


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    if not os.path.isfile(OUTER):
        raise SystemExit('缺少外层本体包: ' + OUTER)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    n = 0
    with zipfile.ZipFile(OUTER) as zi, zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as zo:
        for info in zi.infolist():
            name = info.filename
            keep = name.endswith('.class') and name.startswith(KEEP_PREFIXES)
            keep = keep or name in KEEP_FILES
            if not keep:
                continue
            zo.writestr(name, zi.read(name))
            n += 1
    print('entries:', n)
    print('out    :', OUT)
    print('size   :', os.path.getsize(OUT))
    print('sha256 :', sha256(OUT))
    print('source :', OUTER)
    print('src sha:', sha256(OUTER))


if __name__ == '__main__':
    main()
