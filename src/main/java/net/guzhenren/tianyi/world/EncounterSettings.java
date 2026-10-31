package net.guzhenren.tianyi.world;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side encounter settings. Effect/range rules are shared by all trials. */
public final class EncounterSettings {
    public static final int BATTLE_TICKS = 180 * 20;
    public static final int OUTSIDE_TICKS = 5 * 20;
    public static final int SPEED_TICKS = 120 * 20;
    public static final int WEAKNESS_TICKS = 60 * 20;
    public static final double MANA_RETAINED = 0.5;
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue FIRST_SECONDS, MIN_COOLDOWN, MAX_COOLDOWN, BASE, STAKE, BONUS;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Enable owner-specific treasure and optional wager encounters.").define("enabled", true);
        // 本次清理：两个旧开关（自动压制 / 自动资源）已整体删除。
        // 「天意是否会动手」只由天意强度档位（tianyi-force-server.toml 的 intensity）决定；
        // 「世界里有没有产地」只由有限资源点（tianyi-sites-server.toml）决定。
        // 老配置里残留的同名键不会再被声明，NeoForge 读取时会当作未知键忽略。
        FIRST_SECONDS = b.defineInRange("firstEncounterSeconds", 60, 1, 3600);
        MIN_COOLDOWN = b.defineInRange("minimumCooldownSeconds", 480, 5, 86400);
        MAX_COOLDOWN = b.defineInRange("maximumCooldownSeconds", 720, 5, 86400);
        BASE = b.defineInRange("baseYuanshi", 32, 1, 4096);
        STAKE = b.defineInRange("stakeYuanshi", 32, 1, 4096);
        BONUS = b.comment("Rank-2 animal trials pay twice this bonus; first gray wolf pays this amount.")
                .defineInRange("bonusYuanshi", 128, 1, 4096);
        SPEC = b.build();
    }
    private EncounterSettings() {}
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    private static int get(ModConfigSpec.IntValue v, int fallback) { return SPEC.isLoaded() ? v.get() : fallback; }
    public static int firstTicks() { return get(FIRST_SECONDS, 60) * 20; }
    public static int cooldownMin() { return Math.min(get(MIN_COOLDOWN, 480), get(MAX_COOLDOWN, 720)) * 20; }
    public static int cooldownMax() { return Math.max(get(MIN_COOLDOWN, 480), get(MAX_COOLDOWN, 720)) * 20; }
    public static int base() { return get(BASE, 32); }
    public static int stake() { return get(STAKE, 32); }
    public static int bonus() { return get(BONUS, 128); }
}
