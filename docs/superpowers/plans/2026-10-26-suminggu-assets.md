# 宿命蛊物品模型与贴图实施计划

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task.

**Goal:** 补齐宿命蛊完整态与受损态像素贴图，并让 `K_SCARRED` 驱动物品模型外观。

**Architecture:** 沿用两份 `item/generated` 模型。完整模型注册客户端物品属性 `tianyi:scarred`，从 `DestinyGuItem` 读取既有受损标记；普通模型通过 override 切换到已有的受损模型。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1、Java 21、Minecraft item model JSON、64×64 PNG。

**Spec:** `docs/superpowers/specs/2026-10-26-suminggu-assets-design.md`

## Global Constraints

- 只使用 `DestinyVessel.K_SCARRED` 表示受损外观；不改账本真伪、保管或阶段规则。
- 物品属性只在 `TianyiMod` 已有的客户端初始化分支注册，独立服务端不得加载客户端渲染类。
- 完整态是黑白蜘蛛；受损态保持相同构图并增加赤红伤痕。
- 若打包为交付版本，按项目规则从 3.13.1 升至 3.13.2。
- 不新增或运行自动化测试；只按明确请求完成资源实现和构建产物。

## Review Focus

- 空壳或未受损物品读取到 `K_SCARRED=false` 时应使用完整态图标。
- `K_SCARRED=true` 时应切换受损贴图，不应改变账本状态。
- 独立服务端启动路径不得引用客户端物品属性类。
- 贴图须透明、像素边缘清晰，缩至 16×16 时仍能辨认黑色蜘蛛、白色纹样与红痕。
- 打包结果须包含两张贴图、两份模型及 3.13.2 版本元数据。

---

### Task 1: 完成宿命蛊像素贴图与受损模型切换

**Files:**
- Create: `src/main/resources/assets/tianyi/textures/item/suminggu.png`
- Create: `src/main/resources/assets/tianyi/textures/item/suminggu_damaged.png`
- Modify: `src/main/resources/assets/tianyi/models/item/suminggu.json`
- Modify: `src/main/java/net/guzhenren/tianyi/item/DestinyGuItem.java`
- Modify: `src/main/java/net/guzhenren/tianyi/client/JournalClientSetup.java`
- Version metadata: `version.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, and versioned docs via `_pack/bump_version.py`

**Interfaces:**
- Consumes: `DestinyVessel.K_SCARRED` inside the stack's `DataComponents.CUSTOM_DATA`.
- Produces: public read-only `DestinyGuItem.isScarred(ItemStack)` for the client model predicate; item property `tianyi:scarred` returning `1.0F` for scarred stacks and `0.0F` otherwise.

- [ ] **Step 1: Create both transparent pixel textures**

Create 64×64 PNG assets following the latest user reference's bottom-right design: black spider, ivory carapace markings, and wrapped crimson scar threads. The complete icon keeps the same spider and ivory markings without red scar threads; the damaged icon retains the red threads. Keep edges on the pixel grid, preserve transparency, and avoid text, glow, shadows, or extra symbols. Confirm both remain readable at 16×16.

- [ ] **Step 2: Expose the existing visual state and register its client item property**

Change the existing `DestinyGuItem.scarred(ItemStack)` helper to the public static read-only method `isScarred(ItemStack)`. In `JournalClientSetup.init(IEventBus)`, register an `ItemProperties` predicate for `TianyiItems.SUMINGGU` under `tianyi:scarred`, returning `1.0F` only when `DestinyGuItem.isScarred(stack)` is true. Keep registration inside the current client-only initialization path.

- [ ] **Step 3: Connect the item model override**

Add an override to `models/item/suminggu.json` mapping predicate `tianyi:scarred: 1.0` to `tianyi:item/suminggu_damaged`. Keep the existing generated parent and texture layer. Keep the damaged model's texture reference at `tianyi:item/suminggu_damaged`.

- [ ] **Step 4: Prepare the versioned distributable without running tests**

Bump to `3.13.2`, then run the offline build with its test phase disabled. Use the project's configured Python interpreter to invoke `_pack/build.py --bump 3.13.2 --no-tests`. The deliverable is `build/libs/tianyi-3.13.2.jar`; installation into a game instance is out of scope.

- [ ] **Step 5: Inspect the finished assets and packaged resource paths**

Open both PNGs at native size and at 16×16 display size. Inspect the built JAR resource index to confirm both textures and both item models are present. Do not install the JAR into a game instance.
