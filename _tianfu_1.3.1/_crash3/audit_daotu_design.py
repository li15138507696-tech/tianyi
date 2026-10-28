"""Audit the design doc: every field name it claims exists must exist in PlayerVariables."""
import re
import sys
import zipfile

JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
DOC = r'D:\daima\mcmod\蛊真人附属\docs\2026-10-21-daotu-design.md'
sys.stdout.reconfigure(encoding='utf-8')

z = zipfile.ZipFile(JAR)
pv = z.read('net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.class')

# Collect declared fields via descriptor+name adjacency is unreliable; use javap-equivalent:
# simpler -> every Utf8 that matches a plausible field name and appears with a descriptor
names = set(m.decode() for m in re.findall(rb'[A-Za-z_][A-Za-z0-9_]{2,40}', pv))

# Fields referenced by the doc in `code spans`
doc = open(DOC, encoding='utf-8').read()
code_spans = re.findall(r'`([A-Za-z_][A-Za-z0-9_]{2,40})`', doc)
candidates = sorted({c for c in code_spans if c not in ('PersistentData', 'AttributeModifier',
                                                       'GuiGraphics', 'Screen', 'ModList',
                                                       'Holder', 'getHolder', 'getDuration',
                                                       'addEffect', 'removeEffect', 'getEffect',
                                                       'markSyncDirty', 'putString', 'getString',
                                                       'putfield', 'ifge', 'dcmpg', 'dcmpl',
                                                       'ifne', 'javac', 'zipfile', 'mods',
                                                       'static', 'final', 'String', 'Int', 'Long',
                                                       'CompoundTag', 'LivingDamageEvent',
                                                       'PlayerTickEvent', 'ZENGFU', 'ZENGSHANG',
                                                       'DeferredHolder', 'isLoaded')})

missing = [c for c in candidates if c.encode() not in pv]
present = [c for c in candidates if c.encode() in pv]

print('=== doc field names PRESENT in PlayerVariables (%d) ===' % len(present))
for c in present:
    print('   ok  ', c)

print()
print('=== doc field names NOT in PlayerVariables (%d) ===' % len(missing))
for c in missing:
    print('   ??  ', c)

# liupai coverage check against the doc's 6-category table
liupai = sorted(set(m.decode() for m in re.findall(rb'liupai_[a-z0-9]+|Liupai_[a-z0-9]+', pv)))
in_doc = set(re.findall(r'`?(liupai_[a-z0-9]+|Liupai_gudao)`?', doc))
print()
print('=== liupai coverage ===')
print('  PlayerVariables has', len(liupai))
print('  doc mentions      ', len(in_doc))
print('  doc mentions but NOT in PV:', sorted(in_doc - set(liupai)))
print('  PV has but doc omits      :', sorted(set(liupai) - in_doc))
