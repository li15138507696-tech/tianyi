"""文档：把「一键构建 + 依赖自动提取」写进计划文档的构建小节。"""
import os

P = os.path.join(r'D:\daima\mcmod\天意', 'docs', '天意-冷酷天意与逆命-创意计划.md')
s = open(P, encoding='utf-8').read()

anchor = '编译脚本 `_pack/javac_check.py` 与 `build.gradle` 已指向这份提取副本，来源可追溯、可复现。'
extra = '''
### 2.9.3 · 一键构建（依赖自动提取，已实跑验证）

```bash
python _pack/build.py                 # 常规：复用 _pack/libs 缓存
python _pack/build.py --ephemeral     # 依赖只临时提取，构建后删除（工程不占 106 MB）
python _pack/build.py --bump 2.9.3    # 改版本后直接构建
python _pack/build.py --no-tests      # 跳过离线测试（不建议）
```

新增 `_pack/ensure_upstream.py`：从 10.20 本体壳里提取 `META-INF/jarjar/guzhenren-…jar`，
**校验 SHA256**（`b6d314d3…1fe2bc`），缓存命中则跳过解压；找不到壳或哈希不符直接失败，
绝不"凑合编译"。`_pack/build.py` 串起 **依赖 → 编译 → 离线测试 → 版本 → 打包 → 16 项审计**，
任一步失败即退出非 0，避免像以前那样漏跑某个审计。

实测（两种模式都跑过）：

| 模式 | 结果 |
|---|---|
| `build.py` | 依赖复用缓存 · 68 源文件 javac 0 错 · 测试 91 项 0 失败 · 审计 16 项全绿 · 产物 359,298 B |
| `build.py --ephemeral` | 临时提取 106,304,539 B → 构建 → 临时目录已清理（已核对为空） |
'''

if '一键构建（依赖自动提取' not in s:
    s = s.replace(anchor, anchor + '\n' + extra)
    print('  已追加「一键构建」小节')
else:
    print('  已存在，跳过')

s = s.replace('- 构建：`_pack/javac_check.py` → `_pack/pack_jar.py` → `_pack/verify_jar.py`',
              '- 构建：`python _pack/build.py`（依赖提取 → 编译 → 测试 → 打包 → 16 项审计）')
open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('  构建命令已更新')
