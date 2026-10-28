package net.guzhenren.tianyi.resource;

import java.util.List;

/** Candidates are evidence, not permission to generate. C must verify registration and opt in.
 * Evidence: _pack/refs/finite-resources-main.md. No NPC or random beast fallback.
 * Terrain gating (which 流派专属地形 a template may appear in) is pure data: see {@link SiteTerrain}. */
public final class SiteCatalog {
    /** {@code biomeCandidates} is an optional terrain whitelist of real guzhenren biome ids.
     * An empty list means "no terrain gate" (the pre-3.11.0 behaviour); a non-empty list means the
     * candidate position must be inside it, otherwise this template simply does not generate there. */
    public record Candidate(String template, FiniteSite.Kind kind, List<String> blockCandidates,
            List<String> itemCandidates, List<String> guardianCandidates, List<String> biomeCandidates,
            int minimumInitial, int maximumInitial, int lifetimeGrowthBudget,
            boolean verifiedForEnablement, String remainingVerification) {
        public Candidate {
            java.util.Objects.requireNonNull(kind);
            if (!FiniteSite.resourceId(template) || minimumInitial < 1 || maximumInitial < minimumInitial
                    || maximumInitial > 64 || lifetimeGrowthBudget < 0 || lifetimeGrowthBudget > 4
                    || (kind != FiniteSite.Kind.PLANT && lifetimeGrowthBudget != 0)
                    || remainingVerification == null || remainingVerification.isBlank())
                throw new IllegalArgumentException("invalid finite template");
            blockCandidates = List.copyOf(blockCandidates);
            itemCandidates = List.copyOf(itemCandidates);
            guardianCandidates = List.copyOf(guardianCandidates);
            biomeCandidates = List.copyOf(biomeCandidates);
            if (java.util.stream.Stream.of(blockCandidates, itemCandidates, guardianCandidates, biomeCandidates)
                    .flatMap(List::stream).anyMatch(id -> !FiniteSite.resourceId(id))
                    // Six reviewed guardian ids are a *candidate pool*; a site still binds exactly one.
                    || guardianCandidates.size() > 6 || (kind != FiniteSite.Kind.GUARDED && !guardianCandidates.isEmpty()))
                throw new IllegalArgumentException("invalid candidate bindings");
        }
        /** The block actually placed for this template. Ore outcrops whose reviewed loot table drops a
         * different material (元石露头 → 元石) declare the block separately from the material item;
         * a single-entry block list is unambiguous, so no guessing is involved. Returns null when the
         * material is not one of the declared blocks and there is no single block to fall back to. */
        public String blockFor(String material) {
            if (kind == FiniteSite.Kind.GUARDED) return "minecraft:barrel";
            if (material != null && blockCandidates.contains(material)) return material;
            return blockCandidates.size() == 1 ? blockCandidates.get(0) : null;
        }
    }
    public static final List<Candidate> CANDIDATES = List.of(
        new Candidate("tianyi:plant_patch", FiniteSite.Kind.PLANT,
            List.of("guzhenren:gu_caidanxincao", "guzhenren:gucaileimingcao"),
            List.of("guzhenren:gu_caidanxincao", "guzhenren:gucaileimingcao"), List.of(),
            SiteTerrain.PLANT_BIOMES,
            4, 8, 4, true, "Static FlowerBlock/one-item loot verified; runtime registry, loot and environment gate required"),
        new Candidate("tianyi:ore_outcrop", FiniteSite.Kind.ORE,
            List.of("guzhenren:xuanjin"), List.of("guzhenren:xuanjin"), List.of(),
            SiteTerrain.ORE_JIN_BIOMES,
            3, 6, 0, true, "Static plain Block/one-item xuanjin loot verified; runtime registry and loot gate required"),
        new Candidate("tianyi:guarded_cache", FiniteSite.Kind.GUARDED,
            List.of("minecraft:barrel"), List.of("guzhenren:gu_caidanxincao", "guzhenren:gucaileimingcao"),
            List.of("guzhenren:hui_lang", "guzhenren:tu_lang", "guzhenren:hao_dian_lang",
                    "guzhenren:hu", "guzhenren:huoyanxiong", "guzhenren:yao_ya_quan"),
            SiteTerrain.GUARDED_BIOMES,
            4, 8, 0, true, "Static guardian AI/death/soul procedures reviewed for hui_lang/tu_lang only; "
                    + "the four terrain guardians are registry+type verified at runtime, native death loot only"),
        // ---- 3.11.x 扩展：三种真实矿石露头，永不恢复，掉落表在运行时逐块核验 ----
        new Candidate("tianyi:yuanshi_outcrop", FiniteSite.Kind.ORE,
            List.of("guzhenren:fangkuaiyuanshiyuankuang"), List.of("guzhenren:gucaiyuanshi"), List.of(),
            SiteTerrain.ORE_TU_BIOMES,
            3, 6, 0, true, "Static single-pool/single-roll loot fangkuaiyuanshiyuankuang -> gucaiyuanshi verified; "
                    + "runtime registry, block class and loot gate required; block id itself is UNVERIFIED for the java class"),
        new Candidate("tianyi:chilian_outcrop", FiniteSite.Kind.ORE,
            List.of("guzhenren:chilianjinyuankuang"), List.of("guzhenren:chilianjinyuankuang"), List.of(),
            SiteTerrain.ORE_JIN_BIOMES,
            3, 6, 0, true, "Static single-pool/single-roll loot dropping itself verified; "
                    + "runtime registry, block class and loot gate required; block java class UNVERIFIED"),
        new Candidate("tianyi:wending_outcrop", FiniteSite.Kind.ORE,
            List.of("guzhenren:wen_ding_shi_kuang"), List.of("guzhenren:wen_ding_shi"), List.of(),
            SiteTerrain.ORE_LIAN_BIOMES,
            3, 6, 0, true, "Static single-pool/single-roll loot wen_ding_shi_kuang -> wen_ding_shi verified; "
                    + "runtime registry, block class and loot gate required; block java class UNVERIFIED"));
    private SiteCatalog() {}
    public static Candidate candidate(String id) {
        return CANDIDATES.stream().filter(c -> c.template().equals(id)).findFirst().orElse(null);
    }
    /** Pure terrain→template lookup: which templates may appear in this biome at all.
     * Enablement (config) and evidence (verifiedForEnablement) are deliberately NOT applied here,
     * so this stays a deterministic, offline-testable mapping. */
    public static List<Candidate> templatesForBiome(String biomeId) {
        return CANDIDATES.stream().filter(c -> SiteTerrain.biomeAllowed(c.biomeCandidates(), biomeId)).toList();
    }
}
