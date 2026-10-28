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
    private static final ModConfigSpec.BooleanValue ENABLED, LEGACY_PRESSURE, LEGACY_RESOURCES;
    private static final ModConfigSpec.IntValue FIRST_SECONDS, MIN_COOLDOWN, MAX_COOLDOWN, BASE, STAKE, BONUS;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Enable owner-specific treasure and optional wager encounters.").define("enabled", true);
        LEGACY_PRESSURE = b.comment("Resume the old automatic suppression, ploys and calamities. Off by default to avoid stacked pressure.")
                .define("legacyAutomaticPressure", false);
        LEGACY_RESOURCES = b.comment("Opt in to legacy proximity drops, automatic ownership and cargo. Existing data stays intact when disabled.")
                .define("legacyAutomaticResources", false);
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
    public static boolean legacyResources() { return SPEC.isLoaded() && LEGACY_RESOURCES.get(); }
    public static boolean legacyPressure() { return SPEC.isLoaded() && LEGACY_PRESSURE.get(); }
    private static int get(ModConfigSpec.IntValue v, int fallback) { return SPEC.isLoaded() ? v.get() : fallback; }
    public static int firstTicks() { return get(FIRST_SECONDS, 60) * 20; }
    public static int cooldownMin() { return Math.min(get(MIN_COOLDOWN, 480), get(MAX_COOLDOWN, 720)) * 20; }
    public static int cooldownMax() { return Math.max(get(MIN_COOLDOWN, 480), get(MAX_COOLDOWN, 720)) * 20; }
    public static int base() { return get(BASE, 32); }
    public static int stake() { return get(STAKE, 32); }
    public static int bonus() { return get(BONUS, 128); }
}
