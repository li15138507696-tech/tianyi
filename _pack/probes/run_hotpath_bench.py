"""3.14.1 热路径微基准（纯 Java，不需要 Minecraft classpath）。

用法（仓库根目录）：

    python _pack/probes/run_hotpath_bench.py

它测的是**纯 Java 部分**的开销，用来给 `docs/3.14.1-性能与缺陷.md` 里的数字撑腰：

  1. 32 格球壳循环的骨架成本（65^3 = 274625 次位置访问 + 距离过滤）；
  2. `SitePlacement.matchesOwned` 改法前后：`pos(s).equals(p)` 每个槽位 new 一个位置对象
     + Stream 过滤  vs  纯 int 比较；
  3. `JournalNet.tick` 每 tick `Set.copyOf(PENDING)` 的分配；
  4. `SiteEvidence.remember` 原来的 `map.values().stream().mapToInt(Set::size).sum()`。

**测不到**（必须实机 / spark）：`level.getBlockState`、`level.getBlockEntity`、
`hasChunkAt`、网络包编解码、实体/区块表查询。这些只给静态估算，不给假数字。
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JDK21 = r'C:\Users\ADMIN\.jdks\temurin-21'
HERE = Path(__file__).resolve().parent
OUT = HERE / 'hotpath-out'
SRC = HERE / 'HotPathBench.java'


def tool(name):
    exe = name + ('.exe' if os.name == 'nt' else '')
    for home in (JDK21, os.environ.get('JAVA_HOME')):
        if home:
            p = Path(home) / 'bin' / exe
            if p.is_file():
                return str(p)
    return name


def main():
    if not SRC.is_file():
        print('FAIL 缺源文件', SRC)
        return 1
    if OUT.is_dir():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    print('javac', SRC.name)
    r = subprocess.run([tool('javac'), '-encoding', 'UTF-8', '--release', '21',
                        '-proc:none', '-d', str(OUT), str(SRC)],
                       cwd=str(ROOT), capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    if r.returncode != 0:
        print(r.stdout or '')
        print(r.stderr or '')
        print('FAIL 编译失败')
        return r.returncode

    rel = OUT.relative_to(ROOT).as_posix()
    r = subprocess.run([tool('java'), '-Dfile.encoding=UTF-8', '-cp', rel, 'HotPathBench'],
                       cwd=str(ROOT), capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    sys.stdout.write(r.stdout or '')
    sys.stderr.write(r.stderr or '')
    return r.returncode


if __name__ == '__main__':
    sys.exit(main())
