# 蛊真人升仙机制 Implementation Plan

> **执行方式：** 按任务逐项实施，可用 `superpowers:executing-plans`；用复选框（`- [ ]`）跟踪进度。

**Goal:** 基于给定 10.20 本体，在天意附属中实现有原著依据的完整五转升六转过程。

**Architecture:** 用纯 Java 状态机承载三步主线及并发灾劫、领悟、升炼过程；服务端负责世界接线和持久化。本体兼容层只在已审计调用点读写，仙窍由真实空间和独立数据构成；所有特殊规则以有依据的 profile 驱动。

**Tech Stack:** Minecraft 1.21.1、NeoForge（运行小版本待查 profile，依赖最低 21.1.232）、Java 21、现有离线 javac/Python 打包链。

**Spec:** `docs/superpowers/specs/2026-10-25-immortal-ascension-design.md`。

**状态：** 用户已于 2026-10-26 要求继续完善升仙项目，实施进行中。Task 1 已增加固定哈希本体的 rank/stage 潜在调用图和可复现扫描器（见 `docs/升仙-六转兼容审计.md`），但完整本体兼容审计、仙窍空间能力及特殊 profile 仍未冻结；Task 2 的纯逻辑状态机与 Task 3 的 SavedData 会话账本已加入，玩家动作/物品/世界效果服务仍未接线。不得将缺少原文证据的规则留给后续程序员“凭感觉补完”。

## Global Constraints

- 用户文件：`D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar`。
- 内层 SHA256：`b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`。
- 沿用当前 NeoForge 1.21.1、Java 21 工程。
- 本计划现处于实施阶段。允许在本工作区完善代码与证据文档，并使用项目现有依赖做编译检查；不修改给定本体 JAR，不安装到真实游戏目录，不改玩家存档。测试执行须由用户明确要求。
- 碎窍、纳气、放蛊是三大步骤；灾劫、人劫、师法自然是与之交错的过程。
- 碎窍后不可无代价重试；师法自然不是日常观察加成功率；宿命完整不普遍封锁升六转。
- 升仙不等于炼成仙蛊，不自动增加寿元或恢复青春。
- 待核项没有补齐前，不能宣称覆盖原著全部升仙变体。
- 不覆盖现有 `PLAN.md` 或其他功能计划，不修改给定 JAR，不改真实游戏目录和存档。若未来用户明确要求改本体或另建工程，再调整本计划。

## Review Focus

1. 六转被本体修炼/每刻/死亡复制程序重置：Task 1 审计并在 Task 8 实机验证，不能仅用纯逻辑通过作证明。
2. 重启落在物品转移或仙窍提交的中间窗口：Task 3/7 注入故障，未知记录隔离，不重发、不恢复已碎空窍。
3. 第二空窍及特殊体质套错普通路线：Task 2 用空窍 ID 和 profile 检查，缺能力在碎窍前拒绝。
4. 领悟、纳气和灾劫被做成串行、菜单暂停世界：Task 4 同刻运行，Task 8 双人实机验证。
5. 内劫被误写成永远不能援助、普通人被强制持有五转/仙级本命蛊：Task 6/7 覆盖明确反例。

## 文件与责任边界

以下新增路径是本次提出的文件名，不代表本体已经有这些 API。Java 相对目录均为 `src/main/java/net/guzhenren/tianyi/`。

- `ascension/AscensionModel.java`：不可变输入、状态、结果类型；不引用 Minecraft。
- `ascension/AscensionRules.java`：三气、阶段、失败原因、规则 profile。
- `ascension/AscensionMachine.java`：单步状态演化和并发子过程协调。
- `ascension/InsightCatalog.java`：可求知条目及有限预算结算。
- `ascension/TribulationCatalog.java`：有章节依据的灾劫与载体定义。
- `ascension/ApertureFormation.java`：仙窍形成、定型、底蕴结算。
- `ascension/GuRefinement.java`：可选余气升炼，独立于成仙结果。
- `world/AscensionStore.java`：会话、事务、版本、挂起与恢复。
- `world/AscensionService.java`：服务器动作、过程载体及世界事件协调。
- `world/AscensionUpstream.java`：唯一的本体字段/物品/过程适配入口。
- `world/ImmortalApertureStore.java`：仙窍主人、时钟、资源、仙元、访问权限。
- `world/ImmortalApertureService.java`：仙窍空间、门户、来访与内部灾劫。
- `net/AscensionNet.java`、`net/AscensionActionPayload.java`、`net/AscensionSyncPayload.java`：受控动作与可见快照。
- `client/AscensionScreen.java`、`client/AscensionClientState.java`：玩家的调气、求知、放蛊界面与快照。
- `TianyiMod.java`、`TianyiEvents.java`：必要的注册、刻更新、登录退出、死亡及跨维度接线。
- `_pack/tests/net/guzhenren/tianyi/ascension/`：纯逻辑用例；运行器 `_pack/run_tests.py` 登记新包与套件。
- `_pack/refs/ascension/`：准确字节码、证据索引与依赖指纹；`docs/升仙-原著规则清单.md`、`docs/升仙-验证记录.md`：可追溯结论。
- 仙窍维度资源及传送适配的精确文件清单在 Task 1 按现有运行库核实后登记，必须在 Task 7 开始前固定，不猜测新版本 NeoForge API。

## 公共接口约定

为防任务间自行发明类型，统一从 `AscensionModel` 导入以下类型；具体字段以设计文档第 5 节及 Task 1 的冻结规则补全：

- `SessionKey(UUID sessionId, UUID ownerId, String apertureId)`。
- `Phase`：`PREPARING, BREAKING, BROKEN, ABSORBING, OPENING, FORMING, IMMORTAL, FAILED`。
- `Outcome`：`RUNNING, IMMORTAL, FAILED_ALIVE, DEAD, SUSPENDED, QUARANTINED`；后两者为技术状态，不冒充原著修为状态。
- `Action`：`START, STOP_BREAKING, CONTROL_QI, SEEK_INSIGHT, PLACE_GU, END_INSIGHT, TRY_REFINE, DECLINE_REFINE`。
- `QiState`：外部二气储量、已纳三气、人气可用量、失衡累积；全部有限非负数。
- `Session`：设计文档第 5 节的会话数据；`Frame`：本刻环境、动作与经过的服务端刻；`Transition`：新状态、领域事件、物品及世界副作用意图。
- `RuleProfile`：证据 ID、支持能力、体质/空窍规则及游戏参数；未知特殊规则不可返回默认普通路线。
- `ApertureDraft / ApertureResult`：形成中的仙窍与已提交结果；`GuRefinementResult`：未尝试/进行/成功/失败及真实消耗意图。

这些是拟实现的应用接口，不是本体签名。服务层负责将本体/Minecraft 对象转成上述快照；纯逻辑测试不得依赖游戏类。

### Task 1：冻结原著规则与本体适配证据

**Files:** 新建 `docs/升仙-原著规则清单.md`、`_pack/refs/ascension/INDEX.md` 及命名明确的 `.javap.txt` 证据；只读给定 JAR、本体缓存、现有源码与构建脚本。

**Interfaces:** 输入为指定 JAR 和设计文档；输出为每条规则的章节/上下文/例外/可信状态、字段读写表、profile 支持清单、六转冲突点、仙窍资源清单、构建解释器路径。

- [ ] 核对设计文档第 4 节所有待核项；关键规则至少核对完整段落，不用搜索摘要或百科当结论。无法核实的路线列为阻塞，普通主线与特例不能混为一条。
- [ ] 重新计算外层/内层哈希；用 `C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe` 追踪变量写入、突破、用蛊、寿元、同步、死亡克隆、本命蛊托管与真实库存。确认五转巅峰的实际编码及六转上限冲突。
- [ ] 查运行 profile，核实事件、SavedData、payload、物品组件、维度注册和传送签名；冻结仙窍维度及资源文件清单。记录每个注入点的原类、原方法和必要性。
- [ ] 解析 Python 可执行文件位置并记录为后续 `$PythonExe`；本次终端 `python` 不在 PATH，不能照抄命令声称已能运行。优先查现有 `py`/已安装运行时，不擅自安装环境。
- [ ] 以设计第 7 节逐项检查证据覆盖，输出“已核/游戏映射/阻塞”三种状态。若普通主线必需能力缺失，报告需补的具体模块，不能让玩家先碎窍。

**验收：** 另一开发者能根据文档定位完整原文和字节码，并判断每个 profile 是否可实现。这个任务只做只读分析及证据文档，不先写玩法。

**进度记录（2026-10-29）：** 新增了固定输入校验、ASM 合成字节码回归和 rank/stage 潜在调用边报告；此交付只补足字段写入调用图这一部分。所有字段消费者、效果/克隆运行时语义、仙窍空间资源与特殊 profile 仍未冻结，Task 1 保持未完成。

### Task 2：建立三步状态机与规则校验

**Files:** 新建 `ascension/AscensionModel.java`、`AscensionRules.java`、`AscensionMachine.java`；新建测试 `AscensionMachineTest.java`；修改 `_pack/run_tests.py` 的纯逻辑源码/测试登记。

**Interfaces:** `AscensionRules.validateStart(Session, RuleProfile): StartDecision`；`AscensionMachine.advance(Session, Frame, RuleProfile): Transition`。`StartDecision` 在 `AscensionModel` 中定义为 `allowed/reasons`，原因列表有限长度。

- [ ] 写失败测试：`cannotStartWithoutVerifiedAperture`、`cannotBreakBeforeRankFivePeak`、`completeBreakNeverReturnsToPreparing`、`wrongProfileDoesNotFallback`、`secondApertureKeepsFirstAperture`。断言分属空窍 ID、不可逆标志与不产生副作用。
- [ ] 将测试登记后执行 `& $PythonExe _pack/run_tests.py`，确认新用例因目标行为未实现而失败；若只是环境报错，先修环境，不能当红灯证据。
- [ ] 实现上述接口，三气归一化与容差使用 Task 1 冻结的游戏映射；拒绝 NaN、Infinity、负数、非法阶段跳转及重复动作，不写总成功率函数。
- [ ] 用 `renExcessExplodes / tianExcessDissolves / diExcessPetrifies` 分别固定已核死因；失败发生前必须达到 profile 的连续失衡条件，测试短暂偏差可恢复。
- [ ] 重跑测试，全部新用例通过，再在工程现有版本管理方式中保存检查点；此目录目前非 Git 仓库，不擅自初始化。

### Task 3：服务器会话、物品托管与不可逆存档

**Files:** 新建 `world/AscensionStore.java`、`AscensionUpstream.java`、`AscensionService.java`；新建 `AscensionRecoveryTest.java` 和 `GuEscrowTest.java`；必要修改 `TianyiEvents.java`。

**Interfaces:** `AscensionService.handle(ServerPlayer, ActionRequest): ActionResult`；`tick(MinecraftServer): void`；`AscensionUpstream.inspect(ServerPlayer, String apertureId): UpstreamSnapshot`。`ActionRequest/ActionResult/UpstreamSnapshot` 在服务类或适配类内定义，字段必须明确序号、期望 revision 和结果原因，网络层只引用这些固定类型。

- [ ] 写失败测试：重复放蛊只转移一次；本命数据占位不复制实物；存档 roundtrip 保留碎窍；未来 schema 不覆盖；角色死亡克隆后不恢复旧空窍；第二空窍不串会话。
- [ ] 执行离线测试得到有效失败，再实现会话读写、revision、唯一物品来源和幂等副作用提交。对外只允许受控本体适配写入，未知调用点不反射猜名后静默继续。
- [ ] 注入破窍已提交但世界副作用未完成、物品已托管但状态未提交等故障。正常恢复完成对账；无法判断的强杀窗口进入 `QUARANTINED`，保留原始记录且不自动补发。
- [ ] 建立退出时过程载体及伤害归属；区块卸载挂起；停服清理内存缓存。技术挂起不清除已碎状态或已领悟记录。
- [ ] 重跑测试；编译 `& $PythonExe _pack/javac_check.py`，期望退出码 0。独立服务器和真实死亡流程留给 Task 8 实测，不能用假接口测试替代。

### Task 4：纳气与师法自然并发运行

**Files:** 完善 `AscensionMachine.java`；新建 `InsightCatalog.java`、`InsightTest.java`；接入 `AscensionService.java`。

**Interfaces:** `InsightCatalog.resolve(Session, Request, List<Question>, RuleProfile, serverValidated): Decision`；窗口从服务端会话三气快照与档案容差计算，request 只含序号、期望 revision、服务端采样 tick 和白名单问题 ID。`Decision` 包含更新会话和知识 grant；服务层 CAS 保存后才返回 grant。

- [ ] 写失败测试：非窗口不可求知；重复序号不重领；断线不刷新预算；稀缺问题不能返回无限完整知识；求知同刻气量仍变化、灾劫时钟继续。
- [ ] 执行测试得到有效失败，实现有依据的知识目录和明确成本。没有原著/本体条目的知识不能自由生成；不借外部 AI 代替天意知识规则。
- [ ] 测试本人控制三气、他人动作无权改变；人气调动不创造总量；长时间离线没有新输入时不会自动获得最佳调控。
- [ ] 重跑测试与编译，记录交感窗口、额度算法中哪些是原著、哪些是游戏参数。

**进度记录（2026-10-29）：** schema 3 已保存有限求知账本和章节支持的“研究力道”主题 ID；schema 4 新增外部天地气储量、已纳三气、人气储备、失衡刻数和最后处理刻。`QiRegulator` 使用显式游戏参数按服务端刻吸收有限储量，重复刻拒绝、人气仅在储备与已纳人气间转移、离线间隔不追补最佳调控；`QiControlService` 校验主人、启用 profile、阶段、刻和 revision，产生的下一版 Session 可与求知在同刻顺序提交。schema 1/2/3 迁移及 schema 4 缺字段隔离已覆盖。全源码 Java 21 编译、8 个纯升仙逻辑测试和 2 个真实 NBT 测试通过。世界环境采样、持久化服务端刻接线及 CAS handler、灾劫并发、客户端/网络入口、知识正文均未接入；Task 4 继续保持未完成。

### Task 5：真实灾劫、反噬与护道

**Files:** 新建 `TribulationCatalog.java`、`TribulationTest.java`；完善 `AscensionService.java`、`AscensionUpstream.java`；Task 1 冻结清单中的对应实体/效果/资源。

**Interfaces:** `TribulationCatalog.advance(Session, Frame, RuleProfile): TribulationStep`；`TribulationStep` 为可持久化的气机消耗、载体生成/变更/结束意图。实体 UUID 由服务层落地后回填，不能凭未加载判死亡。

- [ ] 写失败测试：二气积聚驱动外劫；破坏载体与灾劫终止条件一致；护道不能代替调气；神不知 profile 的已核免灾有效；未知“免天意”标记无效。
- [ ] 执行测试失败后，按原著目录逐个实现。每个灾劫有起因、载体、作用、应对、结束和掉落规则；不拿随机刷怪或固定九雷充数。
- [ ] 接入真实用蛊调用点的反噬，分别测试普通蛊、本命蛊、仙蛊及有依据的减免。未核定的特殊反噬不得套通用销毁。
- [ ] 验证同场多人只受合法现场影响，不读取他人私密数据；临时灾劫实体不产无限战利品。重跑测试、编译。

### Task 6：放蛊开窍与底蕴结算

**Files:** 新建 `ApertureFormation.java`、`ApertureFormationTest.java`；衔接 `AscensionMachine.java` 和物品托管。

**Interfaces:** `ApertureFormation.placeGu(Session, GuToken, RuleProfile): Transition`；`settle(Session, RuleProfile): ApertureResult`。`GuToken` 在模型定义，表示服务器审计过的来源、蛊种、所属空窍和物品实例，客户端不能自行构造有效 token。

- [ ] 写失败测试：普通四转本命蛊可按已核路线参与；错误本命/普通物品拒绝；放蛊开窍不自动炼成仙蛊；天地二气不足影响结果；特殊体质不套普通档次。
- [ ] 执行测试失败后实现形成过程、持续二气吸收和核心蛊投入；不能将所有“投入”都解释成物品销毁。
- [ ] 固定原著档次边界测试：普通小福地至多三百万亩，中等四至六百万亩，上等七至九百万亩。输入落在原文未定义的间隙时，按 Task 1 冻结映射生成合法档次结果，不临时编原著补段。
- [ ] 验证 `ApertureResult` 有唯一 ID、面积、流派、内部时间、仙元及生命状态；仙元取该档已核范围内配置，寿元保持实际来源，不自动恢复。重跑测试。

### Task 7：真实仙窍、仙元与可选余气升炼

**Files:** 新建 `ImmortalApertureStore.java`、`ImmortalApertureService.java`、`GuRefinement.java`、`ApertureAccessTest.java`、`GuRefinementTest.java`；修改 Task 1 冻结的维度/注册资源与接线文件。

**Interfaces:** `ImmortalApertureService.commit(ServerPlayer, ApertureResult): CommitResult`；`enter(ServerPlayer visitor, UUID apertureId): AccessResult`；`GuRefinement.start(Session, String recipeId, RuleProfile): GuRefinementResult`。`CommitResult/AccessResult` 在服务类内定义为成功标志、原因与已存在记录 ID，重复提交返回同一记录。

- [ ] 写失败测试：同一结算 ID 不生成两座仙窍；未经主人授权不得进入；本体不能每刻把六转恢复成五转；仙元不足不能发动仙蛊；内部时钟不重复推进。
- [ ] 执行测试失败后实现真实有界空间和出入口、访问授权、内部时钟及仙元收支。本体六转兼容需要 Task 1 已核调用点，不能仅靠显示覆盖。
- [ ] 写并跑升炼失败用例：成仙但不炼蛊仍成功；余气只能支出一次；错误蛊方不可产仙蛊；炼制失败不必然毁仙窍；本命保护按已核规则；主人授权护道者可进入处理内部灾劫。
- [ ] 实现真实仙蛊目录、能力、消耗、饲养及唯一性约束；不存在的仙蛊不能用改名物品冒充。现存同种仙蛊的处理依 Task 1 冻结规则。
- [ ] 对开窍、仙蛊生成和仙元提交分别模拟中断/重放，重跑测试与编译。长期经营不在本任务，但首日仙窍能力必须可用。

### Task 8：客户端、整体验证与交付

**Files:** 新建三个 `net/Ascension*` 文件、两个 `client/Ascension*` 文件；修改 `TianyiMod.java`、`TianyiEvents.java`；更新 `docs/升仙-验证记录.md`、`docs/未完成与技术边界.md`。只有实施获授权且真正出包时才更新版本与产物。

**Interfaces:** `AscensionNet.registerPayloads(RegisterPayloadHandlersEvent): void`；`sync(ServerPlayer): void`。客户端动作只上传 `sessionId / expectedRevision / sequence / action / boundedArguments`，不上传结果和他人 UUID；服务端重新检查本人、阶段和物品来源。

- [ ] 写并跑恶意包测试：重复序号、未来 revision、巨长字符串、非法气量、伪造本命 token、他人会话均不能改变真实状态。
- [ ] 实现界面与异象：裂纹、三气、调控反馈、求知窗口、放蛊及内视仙窍；关闭菜单不暂停。准确成功率、后台宿命分数及未获取蛊方不向玩家展示。
- [ ] 运行 `& $PythonExe _pack/run_tests.py`、`& $PythonExe _pack/javac_check.py`，退出码均为 0 后再运行 `& $PythonExe _pack/build.py`；保留实际输出，不沿用旧版本日志。
- [ ] 若版本已按获授权的发布目标调整，检查 `verify_jar.py / verify_version.py / check_bom.py` 及现有审计，记录新增/修改文件及依赖哈希。打包不能嵌入本体。
- [ ] 在隔离客户端与独立服务器、副本存档上完成设计文档 12 类案例；至少双人覆盖护道、袭杀、关菜单、退出、恢复、跨维度、内劫入窍及六转不被重置。
- [ ] 逐个 profile 列出原著证据、离线测试、编译、实机四种状态。缺任一必要阶段不得标“完整还原”；无法实机运行就明确“未实机验证”。交付 JAR 不等于获准安装真实实例。

## 本计划自查与后续启动条件

覆盖关系：原著准备与特殊路线→Task 1/2；碎窍→Task 2/3；纳气和师法自然→Task 2/4；灾劫、反噬、人劫→Task 5；放蛊和底蕴→Task 6；仙窍、仙元、余气升炼→Task 7；操作、多人、兼容、存档→Task 3/8。

Task 1 是有明确产出的调查关口。其结果会决定特殊 profile、数值映射及本体补丁，冻结后才能让实施者按本计划写后续代码；当前不得假装这些不确定项已有精确 API。任务中所列测试均是未来要执行的验收，没有在本次规划中运行。

用户审阅计划后若要求实施，优先沿当前工程执行；本次未替用户选定多代理开发方式、发布版本或安装目标。
