import re

vfx = open('src/main/java/net/guzhenren/tianfu/TianfuVfx.java', encoding='utf-8').read()
pool = open('src/main/java/net/guzhenren/tianfu/TianfuPool.java', encoding='utf-8').read()

vfx_ids = set(int(m) for m in re.findall(r'put\(\s*(\d+)\s*,', vfx))
body_tz = set(int(t) for t in re.findall(r'body\(\s*"\w+"\s*,\s*"[^"]*"\s*,\s*(\d+)', pool))
sijue_tz = set(range(1, 11))
all_tz = body_tz | sijue_tz | {54}

print('VFX mapped tizhi:', sorted(vfx_ids))
print('all purchasable tizhi:', sorted(all_tz))
print()
print('purchasable WITHOUT vfx:', sorted(all_tz - vfx_ids))
print('vfx for non-purchasable:', sorted(vfx_ids - all_tz))

# duplicates in put()?
nums = re.findall(r'put\(\s*(\d+)\s*,', vfx)
dups = [n for n in set(nums) if nums.count(n) > 1]
print('duplicate vfx keys:', dups)
