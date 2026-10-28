# 主代理只读核验：10.20 有限资源候选

日期：2026-10-25。未进游戏；以下仅为 ZIP 数据和 javap 静态证据。

## 锁定依赖

`_pack/libs/guzhenren-0.1-neoforge-1.21.1.jar` 与用户指定10.20包内同名嵌套JAR的SHA256一致：
`b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`。

## 植物

- `assets/guzhenren/blockstates/gu_caidanxincao.json` 指向 `guzhenren:block/gu_caidanxincao`。
- `assets/guzhenren/blockstates/gucaileimingcao.json` 存在。
- `data/guzhenren/tags/item/zhiwu.json` 包含上述两个完整物品ID。
- 两者对应 `data/guzhenren/loot_table/blocks/<id>.json` 均为单个pool、rolls=1.0、条件`minecraft:survives_explosion`、单个item entry产出自身ID，无count、fortune或silk touch函数。
- `javap -p`：`net.guzhenren.block.GuCaidanxincaoBlock` 和 `GucaileimingcaoBlock` 均继承 `FlowerBlock`；完整自定义方法仅constructor、getFlammability、appendHoverText、getFireSpreadSpeed。没有自定义随机生长/破坏产物钩子。继承原版行为及全局事件副作用仍需审查。
- constructor字节码：植物颜色、GRASS声音、instabreak、noCollission、XZ偏移、PushReaction.DESTROY。不能当作已存在自动生长机制。

## 矿物

- `assets/guzhenren/blockstates/xuanjin.json` 指向 `guzhenren:block/xuanjin`。
- `data/guzhenren/loot_table/blocks/xuanjin.json` 单pool单roll，survives_explosion，产出`guzhenren:xuanjin`，不是`guzhenren:gu_cai_xuan_jin`。
- `javap -p`：`net.guzhenren.block.XuanjinBlock extends Block`，自定义仅constructor与getLightBlock。constructor有requiresCorrectToolForDrops。
- `data/guzhenren/tags/item/jinshu.json` 包含`guzhenren:gu_cai_xuan_jin`、`gu_cai_chi_lian_jin`、`wen_ding_shi`。此标签不能证明矿石与加工材料的互换关系。

## 执行命令

PowerShell通过`System.IO.Compression.ZipFile.OpenRead`读取上述JAR entries，无解压写入。

```powershell
& 'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe' -classpath '_pack\libs\guzhenren-0.1-neoforge-1.21.1.jar' -p -c net.guzhenren.block.GuCaidanxincaoBlock net.guzhenren.block.GucaileimingcaoBlock net.guzhenren.block.XuanjinBlock
& 'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe' -classpath '_pack\libs\guzhenren-0.1-neoforge-1.21.1.jar' -p net.guzhenren.block.GuCaidanxincaoBlock net.guzhenren.block.GucaileimingcaoBlock net.guzhenren.block.XuanjinBlock
```

均退出码0。启用前仍需实际注册映射、运行时registry检查、工具tag与守护兽相关证据；不代表实机验证。

## 补充原始证据

主代理用同一temurin21 javap实际生成，退出码均为0：

- `finite-registry-main.javap.txt`：GuzhenrenModBlocks/Items/Entities 的 `-p -c` 输出。静态初始化中出现 xuanjin→XUANJIN、gu_caidanxincao→GU_CAIDANXINCAO、gucaileimingcao→GUCAILEIMINGCAO，tu_lang→TU_LANG、hui_lang→HUI_LANG；实体字段泛型对应TuLangEntity/HuiLangEntity。构造工厂invokedynamic的Bootstrap精确映射仍可由D补查。
- `finite-guards-main.javap.txt`：HuiLangEntity/TuLangEntity 完整 `-p -c`。灰狼goal包含HurtByTargetGoal、NearestAttackableTargetGoal<Player>、近战与游走内部类；两者die、finalizeSpawn、baseTick均调用本体procedure。
- `finite-guard-procedures-main.javap.txt`：两兽死亡procedure直接生成ItemEntity（包含GUCAIXIANXUE与多种未炼化蛊）；不允许再叠加天意击杀奖励。灰狼spawn委托ChushishengchenghunpoProcedure，tick委托HunposiwangProcedure；土狼也有魂魄委托。
- `finite-soul-procedures-main.javap.txt`：上述两种共享魂魄procedure的原始输出，待C/D完整审阅副作用。
- `finite-runtime-api-main.javap.txt`：本机1.21.1-20240808.144430 srg与NeoForge21.1.263运行库的ServerLevel/Level/LevelReader/BlockStateBase/BlockEntity/Container/BarrelBlockEntity以及BreakEvent/EntityPlaceEvent/RightClickBlock签名，便于核实新接线API。

这些文件未进游戏，也不能证明所有全局事件无额外副作用。

## 基线验证与备份

本项目无Gradle wrapper。主代理以`PYTHONIOENCODING=utf-8`和`PYTHONUTF8=1`运行`python _pack/build.py`：72源javac exit0、全部离线测试套件通过、7项审计通过，产物3.10.1为484500字节。未修改源码后得到的基线，不能用作新增功能验证。

修改前备份：`_backup/finite-sites-20261025-201444`，内含src、构建/版本/计划、关键_pack脚本与tests、3.10.1产物。`sha256-manifest.json`为160个备份文件的哈希记录。
