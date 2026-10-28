"""Compile and run the actual packet-cache handler with cached Minecraft/NeoForge APIs."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(r'C:/Users/ADMIN/.jdks/temurin-21/bin')
args = (ROOT / '_crash3/javac_out/args.txt').read_text(encoding='utf-8').splitlines()
cp = args[args.index('-cp') + 1].strip('"')
cp += ';' + str(ROOT / '_crash3/javac_out')
cp = cp.replace('\\', '/')
out = ROOT / '_tmp_verify/cache_classes'
out.mkdir(exist_ok=True)
sources = [ROOT / 'src/main/java/net/guzhenren/tianfu/v2/TianfuSelection.java',
           ROOT / 'src/main/java/net/guzhenren/tianfu/net/TianfuV2StatePayload.java',
           ROOT / 'src/main/java/net/guzhenren/tianfu/client/TianfuClientEvents.java',
           ROOT / '_tmp_verify/TianfuCacheCheck.java']
argfile = out / 'compile.txt'
argfile.write_text('-encoding\nUTF-8\n-proc:none\n-d\n"' + out.as_posix() +
                   '"\n-cp\n"' + cp + '"\n' +
                   '\n'.join('"' + p.as_posix() + '"' for p in sources), encoding='utf-8')
subprocess.run([str(JDK / 'javac.exe'), '@' + str(argfile)], check=True, cwd=ROOT)
subprocess.run([str(JDK / 'java.exe'), '-Dfile.encoding=UTF-8', '-cp', str(out) + ';' + cp,
                'TianfuCacheCheck'], check=True, cwd=ROOT)
