# 当前有效计划：3.13.0 宿命蛊机制重做

## 核对结论（本地实际读取 3.12.0 源码）

| 旧模型 | 定义 | 外部调用者 | 游戏事件接线 |
|---|---|---|---|
| `DestinyGu.damage(double,long,String)` | 有 | **无** | 无 |
| `DestinyGu.repair(double,long,String)` | 有 | **无** | 无 |
| `Ascension.heavenLock(Destiny,int)` | 有 | **无** | 无（`attempt/successChance` 亦无调用者，升仙整链未接线） |
| `fateLock()` | 有 | `Immortal.heavenLock`（死代码）+ `WillCore` 报告 map | 无 |
| `heavenControl()` | 有 | `Observation:89`、`WorldExec:450`、`WillCore:132/464` | **有**：真实影响天意预算与灾劫发动条件 |
| 宿命蛊实物 | **不存在** | — | — |

补充：`WillServer:431` 与 `WorldExec:336` 的 `ap.damage(...)` 是 `Immortal.Aperture`，**不是宿命**；普通猎兽损宿命已于 3.10.2 移除。本体 jar 内无 `suming/mingyun` 物品或类，故由天意自建承载物（`tianyi:` 命名空间），不冒充本体。

## 设计要点（必须落地）

- 四阶段：`BODY_DAMAGED` / `BODY_WHOLE` / `BODY_DESTROYED_REVIVABLE` / `SPLIT_AND_DISTRIBUTED`；迁移/丢失/运输属保管状态，不混入阶段。
- 删除 `integrity=0.62` 与 `0.95 即完整`；保留数值只能是「模组修复任务进度」，不得驱动阶段。
- 取消 `fateLock` 万能系数；改为「阶段＋手段＋目标＋证据」规则查询，四态：允许/受限/条件不足/未实现。
- 唯一身份以服务端世界账本为权威（世界身份、逻辑身份、实体化代次、保管版本、是否存在、可验证位置、最近合法转移事务）；物品 UUID 不构成唯一性证明。
- 复制/重放/创造模式 → 判为重复，隔离保留证据，不认定副本为真。
- 火烧/岩浆/虚空/掉落清理 → 只记「载体丢失待核验」，不改阶段。
- 摧毁与后续炼制分发是两个独立事务，各需独立完成事件与证据。
- 天意预算/感知/灾劫保持独立判断，不因重做宿命被一起关闭。

## 实施阶段

1. 模型与账本（纯逻辑、可离线测）：阶段、保管身份、事务、规则查询、旧档迁移。
2. 实物与接线：承载物品、事件、命令、见闻、快照纳入。

---

# 历史计划：3.10.2 兼容可靠性 + 3.11.0 有限资源点

> 2026-10-25，仅规划，未实施。原 3.1.0 正文保留在下方作历史；其 NPC 路线、旧版本和“不要跑 Gradle”不再是当前指令。

## 基线、范围与验收

本项目 build.gradle 为 java 插件、离线 compileOnly classpath、Java toolchain 21，无 ForgeGradle/MDG；META-INF/neoforge.mods.toml 指向 NeoForge / Minecraft 1.21.1。采用现代 NeoForge 1.21.1、Java 21，不重建工程。实际 NeoForge 小版本须由 profile 核实，不能照抄历史 21.1.263。内层依赖 SHA 由用户确认且 ensure_upstream.py 固定为 b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc，本次未重新计算。

实施前基线（本地实际执行）：PYTHONIOENCODING=utf-8、PYTHONUTF8=1 下，72 源文件 javac exit0、全部测试套件通过、7 项审计全绿，3.10.1 jar 484500B。此前 GBK 输出 UnicodeEncodeError 不是 Java 编译失败。以上均由本地实际执行，规划阶段未运行构建，未实机验证。目录非 Git 仓库：先备份明确拟改文件及 3.10.1 产物，留时间戳和哈希；不 init、不建 worktree。

先交付 3.10.2 兼容可靠性，再交付 3.11.0 蛊材生长地、矿脉露头、守护资源地，少量静态核验守护兽、实物线索和个人见闻。沿用注册 ID、payload ID、旧存档键。天意幕后、无善恶好感；允许建造、陷阱、绕行、偷采及多人争夺。新节点不押注、不传送、不设竞技场边界或离场惩罚。旧斗兽独立配置保留，legacyAutomaticPressure 仍默认 false。不依赖 NPC 对话/关系/任务/势力/追捕；不做新维度、回溯、复杂迁移生态、商城。真实实例仅可读依赖，不安装、不改真实存档、本体 jar；只用隔离实例与存档副本。

项目及 mcmod/daima 范围 AGENTS 搜索未发现，D 盘全树有受保护目录访问错误，不能宣称祖先规则已全部核清。未找到可用的技能入口，不能虚报已加载技能。

验收：正常保存/重启、重连、跨维度、卸载不复制库存或守护兽；矿物不恢复；植物条件和有限再生预算明确；个人见闻不越权；旧斗兽不重复奖惩。没有游戏运行证据必须标“未实机验证”。

## 文件所有权及实施顺序

Java 路径相对 src/main/java/net/guzhenren/tianyi/。接口定义先交付，随后串行交付行为实现，最后统一审核并修复，不并行覆盖。PLAN.md 单独维护；未列文件只读，额外改动先登记。

| 环节 | 文件 | 工作 |
|---|---|---|
| 构建 | build.gradle、gradle.properties、_pack/build.py、_pack/javac_check.py、_pack/ensure_upstream.py | 最小构建封装、编码、依赖与备份 |
| 接口→实现 | resource/FiniteSite.java、resource/SiteInventory.java、resource/SiteCatalog.java、world/FiniteSiteState.java、world/SiteSettings.java | 先定模型/NBT 接口，再完成状态机、有限模板，不重构旧 will/ResourceSite |
| 实现 | world/FiniteSiteService.java、world/SitePlacement.java、world/SiteProtection.java、world/SiteEvidence.java | 共享实物库存、位置、保护、线索及重载对账 |
| 实现 | world/EncounterService.java、world/EncounterState.java、world/EncounterSettings.java、encounter/EncounterRun.java | 3.10.2 可靠性及旧斗兽开关 |
| 实现 | TianyiMod.java、TianyiEvents.java、world/WillServer.java、world/WorldSites.java | 最小接线；默认隔离旧靠近吐物、自动归属、物流，不双发 |
| 实现 | world/JournalStore.java、net/JournalNet.java、net/JournalSyncPayload.java、journal/PlayerJournal.java；client/JournalScreen.java、client/JournalClientState.java、client/JournalClientHandler.java（仅必要改动） | 复用既有见闻及界面，不重做协议 |
| 实现 | version.properties、src/main/resources/META-INF/neoforge.mods.toml、实际 assets namespace 下 lang 文件、docs/3.10.2-兼容与可靠性.md、docs/3.11.0-有限资源点.md、docs/天意-介绍.md、docs/未完成与技术边界.md | 版本一致和准确文档 |
| 审查 | _pack/tests/ 下 SiteInventoryTest.java、FiniteSiteTest.java、SiteMigrationTest.java 及现有 EncounterRunTest.java、JournalTest.java；_pack/run_tests.py、_pack/audit_sites.py、_pack/audit_bestiary.py、_pack/verify_jar.py、_pack/verify_version.py、_pack/check_bom.py | 测试登记、字节码审计、校验及必要修复 |
| 审查 | _pack/refs/ 新证据、docs/3.11.0-验证记录.md、build/libs/ 产物 | 命令、退出码、哈希、限制 |

3.10.2 优先检查扣款→生成→startBattle→退款、investigate→give、win/lose→奖惩顺序；spawn false、满背包、损坏记录、死亡/退出/切维度、未加载实体须明确处理。未加载不等于死亡，不能据此发奖励。静态 Map 在退出/停服清理，限流使用统一服务端刻。旧 phase/baseGranted/stakePaid/settled/penaltyPending 保留，迁移不自动奖惩。单独出包通过再做 3.11.0。

## 三类状态机及有限生成

点为世界共享 UUID siteId，不按玩家刷新。保存 dim、pos、template/version、整数初始库存、植物终身预算、物理槽位/方块位、revision、物化进度、守护 UUID、region marker、枯竭墓碑。

RESERVED→MATERIALIZING→ACTIVE→DEPLETED；植物且根基完好可 ACTIVE→DORMANT→ACTIVE；载体/根基毁坏→DESTROYED 永久结束；未知 schema 或物理冲突→QUARANTINED 停发。逐槽位记录物化进度，重载不能重投全批。卸载暂停，不回初态；矿物/守护库存枯竭不回 ACTIVE。

建议默认平衡值（非原著定量）：只在主世界已加载区块，距玩家 48–128 格，每 200 刻全服最多检查 4 候选/落地 1 点；8×8 区块区域至多 1 点，全局初始上限 64。生成记号与墓碑持久化，不因采空删除后重刷；不强加载、不无限扫描。

* 蛊材生长地：1–3 植物位，初始 4–8 单位。必须核实真正植物/材料注册与语义，不将未炼化蛊/蛊方当植物。根基、指定土壤、水分/光照/群系符合，已加载累计 3 游戏日，每次最多再生 1，终身额外预算最多 4。条件及预算可调查；离线/卸载不补算，毁根或用尽预算永久枯竭。
* 矿脉露头：3–6 小型矿石位，永不恢复。核实真实方块及 loot；幸运/精准采集影响数量时选真实有限容器库存或经验证掉落上限，不能将矿石数当固定物品数。
* 守护资源地：真实容器固定 4–8 单位，守护兽 1 只、最多 2；不锁击杀取货，不补击杀奖励，不重生，不以离场失败强制战斗。

具体植物/矿石/材料 ID 当前 UNVERIFIED；启用前须补实际注册、loot/procedure 和数量证据。无法核实植物则关闭模板并报告阻塞，不能虚构物品或把自动送货宣传为三类完成。

## 单一物品来源、库存守恒与崩溃边界

首选原版真实容器库存为唯一权威：一次性有限物化后由原版取物/漏斗/破坏掉落；账本只记批次进度和诊断，容器毁坏不得再按账本发货。载体丢失永久结束、不补发。不采用先采者自动归属和旧物流。

initial + verifiedPlantGrowth = unmaterialized + physicalRemaining + transferredOut + destroyed，全部非负整数，growth≤终身预算，矿物 growth=0。原版掉落、本体掉落、交互赠送只能有一条来源。若主动采集载体须阻止重复原版掉落，并覆盖漏斗、爆炸、活塞、创造模式；做不到就用有限实物容器或关闭模板。

事务 siteId/slot/revision/txnId：PREPARED→APPLIED→COMMITTED，逐槽位进度及物理标识对账。服务端串行校验距离/维度/loaded/方块指纹/库存；两玩家同刻同槽仅一次成功，满背包留容器，未知去向不补货。区块未加载挂起；守护兽仅匹配 UUID 的死亡事件可确认死亡，缺失实体 UNKNOWN、不补生。

SavedData、区块、玩家文件不原子，setDirty 不是 fsync。正常保存/重启须幂等；任意强杀跨文件窗口首版保守隔离、可能损失且不主动复制，不承诺严格 exactly-once。验证时需注入物化各阶段故障并记录限制。

## 合法位置与保护

逐位置核查维度白名单、world border/build height、所有区块已加载、稳定支撑、头部及实体 AABB 无碰撞、无液体/火/岩浆/危险块；高度图仅找候选。避开出生保护、容器/方块实体、床/传送门和可识别建筑，建议距出生点≥128 格、已知建筑/容器≥32 格。只改模板明列自然块/空气，保存原状态指纹，提交前重验，变化则取消、不覆盖或回滚玩家新建筑。守护兽不生水面/空中/屋内/玩家身上。

SiteProtection 返回 ALLOW/DENY/UNKNOWN。普通原版环境经出生保护、边界、自然方块、建筑/容器避让与事件检查通过后返回 ALLOW，不能仅因没有第三方领地 API 就把全世界判 UNKNOWN。已配置/检测到需接入但无法确认的保护系统或无法确定的权限返回 UNKNOWN，须管理员白名单区域显式允许；DENY 永不绕过。无统一领地 API，不宣称全兼容，提供严格白名单模式供整合服选择。准确事件签名先查本地 javap；取消事件须尊重。分别验证容器交互、放置/采集、漏斗、爆炸、活塞及怪物破坏，损坏只消耗或结束，不补货；允许玩家陷阱和绕行。

## 静态核验白名单及 API 证据

2026-10-25 已读 BeastCatalog.java、BeastStats.java、EncounterBeasts.java、docs/3.10.1-奇遇接入蛊兽名录.md、audit_bestiary.py。候选仅 guzhenren:hui_lang 灰狼、guzhenren:tu_lang 土狼，原生 HP/armor/attack 均 20/0/3；两表有条目，历史文档含六兽字节码校准。称“静态核验白名单候选”，不能称实测兽。第三种不默认启用。

现有 audit_bestiary 第一关检查 lang 名字，不足证明 EntityType 注册。审查环节需从锁定 SHA 本体注册初始化字节码记录完整 ID→EntityType→实体类映射及 javap 命令/输出；核 createAttributes、生成/死亡 procedure、loot、繁殖/分裂/召唤再启用。运行时查不到、非合适 Mob、create/addFreshEntity 失败就停点，不随机 fallback。EncounterBeasts 注释 85 与文档 89 的差异按实际表核清，不将全池用于守护。

资源证据逐项列完整 ID、item/block 类型、注册字段/数据文件、loot/procedure、植物语义及数量规则。旧 WorldSites.WILD_GU 含未炼化蛊/元老蛊，不能当植物证据。_pack/refs/INDEX.txt/API_SLIM.txt 主要是外层 NPC 签名，本次不调用，也不能证明实体/植物/保护接口。

复用现有源码调用：EncounterState 的 SavedData.Factory、computeIfAbsent、save(CompoundTag,HolderLookup.Provider)；JournalStore 的 tianyi/journal/entries；JournalNet 的 registrar("2")、playToClient/playToServer、ctx.enqueueWork、PacketDistributor.sendToPlayer。属于现有源码证据，仍需本次编译，不猜新方法。

外部来源：[NeoForge 1.21.1 SavedData](https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/)、[1.21.1 payload](https://docs.neoforged.net/docs/1.21.1/networking/payload/)，核对日期 2026-10-25。web_search 返回 SavedData/payload 注册摘要；完整 fetch 分别 404/超时，准确重载仍 UNVERIFIED，以本地 javap/编译为准，不用最新版或 1.21.8 签名替代。

## 实物线索与见闻同步

线索为有限容器中的原版纸/书或真实现场，书组件签名先查 javap；不发货币、不重复造货，不泄露幕后评分/全图。本人调查/采集/目击→SiteEvidence 重验同维度/距离/现场事实→JournalStore 保存→玩家 dirty→JournalNet 合并同步，复用已有界面和 payload ID。key=siteId+事件种类+revision 去重；客户端仅发动作/已知标识，不接受库存、阶段、奖励或他人 UUID。保留收藏/存疑，只保存本人有证据的观察时刻，不隔空更新别人采空。

保留 10 刻限流但 dirty 必须在下一可发刻发送，不能丢最终状态；登录/重生/切维度/开面板恢复个人数据，退出/停服清静态 Map，统一服务端刻，包条数/字符串有上限，多人隔离。

## schema 与旧档迁移

保留 tianyi_encounters（schema=1、players 字符串快照）、tianyi_will 及其它旧键和 tianyi/journal/entries。新 tianyi_finite_sites 使用 schemaVersion=1，含 regionMarkers/tombstones/transactions。不将旧 ResourceSite 浮点恢复和自动归属物流重解释为新节点；默认隔离旧自动产出，保留历史数据及明确配置入口。

encounter 1→2 仅加诊断/事务字段，保留旧 phase/baseGranted/stakePaid/settled/penaltyPending；升级不自动奖励/退款/罚款/生兽。缺字段保守默认，无版本仅接受已知旧格式，较新 schema 只读隔离、不保存覆盖。坏行保留原 NBT/原因、停奖惩，不能 catch 跳过后写档丢证据。校验 UUID/资源 ID/上限/负数/溢出。关闭旧斗兽保存进行记录并诊断，不伪造 LOST 扣款。仅副本迁移，保留迁移前文件/哈希及 roundtrip/未来版本测试。

## 构建任务及校验

项目 glob 无 gradlew* 或 gradle/wrapper；已确认 wrapper properties 不存在。不能假定 mc_gradle 可用，不手写 wrapper、不 scaffold、不重建 build.gradle。当前未配 foojay，不能声称自动供 JDK；固定 temurin-21/profile/本地 jar 路径须核对，不改全局环境。

可最小追加 Exec 任务 tianyiOfflineBuild→_pack/build.py、tianyiOfflineTests→run_tests.py、tianyiSiteAudit→audit_sites.py，参数化 Python/工作目录、UTF8、传播非零退出码，避免 java jar 覆盖 Python 正式产物。build.py 已有 ensure_upstream→javac→tests→pack→verify_jar/verify_version/check_bom/audit_mixins/audit_bestiary/audit_no_ai/audit_will 七审计，将 audit_sites 纳入同链，不重造打包器，不 --no-tests。

无 wrapper 时直接在本地执行 python _pack/build.py（非 wrapper 构建），本次已实际跑通。如以后确需 wrapper，必须由可追溯官方 Gradle 标准 wrapper 任务生成并记录版本/校验和，不手写。任何实际 wrapper 构建必须用 mc_gradle：projectDir=D:\daima\mcmod\天意，task=tianyiOfflineBuild，args=[--offline,--no-daemon]，timeoutMs=600000；补测 tianyiOfflineTests/tianyiSiteAudit 各 300000。wrapper 缺失且仅 mc_gradle 可用则明确阻塞并交人工执行既有脚本，不能填造通过。设置 PYTHONIOENCODING=utf-8、PYTHONUTF8=1。

验证顺序：

1. 纯逻辑三类状态机、整数守恒、植物条件/预算、矿物 growth=0、双玩家同刻、事务重放、区域/墓碑；resource 加进 run_tests PURE_PKGS 并登记 SUITES。
2. schema/缺字段/坏行/未来版本、旧键及注册/payload ID diff、旧进行斗兽不重复奖惩、关闭入口不扣款。
3. 故障注入物化各阶段、spawn false、卸载/未知实体、满背包、漏斗/容器破坏、死亡/退出/重启、见闻 dirty 限流，载体丢失不补发。
4. 字节码/静态核验 SHA、实体注册映射、真实 item/block/loot、保护签名、新链无 NPC 调用、旧压制/吐物默认关闭、专服不加载客户端类。
5. 修改后完整离线链，逐步真实退出码/测试输出/版本/jar SHA，禁止嵌本体/API jar，3.10.2 与 3.11.0 分别留存；不把基线或旧日志当修改后结果。
6. 环境允许才在隔离实例/副本测三类点、两玩家、陷阱绕行、保护拒绝、漏斗爆炸、保存重启、植物跨日条件/矿物不恢复/见闻隔离。无可启动环境标“未实机验证”；编译/纯逻辑不能证明 AI、procedure、掉落、领地兼容、渲染和性能。

最高风险为名录静态属性不等于真实注册/AI/掉落安全，植物矿石 ID 尚未证实；其余为多文件崩溃窗口、保护 UNKNOWN、无 wrapper/绝对依赖路径及旧吐物物流双发。首版用既有服务/见闻最小接线，真实容器只发一次、载体丢失永久结束；不以大重构或占位模板宣传完成。交付分列离线执行、静态证据、实机未验证、阻塞项。

## 骨架交接记录

已写入 resource/FiniteSite、SiteInventory、SiteCatalog，world/FiniteSiteState、SiteSettings，_pack/tests 下 SiteInventoryTest，以及 build.gradle、_pack/build.py、_pack/javac_check.py 共9文件。本次未运行编译。备份已完成，位置 `_backup/finite-sites-20261025-201444`，160文件哈希清单。

本地实际执行 RED：以temurin21 javac编译三个纯模型与SiteInventoryTest成功，然后java运行测试在 growVerified(1) 抛 UnsupportedOperationException，退出码1，符合增长行为尚未实现的预期。输出目录 `_pack/probes/finite-red`。行为实现与测试登记待补；目前build.py新增audit_sites但该脚本尚待实现，不能称全链通过。

证据入口 `_pack/refs/finite-resources-main.md`；另有 finite-registry-main.javap.txt、finite-guards-main.javap.txt、finite-guard-procedures-main.javap.txt、finite-soul-procedures-main.javap.txt、finite-runtime-api-main.javap.txt。这些均静态证据，不是实机。实测运行基线profile为21.1.263。

## 3.10.2 实施交接记录

可靠性、旧资源独立默认关闭、去猎兽损宿命/拾取雷雨、个人见闻待发同步、schema2隔离保留及金额字符串边界均已完成，版本3.10.2。审查环节独立修复了非字符串warning/message/WAITING id被getString静默吞掉的问题，补真实NBT回归。小修后本地实际运行 `_pack/probes/run_encounter_persistence.py` 与 `_pack/build.py`，退出码均0，77源编译/全部已登记离线测试/8审计通过。产物 `build/libs/tianyi-3.10.2.jar` 501457 B，SHA256 `72a0152d84d208e9debddaa19c3dccd1a7b055ebd4592bee116986816eb5364b`。见 `docs/3.10.2-验证记录.md`。未安装、未实机验证。有限资源骨架仍未启用，growth测试RED尚待3.11实现。

下一阶段：完整实现3.11世界资源、个人线索、保存/对账，登记测试与更新audit_sites使其验证正式接线而非永久要求未接线。随后独立审查，结论以实际命令输出为准。新原始签名 `finite-placement-extra-main.javap.txt` 与 `finite-eventhooks-main.javap.txt` 已核实 onBlockPlace返回true表示取消，BlockSnapshot.create/restore及WorldBorder/NBT/植物签名。遵循合法保护段的ALLOW/UNKNOWN区分，避免全局静默禁用。

## 3.11 模型交接记录

首个3.11大范围实施中途失败且无报告，未作为完成。重试后完成 SiteInventory/FiniteSite/SiteCatalog/FiniteSiteState、FinitePersistenceProbe.java 与 run_finite_persistence.py。此前观察到runner因Windows中文绝对类路径找不到已编译测试类，按既有成功probe改runtime classpath为项目内相对路径后重跑：SiteInventoryTest PASS（原增长RED转绿），FinitePersistenceProbe 237 checks PASS，退出码0。未运行世界/服务器。3.10.2包未触碰，版本仍3.10.2，模型尚无世界接线。

当前模型API：reserve→beginMaterialization→replace→prepare→实物操作→advance(APPLIED)→materializeSlot/materializeGrowth→replace→advance(COMMITTED)。库存观察observeRemaining、破坏destroyCarrier；守护绑定不因实体缺失清除。加载任何PREPARED/APPLIED未决事务会隔离原始整账，禁止补货；首版可保守停止而不伪造恢复。实际签名以最新源码为准。

下一有界阶段：只做SitePlacement/SiteProtection、SiteSettings与SiteCatalog正式模板核验入口（原版环境可生成、严格区域可选），生产方块与实体物化方法及取消事件/不补货规则，暂不接tick/版本。之后完成FiniteSiteService/SiteEvidence、事件及见闻接线、测试与文档版本，最后统一审查。

### 文档修正（本地实际执行）

bump_version.py 会把含“产物/版本/依据/使用（”锚点且带 tianyi-X.Y.Z 的行一律改写成当前版本，导致 3.8.0/3.8.4/3.9.0 三份历史文档的产物声明被改成 3.10.2。这三处已改为明确的历史表述（不再携带 tianyi-X.Y.Z 令牌，后续 bump 不会再改写），并把 `天意-介绍.md` 的过期字节数（432,391→501,457）、源文件数（63→77）、审计项数（7→8）按实测修正，同时把无法复核的逐组测试项数改为“以 run_tests.py 实际输出为准”。随后单独运行 `python _pack/verify_version.py`，输出“版本一致 OK”。这是文档一致性修正，不改变任何代码或产物。

## 3.11.0 交付记录（本地实际执行）

世界服务实现（FiniteSiteService/SiteEvidence/SiteBudget/SiteNotice + 接线）与修复项（H1 逐站隔离与恢复入口、M1 abandon/retire、M2 瞬时拒绝改 PAUSED、M3 终态 observe 守卫、M4 球壳缓存、M5 自然方块补全、M6 32格几何避让、L1 回填防御）均已落盘。

本地实际执行并修复的三处真实缺陷（逐项复现，非仅据报告）：
1. **区域判定反转（致命）**：`SiteBudget.regionAvailable` 把参数当“已占用”再取反，调用方传的是“未占用”，导致区域恒判不可用、资源点永不生成，整个玩法被架空。SiteBudgetTest 抓到；已按调用方语义修正并写明历史坑。
2. 两个 probe 运行器的 Windows 中文绝对路径放进 `-cp` 导致 ClassNotFoundException（不是测试失败）；改为相对仓库根路径。
3. 两处审计误报：`audit_sites` 把类注释里“不调用 com.guzhenrenlibrary”当真实依赖（改为去注释后判定）；`audit_will` 把木桶容器 `setItem` 当写玩家背包（改为只禁背包写入 + 要求 setItem 能认出容器）。

最终验证（均为本地实际执行，退出码0）：
- `javac_check.py`：83 源 `javac exit 0`
- `run_tests.py`：全部套件通过（新登记 SiteInventoryTest / SiteBudgetTest 48项 / SiteNoticeTest 44项）
- `run_finite_persistence.py`：`FinitePersistenceProbe PASS: 279 checks`
- `run_encounter_persistence.py`：真实压缩 NBT 往返 PASS
- `audit_sites.py` / `audit_will.py`：全绿；`build.py` 8 项审计全绿
- 产物 `build/libs/tianyi-3.11.0.jar` 560,826 B，SHA256 `0a217779e28fecc06bc2926221f373212f4a3722f82950ffc0c0e839780fd445`
- `tianyi-3.10.2.jar` 未覆盖，仍为 501,457 B / `72a0152d…364b`

**未实机验证**：三类资源点真实生成、守护兽 AI/掉落/死亡过程、保护插件兼容、漏斗/爆炸/活塞、崩溃窗口跨文件一致性，全部未进游戏。文档见 `docs/3.11.0-有限资源点.md`。

遗留未接线（明确不算完成）：`abandon`/`retire(MATERIALIZING)` 与 `clearTransaction`/`releaseQuarantine` 目前只有模型与账本 API，**尚无游戏内命令/权限入口**；卡死站点退役与隔离恢复仍需管理员命令才能真正使用。`transactions` 无自动修剪（4096 上限），长期生长后需评估。

## 3.12.0 地形资源扩展（本地实际执行）

依据用户提供的《蛊真人整合包知识库_AI版》§7.1「部分蛊材只在专属流派地形刷新，找材料先找地形」与 §6.2/§6.3 蛊兽分布，扩展资源点。

静态核实（本体 jar）：15 个流派地形均为真实群系（data/guzhenren/worldgen/biome/）；真实矿石方块与单条目掉落表：`fangkuaiyuanshiyuankuang`→`gucaiyuanshi`、`chilianjinyuankuang`→自身、`wen_ding_shi_kuang`→`wen_ding_shi`；真实实体类名：HaoDianLangEntity / HuEntity / HuoyanxiongEntity / YaoYaQuanEntity（连同既有 HuiLangEntity / TuLangEntity），矿石类名 FangkuaiyuanshiyuankuangBlock / ChilianjinyuankuangBlock / WenDingShiKuangBlock / XuanjinBlock。

实现：新增 resource/SiteTerrain 纯映射（15 地形、群系白名单、地形→守护）；SiteCatalog 增至 6 模板并加 biomeCandidates；SitePlacement 逐槽位群系门控、方块与掉落物分离、六兽与四矿类名双校验；SiteSettings 增三个默认关闭的露头开关并与 oreEnabled 叠加；FiniteSiteService 改为「先取地形再挑模板」，不匹配只 continue。守护兽不设地形门槛（避免数据包未生成时永不出现），按地形选兽、其余退回灰狼/土狼；不 setTarget，尊重本体转数威压。

本地实际执行（退出码0）：javac 84 源 exit0；全部测试套件通过（SiteTerrainTest 52 项、SiteBudgetTest 48 项、SiteNoticeTest 44 项）；audit_sites 全绿（含 15 地形、逐槽门控、六兽四矿类名核对）；FinitePersistenceProbe 279 项；build.py 8 项审计全绿。产物 `build/libs/tianyi-3.12.0.jar` 565,735 B，SHA256 `9df11ec28fa4996f5e6f03930d20ec6d7e14ac27cb8414ea18f732d6f2b070d9`。3.11.0（560,826 B）与 3.10.2（501,457 B）均未被覆盖。

**未实机**：15 个群系在真实世界的生成与密度、四只地形守护兽 AI/掉落、guzhenren 群系表层是否为原版土壤（影响蛊材能否落地）、元石露头的「地下」形态（仍只按地表高度找点）。文档 `docs/3.12.0-地形资源扩展.md`。

---

# 历史归档（保留原文，不作为当前指令）

# 天意 3.1.0 —— 一至五转世界补强：实施计划（PLAN）

> 本文件是当前实施的唯一依据；其余材料以 `_pack/refs/` 为补充证据。
> 基线核对日期：开工时实测。工程版本 **3.0.0**（不是 2.10.0），NeoForge **21.1.263**，MC 1.21.1，JDK 21。

---

## 0. 已核实的事实（不许再凭记忆推翻）

### 0.1 两个本体的真实分层

| 层 | modId | jar | 内容 |
|---|---|---|---|
| 外层扩展 | `guzhenren_library` 0.1 | `D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar`（sha256 `3733c20f…`） | **696 个 class，`com.guzhenrenlibrary.*`**：势力、NPC 生活、任务、身份、关系、商贸、RTS、地形、五行域 |
| 内层本体 | `guzhenren` 0.1 | 上面那包 `META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar`（sha256 `b6d314d3…`，22371 class） | MCreator 产物：蛊虫、炼蛊、道痕、玩家变量 |

**关键结论：需求里点名的 `FactionBrain / FactionTerritory / FactionJourney / JourneyMaterializer / Resident / ResidentService / IdentityManager / QuestLog / QuestLedger / NpcTrade / NpcStock` 全部在外层 `com.guzhenrenlibrary.*`，内层 `net.guzhenren.*` 里一个都没有。**（已用 zip 索引逐个核过，0 命中于内层。）

工程此前只把**内层** jar 放上编译 classpath，所以 3.0.0 的代码对势力/NPC/商贸是**零对接**——
`grep` 全树，`FactionBrain|NpcStock|Resident|IdentityManager|QuestLog` 在 `src/` 下命中数 **0**。

### 0.2 编译期 classpath（已改好，别回退）

`_pack/libs/guzhenren_library-api.jar`（2.95 MB，697 条目）由 `_pack/extract_library_api.py`
从外层包抽出 `com/guzhenrenlibrary/**` 生成，已加入 `_pack/javac_check.py`。
**已验证**：引用 `FactionTerritory.of(level)` / `ResidentRegistry.of(level)` /
`territory.journeys()` / `NpcStock.restock(mob)` 的一次探针编译 `javac exit: 0`。

`neoforge.mods.toml` 已加 `[[dependencies.tianyi]] modId = "guzhenren_library" type = "required"`。

### 0.3 构建与验证通道（**不要跑 Gradle**）

`build.gradle` 仅供 IDE 索引；真正出包走 Python 直连 javac + zipfile：

```
python _pack/javac_check.py     # 编译全部 src/main/java → _pack/javac_out，期望 javac exit: 0
python _pack/run_tests.py       # 离线行为测试，期望「全部测试套件通过」/ exit 0
python _pack/pack_jar.py        # 打包 → build/libs/tianyi-<ver>.jar（含结构校验）
python _pack/verify_jar.py      # 必需条目校验
python _pack/verify_version.py  # 版本 4 处一致
python _pack/check_bom.py       # BOM 检查
python _pack/bump_version.py X.Y.Z   # 显式版本号（不要用 minor/patch 关键字）
```

环境：`$env:PYTHONIOENCODING='utf-8'`。PowerShell 有引号/编码地狱，**一律写 `.py` 文件再执行，不要命令行内联多行 Python**。

### 0.4 已核实的本体 API 签名（完整版见 `_pack/refs/API_SLIM.txt`，49 KB）

```
FactionTerritory FactionTerritory.of(Level)                    // 唯一入口，按 Level 取
  .journeys() : FactionJourneys
  .addSupplies(FactionDef, int) : int
  .addPower(FactionDef, int) : int
  .stance(FactionDef, FactionDef) : FactionStance
  .addStance(...) / .setStance(...)
  .chronicle() : FactionChronicle
  .toTag() / .fromTag(CompoundTag)

FactionBrain.tick(ServerLevel)
FactionBrain.runRound(ServerLevel, FactionTerritory, long)
FactionBrain.{ally,declareWar,blame,makePeace,absorb,exterminate}(ServerLevel, FactionTerritory, FactionDef, FactionDef)

FactionJourneys: all() / size() / byId(int) / activeCount(long)
                 record(ServerLevel,FactionDef,FactionDef,JourneyKind,long,long) : FactionJourney
                 tick(ServerLevel,FactionTerritory,long) / settleTeam(...) / markFailed(journey,territory,long)
                 inFlight(from,to,kind) / durationFor(kind,double) / toTag() / readTag(...)
JourneyMaterializer.tick(ServerLevel,long) / sweepOrphans(ServerLevel) / abandon(int) / materializedCount()

ResidentService.registry(Level) : ResidentRegistry
ResidentService.residentOf(Entity) : Resident
ResidentService.{tick(ServerLevel), simulate(ServerLevel,Resident,long), enroll(Entity), onDeath(Entity)}

ResidentRegistry.of(Level) / all() : Collection<Resident> / get(UUID) / aliveCount() / size()

Resident: npcId():UUID / persona() / displayName() / zhuanshuInt() / cultivation()
          dimension() / x() y() z() / positionKnown() / state():State / stateUntil()
          gu():List<String> / feuds():List<UUID> / log():List<String> / latestLog()
          travelTo(double,double,double) / at(dim,x,y,z,tick) / setState(State,long)
          remember(String,long) / addFeud(UUID) / markDeceased() / toTag() / fromTag()

NpcStock: restock(Mob):int / tick(Mob):int / dueForRestock(long,long)
          longevityIds():Set<String> / isLongevityGu(ItemStack) / longevityName(String)
          isCurrency(ItemStack) / isTradeable(Mob,ItemStack) / protectedId(String,String)
          PROTECTED_KEY / RESTOCK_KEY / LONGEVITY_KEYWORD / MAX_USED_MAIN_SLOTS

NpcTrade: currency() / priceOf(Item) / shelf(Mob,NpcPersona,int):List<Offer>
          countInInventory(Mob,Item) / takeOne(Mob,Item) / pay(Player,int):Payment
          countCurrency(Player) / consumeCurrency(Player,int)
NpcTrade$Offer: item()/basePrice()/price()/category()/stock()/soldOut()

IdentityManager: active(Player):PlayerIdentity / activeId(Player):UUID / activeTitle(Player)
                 identities(Player):List<PlayerIdentity> / find(Player,UUID) / findByName(Player,String)
                 switchTo(ServerPlayer,UUID):boolean / switchToName / create / rename / delete

QuestLog / QuestLedger / QuestLedgerData  // 见 API_SLIM.txt 669~722 行
```

---

## 1. 架构：三条边界（强制）

```
net.guzhenren.tianyi
├── bridge/     GuzhenrenBridge —— 唯一允许 import com.guzhenrenlibrary.* 的包
│                 · 读：territory/resident/identity/trade/alchemy/寿蛊 事实
│                 · 写：addSupplies/addStance/travelTo/restock 接管/…
│                 · 每个方法失败都返回明确的“不可用/被拒”而不是抛异常穿透 tick
├── fact/       WorldFact + FactLedger + IntelNet —— 纯逻辑，不 import Minecraft
├── action/     InterventionAction / ActResult —— 纯逻辑接口；实现放 action/impl（可碰 bridge）
└── world/      Minecraft 接线：WillServer / Observation / WorldExec / *State(SavedData)
                will/ 纯逻辑核心（已有，扩展不重写）
```

**硬约束**
- `fact/`、`will/`、`action/`（接口与结果类型）**不得 import 任何 `net.minecraft.*` / `net.neoforged.*`**，否则离线测试跑不起来（`run_tests.py` 靠这个）。
- 身份一律 `UUID`；维度一律 `ResourceLocation` 全名 `String`（`"minecraft:overworld"`）；时间一律 `long`（`level.getGameTime()`）。**禁止用 `String.hashCode()` 代替身份。**
- 所有 SavedData 根节点带 `int schemaVersion`，反序列化先看版本再决定路径；旧档只读不删。
- 跨维度/重连/重启/区块卸载不得刷新期限、复制货物、丢失在途事件。
- 服务端权威。不新增第二种通用货币（只有元石 / `NpcTrade.currency()`）。

---

## 2. 八项补强的落点与判定标准

| # | 补强 | 新增/改动的文件 | 验收判定（可测，不是「写了类」） |
|---|---|---|---|
| 1 | 情报传播与身份识破 | `fact/WorldFact.java` `fact/FactLedger.java` `fact/IntelNet.java` `world/IntelState.java` `world/FactSource.java` | 无目击者的 NPC 的 intel 集合里查不到该事实；事实带 `hops/expiresAt/confidence`；同 id 事实只存一条 |
| 2 | 意志载体与凡人反侦察 | `world/CarrierState.java` + 扩 `will/Carrier.java`（保留现有签名） | 载体被清除后 `WillCore` 的观测通量为 0；反侦察只切断渠道，不改数值 |
| 3 | 资源点经营与自然蛊生态 | `resource/ResourceSite.java` `resource/SiteModel.java`（纯）`world/SiteState.java` | 过度采集 → 储量降 → 恢复变慢 → 产量上限生效；两个来源不会同 tick 双发 |
| 4 | 有限库存、商队物流与寿蛊 | `trade/CargoBatch.java` `trade/CargoModel.java`（纯）`world/CargoState.java` `world/RestockGuard.java` | 同一 `batchId` 结算两次只减一次货；被截获的批次不会同时出现在商队和背包；`NpcStock.restock` 被接管后不再无中生有 |
| 5 | 野生兽群与栖息地 | `herd/WildHerd.java` `herd/HerdModel.java`（纯）`world/HerdState.java` | 区块卸载后重新加载不新增计数；捕获/释放/繁殖/死亡账目平衡 |
| 6 | NPC 利益与人劫 | `action/NpcGoal.java`（纯）`action/NpcAction.java` `action/impl/*` | NPC 可拒绝（返回 `REJECTED` 且带理由）；同一事件只生成一条追捕 |
| 7 | 炼蛊与传承 | `alchemy/AlchemyRecord.java`（纯）`bridge` 侧读取 `LianGuJinDu` | 只拾取产物不产生 `SUCCESS` 记录；进度 100 不等于成功 |
| 8 | 天意真实接线 | 扩 `world/WillServer.java` `world/WorldExec.java`；`WillCore` 只做增量 | 事实为空 → 布局不产生；切断渠道 → 布局 `BLOCKED` 并写失败记录 |

### 2.1 天意现状（别推倒重写）

`WillState.core() : WillCore` 已存在；`WillServer.onServerTick` → `runDecision` / `advanceJobs` /
`applyCalamityPhase` / `settleCalamity`；`Observation.observe` 已往 `Knowledge.Store` 写事实。
**链路是通的，但只喂玩家（`SnapshotService` 读本体玩家变量）。** 本次要补的是：世界侧的
势力 / NPC / 资源 / 货物 / 兽群事实进来，以及 `InterventionAction` 真的落到这些系统上。

---

## 3. 分期

- **一期**：桥接 + `WorldFact`/`FactLedger`/`IntelNet` + `IntelState` + `FactSource` 观测接线 +
  `WillServer` 接情报 + 一组 NPC 调查/追捕行动（`NpcAction` 四个原语）。
- **二期**：资源点 + 重点商品物流（`CargoBatch` 幂等）+ 寿蛊来源审计 + 野生兽群。
- **三期**：身份识破证据链 + 意志载体 + 凡人反侦察 + 炼道/传承联动。

**每期结束必须**：`javac_check.py` exit 0 + `run_tests.py` 通过 + `pack_jar.py`/`verify_jar.py` 通过。

---

## 4. 首个必须跑通的场景（一期就要立起来，二期补物流）

本体真实存在的低转蛊材产地：以 `data/guzhenren/tags/item/` 下已核实的标签为锚
（`yuanshi.json` / `yuanlaogu.json` / `ylg.json` 已在工程内）。

```
玩家采集/抢占产地
  → NPC 目击（同维度、距离 ≤ WITNESS_RANGE、NPC 活着且 positionKnown）→ 生成 WorldFact
  → IntelNet 有限跳数/有限范围传播（商队、使者、家族）
  → 势力（FactionDef）依据 stance/利益决定反应 → ActionPlan
  → 商队/库存/供给变化（二期接 CargoBatch）
  → 天意读取这些事实，扶持或压制
  → 玩家用情报/身份/行动破局
```

必须有测试覆盖的反面用例（一期就写进 `_pack/tests/IntelNetTest.java`）：
1. 无情报来源的 NPC 不会立刻知道。
2. 身份切换不抹除已成立证据。
3. 同一事实重复生成 → 只存一条（幂等）。
4. 事实过期后不可再被当作依据。
5. 传播跳数耗尽后不再扩散。

---

## 5. 测试与接线分离

`_pack/run_tests.py` 目前只编译 `will/*` + `ledger/*` + `tests/*`。
本次**必须**把 `fact/*`、`resource/*`、`trade/*`、`herd/*`、`alchemy/*`、`action/*` 的纯逻辑文件加入
`SRC_CORE`，并把新测试套件加入 `SUITES`。测试文件放 `_pack/tests/`，包名与被测类一致。

---

## 6. 交付纪律

- 不改 `D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar`，不把它或内层本体打进附属 jar。
- 不装进真实游戏实例（`D:\game\mcmod\1\.minecraft`）——除非用户明确要求。
- 不创建 git 提交。
- 交付时区分「已验证」（javac/测试/pack/verify 的实际输出）与「未实机验证」（进游戏才看得见的）。
- 备份在 `_backup/pre-v310-*`。
