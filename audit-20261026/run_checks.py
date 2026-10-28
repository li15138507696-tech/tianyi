from pathlib import Path
import hashlib, json, shutil, subprocess, sys

AUDIT = Path(__file__).resolve().parent
baseline = json.loads((AUDIT / 'baseline.json').read_text(encoding='utf-8'))
for name, entry in baseline.items():
    frozen = AUDIT / 'baseline-src' / name / 'src'
    if not frozen.exists():
        original = Path(entry['original'])
        for relative, sha in entry['files'].items():
            if relative.startswith('src/'):
                assert hashlib.sha256((original / relative).read_bytes()).hexdigest() == sha
        shutil.copytree(original / 'src', frozen)

replacements = {
    'tianfu-runtime/regression.py': ("ORIGINAL = Path(r'D:\\daima\\mcmod\\蛊真人附属\\src\\main\\java')", "ORIGINAL = HERE.parent / 'baseline-src/tianfu/src/main/java'"),
    'daotu/regression.py': ("ORIGINAL = Path(r'D:\\daima\\mcmod\\道途附属')", "ORIGINAL = HERE.parent / 'baseline-src/daotu'"),
}
for relative, (old, new) in replacements.items():
    path = AUDIT / relative
    text = path.read_text(encoding='utf-8')
    assert old in text or new in text, relative
    path.write_text(text.replace(old, new), encoding='utf-8')

cases = [
    ('tianfu-origin', 'work/tianfu/_tmp_verify/verify_selection_flow.py', ['origin']),
    ('tianfu-legacy-origin', 'work/tianfu/_tmp_verify/verify_selection_flow.py', ['legacy']),
    ('tianfu-origin-gates', 'work/tianfu/_tmp_verify/verify_selection_flow.py', ['gate']),
    ('tianfu-roll', 'work/tianfu/_tmp_verify/verify_selection_flow.py', ['roll']),
    ('tianfu-transactions', 'work/tianfu/_tmp_verify/verify_selection_flow.py', []),
    ('tianfu-runtime', 'tianfu-runtime/regression.py', []),
    ('tianfu-lifecycle', 'tianfu-runtime/clone_regression.py', []),
    ('daotu', 'daotu/regression.py', ['--fixed']),
    ('malice', 'malice/regression.py', ['--source', str(AUDIT / 'work/malice/src/main/java/net/guzhenren/malice/MaliceEvents.java')]),
]
logs = AUDIT / 'checks'
logs.mkdir(exist_ok=True)
results = []
for name, script, arguments in cases:
    result = subprocess.run([sys.executable, str(AUDIT / script), *arguments], capture_output=True)
    output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
    (logs / (name + '.log')).write_text(output, encoding='utf-8')
    results.append({'name': name, 'exit': result.returncode})
    print(name, 'PASS' if result.returncode == 0 else 'FAIL', flush=True)
    if result.returncode:
        print(output[-5000:])
        raise SystemExit(result.returncode)
(logs / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
print('All nine focused checks passed.')
