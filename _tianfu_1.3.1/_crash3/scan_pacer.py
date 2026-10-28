import zipfile, struct

jar = '_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar'
z = zipfile.ZipFile(jar)
blob = {i.filename: z.read(i) for i in z.infolist() if i.filename.endswith('.class')}
z.close()

# Confirm the pacer pattern: lg_ke/20 >= 1.5  ->  +1 progress every 30 ticks
D20 = struct.pack('>d', 20.0)
D15 = struct.pack('>d', 1.5)
D30 = struct.pack('>d', 30.0)

n_pacer = 0
n_pacer30 = 0
for n, d in blob.items():
    if b'PlayerVariables.lg_ke' in d and D20 in d and D15 in d:
        n_pacer += 1
        if D30 in d:
            n_pacer30 += 1
print('classes with lg_ke + 20.0 + 1.5 (pacer):', n_pacer)
print('  ...that also contain 30.0:', n_pacer30)

# MenuAccessor existence (for GUI-open gate)
print()
print('MenuAccessor present:', any('GuzhenrenModMenus$MenuAccessor' in n for n in blob))
for n in blob:
    if 'GuzhenrenModMenus$MenuAccessor' in n:
        print('  ', n)
