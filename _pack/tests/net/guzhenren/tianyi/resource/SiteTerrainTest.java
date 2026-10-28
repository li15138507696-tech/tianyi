package net.guzhenren.tianyi.resource;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SiteTerrain} 与「地形 → 模板」纯映射的离线确定性测试。
 *
 * <p>覆盖：群系白名单判定（空=不设门槛、非空必须命中、未知群系不匹配）、
 * 地形→守护兽映射（含无匹配退回灰狼/土狼）、以及 {@link SiteCatalog#templatesForBiome} 的
 * 「找材料先找地形」结果。这些是<b>决策</b>；真实群系在世界里刷不刷、守护兽会不会生成，
 * 离线一概证明不了（未实机）。
 *
 * <p>运行：{@code python _pack/run_tests.py}（已登记进 SUITES）。
 */
public final class SiteTerrainTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testWhitelist();
        testBiomeIds();
        testPlantBiomes();
        testGuardianMapping();
        testTemplatesForBiome();
        testNoCrossTerrainLeak();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：只验证纯映射；真实群系生成、守护兽 AI/掉落/威压均为未实机。");
        if (fail > 0) System.exit(1);
    }

    /** 白名单为空 = 不设地形门槛（老模板行为不变）；非空必须命中；拿不到群系 id 一律不匹配。 */
    private static void testWhitelist() {
        check("空白名单不设门槛", SiteTerrain.biomeAllowed(List.of(), "guzhenren:leize"));
        check("null 白名单不设门槛", SiteTerrain.biomeAllowed(null, "anything"));
        check("命中白名单", SiteTerrain.biomeAllowed(SiteTerrain.ORE_JIN_BIOMES, SiteTerrain.SUIJIN_QIULIN));
        check("不在白名单", !SiteTerrain.biomeAllowed(SiteTerrain.ORE_JIN_BIOMES, SiteTerrain.LEI_ZE));
        check("未知群系不匹配", !SiteTerrain.biomeAllowed(SiteTerrain.ORE_JIN_BIOMES, ""));
        check("null 群系不匹配", !SiteTerrain.biomeAllowed(SiteTerrain.ORE_JIN_BIOMES, null));
        check("空白群系不匹配", !SiteTerrain.biomeAllowed(SiteTerrain.ORE_JIN_BIOMES, "   "));
    }

    /** 15 个流派地形：id 合法、唯一、都在 guzhenren 命名空间。 */
    private static void testBiomeIds() {
        check("15 个流派地形", SiteTerrain.ALL_GUZHENREN_BIOMES.size() == 15);
        check("地形 id 唯一", SiteTerrain.ALL_GUZHENREN_BIOMES.stream().distinct().count() == 15);
        check("地形 id 全部合法且属 guzhenren", SiteTerrain.ALL_GUZHENREN_BIOMES.stream()
                .allMatch(id -> FiniteSite.resourceId(id) && id.startsWith("guzhenren:")));
        check("守护地不设地形门槛（否则群系没生成就永不出现）", SiteTerrain.GUARDED_BIOMES.isEmpty());
        check("矿石地形互不重叠",
                !SiteTerrain.ORE_JIN_BIOMES.equals(SiteTerrain.ORE_LIAN_BIOMES)
                        && !SiteTerrain.ORE_JIN_BIOMES.equals(SiteTerrain.ORE_TU_BIOMES)
                        && !SiteTerrain.ORE_LIAN_BIOMES.equals(SiteTerrain.ORE_TU_BIOMES));
    }

    /** 植物群系 = 原版 10 个温和群系 + 苍木林海/迷思幻林；老存档的生长地不会因此停长。 */
    private static void testPlantBiomes() {
        check("植物群系 12 个", SiteTerrain.PLANT_BIOMES.size() == 12);
        check("植物群系唯一", SiteTerrain.PLANT_BIOMES.stream().distinct().count() == 12);
        check("含苍木林海", SiteTerrain.PLANT_BIOMES.contains(SiteTerrain.CANGMU_LINHAI));
        check("含迷思幻林", SiteTerrain.PLANT_BIOMES.contains(SiteTerrain.MISI_HUANLIN));
        check("原版平原仍在（老存档不停长）", SiteTerrain.PLANT_BIOMES.contains("minecraft:plains"));
        check("原版云杉林仍在", SiteTerrain.PLANT_BIOMES.contains("minecraft:old_growth_spruce_taiga"));
        check("植物群系 id 全部合法", SiteTerrain.PLANT_BIOMES.stream().allMatch(FiniteSite::resourceId));
        check("雷泽不产蛊材", !SiteTerrain.PLANT_BIOMES.contains(SiteTerrain.LEI_ZE));
    }

    /** 地形 → 守护兽：四道地形各一只真实兽，其余退回灰狼/土狼，结果确定。 */
    private static void testGuardianMapping() {
        List<String> pool = SiteCatalog.candidate("tianyi:guarded_cache").guardianCandidates();
        check("雷泽→豪电狼", "guzhenren:hao_dian_lang".equals(SiteTerrain.guardianFor(SiteTerrain.LEI_ZE, pool)));
        check("碎金丘陵→虎", "guzhenren:hu".equals(SiteTerrain.guardianFor(SiteTerrain.SUIJIN_QIULIN, pool)));
        check("赤焰烬土→火焰熊", "guzhenren:huoyanxiong".equals(SiteTerrain.guardianFor(SiteTerrain.CHIYAN_JINTU, pool)));
        check("万仞千山→垚犽犬", "guzhenren:yao_ya_quan".equals(SiteTerrain.guardianFor(SiteTerrain.WANREN_QIANSHAN, pool)));
        check("聚气岭退回灰狼", "guzhenren:hui_lang".equals(SiteTerrain.guardianFor(SiteTerrain.JUQI_LING, pool)));
        check("未知群系退回灰狼", "guzhenren:hui_lang".equals(SiteTerrain.guardianFor("minecraft:plains", pool)));
        check("null 群系退回灰狼", "guzhenren:hui_lang".equals(SiteTerrain.guardianFor(null, pool)));
        check("老候选表（只有灰狼/土狼）不会引入新兽",
                "guzhenren:hui_lang".equals(SiteTerrain.guardianFor(SiteTerrain.LEI_ZE, SiteTerrain.FALLBACK_GUARDIANS)));
        check("候选为空返回空串", SiteTerrain.guardianFor(SiteTerrain.LEI_ZE, List.of()).isEmpty());
        check("空候选(null)返回空串", SiteTerrain.guardianFor(SiteTerrain.LEI_ZE, null).isEmpty());
        check("结果确定（连续两次一致）",
                SiteTerrain.guardianFor(SiteTerrain.LEI_ZE, pool).equals(SiteTerrain.guardianFor(SiteTerrain.LEI_ZE, pool)));
        check("映射值都在已审阅的 6 只候选里", pool.containsAll(SiteTerrain.GUARDIAN_BY_BIOME.values()));
        check("映射键都是真实地形",
                SiteTerrain.ALL_GUZHENREN_BIOMES.containsAll(SiteTerrain.GUARDIAN_BY_BIOME.keySet()));
        check("hasTerrainGuardian 认得雷泽", SiteTerrain.hasTerrainGuardian(SiteTerrain.LEI_ZE));
        check("hasTerrainGuardian 不认原版平原", !SiteTerrain.hasTerrainGuardian("minecraft:plains"));
    }

    /** 地形 → 模板：不匹配就不生成该模板（不是全局禁用），也不跨地形串味。
     * 守护地是唯一的例外：它不设地形门槛，所以每个群系都允许它（兽再按地形挑）。 */
    private static void testTemplatesForBiome() {
        check("雷泽只有守护地", templates(SiteTerrain.LEI_ZE).equals(List.of("tianyi:guarded_cache")));
        check("碎金丘陵 = 玄金 + 赤炼金 + 守护地",
                templates(SiteTerrain.SUIJIN_QIULIN).equals(List.of(
                        "tianyi:chilian_outcrop", "tianyi:guarded_cache", "tianyi:ore_outcrop")));
        check("万仞千山 = 元石 + 守护地",
                templates(SiteTerrain.WANREN_QIANSHAN).equals(List.of("tianyi:guarded_cache", "tianyi:yuanshi_outcrop")));
        check("炼玄谷地 = 温鼎石 + 守护地",
                templates(SiteTerrain.LIANXUAN_GUDI).equals(List.of("tianyi:guarded_cache", "tianyi:wending_outcrop")));
        check("苍木林海 = 蛊材 + 守护地",
                templates(SiteTerrain.CANGMU_LINHAI).equals(List.of("tianyi:guarded_cache", "tianyi:plant_patch")));
        check("聚气岭只有守护地", templates(SiteTerrain.JUQI_LING).equals(List.of("tianyi:guarded_cache")));
        check("原版平原 = 蛊材 + 守护地",
                templates("minecraft:plains").equals(List.of("tianyi:guarded_cache", "tianyi:plant_patch")));
        check("原版海洋只有守护地", templates("minecraft:ocean").equals(List.of("tianyi:guarded_cache")));
        check("未知群系只剩守护地（不会因为群系未知就整片停摆）",
                templates("").equals(List.of("tianyi:guarded_cache")));
        check("每个地形都有守护地（15 个全覆盖）", SiteTerrain.ALL_GUZHENREN_BIOMES.stream()
                .allMatch(id -> templates(id).contains("tianyi:guarded_cache")));
        check("守护地是唯一无地形门槛的模板", SiteCatalog.CANDIDATES.stream()
                .filter(c -> c.biomeCandidates().isEmpty())
                .allMatch(c -> c.kind() == FiniteSite.Kind.GUARDED));
    }

    /** 反向断言：某个模板永远不会出现在别人家的地形里。 */
    private static void testNoCrossTerrainLeak() {
        check("元石不在碎金丘陵", !templates(SiteTerrain.SUIJIN_QIULIN).contains("tianyi:yuanshi_outcrop"));
        check("赤炼金不在炼玄谷地", !templates(SiteTerrain.LIANXUAN_GUDI).contains("tianyi:chilian_outcrop"));
        check("温鼎石不在万仞千山", !templates(SiteTerrain.WANREN_QIANSHAN).contains("tianyi:wending_outcrop"));
        check("玄金不在万仞千山", !templates(SiteTerrain.WANREN_QIANSHAN).contains("tianyi:ore_outcrop"));
        check("蛊材不在雷泽", !templates(SiteTerrain.LEI_ZE).contains("tianyi:plant_patch"));
        check("矿石模板的地形白名单都非空", SiteCatalog.CANDIDATES.stream()
                .filter(c -> c.kind() == FiniteSite.Kind.ORE)
                .allMatch(c -> !c.biomeCandidates().isEmpty()));
    }

    /** 排序后的模板 id 列表，方便断言集合相等。 */
    private static List<String> templates(String biomeId) {
        List<String> ids = new ArrayList<>();
        for (SiteCatalog.Candidate candidate : SiteCatalog.templatesForBiome(biomeId)) ids.add(candidate.template());
        ids.sort(null);
        return ids;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }
}
