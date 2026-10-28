"""Extract every description string from the talent/pool tables and flag anomalies."""
import re, unicodedata, collections

BASE = r'D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu'
files = [BASE + r'\TianfuTalents.java', BASE + r'\TianfuPool.java']

# string literal regex (java, no escapes needed for our content)
LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')

rows = []
for path in files:
    src = open(path, encoding='utf-8').read()
    for m in LIT.finditer(src):
        s = m.group(1)
        # descs are the long-ish chinese strings containing effect keywords
        if any(k in s for k in ('+', '−', '%', '×', '−')) and len(s) > 4:
            rows.append((path.split('\\')[-1], s))

print('candidate descs:', len(rows))
print()

# char inventory
chars = collections.Counter()
for _, s in rows:
    for ch in s:
        chars[ch] += 1

print('=== 非 ASCII 字符清单 ===')
for ch, n in sorted(chars.items()):
    if ord(ch) > 127:
        try:
            name = unicodedata.name(ch)
        except ValueError:
            name = '<no name>'
        print(f'  U+{ord(ch):04X} {ch!r} x{n:<4} {name}')

print()
print('=== 可疑字符（控制符 / 非 BMP / 私用区 / 全角） ===')
bad = 0
for ch, n in sorted(chars.items()):
    o = ord(ch)
    if o < 32 or (0xE000 <= o <= 0xF8FF) or o > 0xFFFF or (0xFF00 <= o <= 0xFFEF):
        print(f'  U+{o:04X} {ch!r} x{n}')
        bad += 1
print('  (none)' if not bad else f'  {bad} suspicious')

print()
print('=== 空 / 极短描述 ===')
for f, s in rows:
    if len(s) <= 4:
        print(f'  {f}: {s!r}')

print()
print('=== 超长描述（>40 字符，可能被 fit() 截断） ===')
for f, s in rows:
    if len(s) > 40:
        print(f'  [{len(s)}] {f}: {s}')
