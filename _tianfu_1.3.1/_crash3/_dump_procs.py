# -*- coding: utf-8 -*-
"""把 procedure 的 javap 全量落到文件，便于人眼读控制流。"""
import os, subprocess

ROOT = r'D:\daima\mcmod\天意\_tianfu_1.3.1'
JAR = os.path.join(ROOT, r'_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar')
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUTDIR = os.path.join(ROOT, r'_crash3\proc_dump')

procs = ['BingXueHuiKanTeXiaoDangShiTiGengXinKeShiProcedure',
         'YaotengushitiShiTiYouXiKeGengXinShiProcedure',
         'JianfengShiTiYouXiKeGengXinShiProcedure',
         'DalongjuanfengZaiShiTiKeGengXinShiProcedure',
         'QuanshitexiaoShiTiYouXiKeGengXinShiProcedure']
os.makedirs(OUTDIR, exist_ok=True)
for p in procs:
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR,
                        'net.guzhenren.procedures.' + p],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    path = os.path.join(OUTDIR, p + '.txt')
    open(path, 'w', encoding='utf-8').write(r.stdout)
    print(path, len(r.stdout))
