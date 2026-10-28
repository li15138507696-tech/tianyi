# tianfu 天赋池 · 全条目结构化清单（供跨 mod 复用）

> **抽取方式**：只读源码，未修改任何 `.java`，未编译、未跑 Gradle。
> **抽取对象**：`D:\daima\mcmod\蛊真人附属\src\main\java\net\guzhenren\tianfu\`
> **抽取日期**：2026-10-22
> **核对口径**：所有 id / 名字 / 品质 / 成本 / tizhi / 描述均逐条从源码原文抄录；条数用脚本正则复核（`t("` 111 次、`body(` 20 次、`entry(` 22 次、`sijue(` 10 次 + 2 条 `new Spec`），非估算。

---

## 0. 数据模型总览

`TianfuPool` 里只有**一个**数据载体 `Spec`，四类条目共用它，靠 `Kind` 区分：

```java
public enum Kind { BODY, ENTRY, SIJUE, JIYUAN }

/**
 * @param tizhi 体质类写入本体的 tizhi 值（凡体 17–53、十绝体 1–10、杀神 54）；词条 / 机缘为 0
 */
public record Spec(String id, String name, Kind kind, String quality, int cost, int tizhi, String desc) {
}
```

四类条目的构造入口（决定了 quality / cost / tizhi 的固定取值）：

```java
private static Spec body(String id, String name, int tizhi, String desc) {
    return new Spec(id, name, Kind.BODY, "凡体", BODY_COST, tizhi, desc);
}

private static Spec entry(String id, String name, String quality, int cost, String desc) {
    return new Spec(id, name, Kind.ENTRY, quality, cost, 0, desc);
}

private static Spec sijue(int n, String name, String desc) {
    return new Spec("sijue_" + n, name, Kind.SIJUE, "十绝体", SIJUE_COST, n, desc);
}
```

- `BODY_COST = 3`、`SIJUE_COST = 16`、`GOD_QUALITY = "神"`、`GOD_COST = 12`。
- 机缘四条不走 `sijue()`/`body()`，是 4 个手写 `new Spec(...)`，quality 固定 `"机缘"`。
- 杀神也是手写 `new Spec("shashen", "杀神", Kind.SIJUE, "十绝体", SIJUE_COST, 54, ...)`，**quality 记为「十绝体」而不是「杀神」**。

### ⚠ 条数更正：ENTRY 是 133 条，不是注释里的 129

`TianfuPool` 类注释写「129 条词条（原有 22 + 扩展 107）」，但实际代码是：

```java
out.addAll(List.of( /* 22 条内置 */ ));
out.addAll(TianfuTalents.specs());   // ← 实际 111 条
```

`TianfuTalents.ALL` 实际有 **111** 条（`t("` 出现 111 次，逐行核对无遗漏、无重复 id），因此：

**ENTRY = 22 + 111 = 133 条**，全池 **159 条**。类注释里的 107/129 是过期数字，本清单以代码为准。

`TianfuTalents.specs()` 还会做一次「附属是否安装」过滤（`available()`），因此**运行时池子里的条数是动态的**：装了 `guzhenren_addon` / `hundao_addon` / `yundao` 才是 133，否则会少 4 条（`bingxue_daoti`、`hundao_ronghe`、`qiyun_yizhi`、`shen_liupai_jianji`）。本清单列出全部 111 条（含需附属的 4 条）。

---

## 1. 条目总表

### 1.1 BODY（凡体）· 20 条

`quality` 全部为 `凡体`，`cost` 全部为 `3`。

| # | id | name | quality | cost | tizhi | desc |
|---|----|------|---------|------|-------|------|
| 1 | `tudao` | 九命不灭体 | 凡体 | 3 | 17 | 濒死（血量 <10%）闪避率 +25%；土道道痕 +1000 |
| 2 | `bingdao` | 百战金戈体 | 凡体 | 3 | 18 | 兵道道痕 +1000；兵刃伤害由本体流水线自动体现 |
| 3 | `bingxuedao` | 寒渊素雪体 | 凡体 | 3 | 19 | 冰雪道道痕 +1000；冰系攻防由本体流水线自动体现 |
| 4 | `fengdao` | 长风破浪体 | 凡体 | 3 | 21 | 风道道痕 +1000；移速+8%，风系伤害由本体流水线自动体现 |
| 5 | `gudao` | 白玉铁骨体 | 凡体 | 3 | 22 | 骨道道痕 +1000；附加非护甲减伤（每千 10%，上限 50%） |
| 6 | `hundao` | 幽魂凝识体 | 凡体 | 3 | 24 | 魂魄上限 +25%、承载极限 +25% |
| 7 | `jiandao` | 断岳剑心体 | 凡体 | 3 | 25 | 剑道道痕 +1000；自带「剑道伤害增幅」常驻 |
| 8 | `jindao` | 百炼庚金体 | 凡体 | 3 | 26 | 金道道痕 +1000；自带「镀金」常驻 |
| 9 | `lidao` | 搬山撼岳体 | 凡体 | 3 | 28 | 力道道痕 +1000；力道属性（原版怪物）+12%；精力+15% |
| 10 | `mudao` | 苍木回春体 | 凡体 | 3 | 30 | 生命上限 +10% |
| 11 | `qidao` | 太息养元体 | 凡体 | 3 | 31 | 真元回复 +15%（不破 999 上限）；自带「气道蛊虫威力增幅」常驻 |
| 12 | `xiuluo` | 修罗战体 | 凡体 | 3 | 33 | 每损 10% 血出伤 +5%（最多 +25%）；血道道痕 +1000 |
| 13 | `xudao` | 太虚化形体 | 凡体 | 3 | 37 | 受击 15% 概率虚化免伤（内置 5 秒 CD） |
| 14 | `yingdao` | 如影随形体 | 凡体 | 3 | 39 | 移速 +8%、闪避 +5% |
| 15 | `yuedao` | 明月入怀体 | 凡体 | 3 | 40 | 夜间真元回复 +80% |
| 16 | `zhidao` | 慧心通明体 | 凡体 | 3 | 42 | 念头之道 +25%、念头容量 +25% |
| 17 | `zhoudao` | 光阴逆转体 | 凡体 | 3 | 43 | 宙道时间 +50%、CD −12.5% |
| 18 | `feixingdao` | 御空踏云体 | 凡体 | 3 | 45 | 直接获得飞行能力（常驻） |
| 19 | `mengdao` | 南柯一梦体 | 凡体 | 3 | 48 | 念头回复 +30% |
| 20 | `xueshi` | 血煞蜕变体 | 凡体 | 3 | 53 | 攻击吸血 5%；血量低于 30% 时攻击 +12% |

**BODY 合计：20 条**（tizhi 取值 17/18/19/21/22/24/25/26/28/30/31/33/37/39/40/42/43/45/48/53，无 20/23/27/29/32… 属编号空洞，源码注释明确「编号保留空洞不重排」）。

### 1.2 ENTRY（词条）· 133 条

#### 1.2.1 内置 22 条（直接写在 `TianfuPool.buildEntries()` 里）

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `wanfa_jietong` | 万法皆通 | 极 | 16 | 所有流派熟练度获取速度 ×3（各流派权重增长 ×3） |
| 2 | `shen_tianji_suan` | 神·天机神算 | 神 | 12 | 暴击率 +50%、暴击伤害 +240%、闪避 +24% |
| 3 | `shen_bumie_jinshen` | 神·不灭金身 | 神 | 12 | 生命上限 +100%、受击减伤 +20%、每秒回复 5% 生命 |
| 4 | `shen_tuntian_shidi` | 神·吞天噬地 | 神 | 12 | 吸血 +12%、反伤 +15%、气运上限 +50；击杀怪物累积道痕 |
| 5 | `suhui` | 宿慧 | 天 | 5 | 每转数提供额外修炼基数与道痕获取 |
| 6 | `shazhao_tiancheng` | 杀招天成 | 地 | 3 | 杀招成功率 +5%、10 槽磨损 −1 |
| 7 | `shiyuan_zhixing` | 噬元之性 | 地 | 3 | 击杀灵体回复真元/念头 |
| 8 | `fuze_mianchang` | 福泽绵长 | 地 | 3 | 气运上限 +15（上限越高，本体气运回复空间越大） |
| 9 | `zhenyuan_ninglian` | 真元凝练 | 玄 | 2 | 真元消耗 −10% |
| 10 | `niantou_ninglian` | 念头凝练 | 玄 | 2 | 念头消耗 −10% |
| 11 | `xinshen_ruyuan` | 心神如渊 | 玄 | 2 | 沉默时长减半 |
| 12 | `kuxiu_buchuo` | 苦修不辍 | 玄 | 2 | 修炼速度 +20% |
| 13 | `houji_bofa` | 厚积薄发 | 玄 | 2 | 突破瓶颈 −20% |
| 14 | `suiyue_chendian` | 岁月沉淀 | 玄 | 2 | 已耗寿元越多、修炼越快 |
| 15 | `youdou_gaoshou` | 游斗高手 | 玄 | 2 | 移动中受击 −6% |
| 16 | `jianbi_qingfeng` | 剑指偏锋 | 玄 | 2 | 暴击伤害 +30%（需配合暴击精通） |
| 17 | `shouyuan_mianchang` | 寿元绵长 | 黄 | 1 | 寿元 +50 |
| 18 | `qiyun_weiguang` | 气运微光 | 黄 | 1 | 气运上限 +5 |
| 19 | `jingu_qiangjian` | 筋骨强健 | 黄 | 1 | 生命上限 +10% |
| 20 | `baoji_jingtong` | 暴击精通 | 黄 | 1 | 暴击率 +5%、暴击伤害 +20%（叠加） |
| 21 | `zhanyi_angran` | 战意盎然 | 黄 | 1 | 满血时出伤 +8% |
| 22 | `youshou_nengkai` | 右守能铠 | 黄 | 1 | 受击 4% 概率反弹 10% 伤害 |

#### 1.2.2 扩展 111 条（`TianfuTalents.ALL`，全部 `tizhi = 0`）

**黄（1 点）· 25 条 = 20 条流派亲和 + 5 条入门**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `jiandao_qinhe` | 剑道亲和 | 黄 | 1 | 剑道道痕 +200 |
| 2 | `leidao_qinhe` | 雷道亲和 | 黄 | 1 | 雷道道痕 +200 |
| 3 | `yandao_qinhe` | 炎道亲和 | 黄 | 1 | 炎道道痕 +200 |
| 4 | `bingxuedao_qinhe` | 冰雪道亲和 | 黄 | 1 | 冰雪道道痕 +200 |
| 5 | `fengdao_qinhe` | 风道亲和 | 黄 | 1 | 风道道痕 +200 |
| 6 | `yingdao_qinhe` | 影道亲和 | 黄 | 1 | 影道道痕 +200 |
| 7 | `yuedao_qinhe` | 月道亲和 | 黄 | 1 | 月道道痕 +200 |
| 8 | `shuidao_qinhe` | 水道亲和 | 黄 | 1 | 水道道痕 +200 |
| 9 | `tudao_qinhe` | 土道亲和 | 黄 | 1 | 土道道痕 +200 |
| 10 | `mudao_qinhe` | 木道亲和 | 黄 | 1 | 木道道痕 +200 |
| 11 | `jindao_qinhe` | 金道亲和 | 黄 | 1 | 金道道痕 +200 |
| 12 | `gudao_qinhe` | 骨道亲和 | 黄 | 1 | 骨道道痕 +200 |
| 13 | `lidao_qinhe` | 力道亲和 | 黄 | 1 | 力道道痕 +200 |
| 14 | `hundao_qinhe` | 魂道亲和 | 黄 | 1 | 魂道道痕 +200 |
| 15 | `xuedao_qinhe` | 血道亲和 | 黄 | 1 | 血道道痕 +200 |
| 16 | `xudao_qinhe` | 虚道亲和 | 黄 | 1 | 虚道道痕 +200 |
| 17 | `zhidao_qinhe` | 智道亲和 | 黄 | 1 | 智道道痕 +200 |
| 18 | `zhoudao_qinhe` | 宙道亲和 | 黄 | 1 | 宙道道痕 +200 |
| 19 | `mengdao_qinhe` | 梦道亲和 | 黄 | 1 | 梦道道痕 +200 |
| 20 | `dudao_qinhe` | 毒道亲和 | 黄 | 1 | 毒道道痕 +200 |
| 21 | `tiepi_rougu` | 铁皮肉骨 | 黄 | 1 | 非护甲减伤 +4% |
| 22 | `bufen_zhiwu` | 不焚之物 | 黄 | 1 | 常驻防火 |
| 23 | `shenfa_qingjie` | 身法轻捷 | 黄 | 1 | 移动速度 +5% |
| 24 | `zhaoqi_yinlu` | 朝气饮露 | 黄 | 1 | 每 5 秒回复 1% 生命 |
| 25 | `niantou_zisheng` | 念头自生 | 黄 | 1 | 念头回复 +20% |

> 注意第 17/18 条的字段前缀：`zhidao_qinhe` 写 `dahen_zhidao`、`zhoudao_qinhe` 写 `dahen_zhoudao`（不是 `daohen_`）。本体确实混用两套前缀。

**玄（2 点）· 25 条 = 10 条流派亲和 + 15 条进阶**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `bingdao_qinhe` | 兵道亲和 | 玄 | 2 | 兵道道痕 +500 |
| 2 | `daodao_qinhe` | 刀道亲和 | 玄 | 2 | 刀道道痕 +500 |
| 3 | `guangdao_qinhe` | 光道亲和 | 玄 | 2 | 光道道痕 +500 |
| 4 | `xingdao_qinhe` | 星道亲和 | 玄 | 2 | 星道道痕 +500 |
| 5 | `dandao_qinhe` | 丹道亲和 | 玄 | 2 | 丹道道痕 +500 |
| 6 | `liandao_qinhe` | 炼道亲和 | 玄 | 2 | 炼道道痕 +500 |
| 7 | `yindao_qinhe` | 音道亲和 | 玄 | 2 | 音道道痕 +500 |
| 8 | `huandao_qinhe` | 幻道亲和 | 玄 | 2 | 幻道道痕 +500 |
| 9 | `bianhuadao_qinhe` | 变化道亲和 | 玄 | 2 | 变化道道痕 +500 |
| 10 | `zhendao_qinhe` | 阵道亲和 | 玄 | 2 | 阵道道痕 +500 |
| 11 | `wuying_bi` | 无影壁 | 玄 | 2 | 受击 6% 概率反弹 12% 伤害 |
| 12 | `ruwei_dingshen` | 入微定神 | 玄 | 2 | 非护甲减伤 +8% |
| 13 | `shanbu_suifeng` | 闪步随风 | 玄 | 2 | 闪避率 +6% |
| 14 | `xueqi_rulin` | 血气如林 | 玄 | 2 | 生命上限 +20% |
| 15 | `lijin_ruquan` | 力进如泉 | 玄 | 2 | 攻击伤害 +8% |
| 16 | `quanru_jifeng` | 拳如疾风 | 玄 | 2 | 攻击速度 +10% |
| 17 | `songyue_jianli` | 耸岳坚立 | 玄 | 2 | 击退抗性 +0.4 |
| 18 | `jingli_shengheng` | 精力生生 | 玄 | 2 | 精力上限 +30% |
| 19 | `chuntian_yishou` | 春田益寿 | 玄 | 2 | 寿元 +80 |
| 20 | `qiyun_weiying` | 气运微荫 | 玄 | 2 | 气运上限 +12 |
| 21 | `qiyun_yingmen` | 气运盈门 | 玄 | 2 | 气运上限 +18 |
| 22 | `hunpo_lianxi` | 魂魄炼息 | 玄 | 2 | 魂魄上限 +15% |
| 23 | `hunzai_kuanhou` | 魂载宽厚 | 玄 | 2 | 魂魄承载极限 +15% |
| 24 | `niantou_kuozhan` | 念头扩展 | 玄 | 2 | 念头容量 +15% |
| 25 | `zhenyuan_huiliu` | 真元回流 | 玄 | 2 | 真元回复 +35% |

> 第 4/6/10 条写 `dahen_xingdao` / `dahen_liandao` / `dahen_zhendao`（`dahen_` 前缀）。

**地（3 点）· 21 条**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `tupo_tongtian` | 突破通天 | 地 | 3 | 突破瓶颈 −30% |
| 2 | `dazuo_wangwo` | 打坐忘我 | 地 | 3 | 打坐时修炼进度 +40% |
| 3 | `liangu_rushen` | 炼蛊如神 | 地 | 3 | 炼蛊进度 +40% |
| 4 | `gudao_dup` | 蛊道独步 | 地 | 3 | 蛊冷却缩短（每 tick −0.25） |
| 5 | `shazhao_rufeng` | 杀招如风 | 地 | 3 | 杀招冷却缩短（每 tick −0.25） |
| 6 | `zhenyuan_ruquan` | 真元如泉 | 地 | 3 | 真元回复 +80% |
| 7 | `niantou_ruyuan` | 念头如渊 | 地 | 3 | 念头回复 +60% |
| 8 | `xieshi_ziyu` | 血食自愈 | 地 | 3 | 每 5 秒回复 3% 生命 |
| 9 | `shenfa_ruyan` | 身法如燕 | 地 | 3 | 闪避率 +12% |
| 10 | `baoji_rulie` | 暴击如裂 | 地 | 3 | 暴击率 +15% |
| 11 | `baoshang_rufeng` | 暴伤如锋 | 地 | 3 | 暴击伤害 +80% |
| 12 | `xixue_rumo` | 吸血入魔 | 地 | 3 | 攻击吸血 +6% |
| 13 | `fanshang_ruyu` | 反伤如玉 | 地 | 3 | 受击 10% 概率反弹 20% 伤害 |
| 14 | `jianjia_ruyan` | 坚甲如岩 | 地 | 3 | 非护甲减伤 +15% |
| 15 | `quansheng_ruhong` | 全盛如洪 | 地 | 3 | 满血时出伤 +18% |
| 16 | `canshen_rulang` | 残身如狼 | 地 | 3 | 残血（<30%）时出伤 +25% |
| 17 | `wanjun_lili` | 万钧之力 | 地 | 3 | 常驻力量 II |
| 18 | `shengji_rumu` | 生息如沐 | 地 | 3 | 常驻生命恢复 II |
| 19 | `jiandao_dacheng` | 剑道大成 | 地 | 3 | 剑道道痕 +800 |
| 20 | `leidao_dacheng` | 雷道大成 | 地 | 3 | 雷道道痕 +800 |
| 21 | `lidao_dacheng` | 力道大成 | 地 | 3 | 力道道痕 +800 |

**天（5 点）· 19 条 = 16 条本体 + 3 条附属联动**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `wuxing_daocang` | 五行道藏 | 天 | 5 | 全流派道痕各 +300 |
| 2 | `qiqiao_linglong` | 七窍玲珑 | 天 | 5 | 念头容量 +50%、念头之道 +50% |
| 3 | `hunpo_rugui` | 魂魄如鬼 | 天 | 5 | 魂魄上限 +50%、承载极限 +50% |
| 4 | `jingli_ruhai` | 精力如海 | 天 | 5 | 精力上限 +80% |
| 5 | `shouyuan_ruhe` | 寿元如鹤 | 天 | 5 | 寿元 +200 |
| 6 | `qiyun_ruhong` | 气运如虹 | 天 | 5 | 气运上限 +60 |
| 7 | `zhenyuan_bujie` | 真元不竭 | 天 | 5 | 真元回复 +150% |
| 8 | `niantou_bujie` | 念头不竭 | 天 | 5 | 念头回复 +120% |
| 9 | `huixue_rusheng` | 回血如生 | 天 | 5 | 每 5 秒回复 5% 生命 |
| 10 | `baoji_rushen` | 暴击如神 | 天 | 5 | 暴击率 +25%、暴击伤害 +120% |
| 11 | `xixue_rushi` | 吸血如饕 | 天 | 5 | 攻击吸血 +10% |
| 12 | `fanshang_rushan` | 反伤如山 | 天 | 5 | 受击 20% 概率反弹 35% 伤害 |
| 13 | `jianjia_rushen` | 坚甲如神 | 天 | 5 | 非护甲减伤 +25% |
| 14 | `xiulian_rushen` | 修炼如神 | 天 | 5 | 修炼速度 +60%、突破瓶颈 −40% |
| 15 | `liangu_bujie` | 炼蛊不竭 | 天 | 5 | 炼蛊进度 +60%、蛊冷却缩短（每 tick −0.3） |
| 16 | `jixing_ruying` | 疾行如影 | 天 | 5 | 常驻迅捷 III |
| 17 | `bingxue_daoti` | 冰雪道体 | 天 | 5 | 常驻「冰雪道增幅」（兼修附属） |
| 18 | `hundao_ronghe` | 魂道融彻 | 天 | 5 | 常驻「魂道增幅」（魂道附属） |
| 19 | `qiyun_yizhi` | 一掷乾坤 | 天 | 5 | 常驻「一掷乾坤」、气运上限 +40 |

> 17/18/19 三条**只在对应附属安装时才进池**（`TianfuTalents.available()` → `ModList.isLoaded(ns)`）。

**神（12 点）· 9 条 = 8 条本体 + 1 条附属联动**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `shen_qiyun_zhizi` | 神·气运之子 | 神 | 12 | 气运上限 +140 |
| 2 | `shen_hunxi_zhuzai` | 神·魂兮主宰 | 神 | 12 | 魂魄上限 +100%、承载极限 +100%、念头容量 +100% |
| 3 | `shen_busi_bumie` | 神·不死不灭 | 神 | 12 | 每 5 秒回复 12% 生命、非护甲减伤 +30% |
| 4 | `shen_zhanshen_linshi` | 神·战神临世 | 神 | 12 | 攻击伤害 +60%、攻击速度 +25% |
| 5 | `shen_jingang_buhuai` | 神·金刚不坏 | 神 | 12 | 击退抗性 +1、非护甲减伤 +35% |
| 6 | `shen_xiulian_wuji` | 神·修炼无极 | 神 | 12 | 修炼速度 +150%、突破瓶颈 −60% |
| 7 | `shen_liangu_tianshu` | 神·炼蛊天术 | 神 | 12 | 炼蛊进度 +90%、蛊冷却缩短（每 tick −0.5） |
| 8 | `shen_wandao_guizong` | 神·万道归宗 | 神 | 12 | 全流派道痕各 +1500 |
| 9 | `shen_liupai_jianji` | 神·流派兼济 | 神 | 12 | 常驻「冰雪道增幅」+「魂道增幅」+「一掷乾坤」，三者可叠加 |

**极（16 点）· 2 条**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `ji_wanshi_bumie` | 极·万世不灭 | 极 | 16 | 生命上限 +150%、每 5 秒回复 40% 生命、非护甲减伤 +40% |
| 2 | `ji_zhongdao_zhizun` | 极·众道至尊 | 极 | 16 | 全流派道痕各 +3000、修炼速度 +200%、突破瓶颈 −80% |

**负（−2 点，购买时**返还** 2 点）· 10 条**

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `zhenyuan_kujie` | 真元枯竭 | 负 | −2 | 真元回复 −30% |
| 2 | `niantou_ganhe` | 念头干涸 | 负 | −2 | 念头回复 −30% |
| 3 | `routi_chanruo` | 肉体孱弱 | 负 | −2 | 生命上限 −20% |
| 4 | `quanshi_piruan` | 拳势疲软 | 负 | −2 | 攻击伤害 −10% |
| 5 | `shenfa_chidun` | 身法迟钝 | 负 | −2 | 移动速度 −8% |
| 6 | `shouyuan_duancu` | 寿元短促 | 负 | −2 | 寿元 −100 |
| 7 | `qiyun_xibo` | 气运稀薄 | 负 | −2 | 气运上限 −15 |
| 8 | `hunli_shuairuo` | 魂力衰弱 | 负 | −2 | 魂魄上限 −25%、承载极限 −25% |
| 9 | `nianfu_xiayai` | 念府狭隘 | 负 | −2 | 念头容量 −25% |
| 10 | `xiuxing_chizhi` | 修行迟滞 | 负 | −2 | 修炼速度 −30%、突破瓶颈 +40% |

### 1.3 SIJUE（十绝体 + 杀神）· 11 条

`quality` 全部为 `十绝体`，`cost` 全部为 `16`；id 形如 `sijue_1` … `sijue_10`（由 `sijue(n, …)` 拼出），杀神 id 为 `shashen`。

| # | id | name | quality | cost | tizhi | desc |
|---|----|------|---------|------|-------|------|
| 1 | `sijue_1` | 太日阳莽体 | 十绝体 | 16 | 1 | 余道道痕 2000；本体十绝体效果原生生效 |
| 2 | `sijue_2` | 古月阴荒体 | 十绝体 | 16 | 2 | 周道道痕 2000；宙道时间 +300%、CD −25% |
| 3 | `sijue_3` | 北冥冰魄体 | 十绝体 | 16 | 3 | 魂魄上限 500、承载 50000；随机魂道/冰雪道道痕 2000 |
| 4 | `sijue_4` | 森海轮回体 | 十绝体 | 16 | 4 | 木道道痕 2000；寿蟽数值 +20 |
| 5 | `sijue_5` | 炎煌雷泽体 | 十绝体 | 16 | 5 | 随机炎道/雷道道痕 2000；自带对应流派增幅常驻 |
| 6 | `sijue_6` | 万金妙华体 | 十绝体 | 16 | 6 | 金道道痕 2000；自带「镀金」常驻 |
| 7 | `sijue_7` | 大力真武体 | 十绝体 | 16 | 7 | 力道道痕 2000；精力上限 200、骨骼力 +20 |
| 8 | `sijue_8` | 逍遥智心体 | 十绝体 | 16 | 8 | 智道道痕 2000；念头之道 2000、念头容量 20000 |
| 9 | `sijue_9` | 厚土元央体 | 十绝体 | 16 | 9 | 土道道痕 2000 |
| 10 | `sijue_10` | 宇宙大衍体 | 十绝体 | 16 | 10 | 律道道痕 2000；自带「律道增幅」常驻 |
| 11 | `shashen` | 杀神 | 十绝体 | 16 | 54 | 杀戮值累积伤害成长（封顶 5 倍伤害）+ 杀怪噬道（击杀积道痕）；不给开局道痕，愈杀愈强 |

> 原文错字照抄：`sijue_4` 描述里的「寿蟽」为源码原样（疑为「寿元」之误）；`sijue_2` 写「周道」而代码写的是 `dahen_zhoudao`（宙道）。

### 1.4 JIYUAN（机缘）· 4 条

`quality` 全部为 `机缘`，`tizhi` 全部为 `0`。成本按档位 5 / 8 / 12 / 16。

| # | id | name | quality | cost | desc |
|---|----|------|---------|------|------|
| 1 | `jiyuan_ding` | 丁等机缘 | 机缘 | 5 | 真元上限 200-390、气运上限 1-10、90% 命格 −1（否则天资流畅） |
| 2 | `jiyuan_bing` | 丙等机缘 | 机缘 | 8 | 真元上限 400-590、气运上限 1-3、70% 命格 −1（否则天资流畅） |
| 3 | `jiyuan_yi` | 乙等机缘 | 机缘 | 12 | 真元上限 600-790、气运上限 10-20、50% 命格 −1（否则天资流畅） |
| 4 | `jiyuan_jia` | 甲等机缘 | 机缘 | 16 | 真元上限 800-990、气运 1-10、1/3 命格 −2 且气运上限 35-45（否则天资流畅） |

> 四条**互斥**（`TianfuApply.buy` 里做「机缘唯一」门禁），且 `applyJiyuan` 是**覆写**而非叠加，故不可多选。

### 1.5 条数统计与核对

| 类别 | 条数 | 说明 |
|------|------|------|
| BODY | **20** | 全部凡体，cost 3 |
| ENTRY | **133** | 22 内置 + 111 扩展（原注释「129」过期） |
| SIJUE | **11** | 十绝体 10 + 杀神 1 |
| JIYUAN | **4** | 甲乙丙丁 |
| **合计** | **159** | 全部 id 唯一，无跨类重复（脚本校验：48 个 Pool 内 id + 111 个 Talents id，交集为 0） |

**ENTRY 品质分布（133 条）**

| 品质 | 内置 | 扩展 | 合计 | 单价 |
|------|------|------|------|------|
| 极 | 1 | 2 | 3 | 16 |
| 神 | 3 | 9 | 12 | 12 |
| 天 | 1 | 19 | 20 | 5 |
| 地 | 3 | 21 | 24 | 3 |
| 玄 | 8 | 25 | 33 | 2 |
| 黄 | 6 | 25 | 31 | 1 |
| 负 | 0 | 10 | 10 | −2 |
| **合计** | **22** | **111** | **133** | — |

**成本合计（用于平衡参照）**

- BODY：20 × 3 = **60**
- ENTRY 正成本：3×16 + 12×12 + 20×5 + 24×3 + 33×2 + 31×1 = 48 + 144 + 100 + 72 + 66 + 31 = **461**；负分项返还 10 × 2 = 20，故净 **441**
- SIJUE：11 × 16 = **176**
- JIYUAN：5 + 8 + 12 + 16 = **41**
- 全池毛成本 **738**，净成本（扣负分返还）**718**

**开局自由点**（`TianfuDifficulty.OPTIONS`，选定后不可改）：

| id | name | points | desc |
|----|------|--------|------|
| `easy` | 顺天应命 | 24 | 得天独厚：自由点 24，兑换十绝体后尚有余裕 |
| `normal` | 凡尘问道 | 16 | 脚踏实地：自由点 16，可兑甲等机缘 |
| `hard` | 逆天争命 | 12 | 逆天而行：自由点 12，可兑乙等机缘或神级词条 |
| `hell` | 万劫磨道 | 8 | 九死一生：自由点 8，可兑丙等机缘 |

---

## 2. 效果落地方式分类

源码里的落地通道共有 **6 条**（题目给的 A–E 只覆盖了前 5 条，第 6 条「运行时伤害乘区」不属于任何一类，单列 F）。另有两条辅助通道（记分板、原版能力位）单列 G/H，避免遗漏。

### 2.0 一张表看懂通道

| 通道 | 实现位置 | 是否持久化 | 幂等/回滚方式 |
|------|----------|------------|----------------|
| A 属性修正 | `TianfuAttributes.sync()` → `AttributeModifier`（transient） | 否（每次登录/每 100 tick 重建） | 幂等重建；amount=0 时移除 modifier |
| B 本体字段 | `TianfuEffects.applyOnce` / `TianfuTalents.applyOnce`（反射写 `PlayerVariables`） | 是（写进本体 NBT） | `revokeOnce` 精确反向（乘→除、加→减） |
| C 药水效果 | `TianfuLiupai.maintain` / `TianfuTalents.maintainEffects` | 是（效果实例） | 只删「无限时长且等级等于记录值」的那份 |
| D 道痕 | `TianfuDaohen.write` / `TianfuTalents.addPaired` / `TianfuSlay.addDevourDaohen` | 是 | 成对反向减 |
| E 纯描述 | 无代码 | — | — |
| F 战斗乘区 | `TianfuCombat.onIncomingDamage`（`LivingIncomingDamageEvent`） | 否（每次命中现算） | 无状态，天然幂等 |
| G 记分板 | `TianfuNative.setScore/addScore` | 是（Scoreboard） | 无回滚（SIJUE 不可撤销） |
| H 原版能力位 | `TianfuEffects.updateFlight`（`Abilities.mayfly`） | 否（登录重建） | 按 `fly_granted` 标记回收 |

### 2.1 A 类：纯属性修正（`AttributeModifier`）

统一入口 `TianfuAttributes.sync(Player)`，全部用 **transient** modifier（不写 NBT，靠每 100 tick / 登录重建来避免重复叠加）：

```java
apply(player, Attributes.MAX_HEALTH,         HEALTH_MULT,        health - 1.0,        ADD_MULTIPLIED_TOTAL);
apply(player, Attributes.ATTACK_DAMAGE,      ATTACK_MULT,        attack - 1.0,        ADD_MULTIPLIED_TOTAL);
apply(player, Attributes.MOVEMENT_SPEED,     SPEED_MULT,         speed - 1.0,         ADD_MULTIPLIED_TOTAL);
apply(player, Attributes.ATTACK_SPEED,       ATTACK_SPEED_MULT,  attackSpeedMult-1.0, ADD_MULTIPLIED_TOTAL);
apply(player, Attributes.JUMP_STRENGTH,      JUMP_FLAT,          jumpFlat,            ADD_VALUE);
apply(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_FLAT,   knockbackFlat,       ADD_VALUE);
apply(player, Attributes.SAFE_FALL_DISTANCE, FALL_FLAT,          fallFlat,            ADD_VALUE);
```

涉及的 vanilla Attribute（**7 个**）：

| Attribute | 操作 | 贡献来源（条目 id） |
|-----------|------|----------------------|
| `MAX_HEALTH` | ADD_MULTIPLIED_TOTAL | `jingu_qiangjian`(+10%)、`xueqi_rulin`(+20%)、`ji_wanshi_bumie`(+150%)、`shen_bumie_jinshen`(+100%)、`routi_chanruo`(−20%)；体质 `mudao`/tizhi 30(+10%) |
| `ATTACK_DAMAGE` | ADD_MULTIPLIED_TOTAL | `lijin_ruquan`(+8%)、`shen_zhanshen_linshi`(+60%)、`quanshi_piruan`(−10%)；体质 `lidao`/tizhi 28（按 `daohen_lidao` 每千 +12%） |
| `MOVEMENT_SPEED` | ADD_MULTIPLIED_TOTAL | `shenfa_qingjie`(+5%)、`shenfa_chidun`(−8%)；体质 tizhi 21 / 39（+8%） |
| `ATTACK_SPEED` | ADD_MULTIPLIED_TOTAL | `quanru_jifeng`(+10%)、`shen_zhanshen_linshi`(+25%) |
| `KNOCKBACK_RESISTANCE` | ADD_VALUE | `songyue_jianli`(+0.4)、`shen_jingang_buhuai`(+1.0) |
| `JUMP_STRENGTH` | ADD_VALUE | **当前 0 条使用**（`Op.JUMP_FLAT` 保留但数据表未引用） |
| `SAFE_FALL_DISTANCE` | ADD_VALUE | **当前 0 条使用**（`Op.FALL_FLAT` 同上） |

**A 类条目清单（17 条）**
- 词条 13：`jingu_qiangjian`、`xueqi_rulin`、`ji_wanshi_bumie`、`shen_bumie_jinshen`、`routi_chanruo`、`lijin_ruquan`、`shen_zhanshen_linshi`、`quanshi_piruan`、`shenfa_qingjie`、`shenfa_chidun`、`quanru_jifeng`、`songyue_jianli`、`shen_jingang_buhuai`
- 体质 4：`mudao`(30)、`lidao`(28)、`fengdao`(21)、`yingdao`(39)

> 刻意**不用** `ARMOR`：源码注释「本体把护甲钳在 0–30」，减伤改走 F 类自管乘区。

### 2.2 B 类：本体 `PlayerVariables` 字段读写

全部通过反射（`PlayerVariables.class.getField(name)`），字段名来自本体；写失败静默跳过。

**一次性写入（购买/结算时，`applyOnce`）**

| 字段 | 语义 | 写法 | 条目 id |
|------|------|------|---------|
| `zuida_hunpo` | 魂魄上限 | ×(1+a) | `hunpo_lianxi`(+15%)、`hunpo_rugui`(+50%)、`shen_hunxi_zhuzai`(+100%)、`hunli_shuairuo`(−25%) |
| `hunpochengzaijixian` | 魂魄承载极限 | ×(1+a) | `hunzai_kuanhou`(+15%)、`hunpo_rugui`(+50%)、`shen_hunxi_zhuzai`(+100%)、`hunli_shuairuo`(−25%) |
| `niantou_rongliang` | 念头容量 | ×(1+a) | `niantou_kuozhan`(+15%)、`qiqiao_linglong`(+50%)、`shen_hunxi_zhuzai`(+100%)、`nianfu_xiayai`(−25%) |
| `niantou_zhida` | 念头之道 | ×(1+a) | `qiqiao_linglong`(+50%) |
| `zuida_jingli` | 精力上限 | ×(1+a) | `jingli_shengheng`(+30%)、`jingli_ruhai`(+80%) |
| `shouyuan` | 寿元 | += a | `chuntian_yishou`(+80)、`shouyuan_ruhe`(+200)、`shouyuan_duancu`(−100)；内置 `shouyuan_mianchang`(+50) |
| `qiyun_shangxian` | 气运**上限** | += a（下限夹 0） | `qiyun_weiying`(+12)、`qiyun_yingmen`(+18)、`qiyun_ruhong`(+60)、`qiyun_yizhi`(+40)、`shen_qiyun_zhizi`(+140)、`qiyun_xibo`(−15)；内置 `qiyun_weiguang`(+5)、`fuze_mianchang`(+15)、`shen_tuntian_shidi`(+50) |

> **为什么气运一律走上限**：本体每秒执行 `if (qiyun_shangxian < qiyun) qiyun = qiyun_shangxian`，直接加 `qiyun` 会被下一秒削平；上限本体从不覆写，故只有抬上限才耐久。写入前还会 `Math.max(0.0, ...)` 兜底，防「气运稀薄 −15 + 低上限机缘」把气运压成负数。

**每 tick / 每 5 秒读写（`TianfuEffects.onTick`）**

| 字段 | 语义 | 条目 id |
|------|------|---------|
| `zhenyuan`（读→加→夹 `zuida_zhenyuan`） | 真元回复 | `zhenyuan_ninglian`（消耗 −10% → 折算成 +20% 回复）、`zhenyuan_huiliu`、`zhenyuan_ruquan`、`zhenyuan_bujie`、`zhenyuan_kujie`；体质 tizhi 31(+15%)、40（夜间 +80%） |
| `niantou`（读→加→夹 `niantou_rongliang`） | 念头回复 | `niantou_ninglian`、`niantou_zisheng`、`niantou_ruyuan`、`niantou_bujie`、`niantou_ganhe`；体质 tizhi 48(+30%) |
| `gushi_xiulian_dangqian` / `gushi_xiulian_jindu` | 打坐修炼进度（仅当身上有 `xiulianjingjie` 效果） | `suhui`、`kuxiu_buchuo`、`suiyue_chendian`、`houji_bofa`、`tupo_tongtian`、`dazuo_wangwo`、`xiulian_rushen`、`shen_xiulian_wuji`、`ji_zhongdao_zhizun`、`xiuxing_chizhi` |
| `cd` | 蛊冷却（每 tick 减绝对值） | `gudao_dup`、`liangu_bujie`、`shen_liangu_tianshu` |
| `sanzhao_CD` / `sanzhao_CD1..3` | 杀招冷却 | `shazhao_tiancheng`（固定 −0.1）、`shazhao_rufeng` |
| `LianGu` / `LianGuJinDu` / `GuFang` | 炼蛊进度（独立节拍，见 §5.4） | `liangu_rushen`、`liangu_bujie`、`shen_liangu_tianshu` |
| `zhuanshu` | 转数（只读，用于倍率） | `suhui` |
| `zhoudao_shijian_baifenbi` / `zhoudao_cd_baifenbi` | 宙道时间倍率 / CD 倍率 | 体质 tizhi 43（`+=1.5`、`×0.875`） |
| `tizhi` / `kongqiao` / `zuida_zhenyuan` / `qiyun` / `mingge` | 体质与机缘的原生写入 | 全部 BODY / SIJUE / JIYUAN |
| `daohen_lidao` / `daohen_gudao` / `daohen_bingxuedao` | 只读，用于换算攻击/减伤 | 体质 tizhi 28 / 22 / 19 |
| `liupai_*`（含大写 `Liupai_gudao`） | 流派权重，道痕的 1/10 成对写 + `wanfa_jietong` 差分补偿 | 见 §2.4、§2.5 |

**B 类条目清单（39 条）**
- FIELD_MULT 词条 10：`jingli_shengheng`、`hunpo_lianxi`、`hunzai_kuanhou`、`niantou_kuozhan`、`qiqiao_linglong`、`hunpo_rugui`、`jingli_ruhai`、`shen_hunxi_zhuzai`、`hunli_shuairuo`、`nianfu_xiayai`
- FIELD_ADD 词条 9：`chuntian_yishou`、`qiyun_weiying`、`qiyun_yingmen`、`shouyuan_ruhe`、`qiyun_ruhong`、`qiyun_yizhi`、`shen_qiyun_zhizi`、`shouyuan_duancu`、`qiyun_xibo`
- 内置词条 13：`fuze_mianchang`、`qiyun_weiguang`、`shouyuan_mianchang`、`shen_tuntian_shidi`、`zhenyuan_ninglian`、`niantou_ninglian`、`xinshen_ruyuan`、`kuxiu_buchuo`、`houji_bofa`、`suiyue_chendian`、`shazhao_tiancheng`、`wanfa_jietong`、`shiyuan_zhixing`
- 体质 7：`hundao`(24)、`lidao`(28)、`qidao`(31)、`yuedao`(40)、`zhidao`(42)、`zhoudao`(43)、`mengdao`(48)

### 2.3 C 类：流派 / 药水 buff（`MobEffect`）

**C-1 体质自带的流派 buff**（`TianfuLiupai`，amplifier 恒为 `1`，时长 `-1` 无限，`ambient=false` 隐藏粒子）

| tizhi | 体质 | `GuzhenrenModMobEffects` 字段 | 注册 id | 显示名 |
|-------|------|-------------------------------|----------|--------|
| 25 | `jiandao` 断岳剑心体 | `JIANDAOSHANGHAIZENGFU` | `guzhenren:jiandaoshanghaizengfu` | 剑道伤害增幅 |
| 26 | `jindao` 百炼庚金体 | `JINDAOZENGFU` | `guzhenren:jindaozengfu` | 镀金 |
| 31 | `qidao` 太息养元体 | `QIDAOGUCHONGWEILIZENGFU` | `guzhenren:qidaoguchongweilizengfu` | 气道蛊虫威力增幅 |
| 6 | `sijue_6` 万金妙华体 | `JINDAOZENGFU` | `guzhenren:jindaozengfu` | 镀金 |
| 10 | `sijue_10` 宇宙大衍体 | `LVDAOZENGFU` | `guzhenren:lvdaozengfu` | 律道增幅 |
| 5 | `sijue_5` 炎煌雷泽体 | `YANDAOZENGFU` 或 `LEIDAOZENGSHANG` | `guzhenren:yandaozengfu` / `guzhenren:leidaozengshang` | 按实际道痕字段动态选 |

> 量纲已由字节码确认：`ShangHaiLiuCheng1Procedure` 末尾是 `damage × (1 + Σ amplifier)`，**amplifier=1 即 +1%**。故这些 buff 是「常驻」而非「加强」。
> 刻意**不**挂的：`sijue_2` / tizhi 43（宙道 `zhoudaobuff` 是 NEUTRAL，`onEffectStarted` 做 `zhoudao_shijian_baifenbi -= 0.1×amplifier`，正等级会削弱宙道爆发）、tizhi 28（本体无力道增幅 buff）、其余无 buff 流派。

**C-2 词条常驻药水**（`TianfuTalents.maintainEffects`，每 100 tick 维护一次）

| 效果 id | amplifier | 条目 id |
|---------|-----------|---------|
| `minecraft:fire_resistance` | 0 | `bufen_zhiwu` |
| `minecraft:strength` | 1（力量 II） | `wanjun_lili` |
| `minecraft:regeneration` | 1（生命恢复 II） | `shengji_rumu` |
| `minecraft:speed` | 2（迅捷 III） | `jixing_ruying` |
| `guzhenren_addon:ice_path_boost` | 0 | `bingxue_daoti`、`shen_liupai_jianji` |
| `hundao_addon:hundao_zengfu` | 0 | `hundao_ronghe`、`shen_liupai_jianji` |
| `yundao:yizhi_qiankun` | 0 | `qiyun_yizhi`、`shen_liupai_jianji` |

**C-3 命中附加的标记类效果**（`TianfuZhoushi`，amplifier 恒 `0`，基准 200 tick）

| 效果 id | 触发 | 涉及体质 |
|---------|------|-----------|
| `guzhenren:zhoudaobuff`（宙侵） | 玩家命中目标时施加 | 全部带流派道痕的体质（`TianfuDaohen.hasDaochen(tizhi)`：凡体 19 个 + 十绝体 1–10） |

**C 类条目清单（19 条 + 体质挂载面）**
- 词条 8：`bufen_zhiwu`、`wanjun_lili`、`shengji_rumu`、`jixing_ruying`、`bingxue_daoti`、`hundao_ronghe`、`qiyun_yizhi`、`shen_liupai_jianji`
- 体质（挂 buff）：`jiandao`、`jindao`、`qidao`、`sijue_5`、`sijue_6`、`sijue_10`

### 2.4 D 类：道痕（`daohen_*` / `dahen_*`）

**核心口径：成对写。** 每写 1 个道痕字段，同时写对应 `liupai_<后缀>` 的 **1/10**（本体十绝体即 daohen 2000 / liupai 200）：

```java
private static void addPaired(PlayerVariables v, String daohenName, double amount) {
    add(v, daohenName, amount);
    int cut = daohenName.indexOf('_');
    if (cut < 0) return;
    String suffix = daohenName.substring(cut + 1);
    double share = amount / 10.0;
    if (!tryAdd(v, "liupai_" + suffix, share)) {
        tryAdd(v, "Liupai_" + suffix, share);   // 本体骨道字段首字母大写
    }
}
```

`DAOHEN_ALL` 还会遍历 `PlayerVariables` 上所有 `daohen_*` / `dahen_*` 的 `double` 公共字段（**跳过 `daohen_zong`**，避免与本体统计双计）：

```java
if ((name.startsWith("daohen_") || name.startsWith("dahen_"))
        && !name.equals("daohen_zong") && field.getType() == double.class)
```

**D-1 词条 · 单道痕（33 条）**

| 道痕字段 | 数值 | 条目 id |
|----------|------|---------|
| `daohen_jiandao` | +200 / +800 | `jiandao_qinhe`、`jiandao_dacheng` |
| `daohen_leidao` | +200 / +800 | `leidao_qinhe`、`leidao_dacheng` |
| `daohen_yandao` | +200 | `yandao_qinhe` |
| `daohen_bingxuedao` | +200 | `bingxuedao_qinhe` |
| `daohen_fengdao` | +200 | `fengdao_qinhe` |
| `daohen_yingdao` | +200 | `yingdao_qinhe` |
| `daohen_yuedao` | +200 | `yuedao_qinhe` |
| `daohen_shuidao` | +200 | `shuidao_qinhe` |
| `daohen_tudao` | +200 | `tudao_qinhe` |
| `daohen_mudao` | +200 | `mudao_qinhe` |
| `daohen_jindao` | +200 | `jindao_qinhe` |
| `daohen_gudao` | +200 | `gudao_qinhe` |
| `daohen_lidao` | +200 / +800 | `lidao_qinhe`、`lidao_dacheng` |
| `daohen_hundao` | +200 | `hundao_qinhe` |
| `daohen_xuedao` | +200 | `xuedao_qinhe` |
| `daohen_xudao` | +200 | `xudao_qinhe` |
| `dahen_zhidao` | +200 | `zhidao_qinhe` |
| `dahen_zhoudao` | +200 | `zhoudao_qinhe` |
| `daohen_mengdao` | +200 | `mengdao_qinhe` |
| `daohen_dudao` | +200 | `dudao_qinhe` |
| `daohen_bingdao` | +500 | `bingdao_qinhe` |
| `daohen_daodao` | +500 | `daodao_qinhe` |
| `daohen_guangdao` | +500 | `guangdao_qinhe` |
| `dahen_xingdao` | +500 | `xingdao_qinhe` |
| `daohen_dandao` | +500 | `dandao_qinhe` |
| `dahen_liandao` | +500 | `liandao_qinhe` |
| `daohen_yindao` | +500 | `yindao_qinhe` |
| `daohen_huandao` | +500 | `huandao_qinhe` |
| `daohen_bianhuadao` | +500 | `bianhuadao_qinhe` |
| `dahen_zhendao` | +500 | `zhendao_qinhe` |

（上表 30 个字段对应 33 条条目，其中 `jiandao`/`leidao`/`lidao` 各有「亲和 + 大成」两条。）

**D-2 词条 · 全道痕（3 条）**

| id | 数值 | 说明 |
|----|------|------|
| `wuxing_daocang` | 各 +300 | `DAOHEN_ALL`，逐字段成对写 |
| `shen_wandao_guizong` | 各 +1500 | 同上 |
| `ji_zhongdao_zhizun` | 各 +3000 | 同上 |

**D-3 体质 · 凡体道痕（`TianfuDaohen.write`，统一 +1000 / liupai +100）**

| id | tizhi | 写入 |
|----|-------|------|
| `tudao` | 17 | `daohen_tudao` +1000 |
| `bingdao` | 18 | `daohen_bingdao` +1000 |
| `bingxuedao` | 19 | `daohen_bingxuedao` +1000 |
| `fengdao` | 21 | `daohen_fengdao` +1000 |
| `gudao` | 22 | `daohen_gudao` +1000（`Liupai_gudao` 大写） |
| `hundao` | 24 | `daohen_hundao` +1000 |
| `jiandao` | 25 | `daohen_jiandao` +1000 |
| `jindao` | 26 | `daohen_jindao` +1000 |
| `lidao` | 28 | `daohen_lidao` +1000 |
| `mudao` | 30 | `daohen_mudao` +1000 |
| `qidao` | 31 | `dahen_qidao` +1000（`dahen_` 前缀） |
| `xiuluo` | 33 | `daohen_xuedao` +1000（血道） |
| `xudao` | 37 | `daohen_xudao` +1000 |
| `yingdao` | 39 | `daohen_yingdao` +1000 |
| `yuedao` | 40 | `daohen_yuedao` +1000 |
| `zhidao` | 42 | `dahen_zhidao` +1000 |
| `zhoudao` | 43 | `dahen_zhoudao` +1000 |
| `feixingdao` | 45 | **仅** `liupai_feixingdao` +100（本体无 `daohen_feixingdao` 字段） |
| `mengdao` | 48 | `daohen_mengdao` +1000 |
| `xueshi` | 53 | **无**（血煞蜕变体不写道痕） |

> ⚠ **描述与代码不符（8 处）**：`hundao`、`mudao`、`xudao`、`yingdao`、`yuedao`、`zhidao`、`zhoudao`、`mengdao` 这 8 条体质描述里**没写**道痕，但 `TianfuDaohen.write` 实际会写入 +1000/+100。移植时若要「描述=效果」，需要二选一（补描述 or 删写入）。

**D-4 体质 · 十绝体道痕（`TianfuNative.applySijue`，+2000 / liupai +200）**

| id | tizhi | 写入 |
|----|-------|------|
| `sijue_1` | 1 | `daohen_yudao` 2000 / `liupai_yudao` 200（**覆写**，非累加） |
| `sijue_2` | 2 | `dahen_zhoudao` 2000、`liupai_zhoudao` 200、`zhoudao_shijian_baifenbi = 3.0`、`zhoudao_cd_baifenbi = 0.75` |
| `sijue_3` | 3 | `zuida_hunpo=500`、`hunpochengzaijixian=50000`、随机 `daohen_hundao` 或 `daohen_bingxuedao` 2000（各带 liupai 200） |
| `sijue_4` | 4 | `daohen_mudao` 2000 / `liupai_mudao` 200 + 记分板 `smz` +20 |
| `sijue_5` | 5 | 随机 `daohen_yandao` 或 `daohen_leidao` 2000 + 记分板 `雷道消耗` = 1 |
| `sijue_6` | 6 | `daohen_jindao` 2000 / `liupai_jindao` 200 |
| `sijue_7` | 7 | `daohen_lidao` 2000、`liupai_lidao` 200、`zuida_jingli=200`、记分板 `gjl` +20 |
| `sijue_8` | 8 | `dahen_zhidao` 2000、`liupai_zhidao` 200、`niantou_zhida` 2000、`niantou_rongliang` 20000 |
| `sijue_9` | 9 | `daohen_tudao` 2000 / `liupai_tudao` 200 |
| `sijue_10` | 10 | `daohen_lvdao` 2000 / `liupai_lvdao` 200 |
| `shashen` | 54 | **无开局道痕**，只写 `kongqiao=3`、`zuida_zhenyuan=1000`、`tizhi=54` |

**D-5 击杀累积道痕（`TianfuSlay.addDevourDaohen`）**

每次击杀给 **7 路**各 +5（合计 +35），单字段每日上限 30：

```java
v.dahen_qidao += amount;   v.dahen_zhoudao += amount;
v.daohen_jindao += amount; v.daohen_lvdao += amount;
v.daohen_tudao += amount;  v.daohen_xuedao += amount;
v.daohen_yudao += amount;
```

涉及条目：`shashen`（杀神）、`shen_tuntian_shidi`（神·吞天噬地）。

**D 类条目清单（67 条）**
- 词条 36：33 条单道痕 + 3 条 `DAOHEN_ALL`
- 体质 19：18 条写 `daohen_*` + `feixingdao`（仅 liupai）
- 十绝体 10：`sijue_1` … `sijue_10`
- 击杀通道 2：`shashen`、`shen_tuntian_shidi`

### 2.5 E 类：纯描述 / 落地口径被替换（6 条）

| id | desc 声称 | 实际代码 | 判定 |
|----|-----------|----------|------|
| `shazhao_tiancheng` | 杀招成功率 +5%、10 槽磨损 −1 | 只做 `sanzhao_CD -= 0.1` | **成功率与槽磨损完全未实现** |
| `suiyue_chendian` | 已耗寿元越多、修炼越快 | 固定 `+XIULIAN_TICK*0.10`，不读寿元 | **「随寿元递增」未实现**（退化成固定 +10%） |
| `houji_bofa` | 突破瓶颈 −20% | `+XIULIAN_TICK*0.10` | 口径替换：减免折算成修炼进度（×0.5） |
| `zhenyuan_ninglian` | 真元消耗 −10% | `zy += ZY_REGEN_TICK*0.2` | 口径替换：**消耗减免**变成**回复加成** |
| `niantou_ninglian` | 念头消耗 −10% | `nt += NT_REGEN_TICK*0.2` | 同上 |
| `suhui` | 每转数提供额外**修炼基数与道痕获取** | 只按 `zhuanshu` 加修炼进度 | **「道痕获取」未实现** |

> 另外：`shiyuan_zhixing` 描述里「灵体」在代码中落到 `guzhenren:texiao` 实体标签（击杀 +5% 真元/+5% 念头），属于**已实现但口径需靠标签对齐**，不算 E 类，归 B 类。

### 2.6 F 类（补充）：运行时伤害乘区（无持久化）

`TianfuCombat.onIncomingDamage(LivingIncomingDamageEvent)`，每次命中现算，不写任何字段，因此天然幂等、**最容易移植**。

| 乘区 | 实现 | 条目 id |
|------|------|---------|
| 出伤 · 满血 | `mult *= 1.08` / `1+outFull` | `zhanyi_angran`、`quansheng_ruhong` |
| 出伤 · 残血 <30% | `mult *= 1+outLow` | `canshen_rulang` |
| 出伤 · 残血阶梯 | `1 + min(floor(loss*10),5)*0.05` | 体质 tizhi 33（`xiuluo`） |
| 出伤 · 残血狂化 | `×1.12`（hp<30%） | 体质 tizhi 53（`xueshi`） |
| 出伤 · 杀戮成长 | `TianfuSlay.multiplier(slay)`：10/50/100/300/500/1000 杀 → 1.4/1.96/2.6/3.4/4.2/5.0 | `shashen` |
| 暴击率 | Σ 后 `min(chance, 0.50)` | `baoji_jingtong`、`baoji_rulie`、`baoji_rushen`、`shen_tianji_suan` |
| 暴击伤害 | 基准 `2.0` + Σ | `baoji_jingtong`(+0.20)、`jianbi_qingfeng`(+0.30)、`baoshang_rufeng`(+0.80)、`baoji_rushen`(+1.20)、`shen_tianji_suan`(+2.40) |
| 闪避 | Σ 后 `min(chance, 0.60)` | `shanbu_suifeng`、`shenfa_ruyan`、`shen_tianji_suan`(+0.24)；体质 tizhi 17（<10% 血 +25%）、39(+5%)、37（15% + 5 秒 CD） |
| 非护甲减伤 | Σ 后 `min(r, 0.50)` | `tiepi_rougu`、`ruwei_dingshen`、`jianjia_ruyan`、`jianjia_rushen`、`shen_busi_bumie`、`shen_jingang_buhuai`、`ji_wanshi_bumie`、`youdou_gaoshou`(移动时 +6%)、`shen_bumie_jinshen`(+20%)；体质 tizhi 22（按 `daohen_gudao`）、19（冰系按 `daohen_bingxuedao`） |
| 吸血 | Σ 后 `min(r, 0.15)` | `xixue_rumo`、`xixue_rushi`、`shen_tuntian_shidi`(+12%)；体质 tizhi 53(+5%) |
| 反伤 | **取 `概率×比例` 最大者**，不相加 | `youshou_nengkai`(4%/10%)、`shen_tuntian_shidi`(25%/15%)、`wuying_bi`(6%/12%)、`fanshang_ruyu`(10%/20%)、`fanshang_rushan`(20%/35%) |

> 反伤用 `ThreadLocal IN_REFLECT` 防递归（`hurt()` 会重入同一事件）；只对「攻击者是 `ServerPlayer`」生效。

### 2.7 G 类（补充）：记分板 objective

`TianfuNative` 的 `addScore` / `setScore`，objective 名是中文/缩写裸串（`ObjectiveCriteria.DUMMY`）：

| objective | 写入 | 条目 |
|-----------|------|------|
| `smz` | +20 | `sijue_4` 森海轮回体（对应 desc 里「寿蟽数值 +20」） |
| `gjl` | +20 | `sijue_7` 大力真武体（对应 desc 里「骨骼力 +20」） |
| `雷道消耗` | =1 | `sijue_5` 炎煌雷泽体 |

> 这是本体内的一套「用记分板当属性」的土办法，移植时必须确认目标整合包里这些 objective 名是否还被别的系统读。

### 2.8 H 类（补充）：原版能力位 / 客户端行为

| 行为 | 实现 | 条目 |
|------|------|------|
| 飞行 | `player.getAbilities().mayfly = true` + `fallDistance = 0`（每 tick 强制），靠 `fly_granted` 标记回收 | `feixingdao`（tizhi 45） |
| 沉默时长减半 | 直接改写 `chenmo` 效果的 duration（`floor` 标记防重复折半） | `xinshen_ruyuan` |
| 光环 / 爆发 VFX | `TianfuVfx`（按 id 查 `AURAS` 表，只播最高品质一档） | 几乎全部条目（纯表现，可整段砍掉） |

---

## 3. 无法移植 / 需额外适配的条目

### 3.1 三层依赖关系

| 层级 | 依赖物 | 影响面 |
|------|--------|--------|
| **L0 共性** | `TianfuApply.effective(player)` —— 「已购 id 集合」来自 `TianfuData.purchased()`，且被 `TianfuData.isConfirmed()` 闸门卡住（未最终确认 → 返回 `Set.of()`） | **全部 159 条**：任何条目要生效都必须先有「这个玩家拥有哪些条目」的来源 |
| **L1 引擎** | `TianfuTalents.Op` / `Fx` / `Talent` / `Attrs` + `TianfuAttributes` / `TianfuEffects` / `TianfuCombat` / `TianfuTalents` 四个引擎类 | **111 条扩展词条**：不抄引擎就只剩一张描述表 |
| **L2 自有状态** | `TianfuData` 的 compound 键、`TianfuNative` 的原生写入、`TianfuPool`/`Kind` | **32 条**（见下表） |

### 3.2 L2 强依赖清单（32 条）

| # | 条目 id | 依赖的具体东西 | 适配建议 |
|---|---------|----------------|----------|
| 1 | `wanfa_jietong` | `TianfuData.liupaiSnap()` → compound 键 `liupai_snap`（每秒差分快照） | 换成目标 mod 自己的 `SavedData`/`PersistentData` 子标签；快照必须**首次购买时以当时值为基准**，否则会追补历史 |
| 2 | `xinshen_ruyuan` | `TianfuData` 键 `chenmo_floor`（防重复折半的 floor 标记） | 直接抄逻辑，键名换成目标 mod 命名空间 |
| 3 | `liangu_rushen` | 键 `liangu_acc` / `liangu_prev`（小数累加器 + 换蛊复位判据） | 同上；注意必须保留「补点上限 99」与「投料窗口 50–70 不加速」两条硬约束 |
| 4 | `liangu_bujie` | 同上 | 同上 |
| 5 | `shen_liangu_tianshu` | 同上 | 同上 |
| 6 | `xudao`（体质 37） | 键 `xuhua_cd`（5 秒内置 CD，存 gameTime） | 换键名即可 |
| 7 | `feixingdao`（体质 45） | 键 `fly_granted`（「本模组授予」标记，防误删创造/其它 mod 的飞行） | **必须保留该标记**，否则撤销体质会把创造模式飞行一起关掉 |
| 8 | `shashen`（杀神） | 键 `slay` / `slayDay` / `slayDaily` / `lastKill` / `lastType` / `streak` / `devourDay` / `devourDaily` + `TianfuSlay` 全套防刷（每日 +50 上限、同种连续击杀衰减、5 分钟不杀衰减、死亡 −20%） | 这一整套是**独立子系统**（251 行），建议整类搬运，别拆 |
| 9 | `shen_tuntian_shidi` | 键 `devourDay` / `devourDaily`（与杀神共用通道） | 与 #8 一起搬 |
| 10–17 | `bufen_zhiwu`、`wanjun_lili`、`shengji_rumu`、`jixing_ruying`、`bingxue_daoti`、`hundao_ronghe`、`qiyun_yizhi`、`shen_liupai_jianji` | 键 `"amp:" + 效果id`（记录「本模组实际授予的等级」，撤销时精确比对） | **不能省**：不记授予等级，玩家自己喝了更高等级的药水后再撤销，会把 buff 删错或删不掉 |
| 18–21 | `jiyuan_ding` / `jiyuan_bing` / `jiyuan_yi` / `jiyuan_jia` | `TianfuNative.applyJiyuan` + 本体 `TianziliuchengProcedure.execute()` + 覆写 `kongqiao`/`zuida_zhenyuan`/`qiyun`/`qiyun_shangxian`/`mingge`；且**不可撤销** | 机缘是「改写本体开窍结果」，目标 mod 若没有等价的开窍体系，这 4 条只能改成「直接给属性」；注意 `roll(1,3)==1` 分支必须补 `qiyun_shangxian = max(cap, qiyun)`，否则气运会下一秒被削平 |
| 22–32 | `sijue_1` … `sijue_10` + `shashen` | `TianfuNative.applySijue`：写 `kongqiao=3` + `zuida_zhenyuan=1000` + `tizhi=n`，依赖本体 KE1/KE2 的区间反推与十绝体运行时分支；另写记分板 `smz`/`gjl`/`雷道消耗` | 这是**最不可移植的一类**：效果大半不在 tianfu 里，而在本体 `UikongqiaoguanbiProcedure` 的 `kongqiao==3` 分支。没有本体就得自己实现十绝体效果表 |

### 3.3 其它必须一起搬的「非条目」依赖

| 依赖 | 位置 | 说明 |
|------|------|------|
| `TianfuData`（`ROOT = "tianfu"` 的 PersistentData compound） | `TianfuData.java`（339 行） | `points` / `difficulty` / `purchased` / `applied` / `confirmed` / `settled` + 上述运行期键。**换 mod 必须换 ROOT**，否则同存档两套数据互撞 |
| `TianfuNet` + `net/TianfuConfirmPayload` + `client/TianfuViewScreen` | 网络与 UI | 购买/确认/撤销三条链路；`confirmFinal` 只调 `markConfirmed` 不结算会「买了没效果」，`TianfuEffects.onTick` 里有幂等自愈补丁 |
| `TianfuPool.Spec` / `Kind` / `BY_ID` | `TianfuPool.java` | 数据模型本体 |
| `TianfuVfx`（`AURAS` 按 id 索引、`burst`、`buyBurstFor`） | `TianfuVfx.java`（29724 B） | 纯表现；不搬则删掉所有调用点 |
| Mixin：`JingJieBaoCunProcedureMixin`、`YizhuanrendaoxiwangguItemMixin` | `tianfu/mixin/` | 「寿元耗尽 = 总结算 + `wipeAll`」与「希望蛊开窍入口」；移植时对应「转世重置」机制 |
| 本体类硬引用 | `GuzhenrenModMobEffects`（6 个 `DeferredHolder`）、`GuzhenrenModVariables.PlayerVariables`（反射 + 直接字段）、`TianziliuchengProcedure`、`UikongqiaoguanbiProcedure`、`GuzhenrenModParticleTypes/Sounds` | 全部是**编译期依赖**，目标 mod 没有本体就得全部改写 |
| 标签 | `guzhenren:shengwu` / `renzu` / `texiao`（实体）、`guzhenren:bing_xue_dao`（物品） | 杀神 / 噬元 / 冰系判定的过滤器 |
| 效果 id | `guzhenren:zhoudaobuff`（宙侵）、`guzhenren:chenmo`、`guzhenren:xiulianjingjie` | 宙蚀、沉默、修炼三套机制的挂点 |

---

## 4. 可直接复用的「效果原语」

`TianfuTalents` 里确实有一套通用抽象：**`Op` 枚举 + `Fx` record + `Talent` record + `Attrs` record**。以下为完整定义（原文照抄）。

### 4.1 `Op` 枚举（28 个值）

```java
/** 效果原语。 */
public enum Op {
    HEALTH_MULT, ATTACK_MULT, SPEED_MULT, ATTACK_SPEED_MULT,
    JUMP_FLAT, KNOCKBACK_FLAT, FALL_FLAT,
    MOB_EFFECT, FIELD_MULT, FIELD_ADD, DAOHEN, DAOHEN_ALL,
    REGEN_HP_5S, REGEN_ZY, REGEN_NT, XIULIAN, BREAKTHROUGH, LIANGU, GU_CD, SHAZHAO_CD,
    CRIT_CHANCE, CRIT_DAMAGE, DODGE, LEECH, REDUCTION, REFLECT, OUT_FULL, OUT_LOW
}
```

> 实际是 **28 个值**（源码换行排布容易数错：第一行 4 + 第二行 3 + 第三行 4 + 第四行 8 + 第五行 7 = 26？逐项核对：HEALTH_MULT, ATTACK_MULT, SPEED_MULT, ATTACK_SPEED_MULT(4), JUMP_FLAT, KNOCKBACK_FLAT, FALL_FLAT(7), MOB_EFFECT, FIELD_MULT, FIELD_ADD, DAOHEN, DAOHEN_ALL(12), REGEN_HP_5S, REGEN_ZY, REGEN_NT, XIULIAN, BREAKTHROUGH, LIANGU, GU_CD, SHAZHAO_CD(20), CRIT_CHANCE, CRIT_DAMAGE, DODGE, LEECH, REDUCTION, REFLECT, OUT_FULL, OUT_LOW(28) → **共 28 个枚举值**）。

### 4.2 `Fx`（单条原语实例）

```java
/**
 * 单条效果原语实例。
 *
 * @param op  原语种类
 * @param arg 目标名（{@link Op#MOB_EFFECT}=效果注册名、{@link Op#FIELD_MULT}/{@link Op#FIELD_ADD}/{@link Op#DAOHEN}=本体字段名；其余为 null）
 * @param a   主数值
 * @param b   副数值（仅 {@link Op#REFLECT} 用：反弹比例）
 */
public record Fx(Op op, String arg, double a, double b) {
}
```

### 4.3 `Talent`（词条）

```java
/** 词条（id / 名 / 品质 / 成本 / 描述 / 效果列表）。 */
public record Talent(String id, String name, String quality, int cost, String desc, List<Fx> fx) {
}
```

### 4.4 `Attrs`（属性类聚合值）

```java
/** 属性类聚合值（供 {@link TianfuAttributes} 一次性套用）。 */
public record Attrs(double healthMult, double attackMult, double speedMult,
                    double attackSpeedMult, double jumpFlat, double knockbackFlat, double fallFlat) {
}
```

> 语义：三个 `*Mult` 是**乘区**，初值 `1.0`，逐条 `*= (1.0 + a)`（**连乘**，不是相加）；`*Flat` 是**加值**，初值 `0.0`，逐条 `+= a`。最终 `TianfuAttributes` 用 `mult - 1.0` 作为 `ADD_MULTIPLIED_TOTAL` 的 amount。

### 4.5 构造快捷函数（`arg` 绑定关系全在这里）

```java
/** 常驻效果「授予时等级」的记录前缀（值 = 实际 amplifier）。 */
private static final String AMP_KEY = "amp:";

private static Fx f(Op op, double a) {
    return new Fx(op, null, a, 0.0);
}

private static Fx dao(String field, double a) {
    return new Fx(Op.DAOHEN, field, a, 0.0);
}

private static Fx fld(String field, double a) {
    return new Fx(Op.FIELD_MULT, field, a, 0.0);
}

private static Fx fldAdd(String field, double a) {
    return new Fx(Op.FIELD_ADD, field, a, 0.0);
}

private static Fx mob(String name, double amp) {
    return new Fx(Op.MOB_EFFECT, name, amp, 0.0);
}

private static Fx reflect(double chance, double ratio) {
    return new Fx(Op.REFLECT, null, chance, ratio);
}

private static Talent t(String id, String name, String q, int cost, String desc, Fx... fx) {
    return new Talent(id, name, q, cost, desc, List.of(fx));
}
```

### 4.6 每个 Op 的语义表（复刻用）

| Op | `arg` | `a` | `b` | 聚合方式 | 落地通道 |
|----|-------|-----|-----|----------|----------|
| `HEALTH_MULT` | null | 乘区增量（0.20=+20%） | 0 | `health *= 1+a`（连乘） | A → `MAX_HEALTH` |
| `ATTACK_MULT` | null | 同上 | 0 | `attack *= 1+a` | A → `ATTACK_DAMAGE` |
| `SPEED_MULT` | null | 同上 | 0 | `speed *= 1+a` | A → `MOVEMENT_SPEED` |
| `ATTACK_SPEED_MULT` | null | 同上 | 0 | `attackSpeed *= 1+a` | A → `ATTACK_SPEED` |
| `JUMP_FLAT` | null | 加值 | 0 | `jump += a` | A → `JUMP_STRENGTH`（ADD_VALUE，**当前 0 条使用**） |
| `KNOCKBACK_FLAT` | null | 加值 | 0 | `knockback += a` | A → `KNOCKBACK_RESISTANCE`（ADD_VALUE） |
| `FALL_FLAT` | null | 加值 | 0 | `fall += a` | A → `SAFE_FALL_DISTANCE`（ADD_VALUE，**当前 0 条使用**） |
| `MOB_EFFECT` | 效果 id（裸名或 `ns:path`，裸名默认 `guzhenren:`） | amplifier | 0 | 记录到 `amp:<id>` 后授予无限时长 | C |
| `FIELD_MULT` | `PlayerVariables` 字段名 | 乘区增量 | 0 | 一次性 `field *= 1+a` | B |
| `FIELD_ADD` | `PlayerVariables` 字段名 | 加值 | 0 | 一次性 `field += a` | B |
| `DAOHEN` | 道痕字段名 | 道痕量 | 0 | `+= a`，并成对写 `liupai_* += a/10` | D |
| `DAOHEN_ALL` | null | 每路道痕量 | 0 | 遍历所有 `daohen_*`/`dahen_*` 字段逐条成对写（跳过 `daohen_zong`） | D |
| `REGEN_HP_5S` | null | 每 5 秒回复的**比例** | 0 | 每 100 tick `heal(maxHp*a)` | B |
| `REGEN_ZY` | null | 每 tick 真元增量系数 | 0 | 每 tick `zhenyuan += ZY_REGEN_TICK*a`，夹 `zuida_zhenyuan` | B |
| `REGEN_NT` | null | 每 tick 念头增量系数 | 0 | 每 tick `niantou += NT_REGEN_TICK*a`，夹 `niantou_rongliang` | B |
| `XIULIAN` | null | 每 tick 修炼进度系数 | 0 | 每 tick `gushi_xiulian_jindu += XIULIAN_TICK*a` | B |
| `BREAKTHROUGH` | null | 突破加速系数 | 0 | 折算进 `XIULIAN`（`a*0.5` 并入修炼进度） | B（口径替换） |
| `LIANGU` | null | 炼蛊进度系数 | 0 | 30 tick 独立节拍累加，`floor` 后补点，上限 99 | B |
| `GU_CD` | null | 每 tick 冷却绝对减量 | 0 | 每 tick `cd -= a`（下限 0） | B |
| `SHAZHAO_CD` | null | 每 tick 杀招冷却减量 | 0 | 每 tick `sanzhao_CD -= a` | B |
| `CRIT_CHANCE` | null | 暴击率增量 | 0 | Σ 后 `min(Σ, 0.50)` | F |
| `CRIT_DAMAGE` | null | 暴击伤害增量（加在基准 2.0 上） | 0 | `2.0 + Σ` | F |
| `DODGE` | null | 闪避率增量 | 0 | Σ 后 `min(Σ, 0.60)` | F |
| `LEECH` | null | 吸血比例增量 | 0 | Σ 后 `min(Σ, 0.15)` | F |
| `REDUCTION` | null | 非护甲减伤增量 | 0 | Σ 后 `min(Σ, 0.50)` | F |
| `REFLECT` | null | 触发概率 | 反弹比例 | **取 `a*b` 最大者**（不相加） | F |
| `OUT_FULL` | null | 满血出伤增量 | 0 | 满血时 `mult *= 1+a` | F |
| `OUT_LOW` | null | 残血出伤增量 | 0 | hp<30% 时 `mult *= 1+a` | F |

### 4.7 引擎方法签名（复刻清单）

`TianfuTalents` 对外暴露的方法（全部 `static`，纯函数式，无实例状态）：

```java
public static List<Talent> specs();                       // 全部条目（含不可用）
public static Talent specOf(String id);                   // 按 id 查
public static Talent byIdIncludingUnavailable(String id); // 无视 mod 装载状态查
public static boolean isAvailable(Talent t);              // 依赖的附属是否安装
public static List<Talent> available();                   // 过滤后可用条目
public static boolean modLoadedFor(String ns);            // ModList.isLoaded(ns)

public static Attrs attrs(Collection<String> ids);        // 聚合 A 类
public static void applyOnce(Player, PlayerVariables, Collection<String> ids);   // 一次性 B/D 类
public static Map<String, Double> allDaohen(Collection<String> ids);             // 聚合 D 类

// 聚合器（返回 double，供 TianfuCombat / TianfuEffects 直接相加）
public static double regenZy(Collection<String> ids);
public static double regenNt(Collection<String> ids);
public static double regenHp5s(Collection<String> ids);
public static double xiulian(Collection<String> ids);
public static double breakthrough(Collection<String> ids);
public static double liangu(Collection<String> ids);
public static double guCd(Collection<String> ids);
public static double shazhaoCd(Collection<String> ids);
public static double critChance(Collection<String> ids);
public static double critDamage(Collection<String> ids);
public static double dodge(Collection<String> ids);
public static double leech(Collection<String> ids);
public static double reduction(Collection<String> ids);
public static double outFull(Collection<String> ids);
public static double outLow(Collection<String> ids);
public static Fx bestReflect(Collection<String> ids);     // 取 a*b 最大的一条

// 状态维护
public static void maintainEffects(ServerPlayer, Collection<String> ids);  // 每 100 tick 补挂 C 类
public static void revokeEffects(ServerPlayer, Collection<String> ids);    // 精确撤 C 类
public static MobEffect effectOf(String name);                             // 静态 switch + 注册表兜底

// 底层读/写
public static boolean owned(Collection<String> ids, String id);
public static void add(PlayerVariables v, String field, double a);
public static boolean tryAdd(PlayerVariables v, String field, double a);
public static void multiply(PlayerVariables v, String field, double a);
public static void divide(PlayerVariables v, String field, double a);
public static void subtract(PlayerVariables v, String field, double a);
public static void subtractPaired(PlayerVariables v, String field, double a);
public static void revokeOnce(Player, PlayerVariables, Collection<String> ids);  // 撤销时精确反向
```

> 这套 API 的关键设计：**聚合器只吃 `Collection<String> ids`**，完全不碰 `TianfuData`。只要目标 mod 能提供「这个玩家拥有哪些 id」，这 111 条词条 + 4 个引擎类可以**整段复制**过去，不需要改一行效果代码。

---

## 5. 复刻时必须照抄的细节陷阱

以下每条都是「抄错就静默失效」的点，全部来自源码注释或实际逻辑。

### 5.1 属性 modifier 必须是 transient 且幂等重建

`TianfuAttributes.sync` 每次先用固定 id（`body_health_mult` 等 7 个）`removeModifier` 再 `addTransientModifier`；`amount == 0` 时只移除不添加。**用永久 modifier 会随存档累积，重登一次加一次。**

### 5.2 气运只能抬上限，不能加当前值

本体每秒 `if (qiyun_shangxian < qiyun) qiyun = qiyun_shangxian;`。直接写 `qiyun` 会被下一秒削平，所以 9 条气运类条目全部走 `qiyun_shangxian`，并在写入前 `Math.max(0.0, ...)`。

### 5.3 成对写道痕（1:10）

`daohen_* += N` 必须同时 `liupai_* += N/10`，否则流派权重与道痕脱节。**骨道字段首字母大写**（`Liupai_gudao`），要 `tryAdd("liupai_gudao")` 失败后再 `tryAdd("Liupai_gudao")`。`daohen_zong` 是本体统计字段，遍历时必须排除。

### 5.4 炼蛊补点：`floor` 累加 + 上限 99 + 投料窗口不加速

```java
// 每 30 tick 一拍（独立于其它每 tick 逻辑）
double acc = data.getDouble("liangu_acc") + perTick * 30.0;
int whole = (int) Math.floor(acc);
data.putDouble("liangu_acc", acc - whole);
// 仅在 LianGuJinDu 落在 [50,70) 之外时补点，且补到 99 封顶
```

漏掉 `liangu_acc` 小数累加器会导致「每拍不足 1 点 → 永远 +0」；漏掉 99 上限会让炼蛊直接完成。

### 5.5 授予 buff 要记等级（`amp:`）

`maintainEffects` 写 `amp:<effectId> = amplifier`；`revokeEffects` 只在「当前效果无限时长 **且** amplifier 等于记录值」时才删。否则玩家自己喝了迅捷 IV，撤销词条会把 IV 也删掉。

### 5.6 撤销必须精确反向

`revokeOnce` 对每个 `Fx` 做逆运算：`FIELD_MULT` → `divide`、`FIELD_ADD` → `subtract`、`DAOHEN` → `subtractPaired`、`MOB_EFFECT` → 删 `amp:` 记录 + 撤效果。**必须先读 `amp:` 再删效果。**

### 5.7 战斗乘区的事件语义

`LivingIncomingDamageEvent` 的 `getAmount()` 是**当前累计伤害**，要 `setAmount(amount * mult)`；反伤必须用 `ThreadLocal` 防递归（`hurt()` 会重入），且只对 `ServerPlayer` 攻击者生效。**没有 `LivingIncomingDamageEvent` 的版本要改用 `LivingHurtEvent` + 自行维护递归标记。**

### 5.8 `effective()` 是唯一闸门

所有引擎方法都吃 `TianfuApply.effective(player)`，它在未最终确认时返回 `Set.of()`。**抄引擎但不抄这道闸门，会出现「买了立刻生效但界面还没确认」的错位。**

### 5.9 `wanfa_jietong` 的差分补偿

流派权重每秒被本体自然增长，×3 不能靠 multiplier 实现，而是每秒比对 `liupai_snap` 快照，把「自然增量 × 2」补上去，再刷新快照。**首次购买必须以当时值为基准建快照**，否则会把历史增长一次性补发。

### 5.10 道痕与描述的 8 处不符

`hundao`、`mudao`、`xudao`、`yingdao`、`yuedao`、`zhidao`、`zhoudao`、`mengdao` 描述未提道痕但实际写入 +1000。移植时按「描述=效果」的原则需要显式决定保留哪边。

---

## 6. 复用决策速查

| 想复用的东西 | 可复用度 | 做法 |
|--------------|----------|------|
| `Op` / `Fx` / `Talent` / `Attrs` 四个 record | ★★★★★ 直接抄 | 零依赖，改包名即可 |
| `TianfuTalents` 的 111 条数据表 | ★★★★★ 直接抄 | 只依赖上面 4 个 record |
| `TianfuAttributes`（7 个 modifier） | ★★★★★ 直接抄 | 只依赖 vanilla `Attributes` + `Attrs` |
| `TianfuCombat` 的乘区公式 | ★★★★☆ 抄公式 | 事件类可能要换（`LivingIncomingDamageEvent` ↔ `LivingHurtEvent`） |
| `TianfuEffects` 的每 tick 结算 | ★★★☆☆ 抄逻辑 | 依赖 `PlayerVariables` 字段名，字段对不上要重映射 |
| `TianfuDaohen` 的成对写 | ★★★★☆ 抄逻辑 | 依赖 `daohen_*`/`liupai_*` 字段存在 |
| `TianfuLiupai` 的 6 个 buff 挂载 | ★★★☆☆ 抄映射表 | 依赖本体 `GuzhenrenModMobEffects` 的 6 个 `DeferredHolder` |
| `TianfuSlay` 杀神子系统 | ★★★☆☆ 整类搬 | 251 行独立逻辑，依赖 `TianfuData` 的 8 个键 |
| `TianfuNative` 机缘/十绝体结算 | ★☆☆☆☆ 基本要重写 | 依赖本体 `TianziliuchengProcedure`、`UikongqiaoguanbiProcedure`、记分板、`kongqiao` 体系 |
| `TianfuData` 存档结构 | ★★☆☆☆ 只抄键名设计 | **必须换 ROOT**，否则同存档互撞 |
| `TianfuVfx` 表现层 | ★★★★☆ 可选 | 纯表现，砍掉不影响数值 |
| 网络 / UI（`TianfuNet`、`TianfuViewScreen`） | ★★☆☆☆ 重写 | 购买流程与目标 mod 的 UI 强耦合 |

---

## 7. 结论

1. **数据可 100% 复用**：159 条条目的 id / 名字 / 品质 / 成本 / tizhi / 描述全部可原样搬走，只有 id 冲突风险（建议加前缀）。
2. **效果原语可 100% 复用**：`Op`(28) + `Fx` + `Talent` + `Attrs` 是零依赖的纯数据抽象，111 条扩展词条不需要改一行效果代码。
3. **效果落地分 6 条通道**（A 属性 / B 字段 / C buff / D 道痕 / E 纯描述 / F 战斗乘区），另有 G 记分板、H 能力位两条补充通道。
4. **32 条强依赖 tianfu 自有结构**（§3.2），其中 11 条十绝体基本只能重写；**6 条为 E 类纯描述或口径被替换**（§2.5）。
5. 最大的隐性成本不在效果，而在 **`TianfuData` 存档结构 + `TianfuApply` 的确认闸门 + 本体 `PlayerVariables` 字段名**这三件事上。

