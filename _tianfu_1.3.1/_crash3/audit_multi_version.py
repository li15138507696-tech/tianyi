"""跨版本兼容矩阵：tianfu 1.2.37 在 9.8 / 10.4 / 10.20 上是否都能跑。

检查四层：
  L1 类存在性  —— tianfu 常量池里引用的每个 net.guzhenren.* 类
  L2 成员存在性 —— 引用的每个方法名 / 字段名
  L3 Mixin 注入点 —— 4 个 mixin 的目标类 + 目标方法签名
  L4 开窍链路关键点 —— 希望蛊 / 收尾 / 客户端面板 / 粒子 / 音效

注：10.4 与 10.20 的内层 jar 字节相同（sha256 b6d314d3…），
所以这两列必然一致；矩阵里仍分别列出以便对照。
"""
import zipfile
import subprocess
import re
import os

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
TF = os.path.join(ROOT, 'build', 'libs', 'tianfu-1.2.37.jar')
BASES = {
    '9.8': os.path.join(ROOT, '_crash3', 'basever', 'guzhenren-9.8.jar'),
    '10.4': os.path.join(ROOT, '_crash3', 'basever', 'guzhenren-10.4.jar'),
    '10.20': os.path.join(ROOT, '_crash3', 'basever', 'guzhenren-10.20.jar'),
}

VER_ORDER = ['9.8', '10.4', '10.20']


def classes_of(jar):
    with zipfile.ZipFile(jar) as z:
        return {n[:-6].replace('/', '.') for n in z.namelist() if n.endswith('.class')}


base_classes = {v: classes_of(p) for v, p in BASES.items()}

# ---------------------------------------------------------------- L1: 收集引用
tf_classes = [n[:-6].replace('/', '.') for n in
              zipfile.ZipFile(TF).namelist()
              if n.endswith('.class') and n.startswith('net/guzhenren/tianfu/')]

cp = os.pathsep.join([TF, BASES['10.4']])
refs_class = set()
refs_member = {}

for fq in tf_classes:
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

refs_class = {c for c in refs_class if not c.startswith('net.guzhenren.tianfu.')}
refs_member = {o: s for o, s in refs_member.items()
               if not o.startswith('net.guzhenren.tianfu.')}

print('tianfu 引用的本体类: %d 个' % len(refs_class))
print('引用的成员所属类: %d 个' % len(refs_member))
print()

# ---------------------------------------------------------------- 成员表（每版本缓存）
member_cache = {}


def members_of(ver, owner):
    key = (ver, owner)
    if key in member_cache:
        return member_cache[key]
    r = subprocess.run([JAVAP, '-p', '-classpath', BASES[ver], owner],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    txt = r.stdout or ''
    if r.returncode != 0:
        member_cache[key] = None
        return None
    names = set()
    for ln in txt.splitlines():
        s = ln.strip()
        m = re.search(r'\b([\w$]+)\s*\(', s)
        if m:
            names.add(m.group(1))
        m2 = re.search(r'\b([\w$]+)\s*;\s*$', s)
        if m2:
            names.add(m2.group(1))
    member_cache[key] = names
    return names


# ---------------------------------------------------------------- 逐版本检查
print('=' * 78)
print('L1 类存在性 / L2 成员存在性')
print('=' * 78)
matrix = {}
for ver in VER_ORDER:
    miss_cls = sorted(c for c in refs_class if c not in base_classes[ver])
    miss_mem = []
    for owner, names in refs_member.items():
        if owner not in base_classes[ver]:
            continue  # 类本身已缺，L1 会报
        got = members_of(ver, owner)
        if got is None:
            continue
        for nm in names:
            if nm not in got:
                miss_mem.append('%s.%s' % (owner, nm))
    matrix[ver] = (miss_cls, sorted(miss_mem))
    status = 'OK' if not miss_cls and not miss_mem else 'FAIL'
    print('  %-6s %s  缺类 %d / 缺成员 %d'
          % (ver, status, len(miss_cls), len(miss_mem)))
    for c in miss_cls[:12]:
        print('        缺类:', c)
    if len(miss_cls) > 12:
        print('        ... 还有 %d 个缺类' % (len(miss_cls) - 12))
    for m in miss_mem[:12]:
        print('        缺成员:', m)
    if len(miss_mem) > 12:
        print('        ... 还有 %d 个缺成员' % (len(miss_mem) - 12))

# ---------------------------------------------------------------- L3: Mixin 注入点
print()
print('=' * 78)
print('L3 Mixin 注入点')
print('=' * 78)
MIXINS = [
    ('YizhuanrendaoxiwangguItemMixin', 'net.guzhenren.item.YizhuanrendaoxiwangguItem',
     'use', ['use(net.minecraft.world.level.Level',
             'net.minecraft.world.entity.player.Player',
             'net.minecraft.world.InteractionHand']),
    ('UikongqiaoquedingProcedureMixin', 'net.guzhenren.procedures.UikongqiaoquedingProcedure',
     'execute', ['execute(net.minecraft.world.level.LevelAccessor',
                  'net.minecraft.world.entity.Entity']),
    ('UishuxingshijuetiProcedureMixin', None, None, None),
    ('JingJieBaoCunProcedureMixin', None, None, None),
]

# 先把两个没写目标的 mixin 的目标从源码里读出来
# 注意：@Mixin(value = Foo.class) 里的 Foo 是【简单名】，必须用 import 解析成全限定名，
# 否则拿简单名去和 FQCN 集合比对必然「缺类」（这个坑第一版就踩了）。
MIXIN_SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianfu', 'mixin')
for i, (name, tgt, meth, _) in enumerate(MIXINS):
    if tgt is None:
        p = os.path.join(MIXIN_SRC, name + '.java')
        src = open(p, encoding='utf-8').read()
        m = re.search(r'@Mixin\(value\s*=\s*([\w.]+)\.class', src)
        mm = re.search(r'@Inject\(\s*method\s*=\s*"([^"]+)"', src, re.S)
        simple = m.group(1) if m else None
        fqcn = None
        if simple:
            if '.' in simple:
                fqcn = simple
            else:
                imp = re.search(
                    r'^import\s+((?:net\.guzhenren|net\.minecraft)[\w.]*\.' + re.escape(simple) + r')\s*;',
                    src, re.M)
                fqcn = imp.group(1) if imp else simple
        MIXINS[i] = (name, fqcn, mm.group(1) if mm else None, [])

mixin_result = {}
for name, tgt, meth, sig_hints in MIXINS:
    if not tgt:
        print('  %-34s !! 无法从源码解析目标' % name)
        continue
    # meth 可能是裸方法名（"use"）或 JVM 描述符（"execute(Lnet/...;)V"）。
    # javap 输出是点分格式，必须把描述符里的 Lx/y/Z; 转成 x.y.Z 才能比对。
    meth_name = meth.split('(')[0] if meth else None
    desc_types = []
    if meth and '(' in meth:
        desc_types = re.findall(r'L([\w/$]+);', meth)
        desc_types = [d.replace('/', '.') for d in desc_types]

    row = []
    for ver in VER_ORDER:
        cls_ok = tgt in base_classes[ver]
        meth_ok = False
        if cls_ok and meth_name:
            r = subprocess.run([JAVAP, '-p', '-classpath', BASES[ver], tgt],
                               capture_output=True, text=True, encoding='utf-8', errors='replace')
            txt = r.stdout or ''
            # 找到名为 meth_name 的所有重载行
            lines = [ln for ln in txt.splitlines()
                     if re.search(r'\b' + re.escape(meth_name) + r'\s*\(', ln)]
            meth_ok = bool(lines)
            if desc_types and meth_ok:
                # 必须有一行重载同时包含全部参数类型
                meth_ok = any(all(t in ln for t in desc_types) for ln in lines)
            if sig_hints:
                meth_ok = meth_ok and all(h in txt for h in sig_hints)
        row.append((cls_ok, meth_ok, meth_ok))
    mixin_result[name] = row
    line = '  %-34s' % name
    for ver, (c, m, s) in zip(VER_ORDER, row):
        line += '  %s:%s' % (ver, 'OK' if (c and m and s) else
                             ('缺类' if not c else '缺方法/签名不符'))
    print(line)

# ---------------------------------------------------------------- L4: 开窍链路
print()
print('=' * 78)
print('L4 开窍链路关键点')
print('=' * 78)
KEY_POINTS = [
    ('希望蛊物品类', 'net.guzhenren.item.YizhuanrendaoxiwangguItem', None),
    ('希望蛊物品注册', 'net.guzhenren.init.GuzhenrenModItems', 'YIZHUANRENDAOXIWANGGU'),
    ('开窍收尾 queding', 'net.guzhenren.procedures.UikongqiaoquedingProcedure', 'execute'),
    ('开窍收尾 guanbi（仅 10.x）', 'net.guzhenren.procedures.UikongqiaoguanbiProcedure', 'execute'),
    ('本体开窍面板 Screen', 'net.guzhenren.client.gui.UIKAIQIAOMIANBANScreen', None),
    ('希望蛊粒子', 'net.guzhenren.init.GuzhenrenModParticleTypes', 'XIWANGGULIZI'),
    ('开窍音效', 'net.guzhenren.init.GuzhenrenModSounds', 'GUCHONG_CUIDONG_4'),
    ('变量类', 'net.guzhenren.network.GuzhenrenModVariables$PlayerVariables', 'kongqiao'),
    ('变量类 zhuanshu', 'net.guzhenren.network.GuzhenrenModVariables$PlayerVariables', 'zhuanshu'),
]
for label, cls, member in KEY_POINTS:
    line = '  %-26s' % label
    for ver in VER_ORDER:
        okc = cls in base_classes[ver]
        if not okc:
            line += '  %s:缺类' % ver
            continue
        if member is None:
            line += '  %s:OK' % ver
            continue
        got = members_of(ver, cls)
        if got is None:
            line += '  %s:?' % ver
        else:
            line += '  %s:%s' % (ver, 'OK' if member in got else '缺')

    print(line)

print()
print('=' * 78)
print('总结')
print('=' * 78)
for ver in VER_ORDER:
    miss_cls, miss_mem = matrix[ver]
    mix_bad = [n for n, row in mixin_result.items()
               if not (row[VER_ORDER.index(ver)][0] and row[VER_ORDER.index(ver)][1]
                       and row[VER_ORDER.index(ver)][2])]
    verdict = '可用' if (not miss_cls and not miss_mem and not mix_bad) else '有问题'
    print('  %-6s %s   缺类 %d / 缺成员 %d / Mixin 失效 %d %s'
          % (ver, verdict, len(miss_cls), len(miss_mem), len(mix_bad),
             ('-> ' + ', '.join(mix_bad)) if mix_bad else ''))
