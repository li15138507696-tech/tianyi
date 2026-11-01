package net.guzhenren.tianyi.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 宿命蛊取得路径（3.14.2）的离线测试：<b>材料数量、幂等键、前置判定顺序、缺口文案、参数解析</b>。
 *
 * <p>这里能证明的只有「判定的顺序与口径」；真实群系、真实守护兽死亡事件、真实背包扣除、
 * 真实掉落物都只能在游戏里看见（见 {@code docs/3.14.2}，未实机）。
 *
 * <p>运行：{@code python _pack/run_tests.py}（已登记进 SUITES）。
 */
public final class DestinySeekTest {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        testCosts();
        testShortfall();
        testMaterialsComplete();
        testKey();
        testRejectOrder();
        testTerrainMapping();
        testNote();
        testSiteArgs();
        testTemplateWiring();

        System.out.println();
        System.out.println("结果：" + pass + " 项通过，" + fail + " 项失败");
        System.out.println("覆盖边界：只验证纯判定/材料/幂等键/参数解析；"
                + "真实群系、真实守护兽死亡、真实背包扣除与真实掉落均为未实机。");
        if (fail > 0) System.exit(1);
    }

    /** 价目表：三种真实材料、数量为正、名字有中文，且都在本体既有物品里出现过。 */
    private static void testCosts() {
        check("材料共 3 种", DestinySeek.COSTS.size() == 3);
        check("材料 id 合法", DestinySeek.COSTS.stream().allMatch(c -> FiniteSite.resourceId(c.item())));
        check("材料数量都在 1..64", DestinySeek.COSTS.stream().allMatch(c -> c.count() >= 1 && c.count() <= 64));
        check("材料都有中文名", DestinySeek.COSTS.stream().allMatch(c -> !c.cn().isBlank()));
        check("材料 id 不重复",
                DestinySeek.COSTS.stream().map(DestinySeek.Cost::item).distinct().count() == 3);
        // 取得价目必须是「已经存在的东西」：两味蛊材来自守护资源地，元石来自元石露头。
        SiteCatalog.Candidate cache = SiteCatalog.candidate("tianyi:guarded_cache");
        SiteCatalog.Candidate yuanshi = SiteCatalog.candidate("tianyi:yuanshi_outcrop");
        check("守护资源地模板存在", cache != null && cache.kind() == FiniteSite.Kind.GUARDED);
        check("元石露头模板存在", yuanshi != null);
        check("两味蛊材确实是守护资源地的特产", cache != null
                && cache.itemCandidates().contains("guzhenren:gu_caidanxincao")
                && cache.itemCandidates().contains("guzhenren:gucaileimingcao"));
        check("元石确实是元石露头的产物", yuanshi != null
                && yuanshi.itemCandidates().contains("guzhenren:gucaiyuanshi"));
        check("价目里的材料都在资源点目录里出现过", allCostItemsAreCatalogItems());
        check("describeCosts 与价目同长度", DestinySeek.describeCosts().size() == DestinySeek.COSTS.size());
        check("describeCosts 带数量", DestinySeek.describeCosts().get(0).contains("×"));
    }

    private static boolean allCostItemsAreCatalogItems() {
        List<String> known = new ArrayList<>();
        for (SiteCatalog.Candidate candidate : SiteCatalog.CANDIDATES) known.addAll(candidate.itemCandidates());
        for (DestinySeek.Cost cost : DestinySeek.COSTS) {
            if (!known.contains(cost.item())) return false;
        }
        return true;
    }

    /** 缺口文案：够→空串；不够→按 item 列出「还差：名字 ×N」，多味用顿号连接。 */
    private static void testShortfall() {
        Map<String, Integer> none = new LinkedHashMap<>();
        for (DestinySeek.Cost cost : DestinySeek.COSTS) none.put(cost.item(), cost.count());
        check("材料齐全时没有缺口", DestinySeek.shortfall(DestinySeek.COSTS, id -> none.getOrDefault(id, 0)).isEmpty());

        Map<String, Integer> partial = new LinkedHashMap<>(none);
        partial.put("guzhenren:gu_caidanxincao", 1);
        String text = DestinySeek.shortfall(DestinySeek.COSTS, id -> partial.getOrDefault(id, 0));
        check("缺一味时给出名字", text.contains("丹心草"));
        check("缺一味时给出差数", text.contains("×2"));
        check("括号前缀是「还差：」", text.startsWith("还差："));

        Map<String, Integer> empty = new LinkedHashMap<>();
        String all = DestinySeek.shortfall(DestinySeek.COSTS, id -> empty.getOrDefault(id, 0));
        check("全空时缺口含三种", countOccurrences(all, "×") == 3);
        check("全空时用顿号分隔", all.contains("、"));
        check("全空时数量正确", all.contains("×6"));

        check("缺一件也算缺", !DestinySeek.shortfall(DestinySeek.COSTS, id -> {
            int base = none.getOrDefault(id, 0);
            return id.equals("guzhenren:gucaiyuanshi") ? base - 1 : base;
        }).isEmpty());
        check("数量为零不被负数骗过", DestinySeek.shortfall(DestinySeek.COSTS, id -> -100).contains("还差："));
        check("null 取值器不炸", DestinySeek.shortfall(DestinySeek.COSTS, null).isEmpty());
    }

    private static void testMaterialsComplete() {
        check("刚好够算够", DestinySeek.materialsComplete(DestinySeek.COSTS, id -> 64));
        check("少一个是少", !DestinySeek.materialsComplete(DestinySeek.COSTS,
                id -> id.equals("guzhenren:gu_caidanxincao") ? 2 : 64));
        check("零个是少", !DestinySeek.materialsComplete(DestinySeek.COSTS, id -> 0));
        check("null 取值器视为不够", !DestinySeek.materialsComplete(DestinySeek.COSTS, null));
        // 与 shortfall 同源：不可能出现「文案说够、判定说不够」
        boolean same = true;
        for (int n = 0; n <= 8; n++) {
            final int have = n;
            boolean complete = DestinySeek.materialsComplete(DestinySeek.COSTS, id -> have);
            boolean silent = DestinySeek.shortfall(DestinySeek.COSTS, id -> have).isEmpty();
            if (complete != silent) same = false;
        }
        check("materialsComplete 与 shortfall 同源", same);
    }

    /** 幂等键：同点同人稳定、不同人/不同点不同、缺参直接抛。 */
    private static void testKey() {
        UUID site = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String k1 = DestinySeek.key(site, alice);
        String k2 = DestinySeek.key(site, alice);
        check("同点同人键稳定", k1.equals(k2));
        check("键里有站点", k1.contains(site.toString()));
        check("键里有玩家", k1.contains(alice.toString()));
        check("换人换键", !k1.equals(DestinySeek.key(site, bob)));
        check("换点换键", !k1.equals(DestinySeek.key(UUID.randomUUID(), alice)));
        check("键带命名空间前缀", k1.startsWith("suminggu|"));
        boolean threw = false;
        try {
            DestinySeek.key(null, alice);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check("缺点站直接抛（不猜）", threw);
        boolean threw2 = false;
        try {
            DestinySeek.key(site, null);
        } catch (IllegalArgumentException expected) {
            threw2 = true;
        }
        check("缺玩家直接抛（不猜）", threw2);
    }

    /** 前置判定的顺序：越基础越先报，玩家一次只看得到最该补的那一条。 */
    private static void testRejectOrder() {
        DestinySeek.Facts ok = allTrue();
        check("全满足 → NONE", DestinySeek.firstReject(ok) == DestinySeek.Reject.NONE);
        check("null → NO_PLAYER", DestinySeek.firstReject(null) == DestinySeek.Reject.NO_PLAYER);
        check("无玩家", DestinySeek.firstReject(new DestinySeek.Facts(false, true, true, true, true,
                true, true, true, true, true, false)) == DestinySeek.Reject.NO_PLAYER);
        check("无站点", DestinySeek.firstReject(new DestinySeek.Facts(true, false, true, true, true,
                true, true, true, true, true, false)) == DestinySeek.Reject.NO_SITE);
        check("非守护地", DestinySeek.firstReject(new DestinySeek.Facts(true, true, false, true, true,
                true, true, true, true, true, false)) == DestinySeek.Reject.NOT_GUARDED);
        check("非流派地形", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, false, true,
                true, true, true, true, true, false)) == DestinySeek.Reject.TERRAIN_NOT_STREAM);
        check("地形与兽不匹配", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, false,
                true, true, true, true, true, false)) == DestinySeek.Reject.TERRAIN_GUARDIAN_MISMATCH);
        check("没绑兽", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                false, true, true, true, true, false)) == DestinySeek.Reject.GUARDIAN_MISSING);
        check("不是本人打死的", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, false, true, true, true, false)) == DestinySeek.Reject.GUARDIAN_NOT_SLAIN);
        check("材料不足", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, true, false, true, true, false)) == DestinySeek.Reject.MATERIALS_MISSING);
        check("账本不可写", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, true, true, false, true, false)) == DestinySeek.Reject.CUSTODY_LOCKED);
        check("已有正品", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, true, true, true, false, false)) == DestinySeek.Reject.CUSTODY_PRESENT);
        check("已领取过", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, true, true, true, true, true)) == DestinySeek.Reject.ALREADY_CLAIMED);
        // 顺序：同时缺好几项时，先报最基础的那一条，不许「随机挑一条」。
        check("无站点优先于材料", DestinySeek.firstReject(new DestinySeek.Facts(true, false, false, false,
                false, false, false, false, false, false, true)) == DestinySeek.Reject.NO_SITE);
        check("守护兽优先于材料", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, false, false, false, false, true)) == DestinySeek.Reject.GUARDIAN_NOT_SLAIN);
        check("材料优先于已有正品", DestinySeek.firstReject(new DestinySeek.Facts(true, true, true, true, true,
                true, true, false, true, false, true)) == DestinySeek.Reject.MATERIALS_MISSING);
        // 每个结论都必须有可读原因，不能有 null
        for (DestinySeek.Reject reject : DestinySeek.Reject.values()) {
            String text = DestinySeek.explain(reject);
            if (text == null || text.isBlank()) {
                check("原因文案缺失：" + reject, false);
            }
        }
        check("原因文案齐全", true);
        check("原因里不含坐标/评分字样", DestinySeek.explain(DestinySeek.Reject.NO_SITE).indexOf("评分") < 0);
    }

    private static DestinySeek.Facts allTrue() {
        return new DestinySeek.Facts(true, true, true, true, true, true, true, true, true, true, false);
    }

    /** 地形映射：四道流派地形各认自己那只兽；没有映射的地形一律不认。 */
    private static void testTerrainMapping() {
        check("雷泽→豪电狼", DestinySeek.terrainGuardianMatches(SiteTerrain.LEI_ZE, "guzhenren:hao_dian_lang"));
        check("碎金丘陵→虎", DestinySeek.terrainGuardianMatches(SiteTerrain.SUIJIN_QIULIN, "guzhenren:hu"));
        check("赤焰烬土→火焰熊", DestinySeek.terrainGuardianMatches(SiteTerrain.CHIYAN_JINTU, "guzhenren:huoyanxiong"));
        check("万仞千山→垚犽犬", DestinySeek.terrainGuardianMatches(SiteTerrain.WANREN_QIANSHAN, "guzhenren:yao_ya_quan"));
        check("雷泽认错兽不行", !DestinySeek.terrainGuardianMatches(SiteTerrain.LEI_ZE, "guzhenren:hu"));
        check("原版平原没有映射", !DestinySeek.terrainGuardianMatches("minecraft:plains", "guzhenren:hui_lang"));
        check("空群系不认", !DestinySeek.terrainGuardianMatches("", "guzhenren:hu"));
        check("null 群系不认", !DestinySeek.terrainGuardianMatches(null, "guzhenren:hu"));
        check("空守护兽不认", !DestinySeek.terrainGuardianMatches(SiteTerrain.LEI_ZE, ""));
        check("候选池里有雷泽那只", DestinySeek.terrainGuardianInPool(SiteTerrain.LEI_ZE));
        check("候选池里有赤焰那只", DestinySeek.terrainGuardianInPool(SiteTerrain.CHIYAN_JINTU));
        check("候选池里没有原版平原的映射", !DestinySeek.terrainGuardianInPool("minecraft:plains"));
        // 四道流派地形都在 15 个地形清单里（防止以后改 id 改出孤儿映射）
        for (String biome : SiteTerrain.GUARDIAN_BY_BIOME.keySet()) {
            if (!SiteTerrain.ALL_GUZHENREN_BIOMES.contains(biome)) {
                check("映射地形不是流派地形：" + biome, false);
            }
        }
        check("映射地形都是流派地形", true);
    }

    /** note：空白无效、两端去空白、超长截断。 */
    private static void testNote() {
        check("null → 空", DestinySeek.note(null).isEmpty());
        check("空白 → 空", DestinySeek.note("   ").isEmpty());
        check("换行也当空白", DestinySeek.note("\n\t ").isEmpty());
        check("正常保留", "核对过实物".equals(DestinySeek.note("  核对过实物  ")));
        String longText = "核".repeat(DestinySeek.NOTE_MAX + 50);
        check("超长截断到上限", DestinySeek.note(longText).length() == DestinySeek.NOTE_MAX);
        check("刚好上限不截断", DestinySeek.note("x".repeat(DestinySeek.NOTE_MAX)).length() == DestinySeek.NOTE_MAX);
    }

    /** 命令参数解析：完整 id、唯一前缀、歧义前缀、未命中、大小写。 */
    private static void testSiteArgs() {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        List<String> known = List.of(a, b);
        check("完整 id 命中", a.equals(SiteCommandArgs.siteId(a, known)));
        check("唯一前缀命中", a.equals(SiteCommandArgs.siteId(a.substring(0, 8), known)));
        check("大小写不敏感", a.equals(SiteCommandArgs.siteId(a.substring(0, 8).toUpperCase(), known)));
        check("两端空白被剪掉", a.equals(SiteCommandArgs.siteId("  " + a.substring(0, 8) + "  ", known)));
        check("未命中返回空", SiteCommandArgs.siteId("zzzzzzzz", known).isEmpty());
        check("空 token 返回空", SiteCommandArgs.siteId("", known).isEmpty());
        check("null token 返回空", SiteCommandArgs.siteId(null, known).isEmpty());
        check("null 清单返回空", SiteCommandArgs.siteId(a, null).isEmpty());
        // 歧义：两条 id 共享前缀时必须拒绝，绝不挑一个动手
        List<String> twins = List.of("abcdef01-1111-1111-1111-111111111111",
                "abcdef02-2222-2222-2222-222222222222");
        check("歧义前缀返回空", SiteCommandArgs.siteId("abcdef0", twins).isEmpty());
        check("歧义可被识别", SiteCommandArgs.ambiguous("abcdef0", twins));
        check("唯一前缀不算歧义", !SiteCommandArgs.ambiguous("abcdef01", twins));
        check("完整 id 在歧义下仍然精确命中",
                !SiteCommandArgs.siteId(twins.get(1), twins).isEmpty());
        // 短 id 显示
        check("短 id 取前 8 位", SiteCommandArgs.shortId(a).equals(a.substring(0, 8)));
        check("短 id 不动短串", "abc".equals(SiteCommandArgs.shortId("abc")));
        check("短 id 的 null 变空串", SiteCommandArgs.shortId(null).isEmpty());
        check("列表上限是 20", SiteCommandArgs.MAX_LIST == 20);
    }

    /** 模板与登记：取得路径认的就是 tianyi:guarded_cache，且它绑着六只候选守护兽。 */
    private static void testTemplateWiring() {
        check("取得模板 id", "tianyi:guarded_cache".equals(DestinySeek.TEMPLATE));
        SiteCatalog.Candidate candidate = SiteCatalog.candidate(DestinySeek.TEMPLATE);
        check("模板在目录里", candidate != null);
        check("模板是守护地", candidate != null && candidate.kind() == FiniteSite.Kind.GUARDED);
        check("候选守护兽 6 只", candidate != null && candidate.guardianCandidates().size() == 6);
        check("四道地形守护兽都在候选里", candidate != null
                && candidate.guardianCandidates().containsAll(SiteTerrain.GUARDIAN_BY_BIOME.values()));
        check("模板不自带成长预算（守护地不重生）", candidate != null && candidate.lifetimeGrowthBudget() == 0);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
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
