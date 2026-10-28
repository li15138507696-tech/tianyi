# guzhenren_malice

蛊真人 × 莱特兰恶意（l2hostility）的适配附属 —— MC 1.21.1 / NeoForge 21.1.263。

- 三档妖兽配置（普通 / 精英 / 妖王）：等级下限、血量攻击倍率、词条白名单
- 四档玩家可切换难度（安逸 / 标准 / 凶险 / 噩梦），存在存档里
- 剧情 NPC / 商人 / 术法特效被挡在恶意之外
- **不改本体任何基础属性**

详细说明：[docs/适配说明.md](docs/适配说明.md) ｜ 可视化版：[docs/适配说明.html](docs/适配说明.html)

## 构建（不走 Gradle）

```powershell
cd D:\daima\mcmod\蛊真人恶意
$env:PYTHONIOENCODING='utf-8'
python _build\javac_check.py   # javac exit: 0
python _build\pack_jar.py      # build/libs/guzhenren_malice-1.0.1.jar
python _build\verify_jar.py    # ALL OK
python _build\audit_ids.py     # 问题 0
python _build\check_bom.py     # BOM 文件数: 0
```

## 目录

| 路径 | 作用 |
|---|---|
| `src/main/java/net/guzhenren/malice/` | 6 个类：入口、标签、难度枚举、存档状态、事件钩子、命令 |
| `src/main/resources/data/guzhenren/tags/entity_type/` | 三档怪物标签 + 排除标签 |
| `src/main/resources/data/guzhenren/l2hostility_config/entity/guzhenren_malice.json` | 恶意的实体配置（三档） |
| `src/main/resources/data/l2hostility/tags/entity_type/whitelist.json` | **关键**：让恶意愿意接管本体妖兽（91 只） |
| `_build/` | 离线构建与审计脚本 |
| `docs/` | 说明文档（md + html） |
