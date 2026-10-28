import re, io

p = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\TianfuVfx.java'
s = io.open(p, encoding='utf-8').read()

period = int(re.search(r'AURA_PERIOD\s*=\s*(\d+)', s).group(1))
print('AURA_PERIOD =', period)

# 从 tierOf 提取品质→档位
body = s[s.index('private static int tierOf'):]
body = body[:body.index('}')+1]
pairs = re.findall(r'case "([^"]+)" -> (\d+)', body)
print('tierOf:', pairs)

# 档位数 = max+1
ntiers = max(int(v) for _, v in pairs) + 1
print('tiers =', ntiers, '| 预筛 phase >', ntiers-1)

print()
print('=== 模拟：每档在 4*period tick 内的播放次数 ===')
ok = True
for t in range(ntiers):
    hits = sum(1 for tick in range(period*4) if (tick % period) == t)
    expect = 4
    st = 'OK' if hits == expect else 'BAD'
    if hits != expect:
        ok = False
    print(f'  tier {t}: {hits} (期望 {expect}) {st}')

# 检查预筛阈值与档位数一致
thr = int(re.search(r'if \(phase > (\d+)\)', s).group(1))
print()
print(f'预筛阈值 phase > {thr} ; 档位数 {ntiers} ; 一致: {thr == ntiers-1}')

print()
print('ALL OK' if ok and thr == ntiers-1 else 'FAILED')
