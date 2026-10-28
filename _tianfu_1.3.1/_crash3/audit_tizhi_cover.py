import re, os

src = open('src/main/java/net/guzhenren/tianfu/TianfuPool.java', encoding='utf-8').read()

# body(...) entries: find id + tizhi
bodies = re.findall(r'body\(\s*"(\w+)"\s*,\s*"([^"]*)"\s*,\s*(\d+)', src)
print(f'bodies in pool: {len(bodies)}')
pool_tizhi = {}
for bid, bname, tz in bodies:
    pool_tizhi[int(tz)] = (bid, bname)

# sijue entries
sijues = re.findall(r'sijue\(\s*"(\w+)"\s*,\s*"([^"]*)"\s*,\s*(\d+)', src)
print(f'sijues in pool: {len(sijues)}')
for sid, sname, tz in sijues:
    pool_tizhi[int(tz)] = (sid, sname)

print()
print('pool tizhi -> id / name')
for tz in sorted(pool_tizhi):
    print(f'  {tz:3d}  {pool_tizhi[tz][0]:28s} {pool_tizhi[tz][1]}')

# gather all tizhi literals used in effect/combat/attributes code
handled = set()
for fn in os.listdir('src/main/java/net/guzhenren/tianfu'):
    if not fn.endswith('.java'):
        continue
    txt = open('src/main/java/net/guzhenren/tianfu/' + fn, encoding='utf-8').read()
    for m in re.finditer(r'(?:tizhi|bodyTizhi)\s*==\s*(\d+)', txt):
        handled.add(int(m.group(1)))

print()
print('tizhi handled by effects/combat:', sorted(handled))
missing = sorted(set(pool_tizhi) - handled)
extra = sorted(handled - set(pool_tizhi))
print()
print('pool bodies with NO effect implementation:', missing)
print('  ->', [pool_tizhi[t][1] for t in missing])
print()
print('handled tizhi NOT in pool (dead code):', extra)
