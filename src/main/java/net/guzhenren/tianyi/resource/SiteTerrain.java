package net.guzhenren.tianyi.resource;

import java.util.List;
import java.util.Map;

/**
 * 流派专属地形 ↔ 有限资源点模板/守护兽的<b>纯映射</b>。
 *
 * <h2>为什么单独一个类</h2>
 * 「找材料先找地形」是一条<b>决策</b>，不是世界操作：给它一个群系 id（字符串），
 * 它就该回答「这里允许哪些模板」「这里该放哪只守护兽」。
 * 本类<b>不 import 任何 Minecraft 类型</b>，所以可以在 {@code _pack/run_tests.py} 里
 * 用纯 javac + java 做确定性测试（{@code _pack/tests/.../SiteTerrainTest.java}）；
 * 世界侧（{@link net.guzhenren.tianyi.world.SitePlacement}）只负责把
 * {@code level.getBiome(pos)} 翻成 id 再传进来。
 *
 * <h2>群系 id 的来源</h2>
 * 15 个「流派专属地形」来自本体数据包 {@code data/guzhenren/worldgen/biome/}（namespace {@code guzhenren}），
 * 由主代理静态核实后登记在 {@code _pack/refs/finite-resources-main.md} 的同批证据里。
 * <b>静态 id 不等于实机生成</b>：这些群系在真实世界里到底刷不刷、刷多密，只能进游戏看。
 *
 * <h2>刻意不做的事</h2>
 * <ul>
 *   <li>不做随机替代：群系不匹配就是「这个模板本轮不生成」，绝不换一个群系或换一种材料；</li>
 *   <li>不按玩家刷新、不因为某处稀有就放宽白名单；</li>
 *   <li>不在这里判断「能不能生成」（配置开关、区域预算、保护插件都不属于纯映射）。</li>
 * </ul>
 */
public final class SiteTerrain {
    // ---- 15 个流派专属地形（真实群系 id，namespace guzhenren）----------------
    /** 苍木林海（木道）。 */
    public static final String CANGMU_LINHAI = "guzhenren:cangmulinhai";
    /** 赤焰烬土（炎道）。 */
    public static final String CHIYAN_JINTU = "guzhenren:chiyanjintu";
    /** 诡盗荒地（偷道）。 */
    public static final String GUIDAO_HUANGDI = "guzhenren:guidaohuangdi";
    /** 毁伤泽滩（律道）。 */
    public static final String HUISHANG_ZETAN = "guzhenren:huishangzetan";
    /** 聚气岭（气道）。 */
    public static final String JUQI_LING = "guzhenren:juqiling";
    /** 空宇平原（宇道）。 */
    public static final String KONGYU_PINGYUAN = "guzhenren:kongyupingyuan";
    /** 枯骨血岭（血道）。 */
    public static final String KUGU_XUELING = "guzhenren:kuguxueling";
    /** 雷泽（雷道）。 */
    public static final String LEI_ZE = "guzhenren:leize";
    /** 炼玄谷地（炼道）。 */
    public static final String LIANXUAN_GUDI = "guzhenren:lianxuangudi";
    /** 流年森境（宙道）。 */
    public static final String LIUNIAN_SENJING = "guzhenren:liuniansenjing";
    /** 迷思幻林（智道）。 */
    public static final String MISI_HUANLIN = "guzhenren:misihuanlin";
    /** 碎金丘陵（金道）。 */
    public static final String SUIJIN_QIULIN = "guzhenren:suijinqiulin";
    /** 万仞千山（土道）。 */
    public static final String WANREN_QIANSHAN = "guzhenren:wanrenqianshan";
    /** 万潭疆（水道）。 */
    public static final String WANTAN_JIANG = "guzhenren:wantanjiang";
    /** 乌鬼荒地（魂道）。 */
    public static final String WUGUI_HUANGDI = "guzhenren:wuguihuangdi";

    /** 全部 15 个流派地形（真实群系 id 的唯一定义处；矿石/植物白名单与守护兽映射都从这里取）。 */
    public static final List<String> ALL_GUZHENREN_BIOMES = List.of(
            CANGMU_LINHAI, CHIYAN_JINTU, GUIDAO_HUANGDI, HUISHANG_ZETAN, JUQI_LING, KONGYU_PINGYUAN,
            KUGU_XUELING, LEI_ZE, LIANXUAN_GUDI, LIUNIAN_SENJING, MISI_HUANLIN, SUIJIN_QIULIN,
            WANREN_QIANSHAN, WANTAN_JIANG, WUGUI_HUANGDI);

    /**
     * 蛊材生长地：原版 10 个温和群系 <b>加上</b> 苍木林海/迷思幻林。
     *
     * <p>原版部分与 3.11.0 之前的生长环境判定完全一致（土壤/水/光照/群系四条里的一条），
     * 所以老存档里的生长地不会因为这次扩展而停止生长；新增的两个 guzhenren 群系只是让
     * 「木道/智道的林子里更容易找到蛊材」。
     *
     * <p><b>未实机</b>：guzhenren 群系的表层是不是原版泥土/草方块尚未核实。
     * 若其表层是自定义方块，植物在这些群系里会因土壤判定失败而不生成（保守：不生成，不替代）。
     */
    public static final List<String> PLANT_BIOMES = List.of(
            "minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:flower_forest",
            "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:meadow",
            "minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
            CANGMU_LINHAI, MISI_HUANLIN);

    /** 玄金 / 赤炼金露头：金道地形。 */
    public static final List<String> ORE_JIN_BIOMES = List.of(SUIJIN_QIULIN);
    /** 温鼎石露头：炼道地形。 */
    public static final List<String> ORE_LIAN_BIOMES = List.of(LIANXUAN_GUDI);
    /**
     * 元石露头：土道地形。
     *
     * <p>知识库的写法是「万仞千山或地下」。当前生成器只在<b>地表高度图</b>上取点
     * （{@code Heightmap.Types.WORLD_SURFACE}），没有地下取点策略，也不会为了地下而强加载区块，
     * 所以「地下」这一支<b>没有实现</b>，只保留万仞千山。宁可少一条来源，也不假装支持。
     */
    public static final List<String> ORE_TU_BIOMES = List.of(WANREN_QIANSHAN);

    /** 守护资源地<b>不设</b>地形门槛：木桶可以出现在任何自然地表（与 3.11.0 相同），
     * 兽才按地形挑。若把它也锁进 15 个流派地形，一旦整合包世界里这些数据包群系没生成，
     * 守护地就永远不出现——「启用后必须真的能生成」会落空。 */
    public static final List<String> GUARDED_BIOMES = List.of();

    /** 没有地形匹配时的保守守护兽（3.11.0 已静态审阅的两只）。顺序即优先级，保证确定性。 */
    public static final List<String> FALLBACK_GUARDIANS = List.of("guzhenren:hui_lang", "guzhenren:tu_lang");

    /**
     * 地形 → 守护兽（真实实体 id，知识库 §6.2/§6.3 的流派兽名）。
     *
     * <p>雷泽→豪电狼、碎金丘陵→虎、赤焰烬土→火焰熊、万仞千山→垚犽犬；
     * 其余地形退回 {@link #FALLBACK_GUARDIANS}（灰狼/土狼）。
     */
    public static final Map<String, String> GUARDIAN_BY_BIOME = Map.of(
            LEI_ZE, "guzhenren:hao_dian_lang",
            SUIJIN_QIULIN, "guzhenren:hu",
            CHIYAN_JINTU, "guzhenren:huoyanxiong",
            WANREN_QIANSHAN, "guzhenren:yao_ya_quan");

    private SiteTerrain() {}
    static {
        // 防手滑：地形守护兽的键必须是真实流派地形，值必须是合法资源 id。
        for (var entry : GUARDIAN_BY_BIOME.entrySet()) {
            if (!ALL_GUZHENREN_BIOMES.contains(entry.getKey()))
                throw new IllegalStateException("terrain guardian without a declared terrain: " + entry.getKey());
            if (!FiniteSite.resourceId(entry.getValue()))
                throw new IllegalStateException("invalid terrain guardian id: " + entry.getValue());
        }
    }

    /**
     * 群系白名单判定。
     *
     * <p>白名单为空 = <b>不设地形门槛</b>（老模板保持原行为）；白名单非空就必须命中。
     * 群系 id 缺失（拿不到 biome key）时按不匹配处理——宁可本轮不生成，也不猜。
     */
    public static boolean biomeAllowed(List<String> whitelist, String biomeId) {
        if (whitelist == null || whitelist.isEmpty()) return true;
        return biomeId != null && !biomeId.isBlank() && whitelist.contains(biomeId);
    }

    /**
     * 按地形挑守护兽，结果<b>完全确定</b>（同样输入永远同样输出，方便离线测试与复现）。
     *
     * <p>优先级：地形映射命中且该兽在候选里 → 用它；否则按 {@link #FALLBACK_GUARDIANS}
     * 顺序取第一只仍在候选里的；再否则取候选第一个；候选为空返回空串（调用方据此判为不可用）。
     * 绝不随机、绝不引入候选之外的兽。
     */
    public static String guardianFor(String biomeId, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return "";
        String mapped = biomeId == null ? null : GUARDIAN_BY_BIOME.get(biomeId);
        if (mapped != null && allowed.contains(mapped)) return mapped;
        for (String fallback : FALLBACK_GUARDIANS) {
            if (allowed.contains(fallback)) return fallback;
        }
        return allowed.get(0);
    }

    /** 该地形是否有专属守护兽（用于文档/诊断；不参与生成决策）。 */
    public static boolean hasTerrainGuardian(String biomeId) {
        return biomeId != null && GUARDIAN_BY_BIOME.containsKey(biomeId);
    }
}
