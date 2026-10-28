# Four Legendary Gu Assets Implementation Plan

> **执行方式：** 按任务逐项实施，可用 `superpowers:executing-plans`；用复选框（`- [ ]`）跟踪进度。

**Goal:** Deliver four canon-aware 16×16 pixel icons, editable Blockbench models, and Minecraft item model JSONs in an isolated project asset directory.

**Architecture:** Keep all deliverables under `art/legendary-gu/` so they can be reviewed and imported without touching registered items or runtime behavior. Keep each approved 16×16 icon separate; give the low-poly Blockbench model and matching item JSON a separate detailed 128×128 texture so model-view details stay sharp. README and previews carry the canon-versus-art-addition notes.

**Tech Stack:** Built-in image generation for art drafts, PNG RGBA pixel assets, Blockbench `.bbmodel`, Minecraft item model JSON, PowerShell/.NET for file and image metadata inspection.

**Spec:** `docs/superpowers/specs/2026-10-26-legendary-gu-assets-design.md`

## Global Constraints

- Four final item icon textures must be transparent 16×16 PNGs; four separate model textures must be transparent 128×128 PNGs.
- Visual style is the user-selected water-ink and three-qi direction; keep the insect silhouette readable at native size.
- Label every invented Wisdom Gu or Strength Gu body detail as an art addition, not a canon fact.
- Use the weakened, withered-wing Spring Autumn Cicada look and the black-and-white, red-scarred Fate Gu look described in the spec.
- Keep all files inside `art/legendary-gu/`; do not change Java, item registration, gameplay, existing resources, or dependencies.
- Do not add or run automated tests; use manual asset and resource checks only.

## Review Focus

- Native-size readability: inspect each icon at 16×16 and enlarged with nearest-neighbor scaling; all four insect identities must remain distinguishable.
- Alpha correctness: verify each PNG is RGBA and has transparent pixels around the icon, with no opaque card/background.
- Canon boundaries: verify Wisdom Gu and Strength Gu additions are marked as invented, and the red Fate Gu scar is identified as its damaged appearance.
- Model/resource references: verify each item JSON references its matching 128×128 model texture and the Blockbench source uses a matching UV canvas; keep the 16×16 icon files intact.
- Isolation: verify all outputs remain in `art/legendary-gu/` and no existing `src/main/resources/assets/tianyi` or Java files were modified.

---

### Task 1: Create the isolated asset package and provenance README

**Files:**
- Create: `art/legendary-gu/README.md`
- Create directories: `art/legendary-gu/textures/legendary_gu/`, `art/legendary-gu/blockbench/`, `art/legendary-gu/models/item/legendary_gu/`, `art/legendary-gu/preview/`

**Interfaces:**
- Produces: canonical file names, palette notes, and source-versus-art-addition labels consumed by Tasks 2–4.

- [x] **Step 1: Create the README and output folders** with exact names `chunqiuchan`, `zhihuigu`, `lilianggu`, and `suminggu`.
- [x] **Step 2: Record the approved visual specification** for each Gu, including Spring Autumn Cicada's weakened state and Fate Gu's red scar.
- [x] **Step 3: Mark Wisdom Gu and Strength Gu body shapes, body colors, and all extra aura colors as art additions.** Only record claims supported by the design spec as canon evidence.
- [x] **Step 4: Record the isolation rule** that these are portable assets and do not register or overwrite in-game items.
- [x] **Step 5: Review the README against the design spec** and confirm every goal, non-goal, and canon boundary is represented.

### Task 2: Produce four final pixel icons and previews

**Files:**
- Create: `art/legendary-gu/textures/legendary_gu/chunqiuchan.png`
- Create: `art/legendary-gu/textures/legendary_gu/zhihuigu.png`
- Create: `art/legendary-gu/textures/legendary_gu/lilianggu.png`
- Create: `art/legendary-gu/textures/legendary_gu/suminggu.png`
- Create: `art/legendary-gu/preview/gu-icons-overview.png`
- Create: `art/legendary-gu/preview/gu-icons-16x-preview.png`

**Interfaces:**
- Consumes: Task 1's names, visual notes, and canon labels.
- Produces: four RGBA 16×16 textures used by Task 3.

- [x] **Step 1: Generate pixel-art drafts** from the approved comparison direction, using one stable silhouette per Gu and no background plate.
- [x] **Step 2: Refine each icon to the exact 16×16 grid** while preserving the selected palette and the silhouette rules in the README.
- [x] **Step 3: Build the overview and native-size previews** from the final four PNGs; use nearest-neighbor enlargement only for the enlarged view.
- [x] **Step 4: Inspect all four icons at native size** and revise any pixel that obscures the cicada wings, Wisdom Gu core, Strength Gu armor silhouette, or Fate Gu spider/scar.
- [x] **Step 5: Inspect file metadata** and confirm every final texture is exactly 16×16 RGBA with transparency; confirm previews contain all four icons.

### Task 3: Build four Blockbench sources and item model JSONs

**Files:**
- Create: `art/legendary-gu/textures/legendary_gu/model/chunqiuchan.png`
- Create: `art/legendary-gu/textures/legendary_gu/model/zhihuigu.png`
- Create: `art/legendary-gu/textures/legendary_gu/model/lilianggu.png`
- Create: `art/legendary-gu/textures/legendary_gu/model/suminggu.png`
- Create: `art/legendary-gu/blockbench/chunqiuchan.bbmodel`
- Create: `art/legendary-gu/blockbench/zhihuigu.bbmodel`
- Create: `art/legendary-gu/blockbench/lilianggu.bbmodel`
- Create: `art/legendary-gu/blockbench/suminggu.bbmodel`
- Create: `art/legendary-gu/models/item/legendary_gu/chunqiuchan.json`
- Create: `art/legendary-gu/models/item/legendary_gu/zhihuigu.json`
- Create: `art/legendary-gu/models/item/legendary_gu/lilianggu.json`
- Create: `art/legendary-gu/models/item/legendary_gu/suminggu.json`

**Interfaces:**
- Consumes: Task 2's four icon textures and four detailed model textures.
- Produces: editable, grouped low-poly insect models and standalone Minecraft item model exports referencing the matching 128×128 model textures.

- [x] **Step 1: Create four 128×128 detailed model textures** that retain the established silhouettes and water-ink/three-qi style; keep the approved 16×16 icon files separate and unchanged.
- [x] **Step 2: Create the four Blockbench model sources** with named anatomical groups, 16-unit overall bounds, 0.5-unit geometry subdivisions, and 128×128 UVs matching their embedded model textures.
- [x] **Step 3: Export one Minecraft item model JSON per Gu** into `art/legendary-gu/models/item/legendary_gu/`, using texture ids `tianyi:item/legendary_gu/model/<name>`; document that the texture and model subtrees map to `assets/tianyi/` if integrated later, without placing files in runtime resources now.
- [x] **Step 4: Inspect each JSON** and confirm its texture reference matches the corresponding 128×128 PNG, its geometry matches the Blockbench source, and the file parses as JSON.
- [x] **Step 5: Attempt to open a current `.bbmodel`** in Blockbench; system denied app launch, so verify all four files structurally, confirm each embeds its matching 128×128 PNG, and record the remaining interactive review limit.

### Task 4: Package the final previews and perform the manual handoff review

**Files:**
- Modify: `art/legendary-gu/README.md`
- Review: all files under `art/legendary-gu/`

**Interfaces:**
- Consumes: Tasks 1–3 outputs.
- Produces: complete, self-describing portable asset package ready for user review or later mod integration.

- [x] **Step 1: Add the final file inventory and import notes** to the README, including the distinction between canon details and invented visual details.
- [x] **Step 2: Compare each final icon and 128×128 model-texture preview** to the approved style direction and confirm all four identities remain clear.
- [x] **Step 3: Confirm the existing runtime project is untouched** by checking that no Java files or `src/main/resources/assets/tianyi` files changed.
- [x] **Step 4: Report the absolute paths** for the asset folder, both previews, Blockbench sources, and item model JSONs; state that no automated tests were added or run.
