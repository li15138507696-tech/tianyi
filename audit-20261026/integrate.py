from pathlib import Path
import hashlib, json, shutil

AUDIT = Path(__file__).resolve().parent
baseline = json.loads((AUDIT / 'baseline.json').read_text(encoding='utf-8'))
for name, overlay in [('daotu', AUDIT / 'daotu/patched-src'),
                      ('tianfu', AUDIT / 'tianfu-runtime/patched-src')]:
    files = list(overlay.rglob('*.java'))
    for source in files:
        relative = source.relative_to(overlay)
        if relative.parts[0] != 'src':
            relative = Path('src/main/java') / relative
        target = AUDIT / 'work' / name / relative
        assert relative.as_posix() in baseline[name]['files'], ('unknown source', relative)
        shutil.copy2(source, target)
    print(name, 'integrated', len(files), 'files')
source = AUDIT / 'malice/staged-src/net/guzhenren/malice/MaliceEvents.java'
if not source.exists():
    candidates = list((AUDIT / 'malice/staged-src').rglob('MaliceEvents.java'))
    assert len(candidates) == 1
    source = candidates[0]
target = AUDIT / 'work/malice/src/main/java/net/guzhenren/malice/MaliceEvents.java'
shutil.copy2(source, target)
print('malice integrated one file')
