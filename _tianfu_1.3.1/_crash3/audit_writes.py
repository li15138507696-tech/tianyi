import re, os

base = 'src/main/java/net/guzhenren/tianfu'
files = []
for root, dirs, names in os.walk(base):
    for n in names:
        if n.endswith('.java'):
            files.append(os.path.join(root, n))

print("=== 1. 直接写 v.qiyun（非 cap）的地方 ===")
for f in files:
    for i, line in enumerate(open(f, encoding='utf-8'), 1):
        if re.search(r'\bv\.qiyun\s*(=|\+=|-=|\*=)', line):
            print(f"  {f}:{i}: {line.strip()}")

print()
print("=== 2. 写 zuida_zhenyuan 的地方（应仅 TianfuNative 机缘） ===")
for f in files:
    for i, line in enumerate(open(f, encoding='utf-8'), 1):
        if re.search(r'zuida_zhenyuan\s*(=|\+=|-=|\*=)', line):
            print(f"  {os.path.basename(f)}:{i}: {line.strip()}")

print()
print("=== 3. LianGuJinDu 写入点 ===")
for f in files:
    for i, line in enumerate(open(f, encoding='utf-8'), 1):
        if 'LianGuJinDu' in line:
            print(f"  {os.path.basename(f)}:{i}: {line.strip()}")

print()
print("=== 4. 直接读 v.tizhi 的位置（绕过 active 闸门） ===")
for f in files:
    for i, line in enumerate(open(f, encoding='utf-8'), 1):
        if re.search(r'v\.tizhi\b', line) and 'v.tizhi =' not in line and 'v.tizhi +=' not in line:
            print(f"  {os.path.basename(f)}:{i}: {line.strip()}")
