"""对比「指定的本体 10.20」与「当前编译依赖」两个 jar 的元数据与关键类。

用户要求按 `D:\\cxdownload\\guzhenrn蛊真人模组本体10.20.jar` 这一版本来。
先确认：版本号、是否含我们依赖的类、是否夹带嵌套 jar（影响 javac 类路径）。
"""
import re
import zipfile

JARS = {
    '指定 10.20': r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar',
    '当前编译用': r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar',
}

NEED = [
    'net/guzhenren/network/GuzhenrenModVariables.class',
    'net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class',
]

for label, path in JARS.items():
    print('==', label)
    z = zipfile.ZipFile(path)
    names = z.namelist()
    print('   条目数', len(names))
    for meta in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
        if meta in names:
            text = z.read(meta).decode('utf-8', 'replace')
            mod_id = re.search(r'modId\s*=\s*"([^"]+)"', text)
            version = re.search(r'^\s*version\s*=\s*"([^"]+)"', text, re.M)
            print('   元数据', meta,
                  '| modId =', mod_id.group(1) if mod_id else '?',
                  '| version =', version.group(1) if version else '?')
            break
    for cls in NEED:
        print('   ', '有' if cls in names else '缺', cls)
    nested = [x for x in names if x.endswith('.jar')]
    print('   嵌套 jar:', len(nested), nested[:3])
