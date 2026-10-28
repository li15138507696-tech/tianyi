from pathlib import Path
import hashlib, json, shutil

AUDIT = Path(__file__).resolve().parent
PROJECTS = {'tianfu': Path(r'D:\daima\mcmod\蛊真人附属'),
            'daotu': Path(r'D:\daima\mcmod\道途附属'),
            'malice': Path(r'D:\daima\mcmod\蛊真人恶意')}
manifest = {}
for name, original in PROJECTS.items():
    stage = AUDIT / 'work' / name
    stage.mkdir(parents=True, exist_ok=True)
    for folder in ['src', 'docs']:
        if (original / folder).exists():
            shutil.copytree(original / folder, stage / folder, dirs_exist_ok=True)
    for file in ['version.properties', 'README.md']:
        if (original / file).exists():
            shutil.copy2(original / file, stage / file)
    files = {}
    for file in stage.rglob('*'):
        if file.is_file():
            relative = file.relative_to(stage).as_posix()
            files[relative] = hashlib.sha256(file.read_bytes()).hexdigest()
    manifest[name] = {'original': str(original), 'files': files}
    print(name, 'staged', len(files), 'files')

original = PROJECTS['tianfu']
stage = AUDIT / 'work/tianfu'
for relative in ['_tmp_verify/verify_selection_flow.py', '_tmp_verify/SelectionFlowCheck.java',
                 '_crash3/javac_out/args.txt']:
    target = stage / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(original / relative, target)
(AUDIT / 'baseline.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
