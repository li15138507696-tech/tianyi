# 道途 1.2.0 代码审查报告

- 日期：2026-10-22
- 对象：`daotu-1.2.0.jar`（29 源文件 / 47 class / 111,701 字节）
- 审查方式：静态审查 + 本体字节码核对 + 可复现脚本验证
- 结论：**v1.0.0 的 4 项缺陷（D1~D4）保持修复状态；v1.1.0 十六路重构的 5 项风险点
  已处理；v1.2.0 针对「升阶播报 / buff 挂载 / 属性叠加」三项运行时行为做专项复核，
  查出并修复 3 个新缺陷（D5~D7）**

> 未在游戏内实测。以下结论均来自编译期与字节码核对，运行期行为仍需进游戏确认。

---

## 0. v1.2.0 新增缺陷（D5~D7）

用户点名的三件事，逐条查到「代码看起来对、实际不成立」的问题：

### D5（严重）升阶会强制弹出道途界面

- 位置：`DaotuClientHandler`（原 `open()`）/ `DaotuNet.sendState()`
- 现象：`award()` 升阶后调 `DaotuNet.sendState()`，客户端收到包一律
  `minecraft.setScreen(new DaotuViewScreen(payload))`。而历练是**打怪、走路、打坐**时涨的——
  玩家会在挥剑中途被弹出一个全屏面板。
- 处理：`DaotuStatePayload` 加 `open` 字段区分两种到达方式：
  - `open = true`（玩家按 M 请求，`DaotuNet.openState`）→ 一定建界面；
  - `open = false`（升阶 / 点节点 / 入势力主动推送，`DaotuNet.sendState`）→
    只在界面已开时原地刷新，没开就**静默丢弃**。
- 验证：`grep` 全部 5 个 `sendState` 调用点，仅 `DaotuRequestPayload` 走 `openState`。

### D6（严重）道途 buff 会被本体清空且无人恢复

- 位置：`DaotuLiLian.applyBuff()`
- 现象：本体 `SiqibuffXiaoGuoChiXuShiMeiKeFaShengProcedure` 在四气 buff 到期时，
  对 `LivingEntity` 调 **`removeAllEffects()`**（javap 确认：`487: invokevirtual
  LivingEntity.removeAllEffects:()Z`，无来源过滤）。道途在升阶时发一次 buff，
  之后玩家四气 buff 一到期，流派增幅就没了，且**玩家看不出原因**。
- 处理：新增 `DaotuBuffs`，改为「每秒维护」而非「一次性发放」：
  - 升阶瞬间 `grantNow()` 仍立即发一次（保留即时反馈）；
  - `DaotuRuntime` 每秒调 `maintain()`，按当前阶位检查缺什么、快过期就续；
  - 不覆盖玩家自己更长的同名药水；本体 `-1`（无限）不动。
- 验证：本体 22371 个 class 中仅 2 处 `removeAllEffects`，其中 1 处作用于
  `LivingEntity`（即玩家也可能被清），故必须兜底。

### D7（中）`K_KNOCKBACK` 属性键配了但没有任何消费点

- 位置：`DaotuGrowth.java:152,167` 给力道第 4/5 阶声明 `K_KNOCKBACK, 0.3 / 0.6`
- 现象：`DaotuApply` 里没有对应 getter，`DaotuAttributes.sync()` 也没挂
  `ATTACK_KNOCKBACK`。玩家升到那两阶，属性面板上**什么都没有**，代码却看起来配置完整。
- 处理：`DaotuApply.attackKnockback()` + `sync()` 挂 `Attributes.ATTACK_KNOCKBACK`。
- 防复发：新增 `audit_reward_consumption.py`，对 8 个 `K_*` 常量逐个检查
  「成长表引用数」与「是否有 `growthAttr` 消费点」，配了没人读直接 FAIL。

### 顺带：升阶播报分档（非缺陷，体验优化）

原实现把所有升阶名塞进一行聊天栏。十六路并进时（尤其开局）一次升十几阶会刷屏。
改为：1~2 阶走**动作栏**（不占聊天）、3 阶以上走聊天栏且只列前 3 条 + 「等 N 路」、
任何一阶到达顶阶额外给一条金色消息。

---

## 1. v1.0.0 缺陷（D1~D4）现状

| 编号 | 缺陷 | 状态 |
|------|------|------|
| D1 | 气运上限基准硬编码，凭空加值/零收益 | **保持修复**（双账本增量制，`verify_qiyun4.py` 7 场景仍通过） |
| D2 | 属性同步周期性削血 | **保持修复**（`addOrUpdateTransientModifier`，`audit_fixes.py` R1 仍通过） |
| D3 | `onLogin` 无副作用语句 | **保持修复** |
| D4 | `@EventBusSubscriber` 死参数 `bus` | **保持修复** |

v1.1.0 把 `qiyun_cap` 的数值来源从「单一成长阶段」改成「十六路已达成阶位之和」，
但**记账机制（双账本）一行未改**——`DaotuRuntime` 里 `qiyunSet` / `qiyunBase`
的逻辑原样保留，只换了 `pct` 的输入。所以 D1 的修复依然有效。

---

## 2. v1.1.0 重构新增的风险点

### N1（严重）16 路并行结算可能重复发奖

- 位置：`DaotuLiLian.award()`
- 风险：一次历练同时推进 16 条路线，若每条路线都独立走「升阶→发奖」，
  同一 tick 可能触发 16 次 `DaotuAttributes.sync()` + 16 次 `DaotuNet.sendState()`，
  既浪费又是重复发包。
- 处理：
  - 循环内**只收集**升阶结果到 `promoted` 列表，循环结束后统一播报、统一 `sync()`、统一发包。
  - 奖励发放走 `claimUpTo()`，它自己维护 `claimed` 游标，**幂等**——
    同一条路线重复调用不会重发已结算的阶。
- 残余风险：16 路同时升阶时播报会挤在一行。
  **v1.2.0 已改为分档播报**：1~2 阶走动作栏、3 阶以上走聊天栏且只列前 3 条
  + 「等 N 路」、顶阶额外一条金色消息。不再有刷屏。

### N2（严重）道痕字段名有三条路线是 `dahen_*` 而非 `daohen_*`

- 位置：`DaotuGrowth.java` 的 `zhidao` / `liandao` / `zhoudao`
- 本体事实（字节码核对）：`PlayerVariables` 里**没有** `daohen_zhidao`，
  智道叫 `dahen_zhidao`；炼道 `dahen_liandao`；宙道 `dahen_zhoudao`。
  共 9 个 `dahen_*`、35 个 `daohen_*`，两套前缀并存。
- 若写错：`addDaohen` 会返回 false（走到 default 分支），升阶时打一条 warn 日志，
  玩家拿不到道痕但不会崩。
- 处理：`_crash3/audit_refs.py` 把 `DaotuGrowth` 里出现的**每一个**道痕字面量
  与 `DaotuApply.daohenOf` 的 switch 白名单双向核对，当前 16 个全部命中、0 个多余。
  `audit_growth.py` 另做一层独立核对。

### N3（中等）buff id 必须真实存在，否则静默跳过

- 位置：`DaotuLiLian.applyBuff()`
- 风险：本体只有 8 个流派专属 buff + `weilizengjia` 等通用 buff。
  编造 id（如 `guzhenren:lidaozengfu`）会在 `getHolder` 返回空后被跳过——
  **不崩，但玩家白升一阶**。
- 处理：
  - 只用本体确证的 9 个 id：`lvdaozengfu`、`weilizengjia`、`yandaozengfu`、
    `shuidaozengfu`、`jindaozengfu`、`jiandaoshanghaizengfu`、`leidaozengshang`、
    `yudaoguchongweilizengfu`、`qidaoguchongweilizengfu`。
  - `audit_growth.py` **打开本体 jar**，读 `GuzhenrenModMobEffects.class` 的字符串常量池，
    逐个 id 核对。编造会直接 FAIL 退出 1。
  - 解析失败时只打 `debug`，不抛异常（本体换版本删 buff 也不至于让升阶崩）。

### N4（中等）迁移后可能重复发放旧出身的奖励

- 位置：`DaotuMigrate.migrateIfNeeded()`
- 风险：旧出身在 v1.0.0 里已经发过元石/人气/道痕。若迁移时把十六路阶位设成 N
  却不设 `claimed`，下次登录 `claimUpTo` 会从 -1 补到 N，**重复发一遍**。
- 处理：迁移循环里对每条路线同时写 `setRankOf(p, N)` 和 `setClaimedOf(p, N)`，
  把 `0..N` 全部标记为已结算。`migrated` 标记保证整段只跑一次。

### N5（轻微）十六路快照的网络体积

- 位置：`DaotuStatePayload`
- 变更：`(int growth, double lilian, int claimed)` → `(int[16] ranks, double[16] lilians)`
- 体积：16×varint + 16×8 字节 ≈ 145 字节，比原来多约 130 字节。
  只在开界面和升阶时发（不是每 tick），可忽略。
- 编解码用 `writeVarInt(长度)` 前缀 + 循环，长度自描述，将来加路线不用改协议。

---

## 3. 重构中删除/替换的旧代码

| 旧 | 新 | 说明 |
|----|----|------|
| `DaotuGrowth.Stage` / `STAGES` / `of()` / `next()` / `MAX_INDEX` | `Path` / `Rank` / `PATHS` / `rank()` / `nextRank()` / `maxRank()` | 单阶梯 → 十六路 |
| `DaotuData.growth/lilian/claimed`（标量） | `rankOf/lilianOf/claimedOf`（按 pathId） | 存 compound 而非 double |
| `DaotuOrigin.java` / `DaotuPool.java` | 删除 | 出身系统整体移除 |
| `DaotuChooseOriginPayload` / `DaotuSwitchFactionPayload` | 删除 | 只剩 `DaotuJoinFactionPayload`（入势力/转投复用） |
| `DaotuFaction.shenfenOf()` / `shiliOf()` | 删除（死代码） | 头衔统一由 `DaotuGrowthApply.refreshTitles` 生成 |
| `audit_pool_fields.py` | 删除 | 校验的是出身池字段，已无对象 |
| `audit_layout.py` 解析 `DaotuPool.java` | 改为解析 `DaotuGrowth.java` | 96 个成长行重新做布局审计 |

---

## 4. 已核对通过、无需修改的项

| 项 | 结论 |
|---|---|
| 开窍判定 `hasOpenedQiao` | 本体确实把 `kaiqiao` 置 1，界面可达；v1.1.0 用它替代 `growth >= 1` 作入势力门槛 |
| 转世判定 mixin | 本体判定为 `!FUHUO && (!XIUWEI \|\| shouyuan <= 0)`，与 mixin 逻辑等价 |
| PersistentData 跨死亡 | `Entity.restoreFrom` 走 `saveWithoutId`/`load`，`daotu` 标签随死亡保留 |
| 46 流派映射 | 与本体 `liupai_*` 一一对应，无重复；`liupai_qingmeidao`/`liupai_feixingdao` 本体无对应道痕字段（显式 null） |
| 42 节点 / 12 觉醒 | 每类 7 节点 + 2 觉醒，6 类齐全；`DaotuApply` 引用的节点 id 全部有定义，反向也无死节点 |
| 16 路 / 96 阶 | 路线数 16、每路 6 阶、阶序 0..5、门槛严格递增 240/840/2000/4200/8400/16000 |
| 属性键 | 8 个 `K_*` 常量，`DaotuGrowth` 引用全部有定义 |
| 版本一致性 | `version.properties` / `mods.toml` / jar 内 toml 三方均为 1.1.0，无 BOM |
| jar 结构 | 46 class、mixin 配置、lang 全部就位；mixin 目标类与方法签名存在 |
| 无模糊 | `verify_ui_bytecode.py` 确认 `render()` 无 `renderBackground` 调用，`renderBackground` 为纯 `fill` 自绘 |

---

## 5. 审查/重构新增或改写的脚本

| 脚本 | 用途 |
|------|------|
| `audit_growth.py` | **新增**。核对 16 路 × 6 阶结构、门槛递增、道痕字段白名单、buff id 在本体 jar 中真实存在、属性键有定义 |
| `audit_refs.py` | **改写**。原为「出身→势力」引用核对，现为「道痕字段对白名单 + `addDaohen` 覆盖率 + 节点 id 双向匹配」 |
| `audit_layout.py` | **改写**。原解析 `DaotuPool.java`，现解析 `DaotuGrowth.java`，审计 96 个成长行 |
| `audit_jar.py` | **改写**。`DaotuPool` 计数段改为 `DaotuGrowth` 路线/阶数计数 |
| `bump_toml.py` | **新增**。安全地把 `mods.toml` 版本号 +1 并校验 BOM 不变 |
| `audit_pool_fields.py` | **删除**。对象已不存在 |
| `verify_qiyun4.py` | 保持。7 场景气运记账仿真 |
| `audit_fixes.py` | 保持。D1~D4 回归断言 |

---

## 6. 遗留与风险

1. **未实测**。以上均为静态结论。首次进游戏应重点验：
   - 升阶播报是否只播一次（16 路同时升阶时合并成一行）
   - buff 是否真的挂上、时长是否正确
   - 属性加成与本体/tianfu 是否叠加而非覆盖
   - 旧存档迁移后十六路阶位是否一致、有没有重复发奖
2. **`pct` 回落不削上限**：刻意为之（削上限会连带削掉本体已攒气运）。
   十六路并进时 `qiyun_cap` 只增不减，与 v1.0.0 语义一致。
3. **`qiyunBase` 极端场景可能滞后**：本体若在同一 tick 内既改写上限又让我们同步，
   账本会以本体新值为基准，属可接受近似。
4. **属性 modifier 不落盘**：transient modifier 在重登后由 `onLogin` 重建；
   重建时机在 `PlayerLoggedInEvent`。若本体在登录早期覆盖属性，需确认时序。
5. **历练值无上限**：`lilian` 是 double，理论上可以涨到 1e308。
   实际上 16000 就到顶阶，之后继续累积但不再有任何效果——
   **不封顶是刻意的**，留着给将来加第 7 阶。
6. **吸收系数的偏科程度未经实测调优**：当前 0.6~1.5（最大差 2.14 倍）。
   若实测发现「非主修路线永远长不起来」或「全都能轻松满级」，需要调这个区间。
   相关代码集中在 `DaotuLiLian.absorb()` 一个 switch 里，改起来是一处。
