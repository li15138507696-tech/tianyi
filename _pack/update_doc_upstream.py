"""把「炼蛊 100.0 未验证」升级为「已用字节码核实」，并记录上游版本依据。"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', 'docs', '天意-冷酷天意与逆命-创意计划.md')
s = open(P, encoding='utf-8').read()

old = '2. **本体炼蛊 `== 100.0` 精确完成判定**：尚未逐行核对，第四期前必须验证；'
new = ('2. ~~**本体炼蛊 `== 100.0` 精确完成判定**：尚未逐行核对~~ → **已核实（2.9.2）**：\n'
       '   在指定本体 jar 的字节码里找到 27 处 `getfield LianGuJinDu → ldc2_w 100.0d → dcmpl → ifne`，\n'
       '   即**精确等于 100.0 才算完成**。因此本模组的炼道研究只读该字段、从不写入，\n'
       '   结构上不可能越过本体结算阈值（探测脚本：`_pack/probe_alchemy_100b.py`）。')
if old in s:
    s = s.replace(old, new)
    print('  已更新「炼蛊 100.0」条目')
else:
    print('  ! 未找到待更新条目')

anchor = '## 10. 交付与版本'
extra = '''## 9.5 上游版本依据（2.9.2）

| 项 | 值 |
|---|---|
| 用户指定包 | `D:\\cxdownload\\guzhenrn蛊真人模组本体10.20.jar`（120,223,219 B） |
| 该包性质 | **jarjar 壳**：modId `guzhenren_library`，1386 条目，**不含** `net.guzhenren.*` |
| 真实代码本体 | 包内 `META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar`（36,880 条目，modId `guzhenren`） |
| 提取副本 | `_pack/libs/guzhenren-0.1-neoforge-1.21.1.jar` |
| SHA256 | `b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc` |
| 与旧依赖 | 逐字节相同（哈希一致）→ 之前编译依据本就是 10.20 里的那一份，本次只是把来源固定下来 |

编译脚本 `_pack/javac_check.py` 与 `build.gradle` 已指向这份提取副本，来源可追溯、可复现。

'''
if '9.5 上游版本依据' not in s:
    s = s.replace(anchor, extra + anchor)
    print('  已追加「上游版本依据」小节')
else:
    print('  上游依据小节已存在')

open(P, 'w', encoding='utf-8', newline='\n').write(s)
