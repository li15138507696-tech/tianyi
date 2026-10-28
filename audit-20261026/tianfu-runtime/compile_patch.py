"""Compile patched classes against the existing runtime and original 1.3.4 jar; local output only."""
from pathlib import Path
import subprocess

HERE=Path(__file__).resolve().parent
ORIGINAL=Path(r'D:\daima\mcmod\蛊真人附属')
args=(ORIGINAL/'_crash3/javac_out/args.txt').read_text(encoding='utf-8').splitlines()
cp=args[args.index('-cp')+1]+';'+str(ORIGINAL/'build/libs/tianfu-1.3.4.jar')
out=HERE/'compiled'
out.mkdir(exist_ok=True)
empty=HERE/'empty-sourcepath'
empty.mkdir(exist_ok=True)
sources=sorted((HERE/'patched-src').rglob('*.java'))
argfile=HERE/'compile-args.txt'
argfile.write_text('\n'.join(['-encoding','UTF-8','-proc:none','-implicit:none','-sourcepath','"'+empty.as_posix()+'"','-d','"'+out.as_posix()+'"','-cp','"'+cp.replace('\\','/')+'"']+['"'+p.as_posix()+'"' for p in sources]),encoding='utf-8')
r=subprocess.run([r'C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe','@'+str(argfile)],capture_output=True,text=True,encoding='utf-8',errors='replace')
msg=f'Patched source files: {len(sources)}\nJavac exit: {r.returncode}\n'+r.stdout+r.stderr
(HERE/'compile-result.txt').write_text(msg,encoding='utf-8')
print(msg)
raise SystemExit(r.returncode)
