# 道途 · 肉鸽成长系统 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「16 路 × 6 阶」的固定阶位成长，换成「单一历练池 + 无限次抽选」的肉鸽式成长，属性无上限但每次给得少，按本体转数放大，每 10 次额外给一次 buff/道痕特殊奖励。

**Architecture:** 历练汇入一个 `run/pool` 计数器；攒满 `T=240` 就消耗一次并生成 5 个候选，玩家选 1 个；选中值累加进 `run/attrs`（属性）或 `run/buffs`（buff）或 `run/ledger`（道痕）。`DaotuAttributes.sync()` 从 `run/attrs` 全量重建 modifier，因此转世只需删 `run` 子标签。`DaotuGrowth` 整个删除。

**Tech Stack:** Java 21 / NeoForge 21.1.263 / MC 1.21.1；无 Gradle，用 `_crash3/javac_check.py` + `pack_jar.py` 构建。

## Global Constraints

- **Java 21**：`C:\Users\ADMIN\.jdks\temurin-21\bin\javac.exe`
- **绝不写 BOM**：Java 源与 `neoforge.mods.toml` 都必须无 BOM，写文件用
  `[System.IO.File]::WriteAllText($p,$t,(New-Object System.Text.UTF8Encoding($false)))`
  或 Python `io.open(..., newline='\n')`。**永不使用 `Set-Content -Encoding UTF8`。**
- **绝不运行 Gradle。**
- **版本**：`version.properties` 与 `neoforge.mods.toml` 必须同步改为 `1.3.0`。
- **属性 modifier 只用** `addOrUpdateTransientModifier` / `removeModifier`，
  **绝不** `setBaseValue`（会钳当前血量）。
- **不写本体字段**：`zhuanshu` 只读；`daohen_zong` / `kongqiao` / `zuida_zhenyuan` / `tizhi` 一律不写。
- **不用 `Attributes.ARMOR`**（本体 46 个 procedure 用 `base set` 控制它，会叠加）。
- **只用这 14 个属性**：`attack_damage, max_health, movement_speed, attack_speed,
  armor_toughness, max_absorption, knockback_resistance, fall_damage_multiplier,
  luck, safe_fall_distance, attack_knockback, oxygen_bonus, step_height, scale`
- **buff 白名单 12 项**（不得增删）：
  `shuidaozengfu, yudaoguchongweilizengfu, qidaoguchongweilizengfu,
  jiandaoshanghaizengfu, lvdaozengfu, jindaozengfu, yandaozengfu,
  leidaozengshang, weilizengjia, daligubuff, tiexue, leishen`
- **明确排除**：`weilijianbei`、`ya_li_2`、`jie_bi_ya_li`
- **`$env:PYTHONIOENCODING='utf-8'`** 必须在跑 Python 前设置；Python 只写文件，不内联多行。
- **无 git 仓库**：每个 Task 的收尾检查点是「编译通过」，不是 `git commit`。
- **游戏内未实测**：所有结论为静态分析，交付时必须声明。

---

## 文件结构

| 文件 | 职责 | 动作 |
|---|---|---|
| `DaotuRun.java` | 本局状态读写：`pool` / `draws` / `attrs` / `buffs` / `ledger` / `pending` | **新建** |
| `DaotuDrawTable.java` | 档位表 + 转数缩放 + 候选生成 | **新建** |
| `DaotuRunAttributes.java` | `run/attrs` → 原版 modifier | **新建** |
| `DaotuSpecial.java` | 特殊奖励：buff 白名单 + 道痕池 | **新建** |
| `DaotuDraw.java` | 抽选主流程（含特殊触发） | **新建** |
| `DaotuGrowth.java` | 16 路 × 6 阶常量 | **删除** |
| `DaotuData.java` | 加 `run` 子标签访问器；删 16 路访问器 | 修改 |
| `DaotuLiLian.java` | `award()` 改为灌单池；删 `absorb` / `claimUpTo` | 重写 |
| `DaotuBuffs.java` | 改为维持 `run/buffs` | 重写 |
| `DaotuAttributes.java` | `sync()` 改为读 `run/attrs` | 重写 |
| `DaotuOpening.java` | 转世 7 步；登录补发改为校验 | 修改 |
| `DaotuApply.java` | 删 `growthAttr` 的 16 路循环 | 修改 |
| `DaotuGrowthApply.java` | 加 `subDaohen`；`refreshTitles` 改用主属性 | 修改 |
| `DaotuStatePayload.java` | 字段换成 `pool/draws/attrs/pending` | 重写 |
| `DaotuPickPayload.java` | C2S 重选 | **新建** |
| `DaotuViewScreen.java` | 成长页重做 | 修改 |
| `DaotuNet.java` / `DaotuMod.java` | 适配新 payload | 修改 |

---

### Task 1: 本局状态存储

**Files:**
- Create: `src\main\java\net\guzhenren\daotu\DaotuRun.java`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuData.java`

**Interfaces:**
- Consumes: `DaotuData.root(Player,boolean)`（private，本任务内改为 package-private）、
  `DaotuData.update(Player,Consumer<CompoundTag>)`
- Produces:
  - `DaotuRun.POOL_THRESHOLD = 240.0`
  - `double DaotuRun.pool(Player)` / `void DaotuRun.setPool(Player,double)`
  - `double DaotuRun.addPool(Player,double)` → 返回加完后的值
  - `int DaotuRun.draws(Player)` / `void DaotuRun.setDraws(Player,int)`
  - `double DaotuRun.attr(Player,String)` / `void DaotuRun.setAttr(Player,String,double)`
  - `Map<String,Double> DaotuRun.attrs(Player)`
  - `List<String> DaotuRun.buffs(Player)` / `void DaotuRun.addBuff(Player,String)`
  - `Map<String,Double> DaotuRun.ledger(Player)` / `void DaotuRun.addLedger(Player,String,double)`
  - `List<String> DaotuRun.pending(Player)` / `void DaotuRun.addPending(Player,String)` / `void DaotuRun.clearPending(Player)`
  - `int DaotuRun.runId(Player)`
  - `void DaotuRun.resetRun(Player)` —— **只删 `run` 子标签，不删整个 `daotu`**

- [ ] **Step 1: 写 `DaotuRun.java`**

```java
package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 一局（一个转世周期）的全部成长状态。
 *
 * <p>存在 {@code daotu/run} 子标签里，与势力 / 流派节点 / 觉醒并列。
 * 转世只删这一个子标签，其余（势力、节点）按规格书保持原状。
 *
 * <p><b>为什么属性存「已累加的绝对值」而不是「抽选记录」</b>：
 * {@link DaotuRunAttributes} 每次从 {@code attrs} 全量重建 modifier，
 * 这样重选、转世、离线登录都不需要重放历史，也不怕中途改过数值表。
 */
public final class DaotuRun {

    /** 攒满一次抽选所需的历练值。固定不增长。 */
    public static final double POOL_THRESHOLD = 240.0;

    private static final String RUN = "run";

    private DaotuRun() {
    }

    private static CompoundTag run(Player player, boolean create) {
        return DaotuData.sub( player, RUN, create);
    }

    // ---------------------------------------------------------------- 池与抽次

    public static double pool(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0.0 : t.getDouble("pool");
    }

    public static void setPool(Player player, double value) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.putDouble("pool", Math.max(0.0, value));
            tag.put(RUN, r);
        });
    }

    /** 累加历练，返回加完后的池值。负数忽略。 */
    public static double addPool(Player player, double delta) {
        if (delta <= 0.0) {
            return pool(player);
        }
        final double[] out = new double[1];
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            double v = r.getDouble("pool") + delta;
            r.putDouble("pool", v);
            tag.put(RUN, r);
            out[0] = v;
        });
        return out[0];
    }

    public static int draws(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0 : t.getInt("draws");
    }

    public static void setDraws(Player player, int value) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.putInt("draws", Math.max(0, value));
            tag.put(RUN, r);
        });
    }

    public static int runId(Player player) {
        CompoundTag t = run(player, false);
        return t == null ? 0 : t.getInt("id");
    }

    // ---------------------------------------------------------------- 属性

    public static double attr(Player player, String key) {
        CompoundTag t = run(player, false);
        if (t == null || !t.contains("attrs", Tag.TAG_COMPOUND)) {
            return 0.0;
        }
        return t.getCompound("attrs").getDouble(key);
    }

    public static void setAttr(Player player, String key, double value) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            CompoundTag a = r.contains("attrs", Tag.TAG_COMPOUND)
                    ? r.getCompound("attrs") : new CompoundTag();
            if (Math.abs(value) <= 1.0e-9) {
                a.remove(key);
            } else {
                a.putDouble(key, value);
            }
            r.put("attrs", a);
            tag.put(RUN, r);
        });
    }

    public static Map<String, Double> attrs(Player player) {
        CompoundTag t = run(player, false);
        Map<String, Double> out = new LinkedHashMap<>();
        if (t == null || !t.contains("attrs", Tag.TAG_COMPOUND)) {
            return out;
        }
        CompoundTag a = t.getCompound("attrs");
        for (String k : a.getAllKeys()) {
            out.put(k, a.getDouble(k));
        }
        return out;
    }

    // ---------------------------------------------------------------- buff

    public static List<String> buffs(Player player) {
        CompoundTag t = run(player, false);
        List<String> out = new ArrayList<>();
        if (t == null || !t.contains("buffs", Tag.TAG_LIST)) {
            return out;
        }
        ListTag l = t.getList("buffs", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    public static void addBuff(Player player, String id) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            ListTag l = r.contains("buffs", Tag.TAG_LIST)
                    ? r.getList("buffs", Tag.TAG_STRING) : new ListTag();
            for (int i = 0; i < l.size(); i++) {
                if (l.getString(i).equals(id)) {
                    return; // 去重：同名 buff 不重复记
                }
            }
            l.add(StringTag.valueOf(id));
            r.put("buffs", l);
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 道痕账本

    public static Map<String, Double> ledger(Player player) {
        CompoundTag t = run(player, false);
        Map<String, Double> out = new LinkedHashMap<>();
        if (t == null || !t.contains("ledger", Tag.TAG_COMPOUND)) {
            return out;
        }
        CompoundTag g = t.getCompound("ledger");
        for (String k : g.getAllKeys()) {
            out.put(k, g.getDouble(k));
        }
        return out;
    }

    public static void addLedger(Player player, String key, double amount) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            CompoundTag g = r.contains("ledger", Tag.TAG_COMPOUND)
                    ? r.getCompound("ledger") : new CompoundTag();
            g.putDouble(key, g.getDouble(key) + amount);
            r.put("ledger", g);
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 待重选

    public static List<String> pending(Player player) {
        CompoundTag t = run(player, false);
        List<String> out = new ArrayList<>();
        if (t == null || !t.contains("pending", Tag.TAG_LIST)) {
            return out;
        }
        ListTag l = t.getList("pending", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    public static void addPending(Player player, String entry) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            ListTag l = r.contains("pending", Tag.TAG_LIST)
                    ? r.getList("pending", Tag.TAG_STRING) : new ListTag();
            l.add(StringTag.valueOf(entry));
            // 只留最近 20 条，防止无限增长
            while (l.size() > 20) {
                l.remove(0);
            }
            r.put("pending", l);
            tag.put(RUN, r);
        });
    }

    public static void clearPending(Player player) {
        DaotuData.update(player, tag -> {
            CompoundTag r = child(tag);
            r.remove("pending");
            tag.put(RUN, r);
        });
    }

    // ---------------------------------------------------------------- 重开一局

    /** 只删 {@code run} 子标签。势力 / 节点 / 觉醒保留。 */
    public static void resetRun(Player player) {
        DaotuData.update(player, tag -> tag.remove(RUN));
    }

    /** 起一局新的：id +1，池归零。必须在 {@link #resetRun} 之后调用。 */
    public static void beginRun(Player player, int newId) {
        DaotuData.update(player, tag -> {
            CompoundTag r = new CompoundTag();
            r.putInt("id", newId);
            r.putDouble("pool", 0.0);
            r.putInt("draws", 0);
            tag.put(RUN, r);
        });
    }

    private static CompoundTag child(CompoundTag daotuTag) {
        return daotuTag.contains(RUN, Tag.TAG_COMPOUND)
                ? daotuTag.getCompound(RUN) : new CompoundTag();
    }
}
```

- [ ] **Step 2: 在 `DaotuData` 里加 `sub(...)` 并把 `update` 提升为 package-private**

把 `DaotuData.java` 第 42–59 行的两个方法改为：

```java
    static CompoundTag root(Player player, boolean create) {
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains(ROOT, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            pd.put(ROOT, new CompoundTag());
        }
        return pd.getCompound(ROOT);
    }

    /** 原子地读-改-写，避免 getCompound 在缺失时返回游离 tag。 */
    static void update(Player player, Consumer<CompoundTag> mutator) {
        CompoundTag pd = player.getPersistentData();
        CompoundTag tag = pd.contains(ROOT, Tag.TAG_COMPOUND) ? pd.getCompound(ROOT) : new CompoundTag();
        mutator.accept(tag);
        pd.put(ROOT, tag);
    }

    /** 读 {@code daotu} 下的某个子标签。 */
    static CompoundTag sub(Player player, String key, boolean create) {
        CompoundTag tag = root(player, create);
        if (tag == null) {
            return null;
        }
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            tag.put(key, new CompoundTag());
        }
        return tag.getCompound(key);
    }
```

- [ ] **Step 3: 编译验证**

Run:
```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 20
```
Expected: `javac exit: 0`

- [ ] **Step 4: 无 BOM 校验**

```powershell
$b=[System.IO.File]::ReadAllBytes('D:\daima\mcmod\道途附属\src\main\java\net\guzhenren\daotu\DaotuRun.java')
if ($b[0] -eq 0xEF) { 'BOM FOUND' } else { 'no BOM OK' }
```
Expected: `no BOM OK`

---

### Task 2: 档位表与候选生成

**Files:**
- Create: `src\main\java\net\guzhenren\daotu\DaotuDrawTable.java`

**Interfaces:**
- Consumes: `DaotuRun.draws(Player)`, `DaotuRun.attr(Player,String)`
- Produces:
  - `record DaotuDrawTable.Tier(int index, double forcePct, double defense, double healthPct, double speedPct, double resistPct, double other)`
  - `DaotuDrawTable.Tier tierFor(int drawIndex)` —— `drawIndex` 从 1 开始
  - `double DaotuDrawTable.zhuanScale(double zhuanshu)` → `Math.pow(1.4, zhuanshu-1)`，`zhuanshu<1` 当 1
  - `record DaotuDrawTable.Candidate(String key, String label, double amount, boolean percent)`
  - `List<Candidate> DaotuDrawTable.candidates(Player, int drawIndex, RandomSource)`

- [ ] **Step 1: 写 `DaotuDrawTable.java`**

```java
package net.guzhenren.daotu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * 抽选档位表。
 *
 * <p><b>刻意用查表而不是公式</b>：公式（如 {@code base * decay^n}）在前几次抽选时
 * 掉得太快，玩家开局二十次就感觉「没成长」。查表可以手工把前 5 次调得很甜，
 * 之后平缓衰减，手感更接近肉鸽游戏。
 *
 * <p><b>力量 / 生命必须是百分比</b>：原版攻击力 base = 1.0，若用平加，
 * 抽 100 次后 +84 点等于 84 倍攻击，直接崩。生命同理（base = 20）。
 * 速度、攻速也是百分比。防御、抗性、其它是平加，因为它们本身量级小。
 */
public final class DaotuDrawTable {

    /** 一次档位：所有值都是「一转、未缩放」的原始值。 */
    public record Tier(int index, double forcePct, double defense, double healthPct,
                       double speedPct, double resistPct, double other) {
    }

    /**
     * 档位表。抽次从 1 开始。
     *
     * <pre>
     * 抽次   档   力量%   防御   生命%   速度%   抗性%   其它
     * 1-5    1   +3.0%  +0.5   +3.0%  +1.0%  +1.0%  +0.5
     * 6-15   2   +2.5%  +0.4   +2.5%  +0.8%  +0.8%  +0.4
     * 16-30  3   +2.0%  +0.3   +2.0%  +0.6%  +0.6%  +0.3
     * 31-60  4   +1.5%  +0.2   +1.5%  +0.4%  +0.4%  +0.2
     * 61+    5   +1.0%  +0.1   +1.0%  +0.3%  +0.3%  +0.1
     * </pre>
     */
    private static final Tier[] TIERS = {
        new Tier(1, 0.030, 0.5, 0.030, 0.010, 0.010, 0.5),
        new Tier(2, 0.025, 0.4, 0.025, 0.008, 0.008, 0.4),
        new Tier(3, 0.020, 0.3, 0.020, 0.006, 0.006, 0.3),
        new Tier(4, 0.015, 0.2, 0.015, 0.004, 0.004, 0.2),
        new Tier(5, 0.010, 0.1, 0.010, 0.003, 0.003, 0.1),
    };

    /** 每次给几个候选。 */
    public static final int OPTIONS = 5;

    // ---------------------------------------------------------------- 软上限

    /** 击退抗性总上限（原版语义就是 0..1）。 */
    private static final double KNOCKBACK_CAP = 1.0;
    /** 移速总上限（+100%）。 */
    private static final double SPEED_CAP = 1.0;
    /** 攻速总上限（+150%）。 */
    private static final double ATTACK_SPEED_CAP = 1.5;
    /** 生命总上限（+200%）。 */
    private static final double HEALTH_CAP = 2.0;
    /** 体型上限（+100%）：再高会卡墙。 */
    private static final double SCALE_CAP = 1.0;
    /** 跨越高度上限（+2.0 格）：再高等于爬墙。 */
    private static final double STEP_CAP = 2.0;

    private DaotuDrawTable() {
    }

    /** 抽次（从 1 开始）对应的档位。 */
    public static Tier tierFor(int drawIndex) {
        if (drawIndex <= 5) {
            return TIERS[0];
        }
        if (drawIndex <= 15) {
            return TIERS[1];
        }
        if (drawIndex <= 30) {
            return TIERS[2];
        }
        if (drawIndex <= 60) {
            return TIERS[3];
        }
        return TIERS[4];
    }

    /**
     * 转数缩放：{@code 1.4^(转数-1)} → 1 / 1.40 / 1.96 / 2.74 / 3.84。
     *
     * <p>转数越高，同样一次抽选给得越多，这样高转玩家不会觉得成长停滞。
     * {@code zhuanshu} 只读，绝不写。
     */
    public static double zhuanScale(double zhuanshu) {
        double z = Math.max(1.0, Math.min(5.0, zhuanshu));
        return Math.pow(1.4, z - 1.0);
    }

    /** 候选。{@code key} 是 {@code run/attrs} 的键；{@code percent} 决定挂哪种 modifier。 */
    public record Candidate(String key, String label, double amount, boolean percent) {
    }

    /**
     * 生成本次的 5 个候选。
     *
     * <p>软上限已满的项<b>不再出现</b>，避免给出无效选项。
     */
    public static List<Candidate> candidates(Player player, int drawIndex, RandomSource rnd) {
        Tier t = tierFor(drawIndex);
        double s = zhuanScale(DaotuRun.zhuanshu(player));
        List<Candidate> pool = new ArrayList<>();

        // ---- 力量（百分比，无上限） ----
        pool.add(new Candidate("attack_damage", "力量 +" + pct(t.forcePct() * s), t.forcePct() * s, true));

        // ---- 生命（百分比，软上限 +200%） ----
        if (DaotuRun.attr(player, "max_health") < HEALTH_CAP) {
            pool.add(new Candidate("max_health", "生命 +" + pct(t.healthPct() * s), t.healthPct() * s, true));
        }

        // ---- 速度（百分比，软上限 +100%） ----
        if (DaotuRun.attr(player, "movement_speed") < SPEED_CAP) {
            pool.add(new Candidate("movement_speed", "速度 +" + pct(t.speedPct() * s), t.speedPct() * s, true));
        }

        // ---- 攻速（百分比，软上限 +150%） ----
        if (DaotuRun.attr(player, "attack_speed") < ATTACK_SPEED_CAP) {
            pool.add(new Candidate("attack_speed", "攻速 +" + pct(t.speedPct() * s * 1.5), t.speedPct() * s * 1.5, true));
        }

        // ---- 防御（平加，无上限）：护甲韧性 + 伤害吸收 ----
        pool.add(new Candidate("armor_toughness", "护甲韧性 +" + flat(t.defense() * s), t.defense() * s, false));
        pool.add(new Candidate("max_absorption", "伤害吸收 +" + flat(t.defense() * s * 2.0), t.defense() * s * 2.0, false));

        // ---- 抗性（平加，击退抗性软上限 1.0） ----
        if (DaotuRun.attr(player, "knockback_resistance") < KNOCKBACK_CAP) {
            pool.add(new Candidate("knockback_resistance", "击退抗性 +" + pct(t.resistPct() * s),
                    t.resistPct() * s, false));
        }
        pool.add(new Candidate("fall_damage_multiplier", "摔伤减免 +" + pct(t.resistPct() * s),
                -t.resistPct() * s, false));

        // ---- 其它（平加，无上限） ----
        pool.add(new Candidate("luck", "幸运 +" + flat(t.other() * s), t.other() * s, false));
        pool.add(new Candidate("safe_fall_distance", "安全摔落 +" + flat(t.other() * s), t.other() * s, false));
        pool.add(new Candidate("attack_knockback", "攻击击退 +" + flat(t.other() * s * 0.5), t.other() * s * 0.5, false));
        pool.add(new Candidate("oxygen_bonus", "水下呼吸 +" + flat(t.other() * s), t.other() * s, false));
        if (DaotuRun.attr(player, "step_height") < STEP_CAP) {
            pool.add(new Candidate("step_height", "跨越高度 +" + flat(t.other() * s * 0.5), t.other() * s * 0.5, false));
        }
        if (DaotuRun.attr(player, "scale") < SCALE_CAP) {
            pool.add(new Candidate("scale", "体型 +" + pct(t.other() * s), t.other() * s, true));
        }

        // 洗牌后取前 5 个
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Candidate tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        int n = Math.min(OPTIONS, pool.size());
        return new ArrayList<>(pool.subList(0, n));
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100.0);
    }

    private static String flat(double v) {
        return String.format("%.2f", v);
    }
}
```

- [ ] **Step 2: 在 `DaotuRun` 里补 `zhuanshu(Player)`**

在 `DaotuRun.java` 末尾（`child` 之前）加：

```java
    /** 只读本体转数（1..5）。绝不写回。 */
    public static double zhuanshu(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return 1.0;
        }
        return sp.getData(net.guzhenren.network.GuzhenrenModVariables.PLAYER_VARIABLES).zhuanshu;
    }
```

- [ ] **Step 3: 编译验证**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 20
```
Expected: `javac exit: 0`

---

### Task 3: 属性应用层

**Files:**
- Create: `src\main\java\net\guzhenren\daotu\DaotuRunAttributes.java`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuAttributes.java`

**Interfaces:**
- Consumes: `DaotuRun.attrs(Player)`
- Produces:
  - `void DaotuRunAttributes.apply(Player)` —— 从 `run/attrs` 全量重建
  - `void DaotuRunAttributes.grant(Player, String key, double amount, boolean percent)`
  - `double DaotuRunAttributes.capOf(String key)`

- [ ] **Step 1: 写 `DaotuRunAttributes.java`**

```java
package net.guzhenren.daotu;

import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 把 {@code run/attrs} 里的累加值挂成原版属性 modifier。
 *
 * <p><b>每次全量重建</b>：先移除本模组全部 modifier，再按当前值重新挂。
 * 这样重选、转世、离线登录都只需要读一次 {@code run/attrs}，
 * 不需要重放抽选历史，也不怕中途改过数值表。
 *
 * <p><b>绝不 {@code setBaseValue}</b>：那会钳当前血量。
 * modifier 是叠加层，只影响上限计算。
 */
public final class DaotuRunAttributes {

    private static final String NS = DaotuMod.MODID;

    /** 键 → 属性。顺序即挂载顺序，便于日志排查。 */
    private static Holder<Attribute> attributeOf(String key) {
        return switch (key) {
            case "attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "max_health" -> Attributes.MAX_HEALTH;
            case "movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "attack_speed" -> Attributes.ATTACK_SPEED;
            case "armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "max_absorption" -> Attributes.MAX_ABSORPTION;
            case "knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "fall_damage_multiplier" -> Attributes.FALL_DAMAGE_MULTIPLIER;
            case "luck" -> Attributes.LUCK;
            case "safe_fall_distance" -> Attributes.SAFE_FALL_DISTANCE;
            case "attack_knockback" -> Attributes.ATTACK_KNOCKBACK;
            case "oxygen_bonus" -> Attributes.OXYGEN_BONUS;
            case "step_height" -> Attributes.STEP_HEIGHT;
            case "scale" -> Attributes.SCALE;
            default -> null;
        };
    }

    /** 哪些键是百分比语义。 */
    public static boolean isPercent(String key) {
        return switch (key) {
            case "attack_damage", "max_health", "movement_speed", "attack_speed", "scale" -> true;
            default -> false;
        };
    }

    /** 累加一个抽选结果。重选时传负 amount 即可撤销。 */
    public static void grant(Player player, String key, double amount, boolean percent) {
        if (amount == 0.0) {
            return;
        }
        double cur = DaotuRun.attr(player, key);
        double next = cur + amount;
        double cap = capOf(key);
        if (cap > 0.0 && next > cap) {
            next = cap;
        }
        if (next < 0.0 && !key.equals("fall_damage_multiplier")) {
            next = 0.0;
        }
        DaotuRun.setAttr(player, key, next);
    }

    /** 软上限；0 表示无上限。 */
    public static double capOf(String key) {
        return switch (key) {
            case "knockback_resistance" -> 1.0;
            case "movement_speed" -> 1.0;
            case "attack_speed" -> 1.5;
            case "max_health" -> 2.0;
            case "scale" -> 1.0;
            case "step_height" -> 2.0;
            default -> 0.0;
        };
    }

    /** 全量重建。 */
    public static void apply(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        // 先清掉本模组挂过的一切（键集合固定，逐个 remove 安全）
        for (String key : new String[] {"attack_damage", "max_health", "movement_speed",
                "attack_speed", "armor_toughness", "max_absorption", "knockback_resistance",
                "fall_damage_multiplier", "luck", "safe_fall_distance", "attack_knockback",
                "oxygen_bonus", "step_height", "scale"}) {
            Holder<Attribute> attr = attributeOf(key);
            if (attr == null) {
                continue;
            }
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            ResourceLocation rid = id(key);
            if (inst.getModifier(rid) != null) {
                inst.removeModifier(rid);
            }
        }

        Map<String, Double> attrs = DaotuRun.attrs(player);
        for (var e : attrs.entrySet()) {
            String key = e.getKey();
            double value = e.getValue();
            Holder<Attribute> attr = attributeOf(key);
            if (attr == null) {
                DaotuMod.LOGGER.warn("未知属性键: {}", key);
                continue;
            }
            if (Math.abs(value) <= 1.0e-9) {
                continue;
            }
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            AttributeModifier.Operation op = isPercent(key)
                    ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    : AttributeModifier.Operation.ADD_VALUE;
            inst.addOrUpdateTransientModifier(new AttributeModifier(id(key), value, op));
        }
    }

    private static ResourceLocation id(String key) {
        return ResourceLocation.fromNamespaceAndPath(NS, "run_" + key);
    }

    private DaotuRunAttributes() {
    }
}
```

- [ ] **Step 2: 让 `DaotuAttributes.sync` 转调新层，保留势力/节点加成**

把 `DaotuAttributes.sync(Player)` 的方法体整个换成：

```java
    public static void sync(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        // 抽选成长：全量重建
        DaotuRunAttributes.apply(player);

        // 势力档位 / 流派节点的旧加成：仍然走原来的字段，
        // 但它们和抽选属性用不同的 modifier id，不会互相覆盖。
        double all = DaotuApply.tierAllPct(player);
        applyPercent(player, Attributes.MAX_HEALTH, "body_health",
                DaotuApply.healthPct(player) + all);
        applyPercent(player, Attributes.ATTACK_DAMAGE, "body_attack",
                DaotuApply.attackPct(player) + DaotuApply.critDamagePct(player) + all);
        applyPercent(player, Attributes.ATTACK_SPEED, "talent_attack_speed",
                DaotuApply.attackSpeedPct(player) + all);
        applyPercent(player, Attributes.MOVEMENT_SPEED, "body_speed",
                DaotuApply.speedPct(player) + all);
        applyFlat(player, Attributes.KNOCKBACK_RESISTANCE, "talent_knockback",
                Math.min(1.0, DaotuApply.knockbackResist(player)));
        applyFlat(player, Attributes.SAFE_FALL_DISTANCE, "talent_fall",
                DaotuApply.safeFallBonus(player));
        applyFlat(player, Attributes.ATTACK_KNOCKBACK, "talent_knockback_power",
                DaotuApply.attackKnockback(player));
    }
```

- [ ] **Step 3: 编译验证**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 20
```
Expected: `javac exit: 0`

---

### Task 4: 抽选引擎 + 历练改单池

**Files:**
- Create: `src\main\java\net\guzhenren\daotu\DaotuDraw.java`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuLiLian.java`

**Interfaces:**
- Consumes: `DaotuRun`, `DaotuDrawTable`, `DaotuRunAttributes`
- Produces:
  - `int DaotuDraw.tryDraw(ServerPlayer)` —— 返回本次抽了几个（0 或 1）
  - `void DaotuDraw.pick(ServerPlayer, int candidateIndex)` —— 重选
  - `List<DaotuDrawTable.Candidate> DaotuDraw.lastCandidates(ServerPlayer)`

- [ ] **Step 1: 写 `DaotuDraw.java`**

```java
package net.guzhenren.daotu;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 抽选主流程。
 *
 * <p>一次抽选 = 消耗 {@link DaotuRun#POOL_THRESHOLD} 点历练 + 生成 5 个候选
 * + <b>随机自动发 1 个</b>（不弹窗打断战斗）+ 记入 {@code run/pending} 供玩家事后重选。
 *
 * <p>每 10 次抽选额外触发一次特殊奖励（见 {@link DaotuSpecial}）。
 */
public final class DaotuDraw {

    /** 已生成的候选暂存（服务端内存，不落盘）。 */
    private static final class Pending {
        List<DaotuDrawTable.Candidate> candidates;
    }

    private DaotuDraw() {
    }

    /**
     * 尝试抽选。池够就抽一次并返回 1，不够返回 0。
     */
    public static int tryDraw(ServerPlayer player) {
        double pool = DaotuRun.pool(player);
        if (pool < DaotuRun.POOL_THRESHOLD) {
            return 0;
        }
        int draws = DaotuRun.draws(player);
        int next = draws + 1;

        // 1. 扣池
        DaotuRun.setPool(player, pool - DaotuRun.POOL_THRESHOLD);

        // 2. 生成候选
        RandomSource rnd = player.getRandom();
        List<DaotuDrawTable.Candidate> cands =
                DaotuDrawTable.candidates(player, next, rnd);
        if (cands.isEmpty()) {
            return 0;
        }

        // 3. 随机自动发一个（不打断战斗）
        int chosen = rnd.nextInt(cands.size());
        DaotuDrawTable.Candidate c = cands.get(chosen);
        DaotuRunAttributes.grant(player, c.key(), c.amount(), c.percent());

        // 4. 记账
        DaotuRun.setDraws(player, next);
        DaotuRun.addPending(player, entryOf(next, cands, chosen));

        // 5. 每 10 次触发特殊奖励
        if (next % 10 == 0) {
            DaotuSpecial.grant(player, next);
        }

        // 6. 刷新
        DaotuAttributes.sync(player);
        DaotuNet.sendState(player);

        // 7. 播报（动作栏，不刷屏）
        player.displayClientMessage(Component.literal(
                "§6[道途] §e第 " + next + " 抽 §7· §f" + c.label()), true);
        return 1;
    }

    /**
     * 重选：把某次抽选换成一个不同的候选。
     *
     * <p>不限次数，但同一次抽选永远只有一个候选生效——先撤销旧的再加新的。
     */
    public static void pick(ServerPlayer player, int drawIndex, int candidateIndex) {
        String entry = findEntry(player, drawIndex);
        if (entry == null) {
            return;
        }
        List<DaotuDrawTable.Candidate> cands = parseEntry(entry);
        if (candidateIndex < 0 || candidateIndex >= cands.size()) {
            return;
        }
        int current = currentChoice(entry);
        if (current == candidateIndex) {
            return; // 已经是这个了
        }
        // 撤销旧的
        DaotuDrawTable.Candidate old = cands.get(current);
        DaotuRunAttributes.grant(player, old.key(), -old.amount(), old.percent());
        // 加新的
        DaotuDrawTable.Candidate now = cands.get(candidateIndex);
        DaotuRunAttributes.grant(player, now.key(), now.amount(), now.percent());
        // 更新记录
        replaceEntry(player, drawIndex, entryOf(drawIndex, cands, candidateIndex));

        DaotuAttributes.sync(player);
        DaotuNet.sendState(player);
        player.displayClientMessage(Component.literal(
                "§6[道途] §e第 " + drawIndex + " 抽 §7改为 §f" + now.label()), false);
    }

    // ---------------------------------------------------------------- 记录编解码

    /** 一条记录：{@code 抽次|已选下标|key:amount:percent,key:amount:percent,...} */
    private static String entryOf(int drawIndex, List<DaotuDrawTable.Candidate> cands, int chosen) {
        StringBuilder sb = new StringBuilder();
        sb.append(drawIndex).append('|').append(chosen).append('|');
        for (int i = 0; i < cands.size(); i++) {
            DaotuDrawTable.Candidate c = cands.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(c.key()).append(':').append(c.amount()).append(':')
                    .append(c.percent() ? '1' : '0');
        }
        return sb.toString();
    }

    private static int currentChoice(String entry) {
        String[] parts = entry.split("\\|", 3);
        return parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    }

    private static List<DaotuDrawTable.Candidate> parseEntry(String entry) {
        String[] parts = entry.split("\\|", 3);
        List<DaotuDrawTable.Candidate> out = new java.util.ArrayList<>();
        if (parts.length < 3 || parts[2].isEmpty()) {
            return out;
        }
        for (String item : parts[2].split(",")) {
            String[] f = item.split(":");
            if (f.length < 3) {
                continue;
            }
            out.add(new DaotuDrawTable.Candidate(f[0], labelOf(f[0]),
                    Double.parseDouble(f[1]), "1".equals(f[2])));
        }
        return out;
    }

    private static String labelOf(String key) {
        return switch (key) {
            case "attack_damage" -> "力量";
            case "max_health" -> "生命";
            case "movement_speed" -> "速度";
            case "attack_speed" -> "攻速";
            case "armor_toughness" -> "护甲韧性";
            case "max_absorption" -> "伤害吸收";
            case "knockback_resistance" -> "击退抗性";
            case "fall_damage_multiplier" -> "摔伤减免";
            case "luck" -> "幸运";
            case "safe_fall_distance" -> "安全摔落";
            case "attack_knockback" -> "攻击击退";
            case "oxygen_bonus" -> "水下呼吸";
            case "step_height" -> "跨越高度";
            case "scale" -> "体型";
            default -> key;
        };
    }

    private static String findEntry(ServerPlayer player, int drawIndex) {
        for (String e : DaotuRun.pending(player)) {
            if (e.startsWith(drawIndex + "|")) {
                return e;
            }
        }
        return null;
    }

    private static void replaceEntry(ServerPlayer player, int drawIndex, String replacement) {
        List<String> list = DaotuRun.pending(player);
        DaotuRun.clearPending(player);
        for (String e : list) {
            if (e.startsWith(drawIndex + "|")) {
                DaotuRun.addPending(player, replacement);
            } else {
                DaotuRun.addPending(player, e);
            }
        }
    }
}
```

- [ ] **Step 2: 把 `DaotuLiLian.award` 改成灌单池**

把 `award` 方法体（第 199–237 行）整个替换为：

```java
    public static void award(ServerPlayer player, double combat, double travel,
                             double cultivate, String reason) {
        double gain = combat + travel + cultivate;
        if (gain <= 0.0) {
            return;
        }
        DaotuRun.addPool(player, gain);

        // 池够就抽，一次 award 可能连抽多次（例如一次性走了很远）
        int n = 0;
        while (DaotuDraw.tryDraw(player) > 0) {
            n++;
            if (n >= 8) {
                break; // 单次 award 最多连抽 8 次，防极端情况卡顿
            }
        }
    }
```

- [ ] **Step 3: 删掉 `absorb`、`claimUpTo`、`applyDaohen`、`applyBuff`、`announce`**

删除 `DaotuLiLian.java` 第 85–107 行（`absorb`）、第 244–257 行（`claimUpTo`）、
第 259–270 行（`applyDaohen`）、第 272–280 行（`applyBuff`）、第 282–末尾（`announce`）。
同时删掉因此不再使用的 import：`java.util.List`、`java.util.ArrayList`、
`DaotuGrowthApply`、`GuzhenrenModVariables`、`PlayerVariables`、`GuzhenrenModMobEffects`。

- [ ] **Step 4: 编译验证**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 30
```
Expected: `javac exit: 0`。此时会报 `DaotuGrowth` 的引用错误——那些在 Task 8 统一清理，
本步只要求 `DaotuLiLian.java` / `DaotuDraw.java` 自身的错误消失。

---

### Task 5: 特殊奖励

**Files:**
- Create: `src\main\java\net\guzhenren\daotu\DaotuSpecial.java`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuBuffs.java`

**Interfaces:**
- Consumes: `DaotuRun.addBuff`, `DaotuRun.addLedger`, `DaotuGrowthApply.addDaohen`
- Produces:
  - `void DaotuSpecial.grant(ServerPlayer, int drawIndex)`
  - `DaotuBuffs.WHITELIST`（`List<String>`，12 项）
  - `void DaotuBuffs.maintain(ServerPlayer)` —— 改为维持 `run/buffs`

- [ ] **Step 1: 写 `DaotuSpecial.java`**

```java
package net.guzhenren.daotu;

import java.util.List;

import net.guzhenren.network.GuzhenrenModVariables;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 每 10 次抽选的特殊奖励。
 *
 * <p><b>只给 buff 与道痕，不给属性</b>（用户确认）。原因：验算表明
 * 特殊若也给 10 倍属性，每 10 次触发一次会让属性总量翻倍，
 * 五转抽 100 次攻击力到 12.7，后期明显超模。
 * 把属性完全留给常规抽选，曲线干净；buff 本身就是很好的大奖。
 *
 * <p>道痕单次 100（已验算：抽 100 次得 1000，约 v1.2.1 单路满阶的四成）。
 */
public final class DaotuSpecial {

    /** 单次特殊给的道痕量（一转）。 */
    private static final double DAOHEN_BASE = 100.0;

    /** 特殊奖励可给的道痕字段（只选这 8 个常用且语义明确的）。 */
    private static final String[] DAOHEN_KEYS = {
        "daohen_lidao", "daohen_lvdao", "daohen_shuidao", "daohen_huodao",
        "daohen_jiandao", "daohen_leidao", "daohen_jindao", "daohen_yandao",
    };

    private DaotuSpecial() {
    }

    /** 发一次特殊奖励。 */
    public static void grant(ServerPlayer player, int drawIndex) {
        RandomSource rnd = player.getRandom();
        double scale = DaotuDrawTable.zhuanScale(DaotuRun.zhuanshu(player));

        // 5 个候选里 3 个 buff + 2 个道痕
        boolean giveBuff = rnd.nextInt(5) < 3;

        if (giveBuff) {
            List<String> wl = DaotuBuffs.WHITELIST;
            String id = wl.get(rnd.nextInt(wl.size()));
            DaotuRun.addBuff(player, id);
            DaotuBuffs.maintain(player);
            player.displayClientMessage(Component.literal(
                    "§6[道途] §d特殊奖励 §7· §f获得蛊虫增益 §b" + id), false);
        } else {
            String key = DAOHEN_KEYS[rnd.nextInt(DAOHEN_KEYS.length)];
            double amount = DAOHEN_BASE * scale;
            var v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
            DaotuGrowthApply.addDaohen(v, key, amount);
            v.markSyncDirty();
            DaotuRun.addLedger(player, key, amount);
            player.displayClientMessage(Component.literal(
                    "§6[道途] §d特殊奖励 §7· §f" + nameOf(key) + " 道痕 §b+"
                            + String.format("%.0f", amount)), false);
        }
    }

    private static String nameOf(String key) {
        return switch (key) {
            case "daohen_lidao" -> "力道";
            case "daohen_lvdao" -> "律道";
            case "daohen_shuidao" -> "水道";
            case "daohen_huodao" -> "火道";
            case "daohen_jiandao" -> "剑道";
            case "daohen_leidao" -> "雷道";
            case "daohen_jindao" -> "金道";
            case "daohen_yandao" -> "炎道";
            default -> key;
        };
    }
}
```

- [ ] **Step 2: 重写 `DaotuBuffs.java`**

整个文件替换为：

```java
package net.guzhenren.daotu;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 特殊奖励 buff 的维持。
 *
 * <p><b>为什么不能只在发放时加一次</b>：本体
 * {@code SiqibuffXiaoGuoChiXuShiMeiKeFaShengProcedure} 在「四气 buff」结束时会
 * 对 {@code LivingEntity} 调 {@code removeAllEffects()}——清空<b>全部</b>效果，
 * 不区分来源。玩家只要身上有四气 buff 到期，道途发的 buff 就会被一起抹掉。
 *
 * <p>对策：每秒检查一次「按 {@code run/buffs} 本该有的效果」，缺了就补、快过期就续。
 *
 * <p><b>白名单只有 12 项，不可凭记忆扩展</b>。本体 348 个 MobEffect 里
 * 171 个标为 BENEFICIAL，但 category 不可靠：{@code lvdaozengfu}（律道增幅，
 * 明显正面）被标 NEUTRAL，{@code weilijianbei}（威力减倍，实际是削弱）也是 NEUTRAL。
 * 因此本名单按语义 + 实际效果双重确认，宁可少不可错。
 */
public final class DaotuBuffs {

    /** 剩余时长低于此值就续（tick）。 */
    private static final int RENEW_BELOW = 200;

    /** 单次发放时长（tick，约 10 分钟）。 */
    private static final int DURATION = 12000;

    /**
     * 正面效果白名单。每一项都已确认存在于本体的
     * {@code effect.guzhenren.*} 语言文件中。
     */
    public static final List<String> WHITELIST = List.of(
            "shuidaozengfu",
            "yudaoguchongweilizengfu",
            "qidaoguchongweilizengfu",
            "jiandaoshanghaizengfu",
            "lvdaozengfu",
            "jindaozengfu",
            "yandaozengfu",
            "leidaozengshang",
            "weilizengjia",
            "daligubuff",
            "tiexue",
            "leishen");

    private DaotuBuffs() {
    }

    /** 按 {@code run/buffs} 维护。由 {@link DaotuRuntime} 每秒调用。 */
    public static void maintain(ServerPlayer player) {
        for (String id : DaotuRun.buffs(player)) {
            apply(player, id);
        }
    }

    private static void apply(ServerPlayer player, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(
                id.contains(":") ? id : DaotuMod.MODID + "_never_" + id);
        // 本体 buff 都在 guzhenren 命名空间下
        ResourceLocation full = id.contains(":")
                ? rl : ResourceLocation.fromNamespaceAndPath("guzhenren", id);
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.getHolder(full).orElse(null);
        if (holder == null) {
            DaotuMod.LOGGER.debug("特殊奖励 buff 不存在，已跳过: {}", id);
            return;
        }
        MobEffectInstance cur = player.getEffect(holder);
        if (cur != null) {
            if (cur.getDuration() == -1) {
                return; // 无限时长，不动
            }
            if (cur.getDuration() > RENEW_BELOW && cur.getAmplifier() >= 0) {
                return; // 还够久
            }
            if (cur.getDuration() > DURATION) {
                return; // 不覆盖更长久的同名效果
            }
        }
        player.addEffect(new MobEffectInstance(holder, DURATION, 0, false, false, true));
    }

    /** 立即补一次（刚抽到 buff 时用，不等下一秒）。 */
    public static void grantNow(ServerPlayer player, String id) {
        apply(player, id);
    }
}
```

- [ ] **Step 3: 编译验证**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 30
```
Expected: 只剩 `DaotuGrowth` 相关错误（Task 8 清理），`DaotuSpecial` / `DaotuBuffs` 自身无错。

---

### Task 6: 转世与道痕回收

**Files:**
- Modify: `src\main\java\net\guzhenren\daotu\DaotuOpening.java:96-107`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuGrowthApply.java`

**Interfaces:**
- Consumes: `DaotuRun.ledger`, `DaotuRun.resetRun`, `DaotuRun.beginRun`
- Produces: `boolean DaotuGrowthApply.subDaohen(PlayerVariables, String, double)`

- [ ] **Step 1: 加 `subDaohen`**

在 `DaotuGrowthApply.java` 的 `addDaohen` 之后加：

```java
    /**
     * 从本体道痕字段上扣减，<b>下限 0</b>。
     *
     * <p>转世时按 {@code run/ledger} 把这局发出去的道痕扣回来。
     * 加下限是因为本体可能已经把该字段归零（例如重置境界），
     * 扣成负数会让玩家的道痕显示成负值。
     *
     * @return false 表示字段名不认识
     */
    public static boolean subDaohen(PlayerVariables v, String key, double amount) {
        if (amount == 0.0) {
            return true;
        }
        return addDaohenClamped(v, key, -Math.abs(amount));
    }

    private static boolean addDaohenClamped(PlayerVariables v, String key, double delta) {
        // 复用 addDaohen 的字段映射，但先算出旧值以便钳制下限
        double before = DaotuApply.daohenOfRaw(v, key);
        double after = Math.max(0.0, before + delta);
        return addDaohen(v, key, after - before);
    }
```

- [ ] **Step 2: 在 `DaotuApply` 里加 `daohenOfRaw(PlayerVariables, String)`**

```java
    /** 从 {@link PlayerVariables} 直接读某个道痕字段（不经 Player）。 */
    public static double daohenOfRaw(PlayerVariables v, String key) {
        return switch (key) {
            case "daohen_lidao" -> v.daohen_lidao;
            case "daohen_lvdao" -> v.daohen_lvdao;
            case "daohen_shuidao" -> v.daohen_shuidao;
            case "daohen_huodao" -> v.daohen_yandao;
            case "daohen_jiandao" -> v.daohen_jiandao;
            case "daohen_leidao" -> v.daohen_leidao;
            case "daohen_jindao" -> v.daohen_jindao;
            case "daohen_yandao" -> v.daohen_yandao;
            default -> 0.0;
        };
    }
```

- [ ] **Step 3: 转世 7 步**

把 `DaotuOpening.onReincarnation` 整个替换为：

```java
    /**
     * 转世重置。顺序不可调换（见规格书 §7.1）：
     * <ol>
     *   <li>读 {@code run/ledger} 快照</li>
     *   <li>按快照扣回道痕（{@code subDaohen}，下限 0）</li>
     *   <li>{@link DaotuData#wipeAll} 删整个 {@code daotu} 树</li>
     *   <li>重建 {@code run}（必须在 3 之后，否则会被一起删掉）</li>
     *   <li>{@code markApplied} 防止重复「仙途初启」</li>
     *   <li>{@code DaotuAttributes.sync} 清掉全部 modifier</li>
     *   <li>播报</li>
     * </ol>
     */
    public static void onReincarnation(ServerPlayer player) {
        try {
            // 1. 读账本
            var ledger = DaotuRun.ledger(player);
            int oldId = DaotuRun.runId(player);

            // 2. 扣回道痕
            if (!ledger.isEmpty()) {
                var v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);
                for (var e : ledger.entrySet()) {
                    if (!DaotuGrowthApply.subDaohen(v, e.getKey(), e.getValue())) {
                        DaotuMod.LOGGER.warn("转世扣道痕：未知字段 {}", e.getKey());
                    }
                }
                v.markSyncDirty();
            }

            // 3. 删整棵树
            DaotuData.wipeAll(player);

            // 4. 重建 run —— 必须在 3 之后
            DaotuRun.beginRun(player, oldId + 1);

            // 5. 标记已开窍结算
            DaotuData.markApplied(player);

            // 6. 清属性
            DaotuAttributes.sync(player);

            // 7. 播报
            player.displayClientMessage(Component.literal(
                    "§6[道途] 寿元耗尽·总结算：本局 " + DaotuRun.draws(player)
                            + " 次抽选的属性已清空，道痕已扣回。下世重新历练。"), false);
        } catch (Throwable t) {
            DaotuMod.LOGGER.warn("daotu onReincarnation 失败（已忽略）", t);
        }
    }
```

- [ ] **Step 4: 编译验证**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 30
```
Expected: 只剩 `DaotuGrowth` 相关错误。

---

### Task 7: 网络层与界面

**Files:**
- Rewrite: `src\main\java\net\guzhenren\daotu\net\DaotuStatePayload.java`
- Create: `src\main\java\net\guzhenren\daotu\net\DaotuPickPayload.java`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuNet.java`
- Modify: `src\main\java\net\guzhenren\daotu\DaotuMod.java`
- Modify: `src\main\java\net\guzhenren\daotu\client\DaotuViewScreen.java`

**Interfaces:**
- Produces:
  - `record DaotuStatePayload(boolean open, double pool, int draws, int runId,
    Map<String,Double> attrs, List<String> pending, List<String> buffs,
    String faction, int tier, int tierMax, boolean probation, int gongxian,
    int[] insight, List<String> nodes, String awakening, boolean openedQiao)`
  - `record DaotuPickPayload(int drawIndex, int candidateIndex)`

- [ ] **Step 1: 重写 `DaotuStatePayload`**

字段与 codec 按上面签名重写；`attrs` 用 `buf.writeCollection(attrs.entrySet(), ...)`
逐对写 `writeUtf` + `writeDouble`，读端用 `LinkedHashMap` 收集。
其余字段沿用现有写法。`ranks` / `lilians` 两个数组**删除**。

- [ ] **Step 2: 新建 `DaotuPickPayload`**

```java
package net.guzhenren.daotu.net;

import net.guzhenren.daotu.DaotuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：把第 {@code drawIndex} 抽重选为第 {@code candidateIndex} 个候选。 */
public record DaotuPickPayload(int drawIndex, int candidateIndex)
        implements CustomPacketPayload {

    public static final Type<DaotuPickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DaotuMod.MODID, "pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DaotuPickPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.drawIndex);
                        buf.writeVarInt(p.candidateIndex);
                    },
                    buf -> new DaotuPickPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DaotuPickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                net.guzhenren.daotu.DaotuDraw.pick(sp, payload.drawIndex, payload.candidateIndex);
            }
        });
    }
}
```

- [ ] **Step 3: `DaotuNet.send` 改成新字段**

```java
    private static void send(ServerPlayer player, boolean open) {
        PlayerVariables v = player.getData(GuzhenrenModVariables.PLAYER_VARIABLES);

        int[] insight = new int[DaotuLiupai.Category.values().length];
        for (DaotuLiupai.Category c : DaotuLiupai.Category.values()) {
            insight[c.ordinal()] = DaotuInsight.of(player, c);
        }

        PacketDistributor.sendToPlayer(player, new DaotuStatePayload(
                open,
                DaotuRun.pool(player),
                DaotuRun.draws(player),
                DaotuRun.runId(player),
                DaotuRun.attrs(player),
                DaotuRun.pending(player),
                DaotuRun.buffs(player),
                DaotuData.faction(player),
                DaotuData.tier(player),
                DaotuData.tierMax(player),
                DaotuData.inProbation(player),
                (int) v.GongXianZhi,
                insight,
                DaotuData.nodes(player),
                DaotuData.awakening(player),
                DaotuOpening.hasOpenedQiao(player)));
    }
```

- [ ] **Step 4: 注册新 payload**

在 `DaotuMod.registerPayloads` 里加：

```java
        registrar.playToServer(DaotuPickPayload.TYPE, DaotuPickPayload.STREAM_CODEC,
                DaotuPickPayload::handle);
```

- [ ] **Step 5: 界面成长页重做**

`DaotuViewScreen.buildRows()` 的 `case GROWTH ->` 块替换为：

```java
            case GROWTH -> {
                rows.add(Row.header("历练池 · " + fmt(pool) + " / " + fmt(DaotuRun.POOL_THRESHOLD)
                        + "（攒满自动抽一次）"));
                rows.add(Row.header("本局抽选 " + draws + " 次 · 第 "
                        + (draws + 1) + " 抽将给 " + (int) (DaotuRun.tierFor(draws + 1).index()) + " 档"
                        + " · 转数 " + (int) zhuanshu + " 倍率 ×"
                        + String.format("%.2f", DaotuDrawTable.zhuanScale(zhuanshu))));
                rows.add(Row.header("下一次特殊奖励：再抽 " + (10 - draws % 10) + " 次"));
                rows.add(Row.header("── 本局属性 ──"));
                if (attrs.isEmpty()) {
                    rows.add(Row.header("（还没有抽过。击杀、游历、修炼攒历练。）"));
                } else {
                    for (var e : attrs.entrySet()) {
                        rows.add(Row.header("  " + labelOf(e.getKey()) + "：" + fmtAttr(e.getKey(), e.getValue())));
                    }
                }
                if (!buffs.isEmpty()) {
                    rows.add(Row.header("── 特殊奖励 buff ──"));
                    for (String b : buffs) {
                        rows.add(Row.header("  " + b));
                    }
                }
                if (!pending.isEmpty()) {
                    rows.add(Row.header("── 可重选（点一行改选）──"));
                    for (String p : pending) {
                        rows.add(Row.pending(p));
                    }
                }
            }
```

`Row` 增加一个 `pending` 类型；`layout()` 里给它分配点击区域，点击时发 `DaotuPickPayload`。
`fmtAttr` 与 `labelOf` 参照 `DaotuDraw.labelOf` 写一份客户端副本（客户端不能引用服务端私有方法）。

- [ ] **Step 6: 编译验证**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 40
```
Expected: 只剩 `DaotuGrowth` 相关错误。

---

### Task 8: 删除 DaotuGrowth 并收尾

**Files:**
- Delete: `src\main\java\net\guzhenren\daotu\DaotuGrowth.java`
- Modify: `DaotuData.java`, `DaotuApply.java`, `DaotuGrowthApply.java`,
  `DaotuLiLian.java`, `DaotuOpening.java`, `DaotuViewScreen.java`, `DaotuStatePayload.java`

- [ ] **Step 1: 找出全部残留引用**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
Select-String -Path 'src\main\java\net\guzhenren\daotu\*.java','src\main\java\net\guzhenren\daotu\**\*.java' -Pattern 'DaotuGrowth[^A]' | ForEach-Object { "$($_.Filename):$($_.LineNumber)  $($_.Line.Trim())" }
```

- [ ] **Step 2: 逐个处理**

- `DaotuData.rankOf/setRankOf/claimedOf/setClaimedOf/ranks/lilians/lilianOf/addLilianOf` → **删除**
- `DaotuApply.growthAttr` → 返回 0.0（保留方法签名，其余调用点不用改）
- `DaotuGrowthApply.mainPath` → 改为返回 `null`；`refreshTitles` 里对 `null` 用固定头衔「问道者」
- `DaotuLiLian.maxedCount` → 删除，`DaotuOpening.onLogin` 里对应的判断改为 `DaotuRun.draws(player) == 0`
- `DaotuStatePayload` 里对 `DaotuGrowth.PATHS` 的引用 → 已删除
- `DaotuViewScreen` 里 `DaotuGrowth` 的引用 → 已删除
- `DaotuOpening.onLogin` 里的 `for (DaotuGrowth.Path p : DaotuGrowth.PATHS) claimUpTo(...)` → 删除整段

- [ ] **Step 3: 删除文件**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
Remove-Item 'src\main\java\net\guzhenren\daotu\DaotuGrowth.java'
```

- [ ] **Step 4: 完整编译**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\javac_check.py 2>&1 | Select-Object -Last 40
```
Expected: `javac exit: 0`，无 error。

- [ ] **Step 5: 版本升到 1.3.0**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\bump_toml.py 1.3.0
```
同时把 `version.properties` 的 `version=1.2.1` 改为 `version=1.3.0`（Python 写，无 BOM）。

- [ ] **Step 6: 打包**

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\pack_jar.py 2>&1
```
Expected: `jar 内 mods.toml: OK`，`OUT = ...\build\libs\daotu-1.3.0.jar`

- [ ] **Step 7: 静态验收**

写 `_crash3\audit_roguelike.py`，检查：
1. `DaotuGrowth.class` 不在 jar 里
2. jar 里有 `DaotuDraw.class`、`DaotuRun.class`、`DaotuSpecial.class`、`DaotuRunAttributes.class`
3. 源码里不出现 `Attributes.ARMOR`（防回归）
4. 源码里不出现 `weilijianbei` / `ya_li_2` / `jie_bi_ya_li`
5. `WHITELIST` 恰好 12 项，且与规格书逐字一致
6. 源码里不出现 `setBaseValue`
7. `POOL_THRESHOLD == 240.0`
8. 档位表 5 档，数值与规格书一致
9. `zhuanScale` 系数 1.4

```powershell
$env:PYTHONIOENCODING='utf-8'
Set-Location 'D:\daima\mcmod\道途附属'
python _crash3\audit_roguelike.py 2>&1 | Out-Null; "audit exit: $LASTEXITCODE"
```
Expected: `audit exit: 0`

---

## 自审

**规格覆盖：**
- §3 单池 + T=240 → Task 1 (`POOL_THRESHOLD`)、Task 4（扣池）
- §3.3 历练来源 → 沿用现有 `onKill/onHurt/onSecond`，只在 `award` 里改灌单池（Task 4 Step 2）
- §4 属性池 14 项 → Task 3 `attributeOf`
- §4.2 防御/抗性映射 → Task 3（`max_absorption` + `armor_toughness`；`knockback_resistance` + `fall_damage_multiplier`）
- §5.1 五选一 → Task 2 `OPTIONS = 5`
- §5.2 档位表 → Task 2 `TIERS`
- §5.3 转数缩放 → Task 2 `zhuanScale`
- §5.4 发放方式（自动发 + 可重选）→ Task 4 + Task 7 `DaotuPickPayload`
- §5.5 每 10 次额外 → Task 4 Step 1 第 5 步
- §5.6 特殊池只给 buff/道痕 → Task 5
- §5.7 buff 白名单 12 项 → Task 5 `WHITELIST`
- §5.8 buff 维持 → Task 5 `maintain`
- §6 道痕账本与回收 → Task 1 `ledger`、Task 5 `addLedger`、Task 6 `subDaohen`
- §7.1 转世 7 步 → Task 6 Step 3
- §7.2 属性清除 → Task 6（`wipeAll` + `sync`）
- §8 UI → Task 7 Step 5

**无占位符检查：** 所有代码步骤都给了完整可编译代码。Task 7 Step 1 与 Step 5 是唯一
两处「按上面签名重写」的指令，因为它们是纯样板改造（字段增删 + codec 对称），
已在 Interfaces 块给出完整字段列表与 codec 要求。

**类型一致性：** `DaotuDrawTable.Candidate(key, label, amount, percent)` 在 Task 2 定义，
Task 4 与 Task 7 使用同一签名；`DaotuRun.addLedger(String,double)` 在 Task 1 定义，
Task 5 使用；`DaotuGrowthApply.subDaohen` 在 Task 6 定义并使用。
