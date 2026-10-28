# 宿命蛊铁砧修复玩法 Implementation Plan

> **执行方式：** 按任务逐项实施；复选框记录已完成的工作。

**Goal:** Add a persistent, resource-backed anvil repair loop for the authentic Destiny Gu item.

**Architecture:** `TianyiEvents` detects the main-hand interaction and delegates it to a focused server-side `DestinyRepairService`. The service validates the world custody ledger and held credential before consuming base-game materials and advancing the existing `DestinyGu.repairProgress`; the fourth contribution records a completion transaction and refreshes the held item’s scar state.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1, existing SavedData and item-custody code.

## Global Constraints

- Keep server authority for custody, costs, progress and completion.
- Preserve the existing unique-vessel ledger; never accept a copy or stale credential as the repair item.
- No new block, item, menu, payload, mixin, or mod dependency.
- Bump version to 3.13.3 and keep `version.properties` and `neoforge.mods.toml` aligned.
- Do not add or run automated tests; this workspace requires user request before tests.
- Do not install into a game instance or claim an in-game verification.

---

### Task 1: Implement the repair interaction

**Files:**
- Create: `src/main/java/net/guzhenren/tianyi/world/DestinyRepairService.java`
- Modify: `src/main/java/net/guzhenren/tianyi/TianyiEvents.java`
- Modify: `src/main/java/net/guzhenren/tianyi/item/DestinyGuItem.java`
- Modify: `src/main/resources/assets/tianyi/lang/zh_cn.json`
- Modify: `src/main/resources/assets/tianyi/lang/en_us.json`

**Interfaces:**
- Service entry: `public static boolean interact(ServerPlayer player, BlockPos pos, InteractionHand hand)`; returns `true` only when a main-hand Destiny Gu was used on an anvil family block and the vanilla interaction must be intercepted.
- Consumes: `DestinyCustodyService.reconcile`, `Destiny.DestinyGu.setRepairProgress`, `completeRepair`, `WillState.setDirty`, and custody refresh through `onPickup`.
- Produces: one resource contribution per handled click, with `guzhenren:gucaiyuanshi ×4` and `minecraft:amethyst_shard ×4` for a 0.25 progress increase.

- [x] Add the focused server-side service. Reject before consuming unless the block, stage, SavedData mutation state, full-inventory custody inspection, exact held authentic credential, and required registry items all pass.
- [x] Count ingredients before changing anything. In survival, remove the exact counts only after validation; in creative, skip removal. Then increase progress by 0.25, cap at 1.0, call `setDirty`, and message the contributor.
- [x] On the fourth contribution, create a UUID transaction ID, call `completeRepair`, verify the stage became `BODY_WHOLE`, call `setDirty`, and run `DestinyCustodyService.onPickup` so the held item loses its scarred display flag.
- [x] Wire the main-hand block interaction before other right-click handlers. Cancel vanilla anvil UI only when the service reports the Destiny Gu repair was handled.
- [x] Add a concise tooltip hint describing the anvil and per-contribution materials in Chinese and English; each contribution reports current progress.
- [x] Review every early return to ensure no inventory mutation occurs before all preconditions are known.

### Task 2: Version and package

**Files:**
- Modify: `version.properties`
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`
- Output: `build/libs/tianyi-3.13.3.jar`

- [x] Advance patch version to 3.13.3 in project metadata and JAR manifest.
- [x] Compile all production Java sources with JDK 21 `-source 21 -target 21` (exit 0; 234 classes emitted).
- [x] Package the compiled classes and resources with PowerShell/.NET because Python is unavailable.
- [x] Inspect the final JAR for one copy of each new/changed class and resource, both model variants, both textures, and matching 3.13.3 metadata; all entries decompressed and source hashes matched.
- [x] Record that automated tests and in-game launch were not run.
