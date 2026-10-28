package net.guzhenren.tianyi.world;

import java.util.HashMap;
import java.util.Map;

import net.guzhenren.tianyi.resource.SiteBudget;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Config contract; C registers SPEC and performs all placement/protection checks.
 * Admin whitelist is an explicit overworld rectangle in chunk coordinates.
 * It may resolve UNKNOWN only; DENY must always remain DENY.
 * The numeric site policy lives in {@link SiteBudget} (pure, offline-testable); these
 * constants only re-export it so no second copy of "64" or "72000" can drift.
 *
 * <p>Enablement is two layers: the per-kind switch (老开关，默认 false) <b>and</b>, for templates that
 * declare their own flag, a per-template switch that also defaults to false. A template without a
 * flag is controlled by its kind switch alone, so the pre-3.11.x behaviour of the three original
 * templates is unchanged.</p> */
public final class SiteSettings {
    /** 服务端配置文件名。{@code TianyiMod} 用它注册 SPEC。 */
    public static final String CONFIG_FILE = "tianyi-sites-server.toml";
    public static final int CHECK_INTERVAL_TICKS = SiteBudget.CHECK_INTERVAL_TICKS,
            CANDIDATES_PER_CHECK = SiteBudget.CANDIDATES_PER_CHECK,
            PLACEMENTS_PER_CHECK = SiteBudget.PLACEMENTS_PER_CHECK,
            REGION_CHUNKS = SiteBudget.REGION_CHUNKS, GLOBAL_LIMIT = SiteBudget.GLOBAL_LIMIT,
            MIN_PLAYER_DISTANCE = 48, MAX_PLAYER_DISTANCE = 128,
            PLANT_GROWTH_LOADED_TICKS = (int) SiteBudget.GROWTH_TICKS,
            AVOIDANCE_RADIUS = 32, MIN_SPAWN_DISTANCE = 128;
    /** 新模板的独立开关：模板 id → 配置项。没有登记 = 只看 kind 开关（保持老模板行为）。 */
    private static final Map<String, ModConfigSpec.BooleanValue> TEMPLATE_FLAGS = new HashMap<>();
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED, ADMIN_REGION, STRICT_REGION, PLANT, ORE, GUARDED;
    private static final ModConfigSpec.IntValue MIN_X, MAX_X, MIN_Z, MAX_Z;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Enable finite sites only after template registration and evidence checks pass.").define("enabled", false);
        PLANT = b.define("plantEnabled", false);
        ORE = b.define("oreEnabled", false);
        GUARDED = b.define("guardedEnabled", false);
        // 三种新增矿石露头：默认关闭。打开 oreEnabled 之后还要单独打开对应模板才会生成，
        // 这样整合服可以先只放已验证的玄金，确认无副作用后再逐个开新矿。
        TEMPLATE_FLAGS.put("tianyi:yuanshi_outcrop", b.comment("元石露头（万仞千山）。永不恢复。默认关闭。")
                .define("yuanshiOutcropEnabled", false));
        TEMPLATE_FLAGS.put("tianyi:chilian_outcrop", b.comment("赤炼金露头（碎金丘陵）。永不恢复。默认关闭。")
                .define("chilianOutcropEnabled", false));
        TEMPLATE_FLAGS.put("tianyi:wending_outcrop", b.comment("温鼎石露头（炼玄谷地）。永不恢复。默认关闭。")
                .define("wendingOutcropEnabled", false));
        STRICT_REGION = b.comment("Require every physical position to be inside the explicit admin rectangle. Optional; ordinary natural overworld terrain does not need a third-party protection API.")
                .define("strictRegionMode", false);
        ADMIN_REGION = b.comment("Explicit admin permission for UNKNOWN protection in the overworld chunk rectangle. Never bypass DENY, collision, buildings or other placement checks.")
                .define("adminRegionEnabled", false);
        MIN_X = b.defineInRange("adminMinChunkX", 0, -1875000, 1875000);
        MAX_X = b.defineInRange("adminMaxChunkX", 0, -1875000, 1875000);
        MIN_Z = b.defineInRange("adminMinChunkZ", 0, -1875000, 1875000);
        MAX_Z = b.defineInRange("adminMaxChunkZ", 0, -1875000, 1875000);
        SPEC = b.build();
    }
    private SiteSettings() {}
    public static boolean enabled() { return SPEC.isLoaded() && ENABLED.get(); }
    /** 总开关 + 至少一个模板开：服务端每 200 刻才有必要看世界。默认全 false，什么都不生成。 */
    public static boolean active() {
        return SPEC.isLoaded() && ENABLED.get() && (PLANT.get() || ORE.get() || GUARDED.get());
    }
    public static boolean plantEnabled() { return enabled() && PLANT.get(); }
    public static boolean oreEnabled() { return enabled() && ORE.get(); }
    public static boolean guardedEnabled() { return enabled() && GUARDED.get(); }
    public static boolean adminAllowsUnknown(String dimension, int chunkX, int chunkZ) {
        if (!enabled() || !ADMIN_REGION.get() || !"minecraft:overworld".equals(dimension)) return false;
        return insideRectangle(chunkX, chunkZ, MIN_X.get(), MAX_X.get(), MIN_Z.get(), MAX_Z.get());
    }
    public static boolean strictRegionMode() { return SPEC.isLoaded() && STRICT_REGION.get(); }
    public static boolean regionContains(String dimension, int chunkX, int chunkZ) {
        return SPEC.isLoaded() && "minecraft:overworld".equals(dimension)
                && insideRectangle(chunkX, chunkZ, MIN_X.get(), MAX_X.get(), MIN_Z.get(), MAX_Z.get());
    }
    public static boolean templateEnabled(net.guzhenren.tianyi.resource.FiniteSite.Kind kind) {
        return switch (kind) { case PLANT -> plantEnabled(); case ORE -> oreEnabled(); case GUARDED -> guardedEnabled(); };
    }
    /** kind 开关 <b>且</b>（若有）模板独立开关。没登记独立开关的老模板只受 kind 控制。 */
    public static boolean templateEnabled(String template, net.guzhenren.tianyi.resource.FiniteSite.Kind kind) {
        if (!templateEnabled(kind)) return false;
        ModConfigSpec.BooleanValue flag = TEMPLATE_FLAGS.get(template);
        return flag == null || flag.get();
    }
    /** 该模板是否有独立开关（文档/诊断用，不参与生成决策）。 */
    public static boolean hasTemplateFlag(String template) {
        return TEMPLATE_FLAGS.containsKey(template);
    }
    /** Reversed bounds are invalid, rather than silently expanding permission. */
    public static boolean insideRectangle(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return minX <= maxX && minZ <= maxZ && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
