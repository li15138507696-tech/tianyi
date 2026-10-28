"""确保编译期本体依赖就绪（从用户指定的 10.20 包里提取真实本体 jar）。

## 背景

`guzhenrn蛊真人模组本体10.20.jar` 是 **jarjar 壳**（modId `guzhenren_library`，1386 条目），
不含 `net.guzhenren.*` 代码。真正能编译的是它内部那份：

    META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar   （36,880 条目，modId guzhenren）

## 行为

- **幂等**：目标文件已存在且 SHA256 正确 → 直接复用，不重复解压 106 MB；
- **可追溯**：写出 `_pack/libs/README.txt`（来源路径 + 哈希）；
- **可切换**：外层包路径可用环境变量 `TIANYI_UPSTREAM_SHELL` 覆盖；
- **临时模式**：`ephemeral=True` 时解压到临时目录，构建后由调用方删除（不占工程体积）。

对外只暴露 {@link #ensure}，返回可用的 jar 绝对路径；失败抛出 RuntimeError，
调用方（`_pack/build.py`）据此退出，绝不"凑合编译"。
"""
import hashlib
import os
import tempfile
import zipfile

PACK_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(PACK_DIR)
NESTED = 'META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar'
JAR_NAME = 'guzhenren-0.1-neoforge-1.21.1.jar'

#: 已知正确哈希（2026-10 核对；与旧 _reverse/nested 那份逐字节相同）
EXPECTED_SHA256 = 'b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc'

DEFAULT_SHELLS = [
    os.environ.get('TIANYI_UPSTREAM_SHELL', ''),
    r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar',
]


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def _find_shell():
    for p in DEFAULT_SHELLS:
        if p and os.path.isfile(p):
            return p
    return None


def _extract(shell, dest):
    with zipfile.ZipFile(shell) as z:
        if NESTED not in z.namelist():
            raise RuntimeError(f'{shell} 内没有 {NESTED} —— 这个包可能不是 10.20 本体壳')
        data = z.read(NESTED)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(data)
    os.replace(tmp, dest)
    return len(data)


def _write_readme(jar_path, shell, digest):
    readme = os.path.join(PACK_DIR, 'libs', 'README.txt')
    os.makedirs(os.path.dirname(readme), exist_ok=True)
    with open(readme, 'w', encoding='utf-8', newline='\n') as f:
        f.write(f'''编译期本体依赖（自动维护，不要手改）

来源：{shell}
      → {NESTED}（jarjar 嵌套）
SHA256：{digest}
说明：外层 10.20 包的 modId 是 guzhenren_library（壳），
      真正含 net.guzhenren.* 代码的是上面这份嵌套 jar。
      本文件由 `_pack/ensure_upstream.py` 生成，`_pack/build.py` 每次构建都会校验。
''')


def ensure(ephemeral=False, verbose=True):
    """返回可用的本体 jar 路径（不存在则从 10.20 包提取）。"""
    if ephemeral:
        shell = _find_shell()
        if not shell:
            raise RuntimeError('找不到 10.20 本体包：请设置 TIANYI_UPSTREAM_SHELL 或放回默认路径')
        tmpdir = tempfile.mkdtemp(prefix='tianyi-upstream-')
        dest = os.path.join(tmpdir, JAR_NAME)
        size = _extract(shell, dest)
        digest = sha256(dest)
        if digest != EXPECTED_SHA256:
            raise RuntimeError(f'提取出的本体哈希不符：{digest} != {EXPECTED_SHA256}')
        if verbose:
            print(f'[upstream] 临时提取 {size:,} B（ephemeral，构建后删除） sha256={digest[:12]}…')
        return dest

    dest = os.path.join(PACK_DIR, 'libs', JAR_NAME)
    if os.path.isfile(dest):
        digest = sha256(dest)
        if digest == EXPECTED_SHA256:
            if verbose:
                print(f'[upstream] 复用缓存 {dest}（sha256 校验通过）')
            return dest
        if verbose:
            print(f'[upstream] 缓存哈希不符（{digest[:12]}…），重新提取')

    shell = _find_shell()
    if not shell:
        raise RuntimeError(
            '找不到 10.20 本体包，且缓存不可用。\n'
            '  请把 guzhenrn蛊真人模组本体10.20.jar 放回 D:\\cxdownload\\，'
            '或用环境变量 TIANYI_UPSTREAM_SHELL 指定路径。')
    size = _extract(shell, dest)
    digest = sha256(dest)
    if digest != EXPECTED_SHA256:
        raise RuntimeError(f'提取出的本体哈希不符：{digest} != {EXPECTED_SHA256}')
    _write_readme(dest, shell, digest)
    if verbose:
        print(f'[upstream] 已提取 {size:,} B → {dest} sha256={digest[:12]}…')
    return dest


if __name__ == '__main__':
    print(ensure())
