# 蛊真人·道途（daotu）附属设计文档

- 日期：2026-10-21
- 状态：设计待评审
- 目标环境：Minecraft 1.21.1 / NeoForge 21.1.x / Java 21
- 前置：`guzhenren` 0.1（MCreator，未混淆，jar-in-jar）
- 同装兼容：`tianfu`（蛊真人天命）、`20261020-2036_六合一大合集`、`蛊真人流派熟练度查看`、`蛊虫使用记录`、`魂道附属`、`兼修附属`

---

## 1. 定位

「天命」管**开局你是谁**（体质 + 天赋词条 + 机缘），「道途」管**后来你成了谁**（出身 + 势力 + 流派专精）。

两套系统共用同一个开窍时刻，但**互不写入对方的字段**，因此可以同装：

| | tianfu（天命） | daotu（道途） |
|---|---|---|
| 数据位置 | `PersistentData/ tianfu` | `PersistentData/ daotu` |
| 写入本体字段 | `tizhi`、`daohen_*`、`liupai_*` | `shili`、`shenfen`、`yuanshishuliang` |
| 只读本体字段 | 全部 | 全部 |
| 触发 | 希望蛊开窍 → 自动弹窗 | 开窍后 → 提示按 M 手动开 |
| 键位 | K | **M** |

**为什么不自动弹窗**：`tianfu` 已在 `UikongqiaoguanbiProcedure.execute` 的 `@At("TAIL")` 注入并自动 `sendState`。两个附属同时弹窗会互相 `setScreen` 覆盖。daotu 只发聊天提示，玩家自己按 M。这样无论装不装 tianfu 都工作。

---

## 2. 已验证的本体事实（设计依据）

### 2.1 两个免费的显示槽位

```java
// net.guzhenren.network.GuzhenrenModVariables$PlayerVariables
public java.lang.String shili;
public java.lang.String shenfen;
```

- 这两个字段在 `PlayerVariables` 里**已完整序列化**（`putString`/`getString` 各 1 处，就是序列化自身）。
- 对全 jar 947 个 class 做过 `putfield` 扫描：**没有任何类写入过它们**。
- 本体属性面板已接好显示管线：

```java
// UiShiliProcedure.execute(Entity)
return "**" + entity.getData(PLAYER_VARIABLES).shili.replace(...);
// 常量池拼接配方：势力:\u0001

// UishenfenProcedure.execute(Entity)
// 常量池拼接配方：身份:\u0001
```

- 调用方只有 `UIshuhishuxingjiemian2Screen` 与 `UIshuhishuxingjiemian3Screen`。
- 语言键存在：`gui.guzhenren.u_ishuhishuxingjiemian.label_shi_li = "势力:"`。

**结论**：只要写这两个 String，本体属性面板（第 2、3 页）立刻显示 `势力: 青茅山·古月家族` / `身份: 族长嫡系`。零 GUI 成本。

> 注意：拼接配方是 `"**" + shili`，`**` 是本体的占位前缀（可能表示「未定」）。写值后仍会带 `**`，属本体行为，不修改。

### 2.2 贡献值是「有人写、没人看」的字段

```java
public double GongXianZhi;
```

- 58 个本体类会读写它：`GongXianZhiCommand`、`FengYuLou3Procedure`、`FengYuLouRenWuJiangLiProcedure`、`RuoCheng*`、`PaiMaiHang*`、`Rw19..Rw28`、`KE1Procedure` 等。
- `KE1Procedure` 每 tick 只做**负值归零**钳制（`dcmpg` + `ifge 306` → 若 `< 0` 则写 `0.0` + `markSyncDirty`），**没有上限**。
- 全本体**没有任何 GUI 展示 `GongXianZhi`**（语言文件里 `贡献` 一词零命中）。

**结论**：可作为势力晋升的累计货币，且本体不会把它钳到某个上限。**只读不写**——避免和风雨楼 / 拍卖行的消耗互相干扰。

### 2.3 流派道痕是活跃成长轴

- `PlayerVariables` 共 **45 个道痕字段**：36 个 `daohen_*` + 9 个 `dahen_*`（本体命名笔误，少一个 `o`）。
- 其中 `daohen_zong` 是 KE1 每秒重算的**总和**，不归属任何单一流派 → **可归属流派的道痕字段共 44 个**。
- **46 个 `liupai_*` 字段**，被 2492 个 procedure 写入。
- 44 个道痕字段与 46 个 `liupai_*` 的差集（已脚本核实）：
  - `liupai_feixingdao`（飞行道）→ **无** `daohen_feixingdao`
  - `liupai_qingmeidao`（魅情道）→ **无** `daohen_qingmeidao`
  - 其余 44 个 `liupai_*` 与 44 个道痕字段**严格一一对应**（含 9 个走 `dahen_*` 笔误字段的）。
- `KE1Procedure` 每秒把所有 `daohen_*` 累加写入 `daohen_zong`（字节码偏移 8433）。
- `daohen_*` 被 525 个 procedure 写入。

**结论**：感悟点从道痕派生，天然随游玩增长，不需要新增计数器。

### 2.4 本体只有 8 个流派增益 buff

`GuzhenrenModMobEffects` 共 348 个 `DeferredHolder`，其中名字带 `ZENGFU`/`ZENGSHANG` 的只有：

| 字段名 | 流派 |
|---|---|
| `SHUIDAOZENGFU` | 水道 |
| `YUDAOGUCHONGWEILIZENGFU` | 宇道 |
| `QIDAOGUCHONGWEILIZENGFU` | 气道 |
| `JIANDAOSHANGHAIZENGFU` | 剑道 |
| `LVDAOZENGFU` | 律道 |
| `JINDAOZENGFU` | 金道 |
| `YANDAOZENGFU` | 炎道 |
| `LEIDAOZENGSHANG` | 雷道 |

**结论**：其余 38 个流派**没有本体增益 buff**，不能硬造。它们的加成一律走 `AttributeModifier`。

### 2.5 键位占用表（全整合包 51 个 jar 扫描）

| 键 | 占用者 |
|---|---|
| G / W / S / A / D / R / O / Z / X / C / V / L / 0 | 本体 `GuzhenrenModKeyMappings` |
| K | `tianfu`（`key.tianfu.view`）+ 六合一（`key.newguui.switch_pouch`） |
| P | 流派熟练度查看 + 六合一（`key.guji.track_panel`） |
| H / N / B / J / O / U / I | 六合一 |
| F6 | 本体 `RtsKeyMappings` + veil + auratip |
| F8 | 六合一 + asteorbar + auratip |

**空闲字母：E F M Q T Y**。daotu 使用 **M**（`GLFW_KEY_M = 77`）。

### 2.6 硬约束（继承自 tianfu 踩过的坑）

1. **真元上限 999**，由 `KE1` 每秒钳制；`kongqiao` 是**从 `zuida_zhenyuan` 反推**的 → 不写 `zuida_zhenyuan`。
2. **`qiyun_shangxian` 只读不写**（本体从不覆盖它），但本体有 `if (cap < qiyun) qiyun = cap` → 气运加成只能**提升上限**，绝不降低。
3. **`tizhi` 1–16 是本体领地**（1–10 十绝体随机、11–16 本体原生），17–53 是 tianfu 的凡体，54 是杀神 → **daotu 完全不碰 `tizhi`**。
4. **炼蛊完成判定是精确 `LianGuJinDu == 100.0`**（`dcmpl` + `ifne`），本体从不 clamp → 任何加速只补整数点，永不写 100。
5. **无限 buff 的 `getDuration()` 是 `-1`**，`< 600` 恒真 → 就位判定要跟「上次实际授予的等级」比。
6. 效果解析必须用注册表真实 holder（`getHolder(id)`），不能 `Holder.direct`。
7. 「附属没装就不显示」只能用 `ModList.get().isLoaded(ns)`，**绝不查注册表**（`static final` 池在注册表填充前初始化，会永久隐藏内容）。
8. **不跑 Gradle**（沙箱卡在 `C:\Users\ADMIN\.gradle\native\`），走 `_crash3/*.py` → `javac` + `zipfile`。

---

## 3. 三层结构

```
开窍完成（本体 jieduan >= 1）
   └─ 聊天提示：「§b[道途] 按 M 打开道途」
        └─ M → 主界面（四页签）
             ├─【出身】16 选 1（一次性，不可重选）
             ├─【流派】6 大类 × 7 层 + 觉醒分支
             ├─【势力】6 档晋升（读 GongXianZhi）
             └─【道途】总览（当前出身/势力/感悟点/已点节点）
```

---

## 4. 第一层：出身（16 种，原著向）

### 4.1 数据表

每种出身包含：`id`、`name`、`shili`、`shenfen`、`yuanShi`（元石）、`daohenBonus`（各流派道痕加成）、`renqi`（人气增减）、`traits`（特质列表）。

| # | id | 名称 | `shili` | `shenfen` | 元石 | 道痕加成 | 人气 | 特质 |
|---|---|---|---|---|---|---|---|---|
| 1 | `guyue_dixi` | 古月嫡系 | 青茅山·古月家族 | 族长嫡系 | +1200 | 月道 +400、智道 +200 | +30 | 夜间真元回复 +20% |
| 2 | `guyue_pangzhi` | 古月旁支 | 青茅山·古月家族 | 旁支子弟 | +800 | 智道 +300、月道 +100 | +10 | 智道蛊虫威力 +10% |
| 3 | `baijia_shaozhu` | 白家少主 | 青茅山·白家寨 | 寨主之子 | +1500 | 血道 +400、力道 +200 | +25 | 生命上限 +15% |
| 4 | `baijia_liehu` | 白家猎户 | 青茅山·白家寨 | 猎户出身 | +600 | 力道 +400、血道 +100 | +5 | 移速 +6%、击退抗性 +0.1 |
| 5 | `xiongjia_kuangtu` | 熊家狂徒 | 青茅山·熊家寨 | 熊家狂徒 | +500 | 力道 +500 | −30 | 攻击 +12%、击退抗性 +0.2 |
| 6 | `lianxuan_xuetu` | 炼玄谷学徒 | 炼玄谷 | 炼道学徒 | +900 | 炼道 +500、丹道 +150 | +15 | 炼蛊 pacer +25%、起始 3 只一转蛊 |
| 7 | `bingxue_waimen` | 冰雪外门 | 冰雪道准宗 | 外门弟子 | +1000 | 冰雪道 +450、水道 +150 | +20 | 摔落距离 +2、冰雪道增益 |
| 8 | `jianmen_zhujian` | 剑门铸剑 | 剑断山·剑门 | 铸剑弟子 | +1100 | 剑道 +500、金道 +150 | +15 | 剑道增益、攻击速度 +8% |
| 9 | `sanxiu` | 散修 | 散修 | 无门无派 | +2500 | 全流派 +8%（44 个道痕字段各 +80） | −20 | 无势力惩罚减免 |
| 10 | `shangdui_guyi` | 商队遗孤 | 商队 | 商队遗孤 | +4000 | 信道 +200、运道 +150 | +40 | 元石掉落 +15% |
| 11 | `zuitu` | 罪徒 | 罪血营 | 罪徒 | +300 | 血道 +350、暗杀 +200 | −60 | 全属性 +6% |
| 12 | `tianwai_yigu` | 天外遗孤 | 天外之魔 | 天外遗孤 | +700 | 全流派 +15%（44 个道痕字段各 +150） | −40 | 气运上限 +25% |
| 13 | `dushi_dutu` | 赌石坊赌徒 | 赌石坊 | 赌徒 | +1800 | 运道 +500、食道 +100 | +0 | 气运上限 +40%、随机 1~5 只蛊 |
| 14 | `hundao_yishi` | 魂道遗士 | 魂道 | 遗士 | +1000 | 魂道 +500、梦道 +200 | +10 | 魂魄上限 +10% |
| 15 | `daoyou` | 道友好徒 | 万道 | 道友好徒 | +1400 | 万道 +300、律道 +300 | +20 | 全属性 +4%、真元回复 +10% |
| 16 | `qiankun_sanyi` | 乾坤散逸 | 乾坤 | 散逸者 | +2000 | 宇道 +250、宙道 +250、虚道 +200 | −10 | 移速 +8%、摔落距离 +3 |

### 4.2 应用规则

写入（只写这些）：

```java
v.shili = origin.shili();          // 势力面板立刻显示
v.shenfen = origin.shenfen();      // 身份面板立刻显示
v.yuanshishuliang += origin.yuanShi();
v.renqi += origin.renqi();         // 可为负，KE1 会钳到 >= 0
// 道痕：按字段逐个 += （只加不减）
v.daohen_yuedao += 400; ...
v.markSyncDirty();                 // 每个改过的字段都要标记
```

**不写**：`tizhi`、`zuida_zhenyuan`、`kongqiao`、`qiyun_shangxian`（除非提升）、`daohen_zong`（KE1 自己算）。

**幂等**：`DaotuData.originChosen(player)` 为 true 时整层跳过。

### 4.3 不可重选

`DaotuData` 记录 `origin`（String id）与 `originAt`（long 时间戳）。确认后按钮永久置灰。

**唯一重置途径**：本体转世（寿元耗尽）→ `JingJieBaoCunProcedure` 的 TAIL（tianfu 已在此注入，daotu 也注入，各自幂等）→ 清空 `daotu` 子标签。转世后需重新开窍、重新选出身。

---

## 5. 第二层：流派树

### 5.1 六大类划分（46 个 liupai_* 全覆盖）

| 大类 | 收录流派字段 | 数量 |
|---|---|---|
| **杀伐** `sha` | `liupai_jiandao` `liupai_daodao` `liupai_lidao` `liupai_bingdao` `liupai_jindao` `liupai_leidao` `liupai_yandao` `liupai_xuedao` `liupai_dudao` `liupai_bingxuedao` | 10 |
| **术法** `shu` | `liupai_shuidao` `liupai_mudao` `liupai_tudao` `liupai_fengdao` `liupai_guangdao` `liupai_yuedao` `liupai_xingdao` `liupai_tiandao` `liupai_yundao2` | 9 |
| **诡道** `gui` | `liupai_andao` `liupai_yingdao` `liupai_toudao` `liupai_huandao` `liupai_mengdao` `liupai_xudao` `liupai_qingmeidao` `liupai_nudao` | 8 |
| **身法** `shen` | `liupai_feixingdao` `liupai_bianhuadao` `liupai_yudao` `liupai_zhoudao` `liupai_yindao` | 5 |
| **器道** `qi` | `liupai_liandao` `liupai_zhendao` `liupai_dandao` `liupai_huadao` `liupai_shidao` `liupai_jindao2` | 6 |
| **心道** `xin` | `liupai_hundao` `liupai_zhidao` `liupai_xindao` `liupai_rendao` `liupai_lvdao` `liupai_qidao` `Liupai_gudao` `liupai_yundao` | 8 |

合计 10 + 9 + 8 + 5 + 6 + 8 = **46** ✓

> **命名陷阱**：`Liupai_gudao`（大写 L）是骨道；`liupai_jindao` 是金道、`liupai_jindao2` 是禁道；`liupai_yundao` 是运道、`liupai_yundao2` 是云道。映射表必须逐个显式列出，**不能按字符串前缀批量匹配**。

### 5.2 道痕字段对应

不是每个 `liupai_*` 都有同名 `daohen_*`。已核实的映射缺口：

| liupai 字段 | 对应 daohen | 说明 |
|---|---|---|
| `liupai_feixingdao` | **无** | 只有 `liupai_feixingdao`，没有 `daohen_feixingdao` |
| `liupai_qidao` | `dahen_qidao` | 走笔误字段 |
| `liupai_rendao` | `dahen_rendao` | 走笔误字段 |
| `liupai_tiandao` | `dahen_tiandao` | 走笔误字段 |
| `liupai_xingdao` | `dahen_xingdao` | 走笔误字段 |
| `liupai_zhendao` | `dahen_zhendao` | 走笔误字段 |
| `liupai_zhidao` | `dahen_zhidao` | 走笔误字段 |
| `liupai_zhoudao` | `dahen_zhoudao` | 走笔误字段 |
| `liupai_nudao` | `dahen_nudao` | 走笔误字段 |
| `liupai_liandao` | `dahen_liandao` | 走笔误字段 |
| `liupai_qingmeidao` | **无** | 无对应道痕字段 |
| `liupai_jindao2`（禁道） | `daohen_jindao2` | 有 |
| `Liupai_gudao`（骨道） | `daohen_gudao` | 有 |
| 其余 42 个 | `daohen_*` 或 `dahen_*` | 直接 |

> 完整映射表在实现时由脚本生成并审计：44 个 `liupai_*` 严格一一对应 44 个道痕字段，2 个（`feixingdao`/`qingmeidao`）按 0 计入。

**感悟点计算**：`DaotuLiupai.insight(player, category)` = `floor(Σ 该大类所有道痕字段 / 1500)`。
缺字段的流派按 0 计入（不报错）。不读 `daohen_zong`（它是全流派总和，无法按类拆）。

### 5.3 节点设计（每类 7 层 + 觉醒二选一）

每类树形为线性 7 层 + 第 7 层后开一个**觉醒分支（二选一，互斥）**。

| 层 | 成本 | 杀伐 | 术法 | 诡道 | 身法 | 器道 | 心道 |
|---|---|---|---|---|---|---|---|
| 1 | 1 | 攻击 +2% | 气运上限 +4% | 移速 +2% | 摔落距离 +1 | 炼蛊 pacer +5% | 生命上限 +2% |
| 2 | 1 | 攻击 +2% | 气运上限 +4% | 移速 +2% | 摔落距离 +1 | 炼蛊 pacer +5% | 生命上限 +2% |
| 3 | 2 | 攻击 +3%、攻速 +3% | 气运上限 +6%、真元回复 +4% | 移速 +3%、潜行移速 +8% | 击退抗性 +0.05、移速 +2% | 炼蛊 pacer +8% | 生命上限 +3%、回复 +5% |
| 4 | 2 | 攻击 +3%、攻速 +3% | 气运上限 +6%、真元回复 +4% | 移速 +3%、潜行移速 +8% | 击退抗性 +0.05、移速 +2% | 炼蛊 pacer +8% | 生命上限 +3%、回复 +5% |
| 5 | 3 | 攻击 +4%、暴击伤害 +10% | 气运上限 +8%、真元上限 +5% | 移速 +4%、被发现半径 −20% | 摔落距离 +2、移速 +3% | 炼蛊 pacer +12% | 生命上限 +4%、回复 +8% |
| 6 | 3 | 攻击 +4%、暴击伤害 +10% | 气运上限 +8%、真元上限 +5% | 移速 +4%、被发现半径 −20% | 摔落距离 +2、移速 +3% | 炼蛊 pacer +12% | 生命上限 +4%、回复 +8% |
| 7 | 5 | 攻击 +6%、暴击伤害 +15% | 气运上限 +12%、真元上限 +8% | 移速 +6%、潜行暴击 +20% | 移速 +5%、二段跳 | 炼蛊 pacer +20%、投料窗口 +10 tick | 生命上限 +6%、回复 +12% |

**单树总成本** = 1+1+2+2+3+3+5 = **17 点**；**全树 102 点**。

### 5.4 觉醒分支（第 7 层后，二选一）

| 大类 | 觉醒 A | 觉醒 B |
|---|---|---|
| **杀伐** | **杀神一击** — 每 30 秒，下次攻击伤害 ×2（自管 cooldown，走 `LivingDamageEvent`） | **血战不退** — 生命 < 30% 时攻击 +25% |
| **术法** | **万法归元** — 真元归零后 3 秒回满 20%（自管 pacer，不写 `zuida_zhenyuan`） | **气运加身** — 气运上限 +30%，每次突破额外 +5% |
| **诡道** | **遁形** — 潜行时移速 +30%、完全无脚步声 | **夺舍** — 击杀生物有 5% 概率获得其 1 点随机流派道痕 |
| **身法** | **缩地成寸** — 双击跳跃向前位移 6 格（15 秒 CD） | **凌波微步** — 移速 +15%，水中不减速 |
| **器道** | **炼蛊天成** — 炼蛊进度每 pacer tick 额外 +1（**不写 100.0**） | **一器破万法** — 蛊虫耐久消耗 −50% |
| **心道** | **心如止水** — 生命回复 ×2 | **不动明王** — 受伤后 3 秒内减伤 20%（自管乘区） |

**成本**：3 点。**互斥**：选了 A 就不能选 B（记录在 `DaotuData.awakening`，格式 `sha:A`）。

### 5.5 效果实现分工

| 效果类型 | 实现 | 原因 |
|---|---|---|
| 攻击 / 生命 / 移速 / 攻速 / 击退抗性 / 摔落距离 | `AttributeModifier`（transient，登录重建） | 与 tianfu 同乘区叠加，但 id 不同，互不覆盖 |
| 气运上限 | 写 `qiyun_shangxian` **只升不降** | 本体有 `if (cap < qiyun) qiyun = cap` |
| 真元上限 / 回复 | **不写字段**，走 `AttributeModifier` 无法覆盖 → 改为自管 `PlayerTickEvent` 里对 `zhenyuan` 做**回填**（上限不超 999） | 避开 KE1/KE3 的 `kongqiao` 反推 |
| 8 个有本体 buff 的流派 | 挂本体 `ZENGFU` buff（无限时长，amplifier = 1） | 已验证可用 |
| 其余 38 个流派 | 只走属性修正，**不造 buff** | 本体没有对应 buff |
| 炼蛊加速 | 自管 `player.tickCount % 30 == 0` 节拍，补整数点 | 不读 `lg_ke` |
| 大招 | 自管 cooldown + 事件监听 | 无本体钩子可用 |

---

## 6. 第三层：势力晋升（6 档）

读 `GongXianZhi`，**不消耗**，只记录达到过的最高档。

| 档 | 代号 | `shenfen` 后缀 | 阈值 | 效果 |
|---|---|---|---|---|
| 0 | `baishen` | （出身原值） | 0 | — |
| 1 | `waimen` | 外门弟子 | 200 | 全属性 +3% |
| 2 | `neimen` | 内门弟子 | 800 | 全属性 +6%、元石掉落 +10% |
| 3 | `zhenchuan` | 真传弟子 | 3000 | 全属性 +10%、人气 +50 |
| 4 | `zhanglao` | 长老 | 12000 | 全属性 +15%、全流派道痕 +10% |
| 5 | `taishang` | 太上长老 | 40000 | 全属性 +20%、气运上限 +20% |

**`shenfen` 拼接规则**：`出身头衔 · 档位`。例如古月嫡系升到真传 → `族长嫡系·真传弟子`。散修升档 → `无门无派·真传弟子`。

**为什么阈值制不消耗**：本体风雨楼、拍卖行会**扣** `GongXianZhi`。如果 daotu 也消耗，玩家买东西会掉档，体验极差。阈值制下 `GongXianZhi` 只升不降（KE1 只钳负值），档位只增不减。

### 6.1 势力转投（拓展）

达到 `neimen` 后可转投到另一个势力。转投代价：

- `GongXianZhi` 阈值档位**保留**（不重置，因为本体扣贡献会掉档）
- `shili` 改写为新势力
- 出身特质**保留**（出身是「你从哪来」，不可改）
- 转投后 30 分钟内 `shenfen` 显示 `（转投观察期）`，期间无档位加成

**可转投势力**：青茅山·古月家族、青茅山·白家寨、青茅山·熊家寨、炼玄谷、冰雪道准宗、剑断山·剑门、魂道、万道、乾坤、散修。

### 6.2 势力专属奖励（拓展）

每档解锁一个专属被动：

| 势力 | 外门 | 内门 | 真传 | 长老 | 太上 |
|---|---|---|---|---|---|
| 古月家族 | 月道 +5% | 智道蛊虫 +8% | 夜间真元回复 +15% | 月道 +15% | 全流派 +8% |
| 白家寨 | 血道 +5% | 生命上限 +8% | 血道蛊虫 +10% | 生命上限 +15% | 全流派 +8% |
| 熊家寨 | 力道 +5% | 攻击 +8% | 力道蛊虫 +10% | 攻击 +15% | 全流派 +8% |
| 炼玄谷 | 炼蛊 +5% | 炼道 +8% | 炼蛊 +15% | 炼道 +20% | 全流派 +8% |
| 冰雪道准宗 | 冰雪道 +5% | 摔落距离 +2 | 冰雪道 +12% | 摔落距离 +5 | 全流派 +8% |
| 剑断山·剑门 | 剑道 +5% | 攻速 +6% | 剑道 +12% | 攻速 +12% | 全流派 +8% |
| 魂道 | 魂道 +5% | 魂魄上限 +8% | 魂道 +12% | 魂魄上限 +15% | 全流派 +8% |
| 万道 | 万道 +5% | 全属性 +2% | 万道 +12% | 全属性 +5% | 全流派 +8% |
| 乾坤 | 宇道 +5% | 宙道 +5% | 移速 +8% | 摔落距离 +4 | 全流派 +8% |
| 散修 | 元石 +5% | 全属性 +2% | 元石 +15% | 全属性 +5% | 全流派 +8% |

---

## 7. 界面设计

自绘 `Screen`，**不用贴图**（纯 `GuiGraphics` 色块 + 文字），四页签：

```
┌──────────────────────────────────────────────────────┐
│  [出身] [流派] [势力] [道途]                           │
├──────────────────────────────────────────────────────┤
│                                                       │
│   （虚拟列表，固定行高 22px，滚动条右侧）                │
│                                                       │
│                                                       │
├──────────────────────────────────────────────────────┤
│  感悟点：杀 4 / 术 2 / 诡 0 / 身 1 / 器 7 / 心 3        │
│  贡献：1240（外门 → 内门还需 800）                      │
└──────────────────────────────────────────────────────┘
```

- **虚拟列表**：只渲染可见行，`layout()` 是唯一分配命中区的地方（tianfu 的 `TianfuViewScreen` 已验证这套写法）。
- **页签切换**：4 个按钮，`layout()` 统一分配。
- **置灰**：已购节点、已选出身、互斥觉醒分支的另一个 → 灰色文字 + 点击无响应。

---

## 8. 网络协议

| 包 | 方向 | 内容 |
|---|---|---|
| `DaotuStatePayload` | S2C | 完整状态快照（出身/势力/档位/感悟点/已点节点/觉醒） |
| `DaotuRequestPayload` | C2S | 请求状态（按 M 时发） |
| `DaotuChooseOriginPayload` | C2S | 选出身（带 origin id） |
| `DaotuUnlockNodePayload` | C2S | 点节点（带 category + node index） |
| `DaotuAwakenPayload` | C2S | 选觉醒分支（带 category + A/B） |
| `DaotuSwitchFactionPayload` | C2S | 转投势力（带 faction id） |

全部 C2S 包**服务端二次校验**（出身未选、点数够、前置层已点、未互斥），不信任客户端。

---

## 9. 状态存储

`PersistentData` 子标签 `daotu`：

```
daotu: {
  origin: "guyue_dixi",         // String，空 = 未选
  originAt: 1234567890L,        // Long，选出身的时间戳
  faction: "guyue",             // String，当前势力（可转投改变）
  tier: 3,                      // Int，档位 0..5
  tierReached: 3,               // Int，历史最高档
  switchAt: 0L,                 // Long，转投时间（观察期用）
  nodes: [ "sha:0", "sha:1", "qi:0" ],  // String[]，已点节点
  awakening: "sha:A",           // String，觉醒分支
  insightSpent: { "sha": 2, "qi": 1 }   // CompoundTag，各类已花点数（用于校验）
}
```

**不存**感悟点余额——每次从道痕实时算，避免与本体道痕增长不同步。

---

## 10. 类清单（约 34 个）

```
net.guzhenren.daotu
├─ DaotuMod.java                  入口：注册 payload / 事件 / 粒子
├─ DaotuData.java                 PersistentData 读写 + 幂等标记
├─ DaotuPool.java                 16 出身数据表
├─ DaotuTree.java                 6 大类 × 7 层 + 12 觉醒分支数据表
├─ DaotuLiupai.java               46 liupai_* ↔ 大类 ↔ daohen 字段映射
├─ DaotuInsight.java              感悟点计算
├─ DaotuOrigin.java               出身应用（写 shili/shenfen/元石/道痕/人气）
├─ DaotuTrait.java                出身特质被动维护
├─ DaotuFaction.java              6 档晋升 + 转投
├─ DaotuFactionBonus.java         势力专属奖励表
├─ DaotuApply.java                节点效果应用（从 nodes 集合算总加成）
├─ DaotuAttributes.java           属性修正 sync（transient）
├─ DaotuEffects.java              8 个本体流派 buff 维护（无限时长）
├─ DaotuZhenyuan.java             真元回复自管（不写 zuida_zhenyuan）
├─ DaotuLianGu.java               炼蛊加速（自管 pacer，整数点）
├─ DaotuCombat.java               杀神一击 / 血战不退 / 不动明王
├─ DaotuAwakening.java            觉醒分支效果
├─ DaotuEvents.java               登录 / 克隆 / 重生 / tick
├─ DaotuNet.java                  发状态包
├─ DaotuNames.java                名字表（体质不碰，只做势力/档位名）
├─ mixin/UikongqiaoguanbiProcedureMixin.java     TAIL → 发提示，不弹窗
├─ mixin/JingJieBaoCunProcedureMixin.java        TAIL → 转世重置 daotu
├─ net/DaotuStatePayload.java
├─ net/DaotuRequestPayload.java
├─ net/DaotuChooseOriginPayload.java
├─ net/DaotuUnlockNodePayload.java
├─ net/DaotuAwakenPayload.java
├─ net/DaotuSwitchFactionPayload.java
├─ client/DaotuKeyMappings.java                  M 键
├─ client/DaotuClientHandler.java                包处理
├─ client/DaotuViewScreen.java                   主界面（四页签）
├─ client/DaotuOriginTab.java                    出身页
├─ client/DaotuTreeTab.java                      流派页
└─ client/DaotuFactionTab.java                   势力页
```

**两个 Mixin 都必须 `require = 0` + `remap = false` + try-catch 静默**，本体升级不崩。

---

## 11. 交付计划

| 版本 | 内容 | 预估类数 |
|---|---|---|
| **1.0.0** | 骨架 + 出身 16 种 + 势力 6 档 + 主界面（出身/势力/道途三页签） | ~22 |
| **1.1.0** | 流派树 6×7 = 42 节点 + 流派页签 | ~28 |
| **1.2.0** | 觉醒分支 12 个 + 势力转投 + 势力专属奖励 | ~34 |

三版共用同一 `DaotuData` 格式，向后兼容（旧存档缺字段按默认值处理）。

---

## 12. 验证清单

**编译期可验证**：
- `javac exit: 0`（语法 + 签名）
- `verify_jar.py`（jar 结构、必需条目）
- `check_bom.py`（无 BOM）
- 静态审计：46 个 `liupai_*` 字段名逐个存在于 `PlayerVariables`；45 个道痕字段名逐个存在；8 个 buff 字段名逐个存在

**只能游戏内验证**（交付时必须说明「游戏内未实测」）：
- 属性面板「势力:」「身份:」是否真的显示
- 炼蛊加速是否卡在 100 不完成
- 无限 buff 是否每 tick 重建
- 真元回填是否被 KE1 覆盖
- GUI 换行、置灰、滚动条命中
- 觉醒大招的 cooldown 手感

---

## 13. 与 tianfu 的冲突审查

| 冲突面 | 状态 |
|---|---|
| 字段写入 | tianfu 写 `tizhi`/`daohen_*`/`liupai_*`；daotu 写 `shili`/`shenfen`/`yuanshishuliang` → **不重叠** |
| PersistentData | `tianfu` 子标签 vs `daotu` 子标签 → **不重叠** |
| Mixin 注入点 | 都注入 `UikongqiaoguanbiProcedure.execute` TAIL → **顺序未定义，但两者都幂等、不依赖对方状态** |
| Mixin 注入点 | 都注入 `JingJieBaoCunProcedure` TAIL → 同上 |
| 键位 | K vs M → **不冲突** |
| 属性修饰符 id | `tianfu:*` vs `daotu:*` → **命名空间隔离** |
| 道痕加成 | 都只做 `+=`，不做 `=` → **可叠加** |
| 气运上限 | 都只升不降 → **可叠加** |
| 元石 | tianfu 不写 `yuanshishuliang` → **无冲突** |
