# -*- coding: utf-8 -*-
import os, subprocess
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\vars_fields.txt'
r = subprocess.run([JAVAP, '-p', '-classpath', JAR, 'net.guzhenren.network.GuzhenrenModVariables$PlayerVariables'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
lines = [ln for ln in r.stdout.splitlines()]
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
