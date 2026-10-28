"""Run from repository root: python _pack/probes/run_encounter_persistence.py.

Uses the launcher's exact existing full classpath; no downloads and no server.
Compile the three production classes explicitly, avoiding stale full-build output.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '_pack'))
from javac_probe import build_cp, JAVAC

out = ROOT / '_pack' / 'encounter_persistence_probe_out'
out.mkdir(exist_ok=True)
cp = ';'.join(build_cp() + [str(ROOT / '_pack' / 'javac_out')]).replace('\\', '/')
sources = [ROOT / 'src/main/java/net/guzhenren/tianyi/encounter/EncounterRun.java',
           ROOT / 'src/main/java/net/guzhenren/tianyi/world/EncounterSettings.java',
           ROOT / 'src/main/java/net/guzhenren/tianyi/world/EncounterState.java',
           ROOT / '_pack/probes/EncounterPersistenceProbe.java']
compile_args = out / 'compile-args.txt'
compile_args.write_text('-encoding\nUTF-8\n-proc:none\n-d\n"' + out.as_posix() + '"\n-cp\n"' + cp
                        + '"\n' + '\n'.join('"' + p.as_posix() + '"' for p in sources), encoding='utf-8')
subprocess.run([JAVAC, '@' + str(compile_args)], cwd=ROOT, check=True)
run_args = out / 'run-args.txt'
runtime_cp = cp.replace(ROOT.as_posix() + '/', '')
run_args.write_text('-Dfile.encoding=UTF-8\n-cp\n"' + out.relative_to(ROOT).as_posix() + ';' + runtime_cp
                    + '"\nEncounterPersistenceProbe\n', encoding='utf-8')
subprocess.run([str(Path(JAVAC).with_name('java.exe')), '@' + str(run_args)], cwd=ROOT, check=True)
