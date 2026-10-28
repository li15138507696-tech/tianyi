"""把编译/构建依赖切换到「用户指定的 10.20 本体包」内的真实本体 jar。

## 事实核对（先摆证据）

- `D:\\cxdownload\\guzhenrn蛊真人模组本体10.20.jar`
  → modId = **guzhenren_library**，1386 条目，**不含** `network/GuzhenrenModVariables`
  → 它是 **jarjar 壳**，内部含 7 个嵌套 jar；
- 其中 `META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar` 才是代码本体
  （36,880 条目，含 `GuzhenrenModVariables$PlayerVariables`）；
- 这份嵌套 jar 与工程原先编译用的
  `…\\_reverse\\nested\\guzhenren-0.1-neoforge-1.21.1.jar` **SHA256 相同** ——
  也就是说：之前编译用的就是 10.20 里的那一份，只是路径来源不明确。

## 本脚本做什么

1. 把嵌套本体提取到 `_pack/libs/`（工程内、可追溯）；
2. 校验 SHA256 与旧路径一致（不一致就停下，别偷偷换版本）；
3. 把 `_pack/javac_check.py` 与 `build.gradle` 的依赖路径指向这份新副本。
"""
import hashlib
import io
import os
import zipfile

ROOT = r'D:\daima\mcmod\天意'
SHELL = r'D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar'
NESTED = 'META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar'
OLD = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
DEST_DIR = os.path.join(ROOT, '_pack', 'libs')
DEST = os.path.join(DEST_DIR, 'guzhenren-0.1-neoforge-1.21.1.jar')


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


os.makedirs(DEST_DIR, exist_ok=True)
shell = zipfile.ZipFile(SHELL)
data = shell.read(NESTED)
with open(DEST, 'wb') as f:
    f.write(data)

new_hash = sha(DEST)
old_hash = sha(OLD) if os.path.isfile(OLD) else None
print('提取 ->', DEST, f'({len(data):,} bytes)')
print('SHA256 新:', new_hash)
print('SHA256 旧:', old_hash)
if old_hash and old_hash != new_hash:
    raise SystemExit('!! 哈希不一致：指定包里的本体与原先编译用的不是同一份，已停止')

# 改依赖路径
P = os.path.join(ROOT, '_pack', 'javac_check.py')
s = open(P, encoding='utf-8').read()
s = s.replace(
    "GUZHENREN = r'D:\\daima\\mcmod\\蛊真人附属\\_reverse\\nested\\guzhenren-0.1-neoforge-1.21.1.jar'",
    "# 编译依据：用户指定的 10.20 本体包内提取的真实本体 jar（见 _pack/libs/README.txt）\n"
    "GUZHENREN = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'libs',\n"
    "                         'guzhenren-0.1-neoforge-1.21.1.jar')")
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('javac_check.py 依赖路径已切换')

P = os.path.join(ROOT, 'build.gradle')
s = open(P, encoding='utf-8').read()
s = s.replace("compileOnly files('D:/daima/mcmod/蛊真人附属/_reverse/nested/guzhenren-0.1-neoforge-1.21.1.jar')",
              "compileOnly files('_pack/libs/guzhenren-0.1-neoforge-1.21.1.jar')")
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('build.gradle 依赖路径已切换')

with open(os.path.join(DEST_DIR, 'README.txt'), 'w', encoding='utf-8', newline='\n') as f:
    f.write('''编译期本体依赖（不要手改）

来源：D:\\cxdownload\\guzhenrn蛊真人模组本体10.20.jar
      → META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar（jarjar 嵌套）
SHA256：%s
说明：外层 10.20 包的 modId 是 guzhenren_library（壳），
      真正含 net.guzhenren.* 代码的是上面这份嵌套 jar。
      与原先 _reverse/nested 下那份逐字节相同，切换只是为了让"编译依据"可追溯。
''' % new_hash)
print('已写 _pack/libs/README.txt')
