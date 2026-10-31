package net.guzhenren.tianyi.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.guzhenren.tianyi.resource.SiteCatalog;
import net.guzhenren.tianyi.resource.SiteInventory;
import net.guzhenren.tianyi.resource.SiteTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/** Synchronous server-thread world adapter. No tick registration, rewards, chunk loading or retry queue.
 * SavedData is dirtied before side effects; it is not a synchronous disk write. An uncertain operation
 * quarantines that single site. After reload the codec quarantines only the sites a PREPARED/APPLIED
 * transaction touched; the rest of the ledger stays usable. */
public final class SitePlacement {
    public static final String MARKER = "tianyi_finite_site";
    public enum Result { APPLIED, ALREADY_RECORDED, PAUSED, REJECTED, QUARANTINED }
    public enum GuardianStatus { NONE, ALIVE_LOADED, DEAD_LOADED, UNLOADED_OR_ABSENT, CONFLICT }
    /** Values are instructions, never trusted proof; every mutation re-resolves the current registry/resources. */
    public record Selection(String template, String material, String guardian) {}
    public record Observation(boolean loaded, boolean carrierIntact, boolean rootIntact, int remaining) {}
    /** Finite evidence only; inspecting a clue never creates or transfers items. */
    public record Clue(UUID siteId, String dimension, int x, int y, int z,
            FiniteSite.Kind kind, int initial, int extraGrowthLimit, FiniteSite.Phase phase) {}
    private record Resolved(SiteCatalog.Candidate candidate, BlockState state, Item item, EntityType<?> guardian) {}
    private static final Set<String> SOILS = Set.of("grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "moss_block");
    // The allowed plant biomes now live in the pure SiteTerrain list (vanilla ten + 苍木林海/迷思幻林),
    // so the world-side check and the template whitelist cannot drift apart.
    private SitePlacement() {}

    /** Real biome id at a position ({@code namespace:path}), or "" when the biome key is unavailable.
     * Signature evidence: LevelReader#getBiome(BlockPos) and Holder#unwrapKey() were dumped locally
     * for 1.21.1/21.1.263. A missing key means "unknown terrain", which never matches a
     * whitelist (we skip the template instead of guessing). */
    public static String biomeId(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    /** Explicit selection, no random or alternative-species fallback. Missing/changed evidence disables this selection.
     * Runtime datapack block loot must still be the reviewed single-item table, without count/fortune/silk functions. */
    public static Optional<Selection> selection(FiniteSite site) {
        if (site.slots().isEmpty()) return Optional.empty();
        String[] parts=site.slots().get(0).physicalFingerprint().split("\\|material=|\\|guardian=",-1);
        if (parts.length!=3 || !FiniteSite.resourceId(parts[1])
                || (!parts[2].isEmpty() && !FiniteSite.resourceId(parts[2]))) return Optional.empty();
        Selection value=new Selection(site.template(),parts[1],parts[2]);
        String suffix="|material="+parts[1]+"|guardian="+parts[2];
        return site.slots().stream().allMatch(s -> s.physicalFingerprint().endsWith(suffix)) ? Optional.of(value) : Optional.empty();
    }
    public static boolean verified(ServerLevel level, Selection selection) { return resolve(level, selection) != null; }
    private static Resolved resolve(ServerLevel level, Selection selection) {
        if (selection == null) return null;
        var c = SiteCatalog.candidate(selection.template());
        if (c == null || !c.verifiedForEnablement() || !SiteSettings.templateEnabled(c.template(), c.kind())
                || !c.itemCandidates().contains(selection.material())) return null;
        String blockId = c.blockFor(selection.material());
        if (blockId == null || !c.blockCandidates().contains(blockId)) return null;
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId)).orElse(null);
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(selection.material())).orElse(null);
        if (block == null || block==Blocks.AIR || item==null || item==Items.AIR) return null;
        // When block and material are the same id the item must be that block's BlockItem (plant, self-dropping ore).
        // An outcrop whose reviewed loot table drops a *material* item instead (元石露头 → 元石) is gated by
        // singleItemLoot below, so the block is never treated as a container of itself.
        if (c.kind()!=FiniteSite.Kind.GUARDED && blockId.equals(selection.material())
                && (!(item instanceof BlockItem bi) || bi.getBlock()!=block)) return null;
        if (c.kind()==FiniteSite.Kind.PLANT) {
            String expected = selection.material().equals("guzhenren:gu_caidanxincao")
                    ? "net.guzhenren.block.GuCaidanxincaoBlock" : "net.guzhenren.block.GucaileimingcaoBlock";
            if (!(block instanceof FlowerBlock) || !block.getClass().getName().equals(expected)) return null;
        } else if (c.kind()==FiniteSite.Kind.ORE && blockClass(blockId)!=null
                && !block.getClass().getName().equals(blockClass(blockId))) return null;
        if (c.kind()!=FiniteSite.Kind.GUARDED && !singleItemLoot(level, blockId, selection.material())) return null;
        EntityType<?> guardian = null;
        if (c.kind()==FiniteSite.Kind.GUARDED) {
            // Material is also a reviewed real plant BlockItem, never currency or an unrefined Gu.
            Block plant = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(selection.material())).orElse(null);
            String expectedPlant = selection.material().equals("guzhenren:gu_caidanxincao")
                    ? "net.guzhenren.block.GuCaidanxincaoBlock" : "net.guzhenren.block.GucaileimingcaoBlock";
            if (!(item instanceof BlockItem bi) || bi.getBlock()!=plant || !(plant instanceof FlowerBlock)
                    || !plant.getClass().getName().equals(expectedPlant)
                    || !singleItemLoot(level,selection.material(),selection.material())) return null;
            if (!c.guardianCandidates().contains(selection.guardian())) return null;
            guardian = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(selection.guardian())).orElse(null);
            if (guardian == null) return null;
            Entity probe = guardian.create(level);
            if (probe == null) return null;
            boolean valid = probe instanceof Mob && guardianTypeMatches(probe, selection.guardian());
            probe.discard();
            if (!valid) return null;
        } else if (selection.guardian()!=null && !selection.guardian().isEmpty()) return null;
        return new Resolved(c, block.defaultBlockState(), item, guardian);
    }
    /** Java classes reviewed in bytecode. Registry-key equality is always the primary gate; the class
     * check only catches a mod version that re-registers the same id onto a different class.
     * All six guardians and all four ore blocks below were verified by listing the locked inner jar
     * (net/guzhenren/entity/*Entity.class, net/guzhenren/block/*Block.class). */
    private static String guardianClass(String id) {
        return switch (id) {
            case "guzhenren:hui_lang" -> "net.guzhenren.entity.HuiLangEntity";
            case "guzhenren:tu_lang" -> "net.guzhenren.entity.TuLangEntity";
            case "guzhenren:hao_dian_lang" -> "net.guzhenren.entity.HaoDianLangEntity";
            case "guzhenren:hu" -> "net.guzhenren.entity.HuEntity";
            case "guzhenren:huoyanxiong" -> "net.guzhenren.entity.HuoyanxiongEntity";
            case "guzhenren:yao_ya_quan" -> "net.guzhenren.entity.YaoYaQuanEntity";
            default -> null;
        };
    }
    /** Same rule for ore blocks: class names verified in the locked inner jar. */
    private static String blockClass(String id) {
        return switch (id) {
            case "guzhenren:xuanjin" -> "net.guzhenren.block.XuanjinBlock";
            case "guzhenren:fangkuaiyuanshiyuankuang" -> "net.guzhenren.block.FangkuaiyuanshiyuankuangBlock";
            case "guzhenren:chilianjinyuankuang" -> "net.guzhenren.block.ChilianjinyuankuangBlock";
            case "guzhenren:wen_ding_shi_kuang" -> "net.guzhenren.block.WenDingShiKuangBlock";
            default -> null;
        };
    }
    /** Identity of a guardian entity: registered entity-type key must equal the selected id, and the
     * exact class must match when one was reviewed. Never a random substitute, never a kill reward. */
    private static boolean guardianTypeMatches(Entity entity, String guardianId) {
        if (entity == null || guardianId == null || guardianId.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null || !key.toString().equals(guardianId)) return false;
        String expected = guardianClass(guardianId);
        return expected == null || entity.getClass().getName().equals(expected);
    }
    private static boolean singleItemLoot(ServerLevel level, String block, String item) {
        var id = ResourceLocation.parse(block);
        var resource = level.getServer().getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "loot_table/blocks/"+id.getPath()+".json"));
        if (resource.isEmpty()) return false;
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!onlyKeys(root, Set.of("type", "pools", "random_sequence"))) return false;
            var pools = root.getAsJsonArray("pools");
            if (pools==null || pools.size()!=1) return false;
            JsonObject pool = pools.get(0).getAsJsonObject();
            if (!onlyKeys(pool, Set.of("rolls", "bonus_rolls", "entries", "conditions"))
                    || !pool.get("rolls").isJsonPrimitive() || pool.get("rolls").getAsDouble()!=1
                    || (pool.has("bonus_rolls") && pool.get("bonus_rolls").getAsDouble()!=0)) return false;
            if (pool.has("conditions")) for (JsonElement element : pool.getAsJsonArray("conditions")) {
                var condition = element.getAsJsonObject();
                if (!onlyKeys(condition, Set.of("condition")) || !condition.get("condition").getAsString().equals("minecraft:survives_explosion")) return false;
            }
            var entries = pool.getAsJsonArray("entries");
            if (entries==null || entries.size()!=1) return false;
            var entry = entries.get(0).getAsJsonObject();
            return onlyKeys(entry, Set.of("type", "name")) && entry.get("type").getAsString().equals("minecraft:item")
                    && entry.get("name").getAsString().equals(item);
        } catch (Exception invalidOrUnavailable) { return false; }
    }
    private static boolean onlyKeys(JsonObject object, Set<String> keys) { return object.keySet().stream().allMatch(keys::contains); }

    /** Creates an immutable reservation only. The service applies its global/region budget and calls state.reserve.
     * 4-8 real plant blocks, 3-6 ore blocks, or one barrel holding 4-8 material items. */
    public static Optional<FiniteSite> candidate(ServerLevel level, BlockPos origin, Selection selection,
            int units, ServerPlayer actor, SiteProtection.Claims claims) {
        Resolved r = resolve(level, selection);
        if (r==null || units<r.candidate.minimumInitial() || units>r.candidate.maximumInitial()) return Optional.empty();
        List<FiniteSite.Slot> slots = new ArrayList<>();
        int size = r.candidate.kind()==FiniteSite.Kind.GUARDED ? 1 : units;
        // One bounded sphere scan serves every slot; the per-slot DENY test itself is unchanged.
        SiteProtection.Scan scan = SiteProtection.scan(level, origin);
        for (int i=0; i<size; i++) {
            BlockPos pos = origin.offset(i%3, 0, i/3);
            if (!SiteProtection.check(level,pos,actor,claims,null,scan).allowed()
                    // Terrain gate: every physical slot must sit in a biome this template is allowed in.
                    // Mismatch only skips this template here (the service keeps trying other positions);
                    // it is never a global disable and never a random substitute terrain.
                    || !SiteTerrain.biomeAllowed(r.candidate.biomeCandidates(), biomeId(level,pos))
                    || !SiteProtection.replaceable(level.getBlockState(pos))
                    || !solidNaturalRoot(level,pos) || !r.state.canSurvive(level,pos)
                    || (r.candidate.kind()==FiniteSite.Kind.PLANT && !growthEnvironment(level,pos,r.state))) return Optional.empty();
            slots.add(new FiniteSite.Slot(i,pos.getX(),pos.getY(),pos.getZ(),
                    fingerprint(level.getBlockState(pos))+"|root="+fingerprint(level.getBlockState(pos.below())), binding(r,selection),false));
        }
        var site = new FiniteSite(UUID.randomUUID(), level.dimension().location().toString(), origin.getX(),origin.getY(),origin.getZ(),
                selection.template(),1,r.candidate.kind(),FiniteSite.Phase.RESERVED,0,
                "minecraft:overworld:"+Math.floorDiv(origin.getX()>>4,SiteSettings.REGION_CHUNKS)+":"+Math.floorDiv(origin.getZ()>>4,SiteSettings.REGION_CHUNKS),
                SiteInventory.reserved(units,r.candidate.lifetimeGrowthBudget()), slots,List.of(),0,true);
        return Optional.of(site);
    }

    /** Exactly one slot/one plant growth transaction. Call only on the server thread. A failure after PREPARED
     * is terminally quarantined, even if rollback succeeded; there is no delayed delivery/retry. */
    public static Result materialize(ServerLevel level, FiniteSiteState ledger, UUID id, int index,
            Selection selection, ServerPlayer actor, SiteProtection.Claims claims) {
        if (!ledger.mutationReady()) return Result.QUARANTINED;
        FiniteSite site = ledger.sites().get(id);
        if (site==null || site.terminal() || !site.dimension().equals(level.dimension().location().toString())) return Result.REJECTED;
        var slot = site.slots().stream().filter(s -> s.index()==index).findFirst().orElse(null);
        if (slot==null) return Result.REJECTED;
        boolean growth = site.kind()==FiniteSite.Kind.PLANT && site.phase()==FiniteSite.Phase.MATERIALIZING
                && site.inventory().unmaterialized()==1 && site.slots().stream().allMatch(FiniteSite.Slot::materialized);
        if (slot.materialized() && !growth) return Result.ALREADY_RECORDED;
        Resolved r = resolve(level,selection);
        if (!compatible(site,r,selection)) return Result.REJECTED;
        BlockPos pos = pos(slot);
        var permission = SiteProtection.check(level,pos,actor,claims,site);
        // near_player / unloaded / unknown-claim are transient: pause and retry instead of rejecting forever.
        if (!permission.allowed()) return permission.verdict()==SiteProtection.Verdict.UNKNOWN
                || SiteProtection.retryable(permission) ? Result.PAUSED : Result.REJECTED;
        if (!rootMatches(level,slot) || !solidNaturalRoot(level,pos)
                || !SiteProtection.replaceable(level.getBlockState(pos)) || !r.state.canSurvive(level,pos)
                || (!growth && !originalState(slot).equals(fingerprint(level.getBlockState(pos))))
                || (growth && !level.getBlockState(pos).isAir())
                || (site.kind()==FiniteSite.Kind.PLANT && !growthEnvironment(level,pos,r.state))) return Result.REJECTED;
        if (site.phase()==FiniteSite.Phase.RESERVED) {
            var next = site.beginMaterialization();
            if (!ledger.replace(site,next)) return Result.REJECTED;
            site = next;
        }
        if (site.phase()!=FiniteSite.Phase.MATERIALIZING) return Result.REJECTED;
        // A UUID is bound BEFORE spawn. Its absence after a crash never authorizes another entity.
        if (site.kind()==FiniteSite.Kind.GUARDED && site.guardians().isEmpty()) {
            if (!spawnGuardian(level,ledger,site,r,selection,actor,claims)) return Result.QUARANTINED;
            site = ledger.sites().get(id);
        }
        if (site.kind()==FiniteSite.Kind.GUARDED && guardianStatus(level,site)!=GuardianStatus.ALIVE_LOADED) {
            quarantine(ledger,id); return Result.QUARANTINED;
        }
        UUID txid = UUID.randomUUID();
        if (!ledger.prepare(new FiniteSite.Transaction(txid,id,index,site.revision(),FiniteSite.TxPhase.PREPARED))) return Result.REJECTED;
        BlockSnapshot before = null;
        boolean wrote = false;
        BarrelBlockEntity createdBarrel = null;
        try {
            before = BlockSnapshot.create(level.dimension(),level,pos,3);
            // Spawn callbacks can change the terrain; revalidate the captured natural slot immediately before writing.
            if (!SiteProtection.replaceable(level.getBlockState(pos)) || !rootMatches(level,slot)
                    || (!growth && !originalState(slot).equals(fingerprint(level.getBlockState(pos))))
                    || (growth && !level.getBlockState(pos).isAir())) throw new IllegalStateException("changed_before_write");
            wrote = level.setBlock(pos,r.state,3);
            if (!wrote || !fingerprint(level.getBlockState(pos)).equals(physicalState(slot))) throw new IllegalStateException("setBlock_failed");
            if (site.kind()==FiniteSite.Kind.GUARDED) {
                if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) throw new IllegalStateException("barrel_missing");
                createdBarrel = barrel;
                if (!barrel.isEmpty()) throw new IllegalStateException("barrel_not_empty");
                barrel.getPersistentData().putUUID(MARKER,id); barrel.setChanged();
            }
            // true means canceled. Inventory is still empty here. No cancellation path delivers stock.
            if (EventHooks.onBlockPlace(actor,before,Direction.UP)) throw new IllegalStateException("place_canceled");
            if (!fingerprint(level.getBlockState(pos)).equals(physicalState(slot)) || !rootMatches(level,slot))
                throw new IllegalStateException("place_changed_by_hook");
            int units = growth || site.kind()!=FiniteSite.Kind.GUARDED ? 1 : site.inventory().unmaterialized();
            if (site.kind()==FiniteSite.Kind.GUARDED) {
                if (level.getBlockEntity(pos)!=createdBarrel || !createdBarrel.isEmpty()
                        || !createdBarrel.getPersistentData().hasUUID(MARKER) || !id.equals(createdBarrel.getPersistentData().getUUID(MARKER))) throw new IllegalStateException("carrier_changed_by_hook");
                if (units<4 || units>8 || units>new ItemStack(r.item).getMaxStackSize()) throw new IllegalStateException("invalid_stock");
                createdBarrel.setItem(0,new ItemStack(r.item,units)); createdBarrel.setChanged();
                if (createdBarrel.getItem(0).getCount()!=units || !createdBarrel.getItem(0).is(r.item)) throw new IllegalStateException("stock_not_applied");
            }
            if (!ledger.advance(txid,FiniteSite.TxPhase.PREPARED,FiniteSite.TxPhase.APPLIED)) throw new IllegalStateException("journal_apply_failed");
            var next = growth ? site.materializeGrowth() : site.materializeSlot(index,units);
            if (!ledger.replace(site,next) || !ledger.advance(txid,FiniteSite.TxPhase.APPLIED,FiniteSite.TxPhase.COMMITTED))
                throw new IllegalStateException("journal_commit_failed");
            return Result.APPLIED;
        } catch (RuntimeException failure) {
            // Restore ONLY the block written by this invocation, while the fingerprint and barrel instance still match.
            // Never destroy/restore a replacement created by a placement hook/player, even at the same coordinates.
            if (wrote && fingerprint(level.getBlockState(pos)).equals(physicalState(slot))
                    && (site.kind()!=FiniteSite.Kind.GUARDED || (createdBarrel!=null && level.getBlockEntity(pos)==createdBarrel
                        && createdBarrel.isEmpty() && createdBarrel.getPersistentData().hasUUID(MARKER)
                        && id.equals(createdBarrel.getPersistentData().getUUID(MARKER))))) {
                try { if (before!=null) before.restore(3); }
                catch (RuntimeException restoreFailure) { /* uncertain physical result stays quarantined */ }
            }
            quarantine(ledger,id); return Result.QUARANTINED;
        }
    }

    private static boolean spawnGuardian(ServerLevel level, FiniteSiteState ledger, FiniteSite site, Resolved r,
            Selection selection, ServerPlayer actor, SiteProtection.Claims claims) {
        if (level.getDifficulty()==Difficulty.PEACEFUL || r.guardian==null) { quarantine(ledger,site.siteId()); return false; }
        BlockPos spawn = new BlockPos(site.x()-2,site.y(),site.z());
        if (!SiteProtection.check(level,spawn,actor,claims,site).allowed() || !solidNaturalRoot(level,spawn)) {
            quarantine(ledger,site.siteId()); return false;
        }
        Entity entity = r.guardian.create(level);
        if (!(entity instanceof Mob mob) || !guardianTypeMatches(entity, selection.guardian())) {
            if (entity!=null) entity.discard(); quarantine(ledger,site.siteId()); return false;
        }
        mob.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,0,0);
        mob.getPersistentData().putUUID(MARKER,site.siteId());
        mob.setPersistenceRequired();
        var bound = site.bindGuardian(mob.getUUID());
        if (!ledger.replace(site,bound)) { mob.discard(); return false; }
        try {
            if (!mob.isAlive() || !collisionFree(level,mob)) throw new IllegalStateException("guardian_collision");
            // Post the verified finalize event explicitly: its nullable return value cannot distinguish cancellation.
            var finalizeEvent = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(
                    mob,level,mob.getX(),mob.getY(),mob.getZ(),level.getCurrentDifficultyAt(spawn),MobSpawnType.EVENT,null,null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(finalizeEvent);
            if (finalizeEvent.isCanceled()) throw new IllegalStateException("guardian_finalize_canceled");
            mob.finalizeSpawn(level,finalizeEvent.getDifficulty(),finalizeEvent.getSpawnType(),finalizeEvent.getSpawnData());
            if (!mob.isAlive() || mob.isRemoved() || !collisionFree(level,mob)
                    || !EventHooks.checkSpawnPosition(mob,level,MobSpawnType.EVENT)) throw new IllegalStateException("guardian_finalize_failed");
            if (!level.addFreshEntity(mob) || !mob.isAlive() || mob.isRemoved()
                    || level.getEntity(mob.getUUID())!=mob || !collisionFree(level,mob)) throw new IllegalStateException("guardian_spawn_failed");
            // No target assignment, tether, teleport, kill reward, or region failure rule.
            return true;
        } catch (RuntimeException failure) { mob.discard(); quarantine(ledger,site.siteId()); return false; }
    }
    private static boolean collisionFree(ServerLevel level, Mob mob) {
        var box = mob.getBoundingBox();
        for (int x=(int)Math.floor(box.minX); x<=(int)Math.floor(box.maxX); x++)
            for (int z=(int)Math.floor(box.minZ); z<=(int)Math.floor(box.maxZ); z++)
                if (!level.hasChunk(x>>4,z>>4) || !level.getWorldBorder().isWithinBounds(new BlockPos(x,mob.blockPosition().getY(),z))) return false;
        return level.noCollision(mob) && !level.containsAnyLiquid(box);
    }

    /** Counts actual committed blocks or the unique marked barrel; never copies the ledger count into a container.
     * Returns -1 while unloaded/conflicted; caller must pause/quarantine rather than infer harvest or death. */
    public static Observation observe(ServerLevel level, FiniteSite site, Selection selection) {
        // Terminal sites are never observed again: callers must not receive a number they would feed back
        // into observeRemaining/destroyCarrier, which reject terminal phases and would abort the server thread.
        if (site.terminal()) return new Observation(false,false,false,-1);
        Resolved r = resolve(level,selection);
        if (!compatible(site,r,selection) || !site.dimension().equals(level.dimension().location().toString()))
            return new Observation(false,false,false,-1);
        int remaining=0; boolean root=true;
        for (var slot : site.slots()) {
            BlockPos p=pos(slot);
            if (!level.hasChunkAt(p)) return new Observation(false,false,false,-1);
            root &= rootMatches(level,slot);
            if (!slot.materialized()) continue;
            if (site.kind()==FiniteSite.Kind.GUARDED) {
                if (!matchesOwned(level,site,p) || !(level.getBlockEntity(p) instanceof BarrelBlockEntity barrel))
                    return new Observation(true,false,root,0);
                for (int i=0;i<barrel.getContainerSize();i++) {
                    ItemStack stack=barrel.getItem(i);
                    if (!stack.isEmpty() && !stack.is(r.item)) return new Observation(true,true,root,-1);
                    remaining+=stack.getCount();
                }
            } else if (fingerprint(level.getBlockState(p)).equals(physicalState(slot))) remaining++;
            else if (!SiteProtection.replaceable(level.getBlockState(p))) return new Observation(true,false,root,-1);
        }
        // Unexpected additions/replacements cannot enlarge the finite ledger supply.
        if (remaining>site.inventory().physicalRemaining()) return new Observation(true,true,root,-1);
        return new Observation(true,site.kind()==FiniteSite.Kind.GUARDED || root,root,remaining);
    }
    public static GuardianStatus guardianStatus(ServerLevel level, FiniteSite site) {
        if (site.guardians().isEmpty()) return GuardianStatus.NONE;
        if (!site.dimension().equals(level.dimension().location().toString())) return GuardianStatus.UNLOADED_OR_ABSENT;
        Entity entity=level.getEntity(site.guardians().get(0));
        if (entity==null) return GuardianStatus.UNLOADED_OR_ABSENT;
        var chosen=selection(site);
        if (!(entity instanceof Mob) || !entity.getPersistentData().hasUUID(MARKER)
                || !site.siteId().equals(entity.getPersistentData().getUUID(MARKER)) || chosen.isEmpty()
                || !guardianTypeMatches(entity, chosen.get().guardian())) return GuardianStatus.CONFLICT;
        return entity.isAlive() && !entity.isRemoved() ? GuardianStatus.ALIVE_LOADED : GuardianStatus.DEAD_LOADED;
    }
    /** Loaded plant time is accounted by the service; this method checks the soil, water, light and biome only. */
    public static boolean growthConditions(ServerLevel level, FiniteSite site, Selection selection) {
        if (site.kind()!=FiniteSite.Kind.PLANT || !site.rootIntact() || site.terminal()) return false;
        Resolved r=resolve(level,selection);
        if (!compatible(site,r,selection) || !site.dimension().equals(level.dimension().location().toString())) return false;
        for (var slot:site.slots()) {
            BlockPos p=pos(slot);
            if (!loadedSquare(level,p,4) || !rootMatches(level,slot) || !growthEnvironment(level,p,r.state)) return false;
        }
        return true;
    }
    public static Optional<Clue> clue(ServerLevel level, FiniteSite site, BlockPos position) {
        if (!site.dimension().equals(level.dimension().location().toString()) || site.terminal()
                || site.slots().stream().noneMatch(s -> pos(s).equals(position) && s.materialized())
                || !matchesOwned(level,site,position)) return Optional.empty();
        return Optional.of(new Clue(site.siteId(),site.dimension(),site.x(),site.y(),site.z(),site.kind(),
                site.inventory().initial(),site.inventory().lifetimeGrowthBudget(),site.phase()));
    }
    static boolean matchesOwned(ServerLevel level, FiniteSite site, BlockPos p) {
        // 3.14.1：这个方法在 32 格球壳里被每个位置调用一次（约 1.3×10^5 次/次扫描）。
        // 原写法 site.slots().stream().filter(s -> pos(s).equals(p)) 会为每个槽位 new 一个 BlockPos，
        // 单次扫描就是 10^7 级别的临时对象。改成整数比较，语义完全不变（pos(s).equals(p) 即 x/y/z 相等），
        // 并且把 hasChunkAt 挪到"确实命中槽位"之后，没命中的位置不再付区块查询。
        FiniteSite.Slot slot = null;
        for (FiniteSite.Slot s : site.slots()) {
            if (s.materialized() && s.x() == p.getX() && s.y() == p.getY() && s.z() == p.getZ()) {
                slot = s;
                break;
            }
        }
        if (slot == null || !level.hasChunkAt(p)) return false;
        if (!fingerprint(level.getBlockState(p)).equals(physicalState(slot))) return false;
        if (site.kind()!=FiniteSite.Kind.GUARDED) return level.getBlockEntity(p)==null && rootMatches(level,slot);
        return level.getBlockEntity(p) instanceof BarrelBlockEntity barrel && barrel.getPersistentData().hasUUID(MARKER)
                && site.siteId().equals(barrel.getPersistentData().getUUID(MARKER));
    }
    private static boolean growthEnvironment(ServerLevel level, BlockPos p, BlockState plant) {
        if (!loadedSquare(level,p,4) || !plant.canSurvive(level,p) || !solidNaturalRoot(level,p)
                || level.getMaxLocalRawBrightness(p)<9) return false;
        var soil=BuiltInRegistries.BLOCK.getKey(level.getBlockState(p.below()).getBlock());
        // Same list as the plant template whitelist (SiteTerrain), so a site can only ever be built where
        // it is also allowed to grow. guzhenren 苍木林海/迷思幻林 are included; unknown biome never matches.
        if (!soil.getNamespace().equals("minecraft") || !SOILS.contains(soil.getPath())
                || !SiteTerrain.biomeAllowed(SiteTerrain.PLANT_BIOMES, biomeId(level,p))) return false;
        for (BlockPos water:BlockPos.betweenClosed(p.offset(-4,-1,-4),p.offset(4,0,4)))
            if (level.getFluidState(water).is(FluidTags.WATER)) return true;
        return false;
    }
    private static boolean loadedSquare(ServerLevel level, BlockPos p, int radius) {
        for (int x=(p.getX()-radius)>>4;x<=(p.getX()+radius)>>4;x++)
            for (int z=(p.getZ()-radius)>>4;z<=(p.getZ()+radius)>>4;z++) if (!level.hasChunk(x,z)) return false;
        return true;
    }
    private static boolean solidNaturalRoot(ServerLevel level,BlockPos p) {
        return level.hasChunkAt(p) && SiteProtection.natural(level.getBlockState(p.below()))
                && level.getBlockState(p.below()).isSolidRender(level,p.below()) && level.getBlockEntity(p.below())==null;
    }
    private static BlockPos pos(FiniteSite.Slot slot) { return new BlockPos(slot.x(),slot.y(),slot.z()); }
    private static boolean compatible(FiniteSite site, Resolved r, Selection selection) {
        return r!=null && site.templateVersion()==1 && site.template().equals(selection.template())
                && site.kind()==r.candidate.kind() && site.inventory().initial()>=r.candidate.minimumInitial()
                && site.inventory().initial()<=r.candidate.maximumInitial()
                && site.slots().size()==(site.kind()==FiniteSite.Kind.GUARDED ? 1 : site.inventory().initial())
                && site.slots().stream().allMatch(s -> s.physicalFingerprint().equals(binding(r,selection)));
    }
    private static String binding(Resolved r,Selection selection) {
        return fingerprint(r.state)+"|material="+selection.material()+"|guardian="+(selection.guardian()==null ? "" : selection.guardian());
    }
    private static String physicalState(FiniteSite.Slot slot) { return slot.physicalFingerprint().split("\\|material=",2)[0]; }
    private static String fingerprint(BlockState state) {
        // OPEN is transient inventory UI state; facing and carrier UUID remain part of identity.
        if (state.is(Blocks.BARREL)) state=state.setValue(net.minecraft.world.level.block.BarrelBlock.OPEN,false);
        return state.toString();
    }
    private static String originalState(FiniteSite.Slot slot) { return slot.originalFingerprint().split("\\|root=",2)[0]; }
    private static boolean rootMatches(ServerLevel level,FiniteSite.Slot slot) {
        String[] parts=slot.originalFingerprint().split("\\|root=",2);
        return parts.length==2 && parts[1].equals(fingerprint(level.getBlockState(pos(slot).below())));
    }
    private static void quarantine(FiniteSiteState ledger,UUID id) {
        FiniteSite current=ledger.sites().get(id);
        if (current!=null && !current.terminal()) ledger.replace(current,current.quarantine());
    }
}
