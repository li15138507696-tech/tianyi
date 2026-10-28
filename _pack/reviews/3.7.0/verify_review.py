"""Read-only artifact audit; diagnostic outputs stay in this directory."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
JAR = ROOT / 'build/libs/tianyi-3.7.0.jar'
JDK = Path(r'C:/Users/ADMIN/.jdks/temurin-21/bin')

def run(args, log):
    result = subprocess.run([str(a) for a in args], cwd=ROOT, capture_output=True,
                            encoding='utf-8', errors='replace')
    (HERE / log).write_text(result.stdout + result.stderr, encoding='utf-8')
    return result

before = hashlib.sha256(JAR.read_bytes()).hexdigest()
script = ROOT / '_pack/javac_check.py'
source = script.read_text(encoding='utf-8')
old = "OUT = os.path.join(ROOT, '_pack', 'javac_out')"
assert source.count(old) == 1
source = source.replace(old, "OUT = os.path.join(ROOT, '_pack', 'reviews', '3.7.0', 'compiled')")
result = run([sys.executable, '-c',
              "__file__=" + repr(str(script)) + "\n" + source], 'compile.log')
print('fresh_compile_exit:', result.returncode)
if result.returncode:
    print((result.stdout + result.stderr)[-5000:])
    sys.exit(result.returncode)

out = HERE / 'compiled'
with zipfile.ZipFile(JAR) as z:
    packaged = {n for n in z.namelist() if n.endswith('.class')}
    compiled = {p.relative_to(out).as_posix(): p for p in out.rglob('*.class')}
    changed = [n for n in sorted(packaged & compiled.keys()) if z.read(n) != compiled[n].read_bytes()]
    only_jar = sorted(packaged - compiled.keys())
    only_source = sorted(compiled.keys() - packaged)
    comparison = dict(classes_in_jar=len(packaged), fresh_classes=len(compiled),
                      changed=changed, only_jar=only_jar, only_source=only_source)
    print('artifact_comparison:', json.dumps(comparison, ensure_ascii=False))

args = (out / 'args.txt').read_text(encoding='utf-8').splitlines()
cp = args[args.index('-cp') + 1]
for cls, name in [
    ('net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent$Post', 'pickup_post.javap.txt'),
    ('net.minecraft.world.entity.item.ItemEntity', 'item_entity.javap.txt'),
    ('com.guzhenrenlibrary.npc.life.ResidentService', 'resident_service.javap.txt'),
    ('com.guzhenrenlibrary.npc.NpcStock', 'npc_stock.javap.txt'),
    ('cpw.mods.jarhandling.impl.Jar', 'secure_jar.javap.txt'),
    ('cpw.mods.jarhandling.JarContents', 'jar_contents.javap.txt'),
    ('cpw.mods.jarhandling.impl.JarContentsImpl', 'jar_contents_impl.javap.txt'),
]:
    r = run([JDK / 'javap.exe', '-c', '-p', '-classpath', cp, cls], name)
    print('bytecode:', cls, 'exit:', r.returncode)

testout = HERE / 'repro_classes'
testout.mkdir(exist_ok=True)
r = run([JDK / 'javac.exe', '-encoding', 'UTF-8', '-proc:none', '-cp', JAR,
         '-d', testout, HERE / 'ReviewRepro.java'], 'repro_compile.log')
jar_classpath_compile_exit = r.returncode
if r.returncode:
    print('jar_classpath_compile_exit:', r.returncode, '(see repro_compile.log)')
    # Do not rewrite the artifact. Extract only its class bytes for pure-logic reproductions.
packaged_out = HERE / 'packaged_classes'
with zipfile.ZipFile(JAR) as z:
    for entry in packaged:
        target = packaged_out / entry
        assert target.resolve().is_relative_to(packaged_out.resolve())
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(z.read(entry))
    (HERE / 'manifest_bytes.txt').write_text(repr(z.read('META-INF/MANIFEST.MF')), encoding='utf-8')
r = run([JDK / 'javac.exe', '-encoding', 'UTF-8', '-proc:none', '-cp', packaged_out,
         '-d', testout, HERE / 'ReviewRepro.java', HERE / 'ManifestProbe.java'], 'repro_extracted_compile.log')
if r.returncode:
    print(r.stdout + r.stderr)
    sys.exit(r.returncode)
r = run([JDK / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', testout, 'ManifestProbe', JAR], 'manifest_probe.log')
print('manifest_probe:', r.stdout + r.stderr)
r = run([JDK / 'java.exe', '-Dfile.encoding=UTF-8', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
         '-cp', str(testout) + ';' + cp, 'ManifestProbe', JAR, 'neoforge'], 'manifest_neoforge.log')
print('neoforge_manifest_probe:', r.stdout + r.stderr)
r = run([JDK / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', str(packaged_out) + ';' + str(testout), 'ReviewRepro'], 'repro.log')
print(r.stdout + r.stderr)
repro_exit = r.returncode
r = run([JDK / 'javac.exe', '-encoding', 'UTF-8', '-proc:none', '-cp', str(packaged_out) + ';' + cp,
         '-d', testout, HERE / 'StateRoundtripRepro.java'], 'state_compile.log')
if r.returncode == 0:
    r = run([JDK / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', str(packaged_out) + ';' + str(testout) + ';' + cp,
             'StateRoundtripRepro'], 'state_roundtrip.log')
print('state_roundtrip_exit:', r.returncode)
print(r.stdout + r.stderr)
state_exit = r.returncode
after = hashlib.sha256(JAR.read_bytes()).hexdigest()
assert before == after, 'Artifact changed during audit'
(HERE / 'verification.json').write_text(json.dumps({
    'artifact': str(JAR), 'sha256': before, 'artifact_unchanged': before == after,
    'compile_exit': result.returncode, 'comparison': comparison,
    'jar_classpath_compile_exit': jar_classpath_compile_exit, 'repro_exit': repro_exit,
    'state_roundtrip_exit': state_exit,
}, ensure_ascii=False, indent=2), encoding='utf-8')
sys.exit(repro_exit or state_exit)
