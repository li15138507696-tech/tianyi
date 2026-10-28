"""通用跨版本兼容审计：任意蛊真人附属 × 本体 9.8 / 10.4 / 10.20。

用法：
  python audit_multi_version_generic.py <addon.jar> <src/main/java 根> <自身包前缀>

检查四层：
  L1 类存在性   —— 附属常量池里引用的每个 net.guzhenren.* 类
  L2 成员存在性 —— 引用的方法名 / 字段名
  L3 Mixin 注入点 —— 从源码解析 @Mixin 目标 + @Inject 方法，核对类与方法签名
  L4 汇总判定

两个已知坑（tianfu 审计时踩过，这里已处理）：
  - @Mixin(value = Foo.class) 里是【简单类名】，必须用 import 解析成 FQCN
  - @Inject(method = "x(Lnet/…;)V") 是【JVM 描述符】，javap 是点分格式，要转换
"""
import zipfile
import subprocess
import re
import os
import sys

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
BASEVER_DIR = r'D:\daima\mcmod\蛊真人附属\_crash3\basever'
BASES = {
    '9.8': os.path.join(BASEVER_DIR, 'guzhenren-9.8.jar'),
    '10.4': os.path.join(BASEVER_DIR, 'guzhenren-10.4.jar'),
    '10.20': os.path.join(BASEVER_DIR, 'guzhenren-10.20.jar'),
}
ORDER = ['9.8', '10.4', '10.20']

if len(sys.argv) < 4:
    sys.exit('用法: audit_multi_version_generic.py <addon.jar> <java源码根> <自身包前缀>')
ADDON_JAR, SRC_ROOT, OWN_PKG = sys.argv[1], sys.argv[2], sys.argv[3]

for v, p in BASES.items():
    if not os.path.exists(p):
        sys.exit('缺少本体 jar: %s（先跑 extract_base_versions.py）' % p)
if not os.path.exists(ADDON_JAR):
    sys.exit('缺少附属 jar: %s' % ADDON_JAR)

print('审计对象:', os.path.basename(ADDON_JAR))
print('自身包前缀:', OWN_PKG)
print()


def classes_of(jar):
    with zipfile.ZipFile(jar) as z:
        return {n[:-6].replace('/', '.') for n in z.namelist() if n.endswith('.class')}


base_classes = {v: classes_of(p) for v, p in BASES.items()}

# ---------------------------------------------------------------- 收集引用
own_path = OWN_PKG.replace('.', '/') + '/'
addon_classes = [n[:-6].replace('/', '.') for n in zipfile.ZipFile(ADDON_JAR).namelist()
                 if n.endswith('.class') and n.startswith(own_path)]

cp = os.pathsep.join([ADDON_JAR, BASES['10.4']])
refs_class = set()
refs_member = {}

for fq in addon_classes:
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', cp, fq],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    for m in re.finditer(r'//\s*class\s+(net/guzhenren/[\w/$]+)', txt):
        refs_class.add(m.group(1).replace('/', '.'))
    for m in re.finditer(r'//\s*(?:Interface)?Method\s+(net/guzhenren/[\w/$]+)\.([\w$<>]+):', txt):
        owner = m.group(1).replace('/', '.')
        refs_member.setdefault(owner, set()).add(m.group(2))
        refs_class.add(owner)
    for m in re.finditer(r'//\s*Field\s+(net/guzhenren/[\w/$]+)\.([\w$]+):', txt):
        owner = m.group(1).replace('/', '.')
        refs_member.setdefault(owner, set()).add(m.group(2))
        refs_class.add(owner)

# 排除自身包
refs_class = {c for c in refs_class if not c.startswith(OWN_PKG + '.')}
refs_member = {o: s for o, s in refs_member.items() if not o.startswith(OWN_PKG + '.')}

print('附属 class 数:', len(addon_classes))
print('引用的本体类: %d 个' % len(refs_class))
for c in sorted(refs_class):
    print('   ', c)
print()

member_cache = {}


def members_of(ver, owner):
    key = (ver, owner)
    if key in member_cache:
        return member_cache[key]
    r = subprocess.run([JAVAP, '-p', '-classpath', BASES[ver], owner],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        member_cache[key] = None
        return None
    names = set()
    for ln in (r.stdout or '').splitlines():
        s = ln.strip()
        m = re.search(r'\b([\w$]+)\s*\(', s)
        if m:
            names.add(m.group(1))
        m2 = re.search(r'\b([\w$]+)\s*;\s*$', s)
        if m2:
            names.add(m2.group(1))
    member_cache[key] = names
    return names


# ---------------------------------------------------------------- L1/L2
print('=' * 76)
print('L1 类存在性 / L2 成员存在性')
print('=' * 76)
matrix = {}
for ver in ORDER:
    miss_cls = sorted(c for c in refs_class if c not in base_classes[ver])
    miss_mem = []
    for owner, names in refs_member.items():
        if owner not in base_classes[ver]:
            continue
        got = members_of(ver, owner)
        if got is None:
            continue
        for nm in names:
            if nm not in got:
                miss_mem.append('%s.%s' % (owner, nm))
    matrix[ver] = (miss_cls, sorted(miss_mem))
    print('  %-6s %s  缺类 %d / 缺成员 %d'
          % (ver, 'OK' if not miss_cls and not miss_mem else 'FAIL',
             len(miss_cls), len(miss_mem)))
    for c in miss_cls:
        print('        缺类:', c)
    for m in miss_mem:
        print('        缺成员:', m)

# ---------------------------------------------------------------- L3 Mixin
print()
print('=' * 76)
print('L3 Mixin 注入点')
print('=' * 76)

mixin_files = []
for root, _dirs, files in os.walk(SRC_ROOT):
    for f in files:
        if f.endswith('Mixin.java'):
            mixin_files.append(os.path.join(root, f))

if not mixin_files:
    print('  （该附属没有 mixin）')
    mixin_bad = {v: [] for v in ORDER}
else:
    mixin_bad = {v: [] for v in ORDER}
    for path in sorted(mixin_files):
        src = open(path, encoding='utf-8').read()
        name = os.path.basename(path)[:-5]
        mv = re.search(r'@Mixin\(value\s*=\s*([\w.]+)\.class', src)
        if not mv:
            print('  %-40s !! 解析不到 @Mixin 目标' % name)
            continue
        simple = mv.group(1)
        if '.' in simple:
            fqcn = simple
        else:
            imp = re.search(r'^import\s+((?:net\.guzhenren|net\.minecraft)[\w.]*\.'
                            + re.escape(simple) + r')\s*;', src, re.M)
            fqcn = imp.group(1) if imp else simple
        mm = re.search(r'method\s*=\s*"([^"]+)"', src)
        meth = mm.group(1) if mm else None
        meth_name = meth.split('(')[0] if meth else None
        desc = [d.replace('/', '.') for d in re.findall(r'L([\w/$]+);', meth or '')]

        line = '  %-40s' % name
        for ver in ORDER:
            cls_ok = fqcn in base_classes[ver]
            meth_ok = False
            if cls_ok and meth_name:
                r = subprocess.run([JAVAP, '-p', '-classpath', BASES[ver], fqcn],
                                   capture_output=True, text=True,
                                   encoding='utf-8', errors='replace')
                lines = [ln for ln in (r.stdout or '').splitlines()
                         if re.search(r'\b' + re.escape(meth_name) + r'\s*\(', ln)]
                meth_ok = bool(lines)
                if desc and meth_ok:
                    meth_ok = any(all(t in ln for t in desc) for ln in lines)
            ok = cls_ok and meth_ok
            if not ok:
                mixin_bad[ver].append('%s(%s)' % (name, '缺类' if not cls_ok else '缺方法'))
            line += '  %s:%s' % (ver, 'OK' if ok else
                                 ('缺类' if not cls_ok else '缺方法/签名'))
        print(line)
        print('       目标: %s :: %s' % (fqcn, meth))

# ---------------------------------------------------------------- L4
print()
print('=' * 76)
print('总结')
print('=' * 76)
all_ok = True
for ver in ORDER:
    miss_cls, miss_mem = matrix[ver]
    bad = mixin_bad[ver]
    verdict = '可用' if (not miss_cls and not miss_mem and not bad) else '需修'
    if verdict == '需修':
        all_ok = False
    print('  %-6s %s   缺类 %d / 缺成员 %d / Mixin 失效 %d %s'
          % (ver, verdict, len(miss_cls), len(miss_mem), len(bad),
             ('-> ' + ', '.join(bad)) if bad else ''))
print()
print('总体:', '三版本均可运行' if all_ok else '存在需要修复的项')
