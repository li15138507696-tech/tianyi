# -*- coding: utf-8 -*-
import subprocess
JAR = r'D:\daima\mcmod\蛊真人附属\_reverse\nested\guzhenren-0.1-neoforge-1.21.1.jar'
JAVAP = r'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe'
OUT = r'D:\daima\mcmod\蛊真人附属\_crash3\shiqiao_writes.txt'
classes = ['HuaXieLuGuYouJiKongQiShiShiTiDeWeiZhiProcedure',
           'HuanRanYiXin5Procedure', 'JingJieBaoCunProcedure',
           'ShiQiaoGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure',
           'UIxiuliananjianProcedure',
           'XieLuGu5YouJiKongQiShiShiTiDeWeiZhiProcedure']
lines = []
for c in classes:
    r = subprocess.run([JAVAP, '-p', '-c', '-classpath', JAR, 'net.guzhenren.procedures.' + c],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines.append('## ' + c)
    for ln in r.stdout.splitlines():
        if 'shiqiao' in ln:
            lines.append('   ' + ln.strip())
    lines.append('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
