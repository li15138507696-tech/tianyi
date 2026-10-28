import re, io, os

p = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuVfx.java'
s = io.open(p, encoding='utf-8').read()

# 提取 AURA_PERIOD
m = re.search(r'AURA_PERIOD\s*=\s*(\d+)', s)
period = int(m.group(1))
print('AURA_PERIOD =', period)

# 提取各专属 id 的 tier
entries = re.findall(r'AURAS\.put\("([^"]+)",\s*new Aura\((\d)', s)
tiers = {}
for i, t in entries:
    tiers[i] = int(t)
print('explicit auras:', len(tiers))

tier_vals = sorted(set(tiers.values()))
print('tiers present:', tier_vals)

# 模拟：每种 tier 单独拥有时，在 0..2*period 内播几次
print()
print('=== 模拟各档播放次数（每 2*period tick）===')
ok = True
for t in tier_vals:
    hits = 0
    for tick in range(period * 2):
        phase = tick % period
        if phase > 2:
            continue
        if phase != t:
            continue
        hits += 1
    expect = 2
    status = 'OK' if hits == expect else 'BAD'
    if hits != expect:
        ok = False
    print(f'  tier {t}: {hits} 次 (期望 {expect})  {status}')

print()
print('ALL OK' if ok else 'FAILED')
