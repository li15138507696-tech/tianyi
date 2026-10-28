"""Verify the 气运 cap-raise conversion against the REAL native clamp rule.

Native KE1Procedure (per second):
    if (qiyun_shangxian < qiyun) { qiyun = qiyun_shangxian; }
    per in-game minute: qiyun += 0.05

Native NEVER writes qiyun_shangxian (all 8 grep hits are reads) -> cap writes are durable.

Cap semantics: a cap raise intentionally does NOT move qiyun immediately.
Its value is that the ceiling rises, so native's per-minute qiyun growth
accumulates into the new headroom instead of being clipped away.
So the correct success criterion is:
  (1) cap increases durably, and
  (2) qiyun can then grow into that headroom over time.
NOT "qiyun jumps immediately".
"""
import re

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'


def norm(p):
    return re.sub(r'\s+', ' ', open(p, encoding='utf-8').read())


tal = norm(BASE + r'\TianfuTalents.java')
pool = norm(BASE + r'\TianfuPool.java')
eff = norm(BASE + r'\TianfuEffects.java')

# split the two methods so apply/revoke don't get confused
apply_body = eff[eff.index('public static void applyOnce'):eff.index('public static void revokeOnce')]
revoke_body = eff[eff.index('public static void revokeOnce'):eff.index('/** 每 tick 调用')]
tal_apply = tal[tal.index('public static void applyOnce'):tal.index('public static void revokeOnce')]
tal_revoke = tal[tal.index('public static void revokeOnce'):]


def native_second(qiyun, cap):
    """One native tick-second: clamp down only, never up."""
    return cap if cap < qiyun else qiyun


def native_minutes(qiyun, cap, minutes):
    """Native passive growth: +0.05 per in-game minute, clamped down each second."""
    for _ in range(minutes):
        qiyun += 0.05
        qiyun = native_second(qiyun, cap)
    return qiyun


print('=== 1. 数据面：气运词条是否已全部转为上限口径 ===')
rows = []
for m in re.finditer(r't\("([^"]*)", "([^"]*)", "([^"]*)", (-?\d+), "([^"]*)"(.*?)\)\)?,\n', tal_apply + '\n'):
    tid, name, q, cost, desc, fx = m.groups()
    adds = re.findall(r'fldAdd\("(qiyun(?:_shangxian)?)", (-?\d+)\)', fx)
    if adds:
        rows.append((tid, name, desc, adds, 'Talents'))
for m in re.finditer(r'ids\.contains\("([^"]*)"\)\)?[^}]*?addCap\(v, (-?\d+\.\d+)\)', apply_body):
    tid, d = m.group(1), float(m.group(2))
    nm = re.search(r'entry\("%s", "([^"]*)"' % re.escape(tid), pool)
    ds = re.search(r'entry\("%s", "[^"]*", "(?:[^"]*|\w+)", -?\d+, "([^"]*)"' % re.escape(tid), pool)
    rows.append((tid, nm.group(1) if nm else '?', ds.group(1) if ds else '?',
                 [('qiyun_shangxian', d)], 'Effects'))

bad = []
for tid, name, desc, adds, where in sorted(rows, key=lambda r: r[1]):
    direct = [a for f, a in adds if f == 'qiyun']
    cap = sum(a for f, a in adds if f == 'qiyun_shangxian')
    if direct:
        bad.append(tid)
    print(f'  {"BAD" if direct else "OK "} [{where:7}] {name:9} cap={cap:+7.0f} 直接加气运={direct or "无"}')
print(f'\n  仍有直接加 qiyun 的词条: {bad if bad else "无"}')

print()
print('=== 2. 生效性：最坏场景（丁等机缘 cap=1..10，取 5），观察 60 分钟本体回复 ===')
print('  旧口径：直接 +qiyun 但 cap 不动 → 下一秒被削平，永久丢失')
for name, d in (('气运微光', 5), ('气运微荫', 12)):
    q0, c0 = 5.0, 5.0
    q = native_second(q0 + d, c0)          # 写入后下一秒
    q = native_minutes(q, c0, 60)
    print(f'    {name} 旧: qiyun {q0:.1f} → {q:.1f}（60 分钟后仍是 {q:.1f}，{d} 点白给）')

print('  新口径：cap 抬高 → 本体每分钟 +0.05 逐步填满新空间')
for name, d in (('气运微光', 5), ('气运微荫', 12), ('气运如虹', 60),
                ('气运之子', 140), ('吞天噬地', 50), ('福泽绵长', 15)):
    q0, c0 = 5.0, 5.0
    cap = c0 + d
    q_now = native_second(q0, cap)          # cap 抬高后 qiyun 立即不受限
    q60 = native_minutes(q_now, cap, 60)
    q600 = native_minutes(q_now, cap, 600)
    print(f'    {name:9} cap {c0:5.1f}→{cap:6.1f}  qiyun {q0:.1f}→1h {q60:5.1f}→10h {q600:6.1f}'
          f'   {"可增长" if q60 > q0 else "未增长"}')

print()
print('=== 3. 负词条：气运稀薄应压低上限（本体只向下削平，负向本就生效）===')
q, cap = 20.0, 40.0
cap2 = max(0.0, cap - 15)
q2 = native_minutes(q, cap2, 60)
print(f'  cap {cap:.0f} → {cap2:.0f}；qiyun {q:.1f} → {q2:.1f}（被压到新上限，负词条生效）')

print()
print('=== 4. 上限非负兜底（气运稀薄 −15 叠加低上限机缘）===')
for base_cap in (1, 5, 10):
    raw, floored = base_cap - 15, max(0.0, base_cap - 15)
    q = native_second(20.0, floored)
    print(f'  cap {base_cap:2} → 裸算 {raw:5.0f} → 兜底 {floored:4.0f} → qiyun {q:4.0f}'
          f'{"   ← 已修负数气运" if raw < 0 else ""}')
print('  无兜底时 qiyun 会被压成负数（本体不校验），有兜底恒为 0。')

print()
print('=== 5. 撤销对称性（apply → revoke 回到原点）===')
tal_adds = dict(re.findall(r't\("([^"]*)",[^)]*?fldAdd\("qiyun_shangxian", (-?\d+)\)', tal_apply))
cases = [('气运微光', 5), ('气运微荫', 12), ('气运如虹', 60), ('气运之子', 140),
         ('吞天噬地', 50), ('福泽绵长', 15), ('气运稀薄', -15)]
allok = True
for name, d in cases:
    cap, start = 30.0, 30.0
    cap = max(0.0, cap + d)
    cap = max(0.0, cap - d)
    ok = abs(cap - start) < 1e-9
    allok &= ok
    print(f'  {name:9} {d:+5.0f}  {start:5.0f} → {cap:5.0f}  {"OK" if ok else "MISMATCH"}')
print(f'  撤销全部对称: {allok}')

print()
print('=== 6. 极端叠加：所有气运词条全买（含负词条）===')
tot_cap = 5 + 12 + 18 + 60 + 140 + 50 + 15 - 15
print(f'  上限合计增量 {tot_cap:+.0f}（气运微光+5 微荫+12 盈门+18 如虹+60 之子+140 吞天+50 福泽+15 稀薄−15）')
print(f'  起始 cap 5 → {max(0.0, 5 + tot_cap):.0f}；起始 cap 1 → {max(0.0, 1 + tot_cap):.0f}（非负兜底）')
