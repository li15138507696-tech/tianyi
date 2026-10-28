# 四只传奇蛊像素资产

这是一组独立的 Minecraft 物品美术源文件：春秋蝉、智慧蛊、力量蛊、宿命蛊。整体使用用户选定的“水墨三气”方向。成品图标严格为透明底 16×16 像素；图中气流、光环和金色点光是模组美术表达，不是原著新增设定。

16×16 图标保留为物品像素稿；为避免 Blockbench 放大视图过糊，源模型另用透明底 128×128 高清像素贴图，并配 128×128 UV。模型整体仍在 16 单位范围内，但几何细分到 0.5 单位；正面每个半单位部件对应 4×4 贴图像素，比原来的整单位块面能保留更多翅脉、足部与外轮廓。躯干、翅、蛛足、伤痕和三气可以在 Blockbench 中分组选择，且有不同厚度。它是清晰像素风的可编辑低模，不是带骨骼或动画的生物模型。

| 蛊 | 可单独编辑的模型部件 |
|---|---|
| 春秋蝉 | 左右枯叶翅、木质虫体、足部、三气灵光 |
| 智慧蛊 | 左右翅、触须、原创虫体、智慧光核、步足、三气灵光 |
| 力量蛊 | 甲壳虫体、力量核心、步足、三气灵光 |
| 宿命蛊 | 头胸部、腹部、左右蛛足、赤色伤痕、三气灵光 |

## 文件

| 蛊 | 16×16 图标 | 128×128 模型贴图 | Blockbench 源文件 | Minecraft 模型 JSON |
|---|---|---|---|
| 春秋蝉 | `textures/legendary_gu/chunqiuchan.png` | `textures/legendary_gu/model/chunqiuchan.png` | `blockbench/chunqiuchan.bbmodel` | `models/item/legendary_gu/chunqiuchan.json` |
| 智慧蛊 | `textures/legendary_gu/zhihuigu.png` | `textures/legendary_gu/model/zhihuigu.png` | `blockbench/zhihuigu.bbmodel` | `models/item/legendary_gu/zhihuigu.json` |
| 力量蛊 | `textures/legendary_gu/lilianggu.png` | `textures/legendary_gu/model/lilianggu.png` | `blockbench/lilianggu.bbmodel` | `models/item/legendary_gu/lilianggu.json` |
| 宿命蛊 | `textures/legendary_gu/suminggu.png` | `textures/legendary_gu/model/suminggu.png` | `blockbench/suminggu.bbmodel` | `models/item/legendary_gu/suminggu.json` |

上表各行依次为 16×16 图标、128×128 模型贴图、Blockbench 源文件和物品模型 JSON。模型贴图细节由用户选定的水墨三气造型延展；原创身体细节仍按“美术补足”标注。

16×16 图标总览/原尺寸预览与模型高清贴图总览均在 `preview/`。

## 原著依据与美术补足

- **春秋蝉**：采用重生后虚弱状态。棕褐木质躯干、年轮感纹理、宽阔叶片状翅与网状叶脉是识别要点；枯黄翅色表示此阶段。受 16×16 限制，缺角与卷翅只在不影响识别时简化。
- **智慧蛊**：原著依据是智慧之光/光晕。本体虫形与主体配色为原创补足；光晕的具体色彩只是美术选择，不宣称为原著定色。
- **力量蛊**：本体虫形、体色与装甲结构均为原创补足。
- **宿命蛊**：主体为黑白蜘蛛；红色伤痕代表受损状态。本组单张图标采用带赤痕的版本。

水墨气流、光环、青玉/暖金点色都属于美术装饰；像素优先留给虫体轮廓与原著特征。

## 颜色参考

下列色值用于统一像素调色，属于资产制作选择，不是原著设定：

- 墨色：`#20232A`
- 青玉气流：`#3F9B90`
- 暖金点光：`#E1BC69`
- 春秋蝉木褐：`#71523A`
- 宿命蛊象牙白：`#E8E2D2`
- 宿命蛊赤痕：`#C9363A`
- 智慧蛊浅玉色：`#B8D8C5`
- 力量蛊铁灰：`#555961`

## 后续接入方式

本目录不直接接入游戏资源或注册物品。若要导入 `tianyi` 命名空间：

- `textures/legendary_gu/*.png` 复制到 `assets/tianyi/textures/item/legendary_gu/`；`textures/legendary_gu/model/*.png` 复制到其 `model/` 子目录。
- `models/item/legendary_gu/*.json` 复制到 `assets/tianyi/models/item/legendary_gu/`。
- Blockbench 文件保留在 `blockbench/` 供编辑；模型贴图使用 `tianyi:item/legendary_gu/model/<名称>`，Blockbench 的纹理目录字段已设为 `item/legendary_gu/model`。
- 还需单独注册春秋蝉、智慧蛊、力量蛊物品，再将相应模型 ID 接到物品定义；本资产包不做这一步。
- 宿命蛊现有的完整/受损状态和 `assets/tianyi` 资源保持原样。

## 交付核对

- 四张独立物品图标为 16×16 RGBA PNG；四张 Blockbench/Minecraft 模型贴图为透明底 128×128 PNG。
- 四份 Minecraft 模型 JSON 均可解析，并使用对应的 `tianyi:item/legendary_gu/model/<名称>` 贴图 ID；每个面的 UV 坐标均为四项，范围在 0–16，受光设置与 Blockbench 源模型一致。
- 四个 `.bbmodel` 均使用 128×128 UV 并嵌入对应高清 PNG；每个面的 UV 坐标均为四项，范围在 0–128。模型具有已命名的部件分组，整体边界保持在 16 单位内、网格细分为 0.5 单位，部件厚度约为 1–3.5 个单位。
- 宿命蛊把象牙白集中用于腹部纹样与蛛足分段，红色伤痕限制在蜘蛛躯体区域。
- 16×16 图标与 128×128 模型贴图是分开的资源；模型专用高清贴图总览为 `preview/gu-model-textures-overview.png`。
- 当前会话尝试打开 `.bbmodel` 时，系统拒绝启动 Blockbench，因此未能检查应用窗口内的显示；模型结构、高清贴图、UV 和预览图已做手工核对。可在本机 Blockbench 中打开 `blockbench/*.bbmodel` 查看 3D 角度。
- 本轮没有更改 Java、游戏资源或玩法，也没有添加或运行自动化测试。

## 资料线索

- 春秋蝉外观概述（章节转述）：<https://www.newton.com.tw/wiki/%E6%98%A5%E7%A7%8B%E8%9F%AC>
- 宿命蛊黑白蜘蛛与赤痕（章节镜像）：<https://www.sobqg.com/read/guzhenren/18154714.html>
- 智慧蛊光晕（章节镜像）：<https://www.sobqg.com/read/guzhenren/18154043.html>
