# -*- coding: utf-8 -*-
import subprocess
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\jingjie.txt'
r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, 'net.guzhenren.procedures.JingJieBaoCunProcedure'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
open(OUT, 'w', encoding='utf-8').write(r.stdout)
