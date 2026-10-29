# 升仙实现审计证据索引

审计日期：2026-10-26。本文记录当前工作树中与 10.20 本体升仙兼容有关的可复核证据。字节码来自本体 JAR，不能单凭字段名推断语义；无法从字节码和已核章节确定的部分均标为“阻塞”。

## 输入与运行环境

| 项目 | 已核信息 |
|---|---|
| 用户本体外层 JAR | `D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar` |
| 外层 SHA-256 | `3733C20FFBD16D9068BB8188D45B24A0D61B279D7CB6FDC8EA341938A7F4CF04` |
| 内层本体 JAR SHA-256 | `B6D314D3810B8BC5FCFDBC4F588DB8721F0EFDFC14D27A0A06D88143A01FE2BC` |
| 工程缓存 | `_pack/libs/guzhenren-0.1-neoforge-1.21.1.jar`，哈希与上述内层 JAR 相同 |
| 运行配置 | MC 1.21.1、NeoForge 21.1.263、NeoForm 20240808.144430，profile 位于 `D:\game\mcmod\1\.minecraft\versions\1.21.1-NeoForge_21.1.263\1.21.1-NeoForge_21.1.263.json` |
| JDK 工具 | `C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe` |
| 构建/测试入口 | `_pack/javac_check.py`、`_pack/run_tests.py`、`_pack/build.py`；当前 Python 3.13 可执行文件在 `C:\Users\ADMIN\AppData\Local\Programs\Python\Python313\python.exe`，但本环境拒绝启动；工程没有 Gradle Wrapper |

## 字节码证据

| 文件 | 类 / 用途 | 可支持的结论 | 不能据此声称 |
|---|---|---|---|
| `PlayerVariables.javap.txt`、`PlayerVariables.javap-full.txt`、`PlayerVariables-signatures.javap.txt` | 玩家变量字段、序列化接口 | 本体确有转数、阶段、空窍、人气、寿元、真元、本命蛊等字段；玩家变量实现 NBT 序列化 | 不证明字段的唯一写入处、对所有角色有效或升仙兼容 |
| `GuzhenrenModVariables-signatures.javap.txt`、`GuzhenrenModVariables.javap.txt` | 玩家变量注册、登录/重生/跨维度/刻同步、Clone | 本体有同步和克隆钩子；应避免在天意模组中复制第二套同步逻辑 | 不证明六转可以被现有培养 UI 和全套本体逻辑正确处理 |
| `UIxiuliananjianProcedure.javap.txt` | 培养按钮的服务端过程 | 当 `zhuanshu >= 5` 且 `jieduan >= 4` 时，普通培养按钮提前返回；`zhuanshu` 是浮点值 | 不能称全局角色等级上限已被完整查明；其它突破和刻更新入口仍须核对 |
| `JingJieBaoCunProcedure.javap.txt` | 本体死亡事件处理 | 死亡过程会重置转数、阶段、空窍、培养进度、体质及多个修炼相关值 | 不代表所有重生或特殊复活路线都采用相同规则 |
| `BenMingGu1Procedure.javap.txt` | 本命蛊绑定/处理过程 | 过程读取当前 `Player.containerMenu` 的 `MenuAccessor.getSlots().get(0)` 和转数对应蛊虫标签；绑定后把槽中 `ItemStack` 连续复制两次保存进 `PlayerVariables.BenMingGu`，再置 `BenMingGu_1` 并标记同步 | 菜单槽引用不是稳定的玩家物品位置；缓存副本不能作为唯一物品来源 |
| `BenMingGu3Procedure.javap.txt` | 本命蛊相关耐久/恢复过程 | 部分行为操作玩家变量中缓存的 `BenMingGu` 栈 | 不能用缓存副本替代真实库存、唯一蛊虫托管或消耗 |
| `TupochenggonglvProcedure.javap.txt` | 突破概率文本/数值过程 | 本体有概率相关展示过程 | 不是升仙状态变更流程，也不证明升仙应以一次概率掷骰结算 |
| `KongqiaoProcedure.javap.txt`、`RenqiProcedure.javap.txt`、`ZhenyuanliangzengzhangProcedure.javap.txt` | 空窍、人气、真元相关过程 | 作为进一步追踪这些变量的入口 | 字段可被直接套用为原著单位或仙元实现 |
| `progression-candidates.javap.txt` | 一组候选过程的合并反汇编，包含培养、开窍、空窍初始化、真元持续过程等类 | 可定位普通修炼、界面编码、人气/真元与境界持续过程中的字段读写 | 文件名含“candidate”，不代表覆盖本体所有调用点；仍须继续从整个 JAR 追写入引用 |
| `PlayerVariableFieldScanner.java` + `upstream-field-uses.txt` | 对缓存内固定哈希的内层 JAR 逐个 class 做 ASM 字段指令扫描，只匹配玩家变量 owner 的 GETFIELD/PUTFIELD | 覆盖所有直接字节码字段引用；记录按“类/方法/操作/字段”元组去重 | 不识别反射、序列化键字符串或间接调用；同一方法内多次相同字段操作会合并；扫描命中不等于该方法运行时一定作用于玩家 |
| `zhuanshu-procedure-writers.javap.txt` | 对所有直接写入 `zhuanshu` 的 16 个非生成过程类执行 `javap -c -p` | 把高风险审计范围缩到本体培养/空窍 UI、`XiuWei2Procedure` 命令入口、`JingJieBaoCunProcedure` 死亡/克隆处理，以及 12 个实体交互/效果过程 | 其余 137 个类名带 `ShiTiChuShiShengChengShiProcedure`，但命名不等于已证明不会作用到玩家；逐一核对实体类型门槛、调用方和实际游戏路径仍未完成 |

直接字段引用扫描结果（写入类数/不同类方法字段元组数；字段名按大小写区分）：`zhuanshu` 155/156、`jieduan` 55/56、`kongqiao` 9/10、`renqi` 3/4、`shouyuan` 34/36、`zhenyuan` 1849/1850、`zuida_zhenyuan` 68/69、`BenMingGu` 6/7、`BenMingGu_1` 6/7、`benminggu` 41/42、`tizhi` 6/7。它说明 rank 写入分散在大量过程类中；逐类的角色/实体边界分类仍未完成。

`zhuanshu` 的 155 个不同写入类初步分为：137 个实体生成初始化过程、16 个其它过程类、2 个变量宿主/注册类。这个是按类名和 owner 的初筛，不是已完成的运行时角色分类。上述 16 个过程类已另存字节码；其中培养 UI、空窍 UI 初始化、命令入口、死亡/克隆过程明确涉及玩家进度。剩余实体交互/效果过程仍需追调用方与目标类型。故当前不能安全地仅靠天意在升仙后写 `zhuanshu = 6`；本体所有相关消费者还没确认支持六转。

## 工程侧接口

- `src/main/java/net/guzhenren/tianyi/world/WillState.java` 使用 Overworld `SavedData`，schema 当前为 1。已保存的 `apertures` 是 `Map<UUID, Immortal.Aperture>`：每名玩家只有一条记录，尚无空窍 ID、多空窍归属、升仙会话、碎窍提交位或蛊虫托管记录。
- `src/main/java/net/guzhenren/tianyi/world/AscensionStore.java` 使用独立的 Overworld `SavedData` schema 2 保存升仙会话、蛊托管记录、完整物品栈 NBT 和隔离证据。schema 1 的终态历史保留；无法证明托管关系的活动记录原样隔离；未知 schema 只读保留。会话与蛊记录可在同一账本调用中配对 CAS，但这不等于玩家附件、背包和磁盘之间的跨文件原子提交。
- `AscensionProgressionReader` 只在本体玩家附件已经存在时读取六个数值字段并构造不可变快照，不创建附件、不写回字段。该读取器不证明六转境界、真元单位、寿元行为或 UI/成长流程兼容；`AscensionService.ORDINARY_PROFILE` 仍关闭，阻止原因会分别列出上游 rank 适配、仙窍空间、唯一蛊托管和实物未核验。
- `AscensionService.recover` 已由登录与重生事件调用，但当前没有稳定来源适配器，故恢复证据固定为 `SOURCE_UNAVAILABLE`，不推进或修改会话、蛊记录和物品。纯 reducer 与配对 CAS 只在未来取得服务端来源证据后才可应用安全迁移；现在不扫描未加载容器或世界。
- `src/main/java/net/guzhenren/tianyi/net/JournalNet.java` 展示当前 NeoForge payload 注册与服务端动作复核模式；`src/main/java/net/guzhenren/tianyi/TianyiEvents.java` 已在登录/重生时调用升仙恢复服务。尚无升仙 payload、客户端入口或逐刻推进接线。
- `src/main/java/net/guzhenren/tianyi/will/Immortal.java` 的 `Ascension` 以概率、观察累积和可重复 attempt 计算结果；项目内没有调用者。它不能作为真实升仙链路，也不应迁移为已成仙状态。
- 资源和源码搜索未发现已注册的自有仙窍维度或传送实现。运行时 profile/传送注册尚未冻结；因此仙窍入口、空间缩尺与多空窍隔离是实现阻塞，不可先承诺已有支持。

### 本命蛊来源槽审计（2026-10-29）

复核 `_pack/libs/guzhenren-0.1-neoforge-1.21.1.jar` 的 `UilianhuaguchongButtonMessage` 后，按钮动作 4 会调用 `BenMingGu1Procedure.execute`。绑定过程从当前玩家菜单 `MenuAccessor.getSlots().get(0)` 读取栈；菜单类 `UilianhuaguchongMenu` 在构造时可把 0 号槽绑定到手持物品的 ItemHandler、实体 ItemHandler、方块容器，或菜单自身新建的临时 `ItemStackHandler`。该 0 号键只标识当前菜单映射中的槽对象，不包含可跨断线恢复的唯一所有者和持久容器定位。绑定过程复制栈进 `PlayerVariables.BenMingGu`，没有从来源槽移除原物品。

结论：不能把该菜单槽或 `PlayerVariables.BenMingGu` 当作本命蛊托管的唯一真实来源；现有本体路径不能证明物品唯一性和重连后的可定位性。`UNIQUE_GU_ESCROW` 保持关闭，不实现 `PlayerGuEscrowSource`，也不允许升仙服务改变物品。以后若本体提供有明确所有者且跨重连稳定的持久来源，再重新审计。

### 已复核的本体写入点

| 字段/过程 | 字节码观察 | 对六转接入的影响 |
|---|---|---|
| `zhuanshu` / `jieduan` | `UIxiuliananjianProcedure` 执行培养并写入转数/阶段；培养按钮在五转后检查巅峰阶段。`UikongqiaoguanbiProcedure` 只有在转数与阶段都为 0 时才写默认 1/1。 | 有普通五转巅峰入口门槛与默认初始化写入；尚未证明其它 UI、效果和所有转数消费者支持 6.0。 |
| `kongqiao` | `ZhenyuanliangzengzhangProcedure` 按 `zuida_zhenyuan` 数值区间写入编码值；`KongqiaoProcedure` 把这些值转为面板文字。死亡过程将其清零。 | 不能把这个浮点字段当成空窍 UUID、空窍数量或稳定的多空窍索引。 |
| 死亡/克隆 | `JingJieBaoCunProcedure.onEntityDeath` 会重置修炼变量；`clonePlayer` 会将玩家变量复制到新玩家。变量附件另有 NBT save/load 与登录、重生、换维、玩家刻同步钩子。 | SavedData 必须独立记录升仙阶段和空窍 ID；不能从重生后的本体变量推断碎窍没有发生。完整恢复顺序仍需实机核定。 |
| 真元/上限/寿元 | 候选过程显示死亡重置、持续效果和转数分支会写 `zhenyuan`、`zuida_zhenyuan` 与寿元相关值。 | 不能把 `zhenyuan` 改名当仙元，也不能仅抬高上限就声称六转兼容。 |

## 原著来源索引

以下为先前核读的章节转载页，属于二手文本版本。章节名比网站总章号更可靠；重要特例实现前仍须复核完整上下文和版本。逐条适配状态见 [`docs/升仙-原著规则清单.md`](../../../docs/升仙-原著规则清单.md)。

- 第三卷第二百零一章《苏醒》：<https://www.hetushu.com/book/7640/5345844.html>
- 第三卷第二百零四章《不自由毋宁死》：<https://www.hetushu.com/book/7640/5345847.html>
- 第三卷第二百二十三章《枭雄黑楼兰》：<https://www.hetushu.com/book/7640/5345866.html>
- 第三卷第二百二十八章《终成蛊仙》：<https://www.hetushu.com/book/7640/5345871.html>
- 第四卷第三十四章《黑楼兰升仙（上）》：<https://www.hetushu.com/book/7640/5345921.html>
- 第四卷第三十五章《黑楼兰升仙（中）》：<https://cn.ttkan.co/novel/pagea/guzhenren-guzhenren_691.html>
- 第四卷第三十六章《黑楼兰升仙（下）》：<https://www.hetushu.com/book/7640/5345923.html>
- 第五卷《赵怜云升仙》：<https://www.hetushu.com/book/7640/5346677.html>
- 第五卷《方正升仙》：<https://www.hetushu.com/book/7640/5346884.html>

## 当前结论

普通路线的主步骤有章节依据；schema 2 世界账本、不可变修为快照、托管状态模型和保守恢复 reducer 已实现并接入登录/重生事件。由于事件侧没有可靠的蛊来源证据，当前恢复只等待，不会执行物品或会话迁移；本体六转适配、真实仙窍空间和唯一蛊源能力仍关闭。只能说“已建立封闭的升仙会话与托管基础设施”，不能称升仙流程已开放、已实机验证或完全还原原著。
