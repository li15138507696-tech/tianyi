"""宿命蛊世界账本的持久化探针（离线）。

从仓库根目录运行：

    python _pack/probes/run_destiny_custody.py

它只做两件事：
1. 用启动器 profile 里的**真实** NBT/SavedData 依赖编译这一片（账本、凭证、SavedData）；
2. 在同一 classpath 上跑纯逻辑测试与持久化探针。

不下载任何东西，不写世界存档，不改构建产物，也不碰已归档的旧 jar。
坑（沿用 run_finite_persistence.py 的经验）：工程路径含中文，运行期 classpath
必须改成相对路径，否则 java 在 Windows 控制台上会找不到类。
"""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '_pack'))
from javac_probe import build_cp, JAVAC

out = ROOT / '_pack' / 'probes' / 'destiny-custody-out'
out.mkdir(exist_ok=True)
cp = os.pathsep.join(build_cp()).replace('\\', '/')
base = ROOT / 'src/main/java/net/guzhenren/tianyi'
sources = [base / 'will' / 'DestinyCustody.java',
           base / 'will' / 'DestinyVessel.java',
           base / 'world' / 'DestinyCustodyState.java',
           ROOT / '_pack/probes/DestinyCustodyProbe.java',
           ROOT / '_pack/tests/net/guzhenren/tianyi/will/DestinyVesselTest.java']


def quoted(path):
    return '"' + str(path).replace('\\', '/').replace('"', '\\"') + '"'


compile_args = out / 'compile-args.txt'
compile_args.write_text('-encoding\nUTF-8\n--release\n21\n-proc:none\n-d\n' + quoted(out)
                        + '\n-cp\n' + quoted(cp) + '\n'
                        + '\n'.join(map(quoted, sources)) + '\n', encoding='utf-8')
subprocess.run([JAVAC, '@' + str(compile_args)], cwd=ROOT, check=True)

java = str(Path(JAVAC).with_name('java.exe' if os.name == 'nt' else 'java'))
runtime_cp = (str(out) + os.pathsep + cp).replace('\\', '/').replace(ROOT.as_posix() + '/', '')
for main in ('net.guzhenren.tianyi.will.DestinyVesselTest', 'DestinyCustodyProbe'):
    run_args = out / 'run-args.txt'
    run_args.write_text('-Dfile.encoding=UTF-8\n-cp\n' + quoted(runtime_cp)
                        + '\n' + main + '\n', encoding='utf-8')
    subprocess.run([java, '@' + str(run_args)], cwd=ROOT, check=True)

print()
print('宿命蛊账本探针通过：只证明 NBT 往返与失败关闭，物品组件/事件顺序仍需实机验证。')
