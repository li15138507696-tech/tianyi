import io, re, os

src = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
tal = io.open(os.path.join(src, 'TianfuTalents.java'), encoding='utf-8').read()
vfx = io.open(os.path.join(src, 'TianfuVfx.java'), encoding='utf-8').read()

real_ids = set(re.findall(r'\bt\("([^"]+)"', tal))
# 只取 AURAS.put 段（不含 TIER_AURAS）
aura_ids = re.findall(r'AURAS\.put\("([^"]+)"', vfx)
tier_q = re.findall(r'TIER_AURAS\.put\("([^"]+)"', vfx)

print('real talent ids:', len(real_ids))
print('explicit aura entries:', len(aura_ids))
print('tier fallbacks:', tier_q)
print()

bad = [a for a in aura_ids if a not in real_ids]
print('=== 不存在的专属 id（必须为空）===')
print(bad if bad else '  none')

print()
print('=== 有专属特效的词条 ===')
for a in aura_ids:
    if a in real_ids:
        print('  OK', a)

# 统计各档覆盖
import collections
covered = set(aura_ids) & real_ids
print()
print('covered:', len(covered))
