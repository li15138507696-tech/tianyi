"""有限资源点 3.11 服务层：纯决策的离线确定性测试。

用法（仓库根目录）：
    python _pack/probes/run_site_service.py

只编译 resource/ 与 journal/ 里的纯逻辑类 + 两个测试套件，
不需要 Minecraft / NeoForge classpath，也不碰 build/libs 里已验收的产物。

覆盖：预算（每 200 刻 4 候选 / 落地 1 点）、上限（8×8 区块 1 点、全局 64）、
未加载不计时、守护兽不补生、见闻去重与不泄露。

不覆盖：真实区块、真实容器、真实守护兽、事件顺序 —— 那些只能实机验证，
离线由 _pack/audit_sites.py 做静态接线审计。
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
JDK21 = r'C:\Users\ADMIN\.jdks\temurin-21'
BASE = ROOT / 'src/main/java/net/guzhenren/tianyi'
TESTS = ROOT / '_pack/tests/net/guzhenren/tianyi'
OUT = ROOT / '_pack/probes/site-service-out'

SOURCES = [
    BASE / 'resource' / 'FiniteSite.java',
    BASE / 'resource' / 'SiteInventory.java',
    BASE / 'resource' / 'SiteCatalog.java',
    BASE / 'resource' / 'SiteTerrain.java',
    BASE / 'resource' / 'SiteBudget.java',
    BASE / 'journal' / 'PlayerJournal.java',
    BASE / 'journal' / 'SiteNotice.java',
    TESTS / 'resource' / 'SiteBudgetTest.java',
    TESTS / 'journal' / 'SiteNoticeTest.java',
]
SUITES = [
    'net.guzhenren.tianyi.resource.SiteBudgetTest',
    'net.guzhenren.tianyi.journal.SiteNoticeTest',
]


def tool(name):
    exe = name + ('.exe' if os.name == 'nt' else '')
    for home in (JDK21, os.environ.get('JAVA_HOME')):
        if home:
            p = Path(home) / 'bin' / exe
            if p.is_file():
                return str(p)
    return name


def quoted(path):
    return '"' + str(path).replace('\\', '/').replace('"', '\\"') + '"'


def main():
    missing = [str(s) for s in SOURCES if not s.is_file()]
    if missing:
        print('FAIL 缺源文件：')
        for m in missing:
            print('  ' + m)
        return 1

    if OUT.is_dir():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    args = OUT / 'compile-args.txt'
    args.write_text('-encoding\nUTF-8\n--release\n21\n-proc:none\n-d\n' + quoted(OUT) + '\n'
                    + '\n'.join(quoted(s) for s in SOURCES) + '\n', encoding='utf-8')
    print('javac', len(SOURCES), '个源文件')
    r = subprocess.run([tool('javac'), '@' + str(args)], cwd=ROOT,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        print(r.stdout or '')
        print(r.stderr or '')
        print('FAIL 测试编译失败')
        return r.returncode

    # Windows 中文绝对路径放进 -cp 会让 java 找不到主类（实测 ClassNotFoundException），
    # 这里改用相对仓库根的路径，与 run_finite_persistence.py 的修法一致。
    rel_out = OUT.relative_to(ROOT).as_posix()
    failed = 0
    for suite in SUITES:
        print()
        print('==', suite)
        run = OUT / 'run-args.txt'
        run.write_text('-Dfile.encoding=UTF-8\n-Dstdout.encoding=UTF-8\n-Dstderr.encoding=UTF-8\n'
                       '-cp\n' + quoted(rel_out) + '\n' + suite + '\n', encoding='utf-8')
        r = subprocess.run([tool('java'), '@' + str(run)], cwd=ROOT,
                           capture_output=True, text=True, encoding='utf-8', errors='replace')
        sys.stdout.write(r.stdout or '')
        sys.stderr.write(r.stderr or '')
        if r.returncode != 0:
            failed += 1

    print()
    if failed:
        print(f'FAIL {failed} 个测试套件失败')
        return 1
    print('有限资源点纯决策测试全部通过（世界行为仍未实机验证）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
