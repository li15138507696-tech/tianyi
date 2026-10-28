"""一键构建（天意 · NeoForge 1.21.1 / Java 21 / 离线）。

    python _pack/build.py                # 常规：复用 _pack/libs 里的本体 jar
    python _pack/build.py --bump 2.9.3   # 先改版本再构建
    python _pack/build.py --ephemeral    # 本体 jar 只临时提取，构建完删掉（不占 106 MB）
    python _pack/build.py --no-tests     # 跳过离线行为测试（不推荐）

## 步骤（任一失败即停，退出码非 0）

1. **上游依赖**：`ensure_upstream.ensure()` 从 10.20 本体壳里提取真实本体 jar，
   校验 SHA256；缓存命中则跳过解压；
2. **编译**：`javac_check.py`（走 `TIANYI_UPSTREAM_JAR` 环境变量传依赖路径）；
3. **离线行为测试**：`run_tests.py`（账本 + 命契/推演/炼道/情债）；
4. **版本**：可选的 `bump_version.py`；
5. **打包**：`pack_jar.py`；
6. **验证**：`verify_jar.py` / `verify_version.py` / `check_bom.py` + 全部 audit_*。

这些步骤以前是手工一条条敲，容易漏掉某个审计；现在一条命令跑完，且**漏一步就算失败**。
"""
import glob
import os
import shutil
import subprocess
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, 'reconfigure'):
        _stream.reconfigure(encoding='utf-8', errors='replace')

PACK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(PACK)
RUN_KW = dict(capture_output=True, text=True, encoding='utf-8', errors='replace')

#: 打包前必须绿的审计（顺序即执行顺序）
AUDITS = [
    'verify_jar', 'verify_version', 'check_bom',
    'audit_mixins', 'audit_bestiary', 'audit_no_ai', 'audit_will', 'audit_sites',
]


def py(name):
    return os.path.join(PACK, name if name.endswith('.py') else name + '.py')


def run(script, env=None, allow_fail=False):
    print()
    print('=' * 68)
    print('>>', os.path.basename(script))
    r = subprocess.run([sys.executable, script], cwd=ROOT, env=env, **RUN_KW)
    out = (r.stdout or '') + (r.stderr or '')
    # 长输出只留尾部，避免刷屏
    lines = out.strip().splitlines()
    for line in lines[-14:]:
        print('   ', line)
    if r.returncode != 0 and not allow_fail:
        print(f'!! {os.path.basename(script)} 失败，退出码 {r.returncode}')
        return False
    return True


def main(argv):
    bump = None
    ephemeral = '--ephemeral' in argv
    skip_tests = '--no-tests' in argv
    if '--bump' in argv:
        i = argv.index('--bump')
        if i + 1 < len(argv):
            bump = argv[i + 1]
        else:
            print('用法: --bump <版本号>')
            return 2

    os.chdir(ROOT)

    # 1) 上游依赖
    print('=' * 68)
    print('>> ensure_upstream（从 10.20 本体壳提取真实本体 jar）')
    from ensure_upstream import ensure, EXPECTED_SHA256
    try:
        jar = ensure(ephemeral=ephemeral)
    except RuntimeError as e:
        print('!!', e)
        return 1
    print(f'   依赖: {jar}')
    print(f'   sha256: {EXPECTED_SHA256[:16]}…')

    env = dict(os.environ)
    env['TIANYI_UPSTREAM_JAR'] = jar
    env['PYTHONIOENCODING'] = 'utf-8'
    env['PYTHONUTF8'] = '1'

    try:
        # 2) 编译
        if not run(py('javac_check.py'), env=env):
            return 1
        # 3) 测试
        if not skip_tests and not run(py('run_tests.py'), env=env):
            return 1
        # 4) 版本
        if bump:
            script = py('bump_version.py')
            print()
            print('=' * 68)
            print('>> bump_version ->', bump)
            r = subprocess.run([sys.executable, script, bump], cwd=ROOT, env=env, **RUN_KW)
            print('   ', (r.stdout or '').strip().splitlines()[-1] if r.stdout else '')
            if r.returncode != 0:
                return 1
        # 5) 打包
        if not run(py('pack_jar.py'), env=env):
            return 1
        # 6) 审计
        failed = []
        for a in AUDITS:
            path = os.path.join(PACK, a + '.py')
            r = subprocess.run([sys.executable, path], cwd=ROOT, env=env, **RUN_KW)
            if r.returncode != 0:
                failed.append(a)
                print(f'!! 审计失败: {a}')
                for line in ((r.stdout or '') + (r.stderr or '')).strip().splitlines()[-6:]:
                    print('     ', line)
        print()
        print('=' * 68)
        if failed:
            print('审计未通过:', ', '.join(failed))
            return 1
        jars = sorted(glob.glob(os.path.join(ROOT, 'build', 'libs', 'tianyi-*.jar')),
                      key=os.path.getmtime)  # 按时间取最新：文件名排序会把 2.9 排到 2.10 后面
        newest = jars[-1] if jars else '(无)'
        print(f'构建完成，审计 {len(AUDITS)} 项全绿')
        print(f'产物: {newest}  {os.path.getsize(newest) if jars else 0:,} B')
        print('提示: 本脚本不会安装到玩家实例（按约定需手动确认后再装）')
        return 0
    finally:
        if ephemeral:
            shutil.rmtree(os.path.dirname(jar), ignore_errors=True)
            print('[upstream] 临时依赖已清理')


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
