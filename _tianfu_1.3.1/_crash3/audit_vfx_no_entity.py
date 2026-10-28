"""审计：体质形态层必须是「纯粒子、零实体」。

这是 v1.2.27 修复的核心契约。旧实现用 EntityType.create() 生成本体实体做装饰，
结果 31 太息养元体的龙卷风每 tick 对周围 7.5 格内所有实体造成 600 伤害。

本脚本防止回归：
  1. TianfuVfxEntity 里不得出现任何生成实体的调用
  2. 不得引用本体的 EntityType / GuzhenrenModEntities
  3. 引用的每个粒子常量都必须真实存在（本体 javap + 我方注册表）
  4. 形态数与相位槽数必须一致（否则某形态永远不播）
  5. setAnimation / animationprocedure 反射必须已移除（那是为实体服务的）
"""
import re
import os
import subprocess

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianfu')
JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
BASE_JAR = os.path.join(ROOT, '_crash3', 'guzhenren_inner.jar')

fails = []
warns = []
oks = []


def ok(m):
    oks.append(m)


def bad(m):
    fails.append(m)


def warn(m):
    warns.append(m)


vfx = open(os.path.join(SRC, 'TianfuVfxEntity.java'), encoding='utf-8').read()

# 只查代码，不查注释——类文档里会解释「旧实现用了 setNoAi/setInvulnerable 但拦不住」，
# 那是必要的血泪记录，不能被当成残留代码。
vfx_code = re.sub(r'/\*.*?\*/', '', vfx, flags=re.S)
vfx_code = re.sub(r'//[^\n]*', '', vfx_code)

# ---- 1. 禁止生成实体 ----
FORBIDDEN = [
    ('EntityType.create', r'\btype\.create\s*\('),
    ('addFreshEntity', r'addFreshEntity\s*\('),
    ('new Entity[', r'new\s+Entity\s*\['),
    ('GuzhenrenModEntities', r'GuzhenrenModEntities'),
    ('EntityType 字段', r'\bEntityType\s*[<?]'),
    ('discard(', r'\.discard\s*\('),
    ('setNoAi', r'setNoAi\s*\('),
    ('setInvulnerable', r'setInvulnerable\s*\('),
    ('animationprocedure', r'animationprocedure'),
    ('setAnimation', r'setAnimation\s*\('),
    ('Mob 类型转换', r'instanceof\s+net\.minecraft\.world\.entity\.Mob'),
]
for label, pat in FORBIDDEN:
    if re.search(pat, vfx_code):
        bad('TianfuVfxEntity 仍含实体相关代码: %s' % label)
    else:
        ok('已移除: %s' % label)

# ---- 2. 必须仍然发粒子 ----
if re.search(r'\.sendParticles\s*\(', vfx):
    n = len(re.findall(r'\.sendParticles\s*\(', vfx))
    ok('仍通过 sendParticles 播粒子（%d 处）' % n)
else:
    bad('TianfuVfxEntity 已不再发任何粒子——形态层失效')

# ---- 3. 形态数与相位槽 ----
m_shape = re.search(r'private static final int PERIOD\s*=\s*(\d+)\s*;', vfx)
m_enum = re.search(r'private enum Shape \{(.*?)\}', vfx, re.S)
if m_shape and m_enum:
    period = int(m_shape.group(1))
    members = [x.strip().rstrip(',') for x in m_enum.group(1).split(',') if x.strip()]
    real = [x for x in members if x != 'NONE']
    if len(real) <= period:
        ok('相位槽 %d >= 形态数 %d' % (period, len(real)))
    else:
        bad('相位槽 %d < 形态数 %d：会有形态永远播不到' % (period, len(real)))
else:
    bad('找不到 PERIOD 或 Shape 枚举')

# ---- 4. shapeOf 覆盖了每个形态 ----
if m_enum:
    for s in real:
        if ('Shape.%s' % s) in vfx or ('case %s ->' % s) in vfx:
            continue
        bad('形态 %s 没有出现在 shapeOf 的 case 里' % s)
if all(('case %s ->' % s) in vfx for s in real):
    ok('shapeOf 覆盖全部 %d 个形态' % len(real))

# ---- 5. 粒子常量存在性 ----
base_fields = set()
if os.path.exists(BASE_JAR):
    r = subprocess.run([JAVAP, '-p', '-classpath', BASE_JAR,
                        'net.guzhenren.init.GuzhenrenModParticleTypes'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    base_fields = set(re.findall(r'\b([A-Z][A-Z0-9_]+)\s*;', r.stdout or ''))
else:
    warn('本体 jar 不存在，跳过粒子存在性检查')

own = set()
pt = os.path.join(SRC, 'init', 'TianfuParticleTypes.java')
if os.path.exists(pt):
    own = set(re.findall(r'SimpleParticleType>\s+([A-Z0-9_]+)\s*[=;]',
                         open(pt, encoding='utf-8').read()))

missing = []
for name in sorted(set(re.findall(r'GuzhenrenModParticleTypes\.([A-Z0-9_]+)', vfx))):
    if base_fields and name not in base_fields:
        missing.append('本体粒子 %s 不存在' % name)
for name in sorted(set(re.findall(r'TianfuParticleTypes\.([A-Z0-9_]+)', vfx))):
    if own and name not in own:
        missing.append('我方粒子 %s 未注册' % name)
if missing:
    for x in missing:
        bad(x)
else:
    ok('引用的粒子常量全部存在')

# ---- 6. forget 必须是空操作（无实体可清） ----
mf = re.search(r'public static void forget\(ServerPlayer player\)\s*\{(.*?)\n    \}', vfx, re.S)
if mf:
    body = re.sub(r'//.*', '', mf.group(1))
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S).strip()
    if not body:
        ok('forget() 是空操作（无实体需回收）')
    else:
        warn('forget() 有实际逻辑，确认是否必要')
else:
    bad('找不到 forget(ServerPlayer)')

# ---- 7. 调用点注释已更新 ----
eff = open(os.path.join(SRC, 'TianfuEffects.java'), encoding='utf-8').read()
if '实体装饰层' in eff:
    bad('TianfuEffects 仍留有「实体装饰层」旧注释')
else:
    ok('TianfuEffects 注释已改为形态层')
if re.search(r'TianfuVfxEntity\.tick\s*\(', eff):
    ok('TianfuEffects 仍在调用形态层')
else:
    bad('TianfuEffects 不再调用形态层')

print('=' * 70)
print('体质形态层「零实体」审计')
print('=' * 70)
for m in oks:
    print('  OK   ', m)
for m in warns:
    print('  WARN ', m)
for m in fails:
    print('  FAIL ', m)
print('-' * 70)
print('通过 %d / 警告 %d / 失败 %d' % (len(oks), len(warns), len(fails)))
if fails:
    raise SystemExit(1)
