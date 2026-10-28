from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
TARGET = Path(r'D:\daima\mcmod\道途附属\build\libs\daotu-1.6.0.jar')
NEOFORGE = Path(r'D:\game\mcmod\1\.minecraft\libraries\net\neoforged\neoforge\21.1.249\neoforge-21.1.249-client.jar')
JAVAP = Path(r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe')
OUT = HERE / 'evidence'
OUT.mkdir(exist_ok=True)
classes = ['DaotuOpening', 'DaotuLiLian', 'DaotuDraw', 'DaotuMigrate', 'DaotuMod',
           'chronicle/NbtChronicleRepository', 'chronicle/ChronicleCoordinator',
           'chronicle/FactionJournalModule', 'chronicle/MeritArchiveModule',
           'chronicle/ReputationJournalModule']
manifest = {'target': str(TARGET), 'sha256': hashlib.sha256(TARGET.read_bytes()).hexdigest(), 'classes': {}}
with zipfile.ZipFile(TARGET) as jar:
    for short in classes:
        name = 'net/guzhenren/daotu/' + short
        manifest['classes'][name + '.class'] = hashlib.sha256(jar.read(name + '.class')).hexdigest()
        run = subprocess.run([str(JAVAP), '-p', '-c', '-classpath', str(TARGET), name.replace('/', '.')],
                             capture_output=True, encoding='utf-8', errors='replace', check=True)
        (OUT / (short.replace('/', '_') + '.javap.txt')).write_text(run.stdout, encoding='utf-8')
run = subprocess.run([str(JAVAP), '-p', '-c', '-classpath', str(NEOFORGE), 'net.minecraft.server.level.ServerPlayer'],
                     capture_output=True, encoding='utf-8', errors='replace', check=True)
start = run.stdout.index('  public void restoreFrom(')
end = run.stdout.index('\n  protected ', start)
(OUT / 'NeoForge-21.1.249-ServerPlayer-restoreFrom.javap.txt').write_text(run.stdout[start:end], encoding='utf-8')
(OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print('Saved evidence for', manifest['sha256'])
