package net.guzhenren.tianyi.world;

import java.util.Set;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Placement permission only. Never protects the resulting resource from players or traps. */
public final class SiteProtection {
    public enum Verdict { ALLOW, DENY, UNKNOWN }
    public record Decision(Verdict verdict, String reason) {
        public boolean allowed() { return verdict == Verdict.ALLOW; }
    }
    /** Optional adapter supplied by the service when a protection integration is actually present.
     * Absence of an integration is ordinary vanilla permission, not UNKNOWN. */
    @FunctionalInterface public interface Claims {
        Verdict check(ServerLevel level, BlockPos pos, ServerPlayer actor);
    }
    /** Denials a later retry can plausibly clear: players move away, chunks load, claim data comes back.
     * Terrain, height, border, spawn and claim-DENY denials are permanent for this reservation. */
    private static final Set<String> RETRYABLE = Set.of("near_player", "unloaded_safety_margin", "claim_unknown");
    /** Natural blocks whose block entity is terrain, never a player container. Container blocks
     * (chest, barrel, furnace, hopper, dispenser, shulker box...) are not natural blocks at all,
     * so they keep returning DENY and their items are never overwritten. */
    private static final Set<String> NATURAL_BLOCK_ENTITIES = Set.of("bee_nest", "beehive",
            "suspicious_sand", "suspicious_gravel");
    /** Candidate slots sit at most this far from their origin, so one scan can serve all of them. */
    private static final int SCAN_MARGIN = 2;
    /** Ordinary overworld terrain. Known weak guarantee, deliberately unchanged: player builds made of
     * these same vanilla blocks still look natural. Structure/container blocks are never listed here. */
    private static final Set<String> NATURAL = Set.of(
            "air", "cave_air", "void_air", "grass_block", "dirt", "coarse_dirt", "rooted_dirt",
            "podzol", "mycelium", "moss_block", "moss_carpet", "mud", "clay", "sand", "red_sand",
            "sandstone", "red_sandstone", "terracotta", "gravel", "suspicious_sand", "suspicious_gravel",
            "stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "dripstone_block",
            "mossy_cobblestone", "bedrock", "water", "lava", "snow", "snow_block", "powder_snow",
            "ice", "packed_ice", "blue_ice",
            "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "vine", "glow_lichen",
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
            "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
            "sunflower", "lilac", "rose_bush", "peony", "torchflower", "pitcher_plant",
            "brown_mushroom", "red_mushroom",
            "brown_mushroom_block", "red_mushroom_block", "mushroom_stem", "sugar_cane", "cactus",
            "pumpkin", "melon", "sweet_berry_bush", "bee_nest", "beehive",
            "seagrass", "tall_seagrass", "kelp", "kelp_plant", "lily_pad", "bamboo", "bamboo_sapling",
            "pointed_dripstone", "small_dripleaf", "big_dripleaf", "big_dripleaf_stem", "hanging_roots",
            "azalea", "flowering_azalea", "spore_blossom", "cave_vines", "cave_vines_plant",
            "mangrove_roots", "muddy_mangrove_roots",
            "sculk", "sculk_vein", "ochre_froglight", "verdant_froglight", "pearlescent_froglight",
            "amethyst_block", "budding_amethyst", "amethyst_cluster",
            "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud",
            "coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore",
            "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_copper_ore", "deepslate_gold_ore",
            "deepslate_redstone_ore", "deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_emerald_ore");
    private SiteProtection() {}

    /** Lazy, reusable copy of the bounded natural/block-entity scan around one origin.
     * Server thread only. It caches exactly the same test the uncached path performs, so DENY
     * semantics are unchanged: it never relaxes a building, container or foreign-block denial. */
    public static final class Scan {
        private static final byte UNKNOWN = 0, CLEAR = 1, BLOCKED = 2;
        private final ServerLevel level;
        private final int radius, margin, span, originX, originY, originZ, baseX, baseY, baseZ;
        private final byte[] cells;
        private Scan(ServerLevel level, BlockPos origin, int radius, int margin) {
            this.level = level;
            this.radius = radius;
            this.margin = margin;
            this.span = 2 * (radius + margin) + 1;
            this.originX = origin.getX(); this.originY = origin.getY(); this.originZ = origin.getZ();
            this.baseX = originX - radius - margin;
            this.baseY = originY - radius - margin;
            this.baseZ = originZ - radius - margin;
            this.cells = new byte[span * span * span];
        }
        /** The cache is only valid when the whole sphere around {@code pos} was covered from the origin. */
        private boolean covers(BlockPos pos) {
            return Math.abs(pos.getX() - originX) <= margin && Math.abs(pos.getY() - originY) <= margin
                    && Math.abs(pos.getZ() - originZ) <= margin;
        }
        private boolean blockedAt(BlockPos pos) {
            int index = ((pos.getX() - baseX) * span + (pos.getZ() - baseZ)) * span + (pos.getY() - baseY);
            byte value = cells[index];
            if (value == UNKNOWN) {
                BlockState state = level.getBlockState(pos);
                value = !natural(state) || !benignBlockEntity(level, pos, state) ? BLOCKED : CLEAR;
                cells[index] = value;
            }
            return value == BLOCKED;
        }
    }

    /** One scan that every slot of the same candidate can share, instead of one 32-block sphere per slot. */
    public static Scan scan(ServerLevel level, BlockPos origin) {
        return new Scan(level, origin, SiteSettings.AVOIDANCE_RADIUS, SCAN_MARGIN);
    }

    /** True when a denial may clear on a later attempt; callers pause instead of rejecting forever. */
    public static boolean retryable(Decision decision) {
        return decision != null && !decision.allowed() && RETRYABLE.contains(decision.reason());
    }

    /** Full bounded 32-block sphere, only already-loaded chunks; no terrain is read across a missing chunk.
     * Own previously committed blocks are exempt only while their exact physical binding still matches. */
    public static Decision check(ServerLevel level, BlockPos pos, ServerPlayer actor, Claims claims, FiniteSite own) {
        return check(level, pos, actor, claims, own, null);
    }

    /** Same decision as {@link #check(ServerLevel, BlockPos, ServerPlayer, Claims, FiniteSite)}; a shared
     * {@code scan} only replaces repeated block reads when it provably covers this position. */
    public static Decision check(ServerLevel level, BlockPos pos, ServerPlayer actor, Claims claims,
            FiniteSite own, Scan scan) {
        if (!level.dimension().equals(Level.OVERWORLD)) return deny("not_overworld");
        int r = SiteSettings.AVOIDANCE_RADIUS;
        if (pos.getY() - r < level.getMinBuildHeight() || pos.getY() + r >= level.getMaxBuildHeight())
            return deny("height_margin");
        for (int cx = (pos.getX()-r)>>4; cx <= (pos.getX()+r)>>4; cx++)
            for (int cz = (pos.getZ()-r)>>4; cz <= (pos.getZ()+r)>>4; cz++)
                if (!level.hasChunk(cx, cz)) return new Decision(Verdict.UNKNOWN, "unloaded_safety_margin");
        if (!level.getWorldBorder().isWithinBounds(pos)) return deny("world_border");
        BlockPos spawn = level.getSharedSpawnPos();
        long dx = (long)pos.getX()-spawn.getX(), dz = (long)pos.getZ()-spawn.getZ();
        int spawnRadius = Math.max(SiteSettings.MIN_SPAWN_DISTANCE, level.getServer().getSpawnProtectionRadius());
        if (dx*dx+dz*dz < (long)spawnRadius*spawnRadius
                || (Math.abs(dx) <= level.getServer().getSpawnProtectionRadius()
                    && Math.abs(dz) <= level.getServer().getSpawnProtectionRadius())) return deny("world_spawn");
        for (var player : level.players())
            if (player.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) <= (double)r*r)
                return deny("near_player");
        if (actor != null && (!actor.serverLevel().equals(level) || !level.mayInteract(actor, pos)))
            return deny("vanilla_protection");
        String dim = level.dimension().location().toString();
        if (SiteSettings.strictRegionMode() && !SiteSettings.regionContains(dim, pos.getX()>>4, pos.getZ()>>4))
            return deny("outside_strict_rectangle");
        boolean reuse = scan != null && own == null && scan.covers(pos);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r,-r,-r), pos.offset(r,r,r))) {
            if (p.distSqr(pos) > (double)r*r) continue;
            if (own != null && SitePlacement.matchesOwned(level, own, p)) continue;
            if (reuse ? scan.blockedAt(p) : blocked(level, p)) return deny("building_bed_or_container");
        }
        if (claims != null) {
            Verdict verdict;
            try { verdict = claims.check(level, pos, actor); }
            catch (RuntimeException unavailable) { verdict = Verdict.UNKNOWN; }
            if (verdict == Verdict.DENY) return deny("claim_denied");
            if (verdict != Verdict.ALLOW && !SiteSettings.adminAllowsUnknown(dim, pos.getX()>>4, pos.getZ()>>4))
                return new Decision(Verdict.UNKNOWN, "claim_unknown");
        }
        return new Decision(Verdict.ALLOW, "natural_overworld");
    }

    private static boolean blocked(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        return !natural(state) || !benignBlockEntity(level, p, state);
    }

    /** Terrain block entities (beehives, brushable sand) are not player property; containers still deny. */
    private static boolean benignBlockEntity(ServerLevel level, BlockPos p, BlockState state) {
        if (level.getBlockEntity(p) == null) return true;
        return NATURAL_BLOCK_ENTITIES.contains(path(state));
    }

    private static String path(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    static boolean natural(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("minecraft")) return false;
        String name = id.getPath();
        return NATURAL.contains(name) || name.endsWith("_log") || name.endsWith("_leaves")
                || name.endsWith("_sapling");
    }
    static boolean replaceable(BlockState state) {
        return natural(state) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }
    private static Decision deny(String reason) { return new Decision(Verdict.DENY, reason); }
}
