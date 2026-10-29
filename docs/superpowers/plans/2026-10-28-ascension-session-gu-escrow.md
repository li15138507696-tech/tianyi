# 升仙会话与本命蛊托管实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 加入只读本体修为快照、可迁移的升仙会话账本，以及有明确恢复与隔离状态的本命蛊托管层。

**Architecture:** 纯 Java 模型负责校验快照和托管迁移；`AscensionStore` 在同一份世界 `SavedData` 中保存会话、托管元数据和物品栈 NBT；服务端适配器复核玩家、物品槽和能力门槛。登录恢复复用世界账本，任何未核实的来源或能力都保持封闭。

**Tech Stack:** Java 21；Minecraft 1.21.1；NeoForge 21.1.263；`SavedData`、`CompoundTag`、`HolderLookup.Provider`；离线逻辑测试使用 `_pack/run_tests.py`，全源码编译使用 `_pack/javac_check.py`。

**Spec:** `docs/superpowers/specs/2026-10-28-ascension-session-gu-escrow-design.md`

提交说明：续开发开始时，Task 1–6 已在当前工作区以未提交状态落地；本次在完成整体验收与复审后，将全部阶段变更作为一个功能提交保存。下列提交步骤由该提交统一覆盖。

## Global Constraints

- 本阶段不开放六转写入、不创建仙窍维度、不加入客户端入口。
- 服务端读取 `zhuanshu`、`jieduan`、`renqi`、`shouyuan`、`zhenyuan`、`zuida_zhenyuan`；不把它们换算为原著单位，不写回本体字段。
- 客户端不得提交修为事实、物品 ID、物品栈或成功结果。
- 玩家存档、世界 `SavedData` 和其他容器不共享原子提交。设计不承诺硬崩溃下恰好一次；含糊状态必须隔离，不能自动奖惩。
- 托管 ID 只用于关联记录；它本身不证明物品唯一。若有未受控的复制或转移路径，`UNIQUE_GU_ESCROW` 保持关闭。
- 普通路线只有在本体修为适配、真实仙窍空间和本命蛊托管能力全部可用后才允许开始。
- 现有 schema 1 的非终态升仙记录若没有 escrow ID，必须保留原始证据并隔离，不能恢复推进。

## Review Focus

1. 原菜单槽位无法跨断线稳定定位：任务 1 固化证据结论；任务 5 只有在来源稳定时才实现移除，否则断言能力关闭且物品未动。
2. 物品已移除而存档仍处于待提交状态：任务 3 和 6 验证缺失物品时只隔离，不再发物。
3. 复制栈、所有者不符或同一请求重放：任务 2 和 5 验证重复证据进入隔离，托管事务不能重复创建。
4. 未来 schema 或损坏 NBT：任务 3 验证未知 schema 只读保留，损坏记录逐条隔离。
5. 死亡、重生或断线发生在已碎窍阶段：任务 6 验证会话仍保留 `brokenCommitted`，恢复过程不把它重置。

---

### Task 1: 核实本命蛊真实来源槽位

**Files:**
- Read: `_pack/refs/ascension/BenMingGu1Procedure.javap.txt`
- Read: `_pack/refs/ascension/GuzhenrenModVariables-signatures.javap.txt`
- Modify: `_pack/refs/ascension/INDEX.md`

**Interfaces:**
- Produces: 一条可审阅的来源结论，包含槽位持有者、服务端读写位置、跨断线重定位方式，以及该路径是否可排除未监控的副本。

- [x] **Step 1: 反汇编本体绑定过程**

Run:

```powershell
& 'C:\Users\ADMIN\.jdks\temurin-21\bin\javap.exe' -c -p -classpath '_pack\libs\guzhenren-0.1-neoforge-1.21.1.jar' net.guzhenren.procedures.BenMingGu1Procedure
```

Expected: 输出显示实际读取的容器/槽位、绑定标记写入和 `ItemStack` 复制位置。

- [x] **Step 2: 写入审计结论**

更新 `_pack/refs/ascension/INDEX.md` 中「工程侧接口」与「已复核的本体写入点」：若槽位引用可跨重连稳定定位且服务器可独占核验，记录确切引用；否则记录无法定位的原因并保持 `UNIQUE_GU_ESCROW` 关闭。

- [x] **Step 3: 审阅证据边界**

确认文档不把 `PlayerVariables.BenMingGu` 的缓存副本当成真实物品，也不把单个 UUID 当作唯一性证明。

- [x] **Step 4: Commit**

```powershell
git add _pack/refs/ascension/INDEX.md
git commit -m "docs: audit ascension gu source slot"
```

### Task 2: 建立可离线测试的事实与托管状态模型

**Files:**
- Create: `src/main/java/net/guzhenren/tianyi/ascension/ProgressionSnapshot.java`
- Create: `src/main/java/net/guzhenren/tianyi/ascension/GuEscrow.java`
- Modify: `src/main/java/net/guzhenren/tianyi/ascension/AscensionModel.java`
- Modify: `src/main/java/net/guzhenren/tianyi/ascension/AscensionMachine.java`
- Create: `_pack/tests/net/guzhenren/tianyi/ascension/ProgressionSnapshotTest.java`
- Create: `_pack/tests/net/guzhenren/tianyi/ascension/GuEscrowTest.java`
- Create: `_pack/tests/net/guzhenren/tianyi/ascension/AscensionMachineTest.java`
- Modify: `_pack/run_tests.py`

**Interfaces:**
- Produces `ProgressionSnapshot(UUID ownerId, double rank, double stage, double renqi, double shouyuan, double zhenyuan, double maxZhenyuan, long sampledAt)`;构造时拒绝空主人、负 tick、非有限数和负数。
- Produces `GuEscrow.Record(UUID escrowId, UUID ownerId, UUID sessionId, String sourceSlot, String itemFingerprint, State state, long revision)`。
- Produces `GuEscrow.advance(Record current, Action action, long expectedRevision, UUID actorId, Evidence evidence, boolean brokenCommitted, boolean serverValidated) -> Transition`；任何拒绝结果保留原记录，成功迁移 revision 恰好加一。
- `GuEscrow.Transition(boolean accepted, Record record, Rejection rejection)`；`Rejection` 包含 `NONE`, `STALE_REVISION`, `WRONG_OWNER`, `SERVER_VALIDATION_REQUIRED`, `INVALID_STATE`, `SOURCE_MISMATCH`, `SOURCE_UNAVAILABLE`, `BROKEN_SESSION`。
- `GuEscrow.State`: `PREPARED`, `SOURCE_REMOVAL_PENDING`, `HELD`, `DISPOSITION_PENDING`, `RESTORED`, `COMMITTED`, `CANCELLED`, `QUARANTINED`。
- `GuEscrow.Action`: `BEGIN_REMOVAL`, `CONFIRM_HELD`, `BEGIN_DISPOSITION`, `RESTORE`, `COMMIT`, `CANCEL`, `QUARANTINE`。
- `GuEscrow.Evidence`: `EXACT_SOURCE`, `EMPTY_SOURCE`, `CHANGED_SOURCE`, `MULTIPLE_COPIES`, `SOURCE_UNAVAILABLE`。
- `AscensionModel` adds terminal `Phase.CANCELLED` / `Outcome.CANCELLED`; `AscensionMachine` adds `Action.CANCEL_PREPARATION`, accepted only in `PREPARING` before `brokenCommitted`.

- [x] **Step 1: 写快照构造测试**

`ProgressionSnapshotTest.main` 断言有限非负快照可创建；`Double.NaN`、正无穷、负数和负 `sampledAt` 各自抛出 `IllegalArgumentException`。

- [x] **Step 2: 运行测试确认失败**

Run: `python _pack/run_tests.py`

Expected: 新测试因 `ProgressionSnapshot` 与 `GuEscrow` 尚未实现而失败；旧测试不作为本任务失败原因。

- [x] **Step 3: 写托管迁移测试**

`GuEscrowTest.main` 断言：`PREPARED → SOURCE_REMOVAL_PENDING → HELD` 只接受服务端校验且来源证据依次为 `EXACT_SOURCE`、`EMPTY_SOURCE`；跳过阶段、过期 revision、其他 owner、`SOURCE_UNAVAILABLE` 均拒绝且原记录不变；未碎窍时可在来源仍精确匹配时取消；来源改变或多副本证据进入 `QUARANTINED`；终态不能再次迁移。

`AscensionMachineTest.main` 断言：只有 `PREPARING` 且未碎窍的会话能转入 `CANCELLED`；`brokenCommitted=true` 的会话拒绝取消，且 `terminal()` 对 `CANCELLED` 返回真。

- [x] **Step 4: 实现模型并登记离线套件**

在 `_pack/run_tests.py` 的 `SRC_CORE` 加入两个纯 Java源文件与 `AscensionModel` / `AscensionMachine`，在 `SUITES` 加入上述三个 main 类。状态迁移集中在 `GuEscrow.advance`，不得在状态模型里导入 Minecraft 或 NeoForge 类型。

- [x] **Step 5: 运行离线测试**

Run: `python _pack/run_tests.py`

Expected: 输出 `全部测试套件通过`。

最终验证：`python _pack/run_tests.py` 全部测试套件通过，包含五个升仙纯逻辑套件。Codex 默认命令沙箱限制从用户目录启动 Python；使用已授权的提升调用运行，解释器与项目代码均正常。

- [x] **Step 6: Commit**

```powershell
git add src/main/java/net/guzhenren/tianyi/ascension/ProgressionSnapshot.java src/main/java/net/guzhenren/tianyi/ascension/GuEscrow.java src/main/java/net/guzhenren/tianyi/ascension/AscensionModel.java src/main/java/net/guzhenren/tianyi/ascension/AscensionMachine.java _pack/tests/net/guzhenren/tianyi/ascension/ProgressionSnapshotTest.java _pack/tests/net/guzhenren/tianyi/ascension/GuEscrowTest.java _pack/tests/net/guzhenren/tianyi/ascension/AscensionMachineTest.java _pack/run_tests.py
git commit -m "feat: model ascension escrow transitions"
```

### Task 3: 将会话与真实物品快照写入 schema 2 世界账本

**Files:**
- Modify: `src/main/java/net/guzhenren/tianyi/world/AscensionStore.java`
- Modify: `src/main/java/net/guzhenren/tianyi/ascension/AscensionMachine.java`
- Create: `_pack/ascension-testsrc/net/guzhenren/tianyi/world/AscensionStoreNbtTest.java`
- Create: `_pack/run_ascension_store_tests.py`

**Interfaces:**
- `AscensionStore.createWithEscrow(Session session, GuEscrow.Record escrow, int serverStackCount, CompoundTag itemStackTag)` succeeds only when IDs/owner agree, session is `PREPARING`, escrow is `PREPARED`, the server-observed stack count is one, the tag is nonempty, and no active session exists for that owner/aperture.
- `AscensionStore.findEscrow(UUID escrowId) -> Optional<GuEscrow.Record>`.
- `AscensionStore.findEscrowStack(UUID escrowId) -> Optional<CompoundTag>` returns a defensive copy.
- `AscensionStore.compareAndSetEscrow(UUID escrowId, long expectedRevision, GuEscrow.Record replacement)` applies only the next revision and a legal `GuEscrow` transition.
- `AscensionMachine.start(UUID sessionId, UUID ownerId, StartFacts facts, String guEscrowId, long serverTick)` creates a `PREPARING` session linked to the pre-created escrow ID.

- [x] **Step 1: 写 schema 版本红测**

`AscensionStoreNbtTest.main` 先只调用当前已存在的 `new AscensionStore().save(new CompoundTag(), null)`，断言结果 `schema == 2`。用现有 NBT 类运行它，确认断言因当前 schema 仍为 1 而失败。

- [x] **Step 2: 写 schema 1 / future-schema 测试**

在同一测试类加入 Minecraft `CompoundTag` 往返：写入 `PREPARING` session、`PREPARED` escrow 和含多个字段的 stack tag，保存后加载，逐字段比较 session、revision、escrow 状态和 stack tag。构造 schema 1 有效会话，断言终态历史保留、无 escrow ID 的活动记录进入隔离且不能恢复；构造含缺字段记录和 schema 99，分别断言逐条隔离、future schema 只读且重新保存与原始 tag 相等。

- [x] **Step 3: 运行 NBT 测试确认失败**

Run: `python _pack/run_ascension_store_tests.py`

Expected: `schema == 2` 断言失败；测试必须能用目标 Minecraft 1.21.1 / NeoForge 编译类路径运行，不能以伪造 NBT 类替代。

- [x] **Step 4: 实现 schema 2 与迁移**

将 `SCHEMA` 升为 2；保存 `sessions`、`escrows` 和 `quarantined`。schema 1 逐条迁移可解码记录；活动且无 escrow ID 的记录原样隔离；未知未来 schema 整体只读并保留原 tag。物品由服务端用当前 `HolderLookup.Provider` 编码为完整 ItemStack NBT，store 内只保存/返回 tag 副本。`createWithEscrow` 在一个 store 方法中同时插入 session 与 escrow，避免只写入其中一条。

`run_ascension_store_tests.py` 先调用 `python _pack/javac_check.py`；从 `_pack/javac_out/args.txt` 读取 `-cp` 后的 profile classpath，再将 `_pack/ascension-testsrc` 编译进临时测试输出目录，并用同一 classpath 运行 `AscensionStoreNbtTest` 和 `PlayerGuEscrowSourceTest`。

- [x] **Step 5: 运行 NBT 与源码编译验证**

Run: `python _pack/run_ascension_store_tests.py`
Run: `python _pack/javac_check.py`

Expected: NBT 套件通过，后者输出 `javac exit: 0`。

最终验证：`python _pack/run_ascension_store_tests.py` 退出码为 0，schema 迁移、完整物品 NBT 与关闭来源测试通过；runner 中的 `_pack/javac_check.py` 对 107 个源码全编译退出码为 0。之后独立重跑 `_pack/javac_check.py` 同样退出码为 0。

- [x] **Step 6: Commit**

```powershell
git add src/main/java/net/guzhenren/tianyi/world/AscensionStore.java src/main/java/net/guzhenren/tianyi/ascension/AscensionMachine.java _pack/ascension-testsrc/net/guzhenren/tianyi/world/AscensionStoreNbtTest.java _pack/ascension-testsrc/net/guzhenren/tianyi/world/PlayerGuEscrowSourceTest.java _pack/run_ascension_store_tests.py
git commit -m "feat: persist ascension escrow records"
```

### Task 4: 读取本体修为并执行服务器能力门槛

**Files:**
- Create: `src/main/java/net/guzhenren/tianyi/world/AscensionProgressionReader.java`
- Create: `src/main/java/net/guzhenren/tianyi/world/AscensionService.java`
- Modify: `src/main/java/net/guzhenren/tianyi/ascension/AscensionModel.java`
- Modify: `src/main/java/net/guzhenren/tianyi/ascension/AscensionRules.java`
- Create: `_pack/tests/net/guzhenren/tianyi/ascension/AscensionRulesTest.java`
- Modify: `_pack/run_tests.py`

**Interfaces:**
- `AscensionProgressionReader.ReadResult(Status status, Optional<ProgressionSnapshot> snapshot)`; `Status` is `OK`, `NO_PLAYER`, `NO_ATTACHMENT`, `INVALID_VALUE`, or `INVALID_TICK`.
- `AscensionProgressionReader.read(ServerPlayer player, long serverTick) -> ReadResult` accepts only a server player, checks for the upstream attachment, and returns a validated `ProgressionSnapshot` only for `OK`.
- `AscensionService.StartContext(String apertureId, boolean apertureVerified, boolean apertureBroken, String sourceRef)` is constructed by server-side code; no client payload carries it.
- `AscensionService.StartResult(AscensionProgressionReader.Status readStatus, Optional<StartDecision> decision, Optional<Session> session)`; if `readStatus != OK`, both optionals are empty; if `readStatus == OK`, decision is present and session is present only when allowed.
- `AscensionService.begin(ServerPlayer player, StartContext context, RuleProfile profile, long serverTick) -> StartResult` re-reads facts on the server, calls `AscensionRules.validateStart`, and returns blockers before it mutates a slot or store; Task 5 supplies a session only after escrow is `HELD`.
- `AscensionModel.Blocker` adds `MISSING_UPSTREAM_RANK_ADAPTER`, `MISSING_APERTURE_SPACE`, and `MISSING_GU_ESCROW`; `AscensionRules.validateStart` emits each missing capability separately.
- Neither class writes `zhuanshu`, `jieduan`, `zhenyuan`, `zuida_zhenyuan`, `renqi`, nor `shouyuan`.

- [x] **Step 1: Add rule-gate tests**

`AscensionRulesTest.main` asserts missing `UPSTREAM_RANK_ADAPTER`, `IMMORTAL_APERTURE_SPACE`, and `UNIQUE_GU_ESCROW` yield their respective blockers; rank/stage other than `5.0/4.0`, unverified aperture, and missing physical Gu each yield the named blocker.

- [x] **Step 2: Run the rule test and confirm the blocker detail is missing**

Run: `python _pack/run_tests.py`

Expected: the new capability assertion fails because the current rules collapse missing capabilities into `MISSING_CAPABILITY`; existing suites remain green.

- [x] **Step 3: Implement the server reader and admission service**

Read the six upstream variables from `GuzhenrenModVariables.PLAYER_VARIABLES`; never trust payload data. Keep the default ordinary profile disabled because rank adaptation and aperture space are still absent. A blocked start must not create a session or mutate inventory.

在 `_pack/run_tests.py` 的 `SRC_CORE` 加入 `AscensionRules.java`，在 `SUITES` 加入 `AscensionRulesTest`，确保新 blocker 规则确实执行。

- [x] **Step 4: Run logic tests and full compile**

Run: `python _pack/run_tests.py`
Run: `python _pack/javac_check.py`

Expected: all offline suites pass and `javac exit: 0`.

最终验证：`python _pack/run_tests.py` 中的五个升仙规则/状态套件全部通过；服务端 reader/service/source 已包含在 107 个源码的全编译中，退出码为 0。

- [x] **Step 5: Commit**

```powershell
git add src/main/java/net/guzhenren/tianyi/world/AscensionProgressionReader.java src/main/java/net/guzhenren/tianyi/world/AscensionService.java src/main/java/net/guzhenren/tianyi/ascension/AscensionModel.java src/main/java/net/guzhenren/tianyi/ascension/AscensionRules.java _pack/tests/net/guzhenren/tianyi/ascension/AscensionRulesTest.java _pack/run_tests.py
git commit -m "feat: gate ascension with server facts"
```

### Task 5: 建立本命蛊托管边界；无稳定来源时保持事务关闭

**Files:**
- Create: `src/main/java/net/guzhenren/tianyi/world/GuEscrowSource.java`
- Create if Task 1 proves a stable source: `src/main/java/net/guzhenren/tianyi/world/PlayerGuEscrowSource.java`
- Create: `src/main/java/net/guzhenren/tianyi/world/GuEscrowService.java`
- Modify: `src/main/java/net/guzhenren/tianyi/world/AscensionService.java`
- Create: `_pack/ascension-testsrc/net/guzhenren/tianyi/world/GuEscrowServiceTest.java`（Task 1 未证明稳定来源时）

**Interfaces:**
- `GuEscrowSource.SourceCheck(GuEscrow.Evidence evidence, Optional<ItemStack> stackCopy, String fingerprint, String reason)`; only `EXACT_SOURCE` may carry a stack copy and nonempty fingerprint.
- `GuEscrowSource.inspect(ServerPlayer player, String sourceRef) -> SourceCheck` reports exact source evidence and a defensive `ItemStack` copy; it never creates a stack from `PlayerVariables.BenMingGu`.
- `GuEscrowSource.remove(ServerPlayer player, String sourceRef, String expectedFingerprint) -> SourceCheck` removes only the exact one-count stack inspected immediately before the call.
- `GuEscrowSource.restore(ServerPlayer player, String sourceRef, CompoundTag stackTag) -> SourceCheck` restores only when the recorded owner is online and the destination can accept the complete stack; otherwise it leaves the transaction pending.
- `GuEscrowService.BeginResult(StartDecision decision, Optional<Session> session, Optional<GuEscrow.Record> escrow)`; session and escrow are present only after the item is recorded `HELD`.
- `GuEscrowService.begin(ServerPlayer player, AscensionService.StartContext context, ProgressionSnapshot snapshot, RuleProfile profile, long serverTick) -> BeginResult` must not claim a session before `HELD` is persisted.
- Task 1 found no stable source. Therefore this iteration does not implement `PlayerGuEscrowSource`, never inspects or mutates an inventory slot, strips `UNIQUE_GU_ESCROW` from proposed profiles, and returns `MISSING_GU_ESCROW` plus `PHYSICAL_GU_NOT_VERIFIED` with no records.

- [x] **Step 1: Test the closed-source branch**

`GuEscrowServiceTest.main` asserts that even a profile claiming every capability is denied `UNIQUE_GU_ESCROW`, reports `PHYSICAL_GU_NOT_VERIFIED`, creates no session or escrow, and does not need a `ServerPlayer` or source mutation.

- [x] **Step 2: Keep prepare/remove/hold closed without a stable source**

Task 1 could not prove a stable source. The escrow state machine and schema 2 store primitives are implemented, but no player inventory transaction is enabled; admission stays blocked and creates no records.

- [x] **Step 3: Keep disposition closed without custody proof**

There is no active escrow to dispose in this iteration. The pure escrow model defines `RESTORED`, `COMMITTED`, and `QUARANTINED` terminal paths; no inventory delivery or item generation is wired.

- [x] **Step 4: Run targeted tests and full compile**

Run: `python _pack/run_ascension_store_tests.py`
Run: `python _pack/javac_check.py`

Expected: schema/migration/NBT and closed-source tests pass; full compile outputs `javac exit: 0`. The capability remains closed because Task 1 did not prove a source.

最终验证：`python _pack/run_ascension_store_tests.py` 的 NBT 与关闭来源测试通过；全源码 javac 编译退出码为 0。由于没有稳定来源，玩家背包转移仍未实现，入口继续关闭。

- [x] **Step 5: Commit**

```powershell
git add src/main/java/net/guzhenren/tianyi/world/GuEscrowSource.java src/main/java/net/guzhenren/tianyi/world/GuEscrowService.java src/main/java/net/guzhenren/tianyi/world/AscensionService.java _pack/ascension-testsrc/net/guzhenren/tianyi/world/GuEscrowServiceTest.java
if (Test-Path 'src/main/java/net/guzhenren/tianyi/world/PlayerGuEscrowSource.java') { git add src/main/java/net/guzhenren/tianyi/world/PlayerGuEscrowSource.java }
git commit -m "feat: escrow verified ascension gu"
```

### Task 6: 恢复未完成会话并记录最终边界

**Files:**
- Create: `src/main/java/net/guzhenren/tianyi/ascension/AscensionRecovery.java`
- Modify: `src/main/java/net/guzhenren/tianyi/TianyiEvents.java`
- Create: `_pack/tests/net/guzhenren/tianyi/ascension/AscensionRecoveryTest.java`
- Modify: `_pack/run_tests.py`
- Modify: `_pack/refs/ascension/INDEX.md`
- Modify: `docs/升仙-原著规则清单.md`

**Interfaces:**
- `AscensionRecovery.Status`: `WAIT_FOR_SOURCE`, `CANCEL_PREPARATION`, `RESUME_HELD`, `QUARANTINE`, `NO_CHANGE`.
- `AscensionRecovery.RecoveryDecision(Status status, Session session, GuEscrow.Record escrow)` carries the unchanged or revised records; an absent source never returns a mutation decision.
- `AscensionRecovery.reconcile(Session session, GuEscrow.Record escrow, GuEscrow.Evidence evidence, ProgressionSnapshot snapshot) -> RecoveryDecision` is pure logic; `TianyiEvents` applies changes with `AscensionStore.compareAndSetPair` only after server-side evidence is available.
- `AscensionStore.compareAndSetPair(...)` checks both revisions and transaction identity before changing either linked record.
- `AscensionService.recover(ServerPlayer player) -> void` examines that owner's saved sessions. Since there is no stable source, it passes `SOURCE_UNAVAILABLE`, which preserves both records; it does not scan unloaded containers or the world.
- `onServerStarted` continues loading `AscensionStore`; `onLogin` and `onRespawn` invoke the recovery service for that `ServerPlayer`.

- [x] **Step 1: Write recovery table tests**

`AscensionRecoveryTest.main` asserts: `PREPARED + EXACT_SOURCE + unbroken` returns a terminal `CANCELLED` session and escrow; `SOURCE_REMOVAL_PENDING + EMPTY_SOURCE` with valid snapshot advances to `HELD`; missing/changed/multiple evidence quarantines; mismatched session/owner IDs quarantine; `SOURCE_UNAVAILABLE` returns unchanged records; `brokenCommitted` is unchanged across every recovery result.

- [x] **Step 2: Run tests and confirm the cases fail before the reducer exists**

Run: `python _pack/run_tests.py`

Expected: new recovery assertions fail because `AscensionRecovery` is not implemented.

- [x] **Step 3: Implement pure reconciliation and lifecycle hooks**

将 `AscensionRecovery.java` 加入 `_pack/run_tests.py` 的 `SRC_CORE`，将 `AscensionRecoveryTest` 加入 `SUITES`。登录和重生时调用恢复逻辑；不扫描未加载容器或整个世界。玩家离线或来源不可用时不改物品，保留当前账本状态。

- [x] **Step 4: Update evidence and user-visible status**

Update `_pack/refs/ascension/INDEX.md` and `docs/升仙-原著规则清单.md` with implemented storage/recovery behavior and exact remaining capability blockers. Keep claims limited to compiled and tested offline logic; do not claim full in-game ascension support.

- [x] **Step 5: Run final checks**

Run: `python _pack/run_tests.py`
Run: `python _pack/run_ascension_store_tests.py`
Run: `python _pack/javac_check.py`
Run: `git diff --check`

Expected: all suites pass, `javac exit: 0`, and no whitespace errors. No real server or multiplayer result may be claimed unless an isolated game instance is actually run.

最终执行结果：`python _pack/run_tests.py` 全部套件通过；`python _pack/run_ascension_store_tests.py` 中的 schema/NBT/关闭来源测试通过，并显示 107 个源码 `javac exit: 0`；独立 `_pack/javac_check.py` 同样 `javac exit: 0`；`git diff --check` 无空白错误。仅完成了离线与源码编译验证，未运行 Minecraft 客户端/服务器实机流程。

最终复审（2026-10-29）：审阅发现的重复账本冲突、物品栈 NBT 解码、单侧 revision 配对提交、pending 来源保留时取消、CANCELLED 终态拒绝共五项均已加入回归并修复；复审结论 Ready，未发现 Critical/Important/Minor。验证仍仅覆盖离线逻辑与 1.21.1 注册表 NBT，不代表客户端、服务器或玩家流程已实测。

- [x] **Step 6: Commit**

```powershell
git add src/main/java/net/guzhenren/tianyi/ascension/AscensionRecovery.java src/main/java/net/guzhenren/tianyi/TianyiEvents.java _pack/tests/net/guzhenren/tianyi/ascension/AscensionRecoveryTest.java _pack/run_tests.py _pack/refs/ascension/INDEX.md docs/升仙-原著规则清单.md
git commit -m "feat: recover ascension sessions safely"
```
