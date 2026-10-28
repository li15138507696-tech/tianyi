"""检查装饰实体 tick 调用的 procedure 是否造成伤害。

对每个 procedure，反编译并找：
  - DamageSource 构造
  - hurt( / doHurtTarget / dealDamage
  - 以「附近实体」为接收者的伤害
并打印上下文，判断是「伤自己」还是「伤别人」。
"""
import subprocess
import re

JAVAP = r'C:\Program Files\Java\jdk-17\bin\javap.exe'
JAR = r'D:\daima\mcmod\蛊真人附属\_crash3\guzhenren_inner.jar'

PROCS = {
    'JianfengShiTiYouXiKeGengXinShiProcedure': 'Jianfeng(26 御剑)',
    'YaotengushitiShiTiYouXiKeGengXinShiProcedure': 'Yaotengushiti(30 妖藤)',
    'DalongjuanfengZaiShiTiKeGengXinShiProcedure': 'Dalongjuanfeng(31 龙卷)',
    'QuanshitexiaoShiTiYouXiKeGengXinShiProcedure': 'Dizhentexiao(17 地震)',
    'BingXueHuiKanTeXiaoDangShiTiGengXinKeShiProcedure': 'Duochong/Baigui',
    'JingyueshitiZaiShiTiKeGengXinShiProcedure': 'Jingyueshiti(40 镜月)',
    'XueyingliuhuatexiaoShiTiYouXiKeGengXinShiProcedure': 'Xueyingliuhua(53 血影)',
}

ATTACK = re.compile(
    r'//\s*(?:Interface)?Method\s+(?:[\w/$]+\.)?'
    r'(hurt|doHurtTarget|performRangedAttack|mobAttack):')
INVOKE = re.compile(r'^\s+\d+:\s+invoke\w+\s+#\d+')
ALOAD = re.compile(r'^\s+\d+:\s+(aload(?:_\d+|\s+\d+)|getstatic)\s*(.*)')

for fq, label in PROCS.items():
    fqcn = 'net.guzhenren.procedures.' + fq
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, fqcn],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = (r.stdout or '').splitlines()
    print()
    print(f'=== {fq}  [{label}]  rc={r.returncode} ===')

    hits = 0
    for i, ln in enumerate(lines):
        if not INVOKE.match(ln) or not ATTACK.search(ln):
            continue
        hits += 1
        # 往前找 3 条，看接收者
        ctx = [lines[k].strip() for k in range(max(0, i - 3), i + 1)]
        print('  --- 攻击调用 ---')
        for c in ctx:
            print('     ', c)
    if hits == 0:
        print('  （无攻击类调用）')

    # 额外：是否引用 DamageSource 类
    ds = len(re.findall(r'DamageSource', '\n'.join(lines)))
    print(f'  DamageSource 提及次数: {ds}')
