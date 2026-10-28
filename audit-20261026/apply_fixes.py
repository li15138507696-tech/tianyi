"""Plan/apply only audited project files, guarding original hashes and preserving backups."""
from pathlib import Path
import hashlib, json, shutil, sys, zipfile

AUDIT = Path(__file__).resolve().parent
BASELINE = json.loads((AUDIT / 'baseline.json').read_text(encoding='utf-8'))
JARS = {'tianfu': 'tianfu-1.3.5.jar', 'daotu': 'daotu-1.6.1.jar',
        'malice': 'guzhenren_malice-1.1.1-mc1020.jar'}
OLD_JARS = {'tianfu': 'tianfu-1.3.4.jar', 'daotu': 'daotu-1.6.0.jar',
            'malice': 'guzhenren_malice-1.1.0-mc1020.jar'}
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def plan():
    changes = []
    for name, info in BASELINE.items():
        root = Path(info['original'])
        stage = AUDIT / 'work' / name
        for source in stage.rglob('*'):
            if not source.is_file(): continue
            relative = source.relative_to(stage).as_posix()
            if not (relative.startswith(('src/', 'docs/')) or relative == 'version.properties'):
                continue
            sha = digest(source)
            old = info['files'].get(relative)
            if sha == old: continue
            target = root / relative
            changes.append({'project': name, 'relative': relative, 'source': str(source),
                            'target': str(target), 'before': old, 'after': sha})
        jar = AUDIT / 'release' / JARS[name]
        assert jar.is_file()
        with zipfile.ZipFile(root / 'build/libs' / OLD_JARS[name]) as old, zipfile.ZipFile(jar) as new:
            old_classes = {p for p in old.namelist() if p.endswith('.class')}
            new_classes = {p for p in new.namelist() if p.endswith('.class')}
            assert old_classes <= new_classes, ('missing previous classes', name, old_classes-new_classes)
            for relative in old.namelist():
                if relative.endswith(('.class', '/')) or relative in ('META-INF/neoforge.mods.toml','META-INF/MANIFEST.MF'):
                    continue
                assert old.read(relative) == new.read(relative), ('resource drift', name, relative)
        target = root / 'build/libs' / jar.name
        changes.append({'project': name, 'relative': 'build/libs/' + jar.name, 'source': str(jar),
                        'target': str(target), 'before': None, 'after': digest(jar)})
        print(name, 'resources preserved; classes:', len(new_classes))
    (AUDIT / 'apply-plan.json').write_text(json.dumps(changes, ensure_ascii=False, indent=2), encoding='utf-8')
    for name in BASELINE:
        print(name, 'files to synchronize:', sum(p['project'] == name for p in changes))

def apply():
    changes = json.loads((AUDIT / 'apply-plan.json').read_text(encoding='utf-8'))
    checks = json.loads((AUDIT / 'checks/results.json').read_text(encoding='utf-8'))
    assert len(checks) == 9 and all(p['exit'] == 0 for p in checks)
    # Validate every target and source before the first write.
    for change in changes:
        target, source = Path(change['target']), Path(change['source'])
        root = Path(BASELINE[change['project']]['original']).resolve()
        target.resolve().relative_to(root)
        source.resolve().relative_to(AUDIT.resolve())
        assert digest(source) == change['after'], ('staged file changed', source)
        if change['before'] is None:
            assert not target.exists() or digest(target) == change['after'], ('new destination exists', target)
        else:
            assert target.is_file() and digest(target) == change['before'], ('original changed since review', target)
    for change in changes:
        target = Path(change['target'])
        if change['before'] is not None:
            backup = AUDIT / 'backup' / change['project'] / change['relative']
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, backup)
            assert digest(backup) == change['before']
    for change in changes:
        target = Path(change['target'])
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(change['source'], target)
        assert digest(target) == change['after']
    (AUDIT / 'applied.json').write_text(json.dumps(changes, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Applied and hash-verified', len(changes), 'files; previous jars retained.')
    for name in BASELINE:
        print(Path(BASELINE[name]['original']) / 'build/libs' / JARS[name])

if __name__ == '__main__':
    {'plan': plan, 'apply': apply}[sys.argv[1]]()
