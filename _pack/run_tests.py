"""离线行为测试运行器（不依赖 Minecraft / NeoForge / Gradle）。

用法：python _pack/run_tests.py
退出码：0 = 全部通过；非 0 = 有失败（可直接接进交付检查）

原理：`ledger` 与 `contract` 两个包的核心类刻意不 import 任何 Minecraft 类型，
所以它们的测试可以用**纯 javac + java** 跑起来——这才叫运行测试，而不是扫源码文字。

注意：子进程输出必须显式按 UTF-8 解码。Windows 控制台默认 GBK，
直接 text=True 会在中文断言名上抛 UnicodeDecodeError（踩过一次）。
"""
import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
WILL_SRC = [
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'will', n)
    for n in ('WillConfig.java', 'Knowledge.java', 'Assessment.java', 'Intent.java',
              'WillCore.java', 'Ploy.java', 'Opportunity.java', 'Calamity.java',
              'Immortal.java', 'Destiny.java', 'Carrier.java', 'Counter.java',
              'History.java', 'BeastHerd.java',
              'AlchemyTracker.java', 'IdentityCase.java',
              'BeastCatalog.java',
              # 3.13.0 宿命蛊重做：保管账本 / 规则查询 / 旧档迁移（全部纯逻辑）
              'DestinyCustody.java', 'DestinyRules.java', 'DestinyMigration.java',
              # 3.13.0 第二阶段：承载物凭证模型（物品自称的凭证 vs 世界账本）
              'DestinyVessel.java',
              # 3.14 天意强度档位：档位只做乘数/阈值，必须能离线单测
              'WillForce.java')]
# 纯净逻辑包：不 import 任何 net.minecraft.* / net.neoforged.*，因此可以纯 javac + java 跑。
# fact/ = 世界事实与情报传播；action/ = 干预结果、布局步骤与总账。
# resource/ = 有限资源点模型（FiniteSite/SiteInventory/SiteBudget）；journal/ = 见闻条目与站点线索文案。
# 3.11 起 growVerified 已实现，SiteInventoryTest 转为常规回归并登记进 SUITES。
PURE_PKGS = ('fact', 'action', 'journal', 'encounter', 'resource')
PURE_SRC = sorted(
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', pkg, n)
    for pkg in PURE_PKGS
    for n in os.listdir(os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', pkg))
    if n.endswith('.java')
)
SRC_CORE = [
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ledger', 'TianyiLedgerCore.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'AscensionModel.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'AscensionRules.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'AscensionMachine.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'ProgressionSnapshot.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'GuEscrow.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'AscensionRecovery.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'InsightCatalog.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'QiRegulator.java'),
    os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'ascension', 'QiControlService.java'),
] + WILL_SRC + PURE_SRC
#: 与 javac_check.py 相同的 JDK（Java 21）
JDK21 = r'C:\Users\ADMIN\.jdks\temurin-21'
TESTS = os.path.join(ROOT, '_pack', 'tests')
OUT = os.path.join(ROOT, '_pack', '_testout')

RUN_KW = dict(capture_output=True, text=True, encoding='utf-8', errors='replace')

SUITES = [
    'net.guzhenren.tianyi.ledger.TianyiLedgerCoreTest',
    'net.guzhenren.tianyi.ascension.ProgressionSnapshotTest',
    'net.guzhenren.tianyi.ascension.GuEscrowTest',
    'net.guzhenren.tianyi.ascension.AscensionMachineTest',
    'net.guzhenren.tianyi.ascension.AscensionRulesTest',
    'net.guzhenren.tianyi.ascension.AscensionRecoveryTest',
    'net.guzhenren.tianyi.ascension.InsightCatalogTest',
    'net.guzhenren.tianyi.ascension.QiRegulatorTest',
    'net.guzhenren.tianyi.ascension.QiControlServiceTest',
    'net.guzhenren.tianyi.will.WillCoreTest',
    'net.guzhenren.tianyi.fact.IntelCoreTest',
    'net.guzhenren.tianyi.action.ActionLedgerTest',
    'net.guzhenren.tianyi.journal.JournalTest',
    'net.guzhenren.tianyi.journal.JournalLayoutTest',
    'net.guzhenren.tianyi.will.BeastCatalogTest',
    'net.guzhenren.tianyi.encounter.EncounterRunTest',
    'net.guzhenren.tianyi.encounter.EncounterBeastsTest',
    'net.guzhenren.tianyi.resource.SiteInventoryTest',
    'net.guzhenren.tianyi.resource.SiteBudgetTest',
    'net.guzhenren.tianyi.resource.SiteTerrainTest',
    'net.guzhenren.tianyi.journal.SiteNoticeTest',
    # 3.14.2 宿命蛊取得路径：材料价目、缺口文案、幂等键、前置判定顺序、站点命令参数解析
    'net.guzhenren.tianyi.resource.DestinySeekTest',
    # 3.13.0 宿命蛊重做
    'net.guzhenren.tianyi.will.DestinyCustodyTest',
    'net.guzhenren.tianyi.will.DestinyRulesTest',
    'net.guzhenren.tianyi.will.DestinyMigrationTest',
    # 3.13.0 第二阶段：物品↔账本校验、复制检测、旧凭证作废
    'net.guzhenren.tianyi.will.DestinyVesselTest',
    # 3.14 天意强度：档位表、阈值单调、幂等窗口、诱饵比例、兽潮不凭空
    'net.guzhenren.tianyi.will.WillForceTest',
    # 3.14.1 性能：节流契约（周期/预算/窗口整除/容量上限）+ 空账本 due() 快速返回
    'net.guzhenren.tianyi.perf.ThrottleContractTest',
]


def java_tool(name):
    """挑 JDK 21。

    踩过的坑：JAVA_HOME 指向 JDK 17 时，源码里用了 Java 21 才有的 API
    （如 List.getFirst()）会让**测试编译**失败，而 javac_check.py 写死 temurin-21
    所以主构建照常通过 —— 表现为「主构建绿、测试红」。这里优先用同一个 JDK。
    """
    exe = name + ('.exe' if os.name == 'nt' else '')
    for home in (JDK21, os.environ.get('JAVA_HOME')):
        if home:
            p = os.path.join(home, 'bin', exe)
            if os.path.isfile(p):
                return p
    return name


def main():
    for p in SRC_CORE:
        if not os.path.isfile(p):
            print('FAIL 缺核心源文件', p)
            return 1

    srcs = list(SRC_CORE)
    srcs += glob.glob(os.path.join(TESTS, '**', '*.java'), recursive=True)

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT, exist_ok=True)

    print('javac', len(srcs), '个源文件')
    r = subprocess.run([java_tool('javac'), '-encoding', 'UTF-8', '-d', OUT] + srcs, **RUN_KW)
    if r.returncode != 0:
        print(r.stdout or '')
        print(r.stderr or '')
        print('FAIL 测试编译失败')
        return r.returncode

    failed = 0
    for suite in SUITES:
        print()
        print('==', suite)
        cmd = [java_tool('java'), '-Dfile.encoding=UTF-8',
               '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
               '-cp', OUT, suite]
        r = subprocess.run(cmd, **RUN_KW)
        sys.stdout.write(r.stdout or '')
        sys.stderr.write(r.stderr or '')
        if r.returncode != 0:
            failed += 1

    print()
    if failed:
        print(f'FAIL {failed} 个测试套件失败')
        return 1
    print('全部测试套件通过')
    return 0


if __name__ == '__main__':
    sys.exit(main())
