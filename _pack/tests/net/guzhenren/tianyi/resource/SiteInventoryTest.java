package net.guzhenren.tianyi.resource;

/** 有限库存模型回归：守恒、预算、溢出、模板证据。世界行为不在本套件内。 */
public final class SiteInventoryTest {
    public static void main(String[] args) {
        SiteInventory reserved = SiteInventory.reserved(8, 4);
        check(reserved.unmaterialized() == 8 && reserved.totalSupply() == 8, "reservation does not grant physical goods");
        new SiteInventory(8, 1, 4, 0, 5, 3, 1);
        reject(() -> new SiteInventory(8, 0, 0, 0, 9, 0, 0));
        reject(() -> new SiteInventory(8, 5, 4, 13, 0, 0, 0));
        reject(() -> SiteInventory.reserved(-1, 0));
        SiteInventory large = new SiteInventory(Integer.MAX_VALUE, 1, 1, Integer.MAX_VALUE, 1, 0, 0);
        check(large.totalSupply() == 2147483648L, "supply sum must not overflow int");
        check(SiteCatalog.CANDIDATES.size() == 6, "six template contracts (three original + three terrain ore outcrops)");
        check(SiteCatalog.CANDIDATES.stream().allMatch(SiteCatalog.Candidate::verifiedForEnablement), "static evidence reviewed; runtime registry/loot/config still required");
        var plantTemplate=SiteCatalog.candidate("tianyi:plant_patch");
        check(plantTemplate.minimumInitial()==4 && plantTemplate.maximumInitial()==8 && plantTemplate.lifetimeGrowthBudget()==4,
                "plant initial block count and lifetime growth bound");
        var guardedTemplate=SiteCatalog.candidate("tianyi:guarded_cache");
        check(guardedTemplate.blockCandidates().equals(java.util.List.of("minecraft:barrel"))
                && guardedTemplate.itemCandidates().equals(plantTemplate.itemCandidates())
                && guardedTemplate.guardianCandidates().containsAll(SiteTerrain.FALLBACK_GUARDIANS)
                && guardedTemplate.guardianCandidates().containsAll(SiteTerrain.GUARDIAN_BY_BIOME.values())
                && guardedTemplate.guardianCandidates().size() == 6
                && guardedTemplate.minimumInitial()==4 && guardedTemplate.maximumInitial()==8,
                "single real barrel material and an explicit species pool; two bytecode-reviewed, four registry-verified only");
        check(SiteCatalog.candidate("tianyi:ore_outcrop").lifetimeGrowthBudget() == 0, "ore cannot regrow");
        // 三种新露头：方块与材料可以不同（元石露头掉元石），但都必须是永不恢复的真实方块。
        for (String id : new String[]{"tianyi:yuanshi_outcrop", "tianyi:chilian_outcrop", "tianyi:wending_outcrop"}) {
            var ore = SiteCatalog.candidate(id);
            check(ore != null && ore.kind() == FiniteSite.Kind.ORE && ore.lifetimeGrowthBudget() == 0,
                    id + " is a non-regrowing ore template");
            check(ore.blockCandidates().size() == 1 && !ore.biomeCandidates().isEmpty(),
                    id + " declares one real block and a terrain whitelist");
            check(ore.blockFor(ore.itemCandidates().get(0)).equals(ore.blockCandidates().get(0)),
                    id + " resolves its placed block without guessing");
        }
        check(SiteCatalog.candidate("tianyi:yuanshi_outcrop").blockFor("guzhenren:gucaiyuanshi")
                .equals("guzhenren:fangkuaiyuanshiyuankuang"), "yuan-shi outcrop drops a material item, not itself");
        finiteGrowthBehavior();
        System.out.println("SiteInventoryTest: PASS (finite stock model; no world verification)");
    }
    private static void finiteGrowthBehavior() {
        SiteInventory plant = SiteInventory.reserved(8, 4).growVerified(1);
        check(plant.verifiedPlantGrowth() == 1 && plant.unmaterialized() == 9
                && plant.physicalRemaining() == 0, "verified growth is finite unmaterialized stock, never a grant");
        plant = plant.growVerified(3);
        check(plant.totalSupply() == 12 && plant.verifiedPlantGrowth() == 4, "plant consumes lifetime budget");
        final SiteInventory exhausted = plant;
        reject(() -> exhausted.growVerified(1));
        reject(() -> SiteInventory.reserved(6, 0).growVerified(1));
        reject(() -> SiteInventory.reserved(8, 4).growVerified(-1));
        reject(() -> SiteInventory.reserved(8, 4).growVerified(0));
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid inventory accepted");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
