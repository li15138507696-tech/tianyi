"""From repository root: python _pack/probes/run_finite_persistence.py.
Compiles only this slice and real launcher NBT dependencies; no downloads or game saves.
Does not modify build outputs or the verified 3.10.2 jar.
"""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '_pack'))
from javac_probe import build_cp, JAVAC

out = ROOT / '_pack' / 'probes' / 'finite-persistence-out'
out.mkdir(exist_ok=True)
cp = os.pathsep.join(build_cp()).replace('\\', '/')
base = ROOT / 'src/main/java/net/guzhenren/tianyi'
sources = [base / 'resource' / (name + '.java') for name in ('SiteInventory', 'FiniteSite', 'SiteCatalog', 'SiteTerrain')]
sources += [base / 'world/FiniteSiteState.java', ROOT / '_pack/probes/FinitePersistenceProbe.java',
            ROOT / '_pack/tests/net/guzhenren/tianyi/resource/SiteInventoryTest.java']
def quoted(path):
    return '"' + str(path).replace('\\', '/').replace('"', '\\"') + '"'
compile_args = out / 'compile-args.txt'
compile_args.write_text('-encoding\nUTF-8\n--release\n21\n-proc:none\n-d\n' + quoted(out)
                        + '\n-cp\n' + quoted(cp) + '\n' + '\n'.join(map(quoted, sources)) + '\n', encoding='utf-8')
subprocess.run([JAVAC, '@' + str(compile_args)], cwd=ROOT, check=True)
java = str(Path(JAVAC).with_name('java.exe' if os.name == 'nt' else 'java'))
runtime_cp = (str(out) + os.pathsep + cp).replace('\\', '/').replace(ROOT.as_posix() + '/', '')
for main in ('net.guzhenren.tianyi.resource.SiteInventoryTest', 'FinitePersistenceProbe'):
    run_args = out / 'run-args.txt'
    run_args.write_text('-Dfile.encoding=UTF-8\n-cp\n' + quoted(runtime_cp)
                        + '\n' + main + '\n', encoding='utf-8')
    subprocess.run([java, '@' + str(run_args)], cwd=ROOT, check=True)
