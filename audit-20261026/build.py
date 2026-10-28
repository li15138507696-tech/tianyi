"""Build isolated audited sources against local game APIs, preserving the requested jar resources."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, sys, tomllib, zipfile

AUDIT = Path(__file__).resolve().parent
JDK = Path(r'C:/Users/ADMIN/.jdks/temurin-21/bin')
PROJECTS = {
    'tianfu': (Path(r'D:/daima/mcmod/蛊真人附属'), 'tianfu-1.3.4.jar', '1.3.5', '_crash3'),
    'daotu': (Path(r'D:/daima/mcmod/道途附属'), 'daotu-1.6.0.jar', '1.6.1', '_crash3'),
    'malice': (Path(r'D:/daima/mcmod/蛊真人恶意'), 'guzhenren_malice-1.1.0-mc1020.jar', '1.1.1', '_build'),
}

def classpath(name):
    original, _, _, folder = PROJECTS[name]
    argsfile = original / folder / 'javac_out/args.txt'
    if name != 'malice' and argsfile.exists():
        args = argsfile.read_text(encoding='utf-8').splitlines()
        cp = args[args.index('-cp') + 1].strip('"').replace('\\', '/')
        paths = cp.split(';')
    else:
        paths = classpath('tianfu').split(';')
        if name != 'malice':
            raise RuntimeError('Missing original build classpath: ' + str(argsfile))
        mods = Path(r'D:/game/mcmod/蛊2/.minecraft/versions/转生到蛊界就要拿出真本事/mods')
        paths += [str(p) for p in mods.glob('*.jar')
                  if any(key in p.name.lower() for key in ['l2hostility', 'l2library'])]
        paths += [str(p) for p in (original / '_build/libs_nested').glob('l2core*.jar')]
    if name == 'tianfu':
        paths += [str(p) for p in (original / 'libs').glob('veil*.jar')]
    missing = [p for p in paths if not Path(p).is_file()]
    if missing:
        raise RuntimeError('Missing dependencies: ' + repr(missing))
    return ';'.join(dict.fromkeys(paths))

def compile_project(name):
    stage = AUDIT / 'work' / name
    out = stage / 'classes'
    out.mkdir(exist_ok=True)
    # Only this isolated, fixed directory's generated classes may be removed.
    for file in out.rglob('*.class'):
        file.unlink()
    sources = sorted((stage / 'src/main/java').rglob('*.java'))
    receipt = stage / 'compile-success.json'
    if receipt.exists():
        receipt.unlink()
    cp = classpath(name)
    def q(p): return '"' + str(p).replace('\\', '/') + '"'
    args = stage / 'javac-args.txt'
    args.write_text('-encoding\nUTF-8\n-proc:none\n-source\n21\n-target\n21\n-d\n' + q(out)
                    + '\n-cp\n' + q(cp) + '\n' + '\n'.join(q(p) for p in sources), encoding='utf-8')
    result = subprocess.run([str(JDK / 'javac.exe'), '@' + str(args)], capture_output=True)
    log = (result.stdout + result.stderr).decode('gb18030', errors='replace')
    (stage / 'compile.log').write_text(log, encoding='utf-8')
    print(name, 'sources:', len(sources), 'javac exit:', result.returncode)
    if result.returncode:
        print(log[-12000:])
        raise SystemExit(result.returncode)
    receipt.write_text(json.dumps({p.relative_to(stage).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                                   for p in sources}, indent=2), encoding='utf-8')
    print('classes:', len(list(out.rglob('*.class'))))

def pack_project(name):
    original, oldjar, version, _ = PROJECTS[name]
    stage = AUDIT / 'work' / name
    expected = json.loads((stage / 'compile-success.json').read_text(encoding='utf-8'))
    current = {p.relative_to(stage).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in (stage / 'src/main/java').rglob('*.java')}
    assert current == expected, 'Sources changed since the successful compile; rebuild first'
    props = (stage / 'version.properties').read_text(encoding='utf-8')
    assert re.search(r'^version=' + re.escape(version) + '$', props, re.M)
    prefix = 'guzhenren_malice' if name == 'malice' else name
    suffix = '-mc1020' if name == 'malice' else ''
    output = AUDIT / 'release' / f'{prefix}-{version}{suffix}.jar'
    output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(original / 'build/libs' / oldjar) as baseline:
        # Preserve the exact MC10.20 tags and every existing asset from the user-selected build.
        resources = {p: baseline.read(p) for p in baseline.namelist()
                     if not p.endswith('.class') and not p.endswith('/')}
    toml_path = 'META-INF/neoforge.mods.toml'
    original_meta = tomllib.loads(resources[toml_path].decode('utf-8-sig'))
    toml = resources[toml_path].decode('utf-8-sig')
    toml = re.sub(r'^(version\s*=\s*")[^"]+(".*)$', lambda m: m[1] + version + m[2], toml, count=1, flags=re.M)
    resources[toml_path] = toml.encode('utf-8')
    # The source TOML may use variant placeholders; keep it as the source template.
    assert original_meta['mods'][0]['modId'] == tomllib.loads(toml)['mods'][0]['modId']
    manifest = resources.get('META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\r\n\r\n').decode('utf-8')
    if 'Implementation-Version:' in manifest:
        manifest = re.sub(r'Implementation-Version: [^\r\n]+', 'Implementation-Version: ' + version, manifest)
    else:
        manifest = manifest.rstrip('\r\n') + '\r\nImplementation-Version: ' + version + '\r\n\r\n'
    resources['META-INF/MANIFEST.MF'] = manifest.encode('utf-8')
    classes = {p.relative_to(stage / 'classes').as_posix(): p.read_bytes()
               for p in (stage / 'classes').rglob('*.class')}
    assert classes
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as jar:
        for path, data in sorted((resources | classes).items()):
            jar.writestr(path, data)
    with zipfile.ZipFile(output) as jar:
        assert jar.testzip() is None
        meta = jar.read(toml_path)
        assert not meta.startswith(b'\xef\xbb\xbf')
        assert tomllib.loads(meta.decode('utf-8'))['mods'][0]['version'] == version
        assert len(jar.namelist()) == len(set(jar.namelist()))
        for path, data in classes.items():
            assert data[:4] == b'\xca\xfe\xba\xbe'
            assert int.from_bytes(data[6:8], 'big') == 65, path
        for path in jar.namelist():
            if path.endswith('.mixins.json'):
                mixins = json.loads(jar.read(path))
                for section in ['mixins', 'client', 'server']:
                    for cls in mixins.get(section, []):
                        target = (mixins['package'] + '.' + cls).replace('.', '/') + '.class'
                        assert target in classes, target
            elif path.endswith('.json'):
                json.loads(jar.read(path))
        if name == 'malice':
            assert jar.read('malice_variant.txt').strip() == b'1020'
    print(output.name, len(classes), 'classes; jar/metadata/JSON/mixins/Java21: PASS')
    print('SHA256', hashlib.sha256(output.read_bytes()).hexdigest())

if __name__ == '__main__':
    action = sys.argv[1]
    for name in sys.argv[2:] or PROJECTS:
        {'compile': compile_project, 'pack': pack_project}[action](name)
