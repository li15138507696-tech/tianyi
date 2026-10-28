package net.guzhenren.malice;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * 本体版本识别。
 *
 * <p><b>为什么不能读 mod 版本号</b>：9.8 / 10.4 / 10.20 三个包的 {@code modId = guzhenren}、
 * {@code version = 0.1} 全都一样，FML 层面完全分不出来。
 *
 * <p>所以改用**内容指纹**（均已实际核对）：
 *
 * <table border="1">
 *   <tr><th>版本</th><th>jiang_shi_gu_xiu</th><th>gu_xiu</th><th>FactionNpcManager</th></tr>
 *   <tr><td>9.8</td><td>有</td><td>无</td><td>无</td></tr>
 *   <tr><td>10.4</td><td>无</td><td>有</td><td>无</td></tr>
 *   <tr><td>10.20</td><td>无</td><td>有</td><td>有</td></tr>
 * </table>
 *
 * <p>检测结果在第一次查询时算好并缓存（{@code EntityType} 注册表在类加载早期可能还没填完，
 * 不能在静态初始化里查）。
 */
public enum GuzhenrenVersion {

    /** 9.8：独立 jar（没有 guzhenren_library），实体 699 个，新增毛僵 / 跳僵。 */
    V98("9.8",
            "本体是独立 jar；实体 699 个，含毛僵/跳僵；鲨鱼在 WATER_CREATURE、羚羊在 CREATURE 类别"),
    /** 10.4：jar-in-jar 包装（guzhenren_library + 内嵌本体），实体 656 个。 */
    V104("10.4",
            "jar-in-jar 包装；实体 656 个，含 gu_xiu；妖兽生成基本走原版刷怪流程"),
    /** 10.20：本体与 10.4 是同一个内嵌 jar，但库层新增势力/任务/世界地图，会绕过 finalizeSpawn 生成实体。 */
    V1020("10.20",
            "本体与 10.4 是同一个内嵌 jar；库层新增势力/任务/世界地图（+439 类），"
                    + "据点与任务生成的妖兽不经过 finalizeSpawn，必须靠补挂保险丝"),
    /** 认不出来的版本。 */
    UNKNOWN("未知", "没匹配到任何已知指纹，按最保守的方式跑（全部标签 + 补挂保险丝）");

    private final String label;
    private final String note;

    GuzhenrenVersion(String label, String note) {
        this.label = label;
        this.note = note;
    }

    public String label() {
        return label;
    }

    /** 与 jar 内 malice_variant.txt 对应的键（9.8 → "98"）。 */
    public String key() {
        return switch (this) {
            case V98 -> "98";
            case V104 -> "104";
            case V1020 -> "1020";
            case UNKNOWN -> "unknown";
        };
    }

    /** 这个版本需要注意什么（写进日志与 /guzhenren_malice info）。 */
    public String note() {
        return note;
    }

    private static GuzhenrenVersion cached;

    /** 检测本体版本（结果缓存）。 */
    public static GuzhenrenVersion detect() {
        if (cached != null) {
            return cached;
        }
        try {
            GuzhenrenVersion v;
            if (hasEntity("jiang_shi_gu_xiu")) {
                v = V98;
            } else if (hasClass("com.guzhenrenlibrary.faction.FactionNpcManager")) {
                v = V1020;
            } else if (hasEntity("gu_xiu")) {
                v = V104;
            } else {
                v = UNKNOWN;
            }
            cached = v;
            return v;
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    private static boolean hasEntity(String id) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(
                ResourceLocation.fromNamespaceAndPath("guzhenren", id));
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name, false, GuzhenrenVersion.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 当前本体里是否注册了某个实体（调试/文档用）。 */
    public static boolean entityExists(String id) {
        return hasEntity(id);
    }

    /** 供日志/命令显示的一行。 */
    public static String describe() {
        GuzhenrenVersion v = detect();
        return "本体版本 " + v.label + "（" + v.note + "）";
    }
}
