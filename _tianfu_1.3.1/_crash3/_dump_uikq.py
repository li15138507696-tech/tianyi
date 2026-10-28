# -*- coding: utf-8 -*-
import os, subprocess
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\uikongqiao.txt'
r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, 'net.guzhenren.procedures.UikongqiaoguanbiProcedure'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
open(OUT, 'w', encoding='utf-8').write(r.stdout)
print('exit', r.returncode, 'lines', len(r.stdout.splitlines()))
