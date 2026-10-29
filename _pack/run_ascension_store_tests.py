"""Compile and run ascension tests that need the real Minecraft 1.21.1 NBT classes."""
import glob
import os
import shlex
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "_pack", "javac_out")
ARGFILE = os.path.join(OUT, "args.txt")
TEST_SOURCES = sorted(glob.glob(os.path.join(
    ROOT, "_pack", "ascension-testsrc", "**", "*.java"), recursive=True))
JDK21 = r"C:\Users\ADMIN\.jdks\temurin-21\bin"
JAVAC = os.environ.get("TIANYI_JAVAC", os.path.join(JDK21, "javac.exe" if os.name == "nt" else "javac"))
JAVA = os.environ.get("TIANYI_JAVA", os.path.join(JDK21, "java.exe" if os.name == "nt" else "java"))
SUITES = (
    "net.guzhenren.tianyi.world.AscensionStoreNbtTest",
    "net.guzhenren.tianyi.world.GuEscrowServiceTest",
    "net.guzhenren.tianyi.world.PlayerInventoryGuEscrowSourceTest",
)


def run(command):
    result = subprocess.run(command, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", cwd=ROOT)
    output = (result.stdout or "") + (result.stderr or "")
    if output:
        print(output[-10000:], end="" if output.endswith("\n") else "\n")
    return result.returncode


def main():
    if not TEST_SOURCES:
        print("FAIL 没有找到升仙 Minecraft NBT 测试源码")
        return 1

    compile_result = run([sys.executable, os.path.join(ROOT, "_pack", "javac_check.py")])
    if compile_result != 0:
        return compile_result
    if not os.path.isfile(ARGFILE):
        print("FAIL javac_check.py 未生成 profile 参数文件", ARGFILE)
        return 1

    arguments = shlex.split(open(ARGFILE, encoding="utf-8").read())
    try:
        classpath = arguments[arguments.index("-cp") + 1]
    except (ValueError, IndexError):
        print("FAIL 无法从 javac_out/args.txt 读取 profile classpath")
        return 1

    with tempfile.TemporaryDirectory(prefix="tianyi-ascension-tests-") as test_out:
        lang_dir = os.path.join(test_out, "assets", "minecraft", "lang")
        os.makedirs(lang_dir)
        with open(os.path.join(lang_dir, "en_us.json"), "w", encoding="utf-8") as lang:
            lang.write("{}")
        runtime_cp = os.pathsep.join((test_out, OUT, classpath))
        compile_result = run([
            JAVAC, "-encoding", "UTF-8", "--release", "21", "-proc:none",
            "-cp", os.pathsep.join((OUT, classpath)), "-d", test_out, *TEST_SOURCES,
        ])
        if compile_result != 0:
            return compile_result

        for suite in SUITES:
            result = run([JAVA, "-Dfile.encoding=UTF-8", "-cp", runtime_cp, suite])
            if result != 0:
                return result
    print("升仙 Minecraft NBT 测试通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
