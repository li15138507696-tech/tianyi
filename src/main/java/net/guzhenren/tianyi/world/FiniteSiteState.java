package net.guzhenren.tianyi.world;

import java.util.*;
import net.guzhenren.tianyi.resource.FiniteSite;
import net.guzhenren.tianyi.resource.SiteInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Bounded, fail-closed ledger. No physical world operations, spawning, or automatic replenishment. */
public final class FiniteSiteState extends SavedData {
    public static final String STORAGE_KEY = "tianyi_finite_sites";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SITES = 64, MAX_REGIONS = 4096, MAX_TRANSACTIONS = 4096;
    /** Minimum horizontal separation between two sites. Closer slots would collide, so the first
     * materializer wins and the second becomes a permanently unplaceable site. */
    private static final int MIN_SITE_SEPARATION = 32;
    public static final String SITES = "sites", REGIONS = "regionMarkers",
            TOMBSTONES = "tombstones", TRANSACTIONS = "transactions", QUARANTINE = "quarantine";
    private final Map<UUID, FiniteSite> sites = new LinkedHashMap<>();
    private final Map<String, UUID> regions = new LinkedHashMap<>();
    private final Map<UUID, String> tombstones = new LinkedHashMap<>();
    private final Map<UUID, FiniteSite.Transaction> transactions = new LinkedHashMap<>();
    private CompoundTag preserved;
    /** Opaque legacy marker rows, round-tripped only. Isolation is expressed per site by Phase.QUARANTINED,
     * so a non-empty list never freezes unrelated sites. */
    private ListTag quarantine = new ListTag();
    private String failure = "";
    public FiniteSiteState() {}
    public static FiniteSiteState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FiniteSiteState::new, FiniteSiteState::load, null), STORAGE_KEY);
    }
    public boolean recognizedEnvelope() { return preserved == null; }
    /** Only an unparseable whole envelope stops the ledger. Per-site isolation lives in the site phase. */
    public boolean mutationReady() { return preserved == null; }
    public String failureReason() { return failure; }
    public Map<UUID, FiniteSite> sites() { return Map.copyOf(sites); }
    public Map<String, UUID> regions() { return Map.copyOf(regions); }
    public Map<UUID, String> tombstones() { return Map.copyOf(tombstones); }
    public Map<UUID, FiniteSite.Transaction> transactions() { return Map.copyOf(transactions); }
    private void ready() { if (!mutationReady()) throw new IllegalStateException("quarantined ledger"); }
    /** Region markers survive retirement. Reaching a bound stops generation, never evicts history. */
    public boolean reserve(FiniteSite site) {
        ready(); Objects.requireNonNull(site);
        if (sites.containsKey(site.siteId())) return sites.get(site.siteId()).equals(site);
        if (site.phase() != FiniteSite.Phase.RESERVED || site.revision() != 0
                || site.inventory().physicalRemaining() != 0 || site.inventory().verifiedPlantGrowth() != 0
                || site.slots().isEmpty() || site.slots().stream().anyMatch(FiniteSite.Slot::materialized)
                || !site.guardians().isEmpty()) throw new IllegalArgumentException("invalid reservation");
        if (sites.size() >= MAX_SITES || regions.size() >= MAX_REGIONS || regions.containsKey(site.regionMarker())
                || regions.containsValue(site.siteId()) || tombstones.containsKey(site.siteId())) return false;
        if (tooCloseToExistingSite(site)) return false;
        sites.put(site.siteId(), site); regions.put(site.regionMarker(), site.siteId()); setDirty(); return true;
    }
    /** Rejects a candidate whose slots overlap the neighbourhood of a live site in the same dimension. */
    private boolean tooCloseToExistingSite(FiniteSite candidate) {
        long limit = (long)MIN_SITE_SEPARATION*MIN_SITE_SEPARATION;
        for (FiniteSite existing : sites.values()) {
            if (!existing.dimension().equals(candidate.dimension())) continue;
            for (FiniteSite.Slot a : candidate.slots())
                for (FiniteSite.Slot b : existing.slots()) {
                    long dx = (long)a.x()-b.x(), dz = (long)a.z()-b.z();
                    if (dx*dx+dz*dz < limit) return true;
                }
        }
        return false;
    }
    /** Compare-and-set against the immutable snapshot. No stale writer may restore physical stock. */
    public boolean replace(FiniteSite expected, FiniteSite next) {
        ready();
        if (!expected.equals(sites.get(expected.siteId()))) return false;
        if (expected.equals(next)) return true;
        int growth = next.inventory().verifiedPlantGrowth() - expected.inventory().verifiedPlantGrowth();
        if (!sameIdentity(expected, next) || expected.revision() == Long.MAX_VALUE
                || next.revision() != expected.revision() + 1 || !legalPhase(expected.phase(), next.phase())
                || !next.guardians().containsAll(expected.guardians())
                || next.inventory().verifiedPlantGrowth() < expected.inventory().verifiedPlantGrowth()
                || next.inventory().transferredOut() < expected.inventory().transferredOut()
                || next.inventory().destroyed() < expected.inventory().destroyed()
                // Pending stock may only grow by the single legal plant growth below; nothing else may add supply.
                || (growth != 1 && next.inventory().unmaterialized() > expected.inventory().unmaterialized())
                || (!expected.rootIntact() && next.rootIntact())) throw new IllegalArgumentException("invalid replacement");
        if (growth > 1 || (growth == 1 && (expected.kind()!=FiniteSite.Kind.PLANT
                || next.phase()!=FiniteSite.Phase.MATERIALIZING || next.loadedGrowthTicks()!=0
                || (expected.phase()!=FiniteSite.Phase.ACTIVE && expected.phase()!=FiniteSite.Phase.DORMANT)))
                || (!next.guardians().equals(expected.guardians()) && expected.phase()!=FiniteSite.Phase.MATERIALIZING))
            throw new IllegalArgumentException("invalid growth/guardian update");
        for (int i = 0; i < expected.slots().size(); i++) {
            var a = expected.slots().get(i); var b = next.slots().get(i);
            if (a.index() != b.index() || a.x() != b.x() || a.y() != b.y() || a.z() != b.z()
                    || !a.originalFingerprint().equals(b.originalFingerprint()) || !a.physicalFingerprint().equals(b.physicalFingerprint())
                    || (a.materialized() && !b.materialized())) throw new IllegalArgumentException("changed slot binding");
        }
        sites.put(next.siteId(), next); setDirty(); return true;
    }
    private static boolean sameIdentity(FiniteSite a, FiniteSite b) {
        return a.siteId().equals(b.siteId()) && a.dimension().equals(b.dimension()) && a.x()==b.x() && a.y()==b.y() && a.z()==b.z()
                && a.template().equals(b.template()) && a.templateVersion()==b.templateVersion() && a.kind()==b.kind()
                && a.regionMarker().equals(b.regionMarker()) && a.inventory().initial()==b.inventory().initial()
                && a.inventory().lifetimeGrowthBudget()==b.inventory().lifetimeGrowthBudget() && a.slots().size()==b.slots().size();
    }
    private static boolean legalPhase(FiniteSite.Phase a, FiniteSite.Phase b) {
        if (a == FiniteSite.Phase.QUARANTINED || a == FiniteSite.Phase.DESTROYED || a == FiniteSite.Phase.DEPLETED) return false;
        if (b == FiniteSite.Phase.QUARANTINED || b == FiniteSite.Phase.DESTROYED) return true;
        return switch (a) {
            case RESERVED -> b == FiniteSite.Phase.MATERIALIZING;
            case MATERIALIZING -> b == a || b == FiniteSite.Phase.ACTIVE;
            case ACTIVE -> b == a || b == FiniteSite.Phase.DORMANT || b == FiniteSite.Phase.DEPLETED || b == FiniteSite.Phase.MATERIALIZING;
            case DORMANT -> b == a || b == FiniteSite.Phase.MATERIALIZING || b == FiniteSite.Phase.DEPLETED;
            default -> false;
        };
    }
    /** Terminal sites retire. A stuck MATERIALIZING site with no physical units left may retire too, so an
     * unplaceable slot cannot hold a MAX_SITES place forever; unplaced stock is virtual and is written off. */
    public boolean retire(UUID id) {
        ready(); FiniteSite site = sites.get(id);
        if (site == null) return tombstones.containsKey(id);
        if ((site.phase()!=FiniteSite.Phase.DESTROYED && site.phase()!=FiniteSite.Phase.DEPLETED
                && !(site.phase()==FiniteSite.Phase.MATERIALIZING && site.inventory().physicalRemaining()==0))
                || transactions.values().stream().anyMatch(t -> t.siteId().equals(id) && t.phase()!=FiniteSite.TxPhase.COMMITTED)) return false;
        if (tombstones.size() >= MAX_REGIONS) return false;
        tombstones.put(id, site.regionMarker()); sites.remove(id); setDirty(); return true;
    }
    /** Explicit operator recovery: drop one transaction row the human has already reconciled in the world.
     * It never replays the write and never adds stock. A still-pending row is only clearable once its site is
     * quarantined/terminal (or already tombstoned), so a live site cannot silently forget an unresolved write. */
    public boolean clearTransaction(UUID txnId) {
        ready(); if (txnId == null) return false;
        FiniteSite.Transaction tx = transactions.get(txnId);
        if (tx == null) return false;
        if (tx.phase() != FiniteSite.TxPhase.COMMITTED) {
            FiniteSite site = sites.get(tx.siteId());
            if (site != null && !site.terminal()) return false;
        }
        transactions.remove(txnId); setDirty(); return true;
    }
    /** Explicit operator recovery for one quarantined site: clears its pending rows first, then restores the
     * phase its accounting proves. Returns false when the site is missing, not quarantined, or still unresolved. */
    public boolean releaseQuarantine(UUID id) {
        ready(); FiniteSite site = sites.get(id);
        if (site == null || site.phase() != FiniteSite.Phase.QUARANTINED) return false;
        if (transactions.values().stream().anyMatch(t -> t.siteId().equals(id) && t.phase()!=FiniteSite.TxPhase.COMMITTED)) return false;
        try {
            sites.put(id, site.releaseQuarantine()); setDirty(); return true;
        } catch (IllegalStateException exhausted) {
            return false;
        }
    }
    public boolean prepare(FiniteSite.Transaction tx) {
        ready(); var old = transactions.get(tx.txnId());
        if (old != null) return old.equals(tx);
        FiniteSite site = sites.get(tx.siteId());
        if (tx.phase()!=FiniteSite.TxPhase.PREPARED || site == null || site.phase()!=FiniteSite.Phase.MATERIALIZING
                || tx.revision()!=site.revision() || site.slots().stream().noneMatch(s -> s.index()==tx.slot()))
            throw new IllegalArgumentException("invalid transaction binding");
        if (transactions.size() >= MAX_TRANSACTIONS || transactions.values().stream().anyMatch(t -> t.siteId().equals(tx.siteId())
                && t.slot()==tx.slot() && (t.phase()!=FiniteSite.TxPhase.COMMITTED || t.revision()==tx.revision()))
                || (site.slots().stream().anyMatch(s -> s.index()==tx.slot() && s.materialized())
                    && !(site.kind()==FiniteSite.Kind.PLANT && site.inventory().unmaterialized()==1
                        && site.slots().stream().allMatch(FiniteSite.Slot::materialized)))) return false;
        transactions.put(tx.txnId(), tx); setDirty(); return true;
    }
    /** APPLIED requires adapter evidence. PREPARED after a crash is uncertain: quarantine, never blindly replay. */
    public boolean advance(UUID id, FiniteSite.TxPhase expected, FiniteSite.TxPhase next) {
        ready(); var tx = transactions.get(id); if (tx == null) return false;
        if (tx.phase()==next) return true;
        if (tx.phase()!=expected) return false;
        if (next.ordinal()!=expected.ordinal()+1) throw new IllegalArgumentException("transaction phase skip");
        if (next == FiniteSite.TxPhase.COMMITTED) {
            FiniteSite site = sites.get(tx.siteId());
            if (site == null || site.revision() <= tx.revision()
                    || (site.kind()==FiniteSite.Kind.PLANT && site.inventory().unmaterialized()>0
                        && site.slots().stream().allMatch(FiniteSite.Slot::materialized))
                    || site.slots().stream().noneMatch(s -> s.index()==tx.slot() && s.materialized()))
                throw new IllegalStateException("materialization not recorded");
        }
        transactions.put(id, new FiniteSite.Transaction(id, tx.siteId(), tx.slot(), tx.revision(), next)); setDirty(); return true;
    }
    @Override public CompoundTag save(CompoundTag destination, HolderLookup.Provider registries) {
        if (preserved != null) return preserved.copy();
        CompoundTag out = new CompoundTag(); out.putInt("schemaVersion", SCHEMA_VERSION);
        ListTag rows = new ListTag(); for (FiniteSite s : sites.values()) rows.add(encodeSite(s)); out.put(SITES, rows);
        rows = new ListTag();
        for (var e : regions.entrySet()) { CompoundTag r = new CompoundTag(); r.putString("region", e.getKey()); r.putString("siteId", e.getValue().toString()); rows.add(r); } out.put(REGIONS, rows);
        rows = new ListTag();
        for (var e : tombstones.entrySet()) { CompoundTag r = new CompoundTag(); r.putString("siteId", e.getKey().toString()); r.putString("region", e.getValue()); rows.add(r); } out.put(TOMBSTONES, rows);
        rows = new ListTag();
        for (var tx : transactions.values()) { CompoundTag r = new CompoundTag(); r.putString("txnId", tx.txnId().toString()); r.putString("siteId", tx.siteId().toString()); r.putInt("slot", tx.slot()); r.putLong("revision", tx.revision()); r.putString("phase", tx.phase().name()); rows.add(r); } out.put(TRANSACTIONS, rows);
        out.put(QUARANTINE, quarantine.copy()); return out;
    }
    public static FiniteSiteState load(CompoundTag tag, HolderLookup.Provider registries) {
        FiniteSiteState state = new FiniteSiteState();
        try {
            keys(tag, "schemaVersion", SITES, REGIONS, TOMBSTONES, TRANSACTIONS, QUARANTINE);
            if (integer(tag,"schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unknown schema");
            for (Tag t : list(tag, SITES, MAX_SITES)) { FiniteSite site = decodeSite((CompoundTag)t); unique(state.sites, site.siteId(), site); }
            for (Tag t : list(tag, REGIONS, MAX_REGIONS)) { CompoundTag r=(CompoundTag)t; keys(r,"region","siteId"); unique(state.regions, text(r,"region"), uuid(r,"siteId")); }
            for (Tag t : list(tag, TOMBSTONES, MAX_REGIONS)) { CompoundTag r=(CompoundTag)t; keys(r,"region","siteId"); unique(state.tombstones, uuid(r,"siteId"), text(r,"region")); }
            for (Tag t : list(tag, TRANSACTIONS, MAX_TRANSACTIONS)) {
                CompoundTag r=(CompoundTag)t; keys(r,"txnId","siteId","slot","revision","phase");
                var tx=new FiniteSite.Transaction(uuid(r,"txnId"),uuid(r,"siteId"),integer(r,"slot"),number(r,"revision"),FiniteSite.TxPhase.valueOf(text(r,"phase")));
                unique(state.transactions,tx.txnId(),tx);
            }
            state.quarantine = list(tag, QUARANTINE, MAX_REGIONS).copy();
            Set<UUID> regionOwners = new HashSet<>();
            for (var e : state.regions.entrySet()) {
                if (!regionOwners.add(e.getValue())) throw new IllegalArgumentException("duplicate region owner");
                FiniteSite s=state.sites.get(e.getValue()); String tomb=state.tombstones.get(e.getValue());
                if (s==null ? !e.getKey().equals(tomb) : tomb!=null || !e.getKey().equals(s.regionMarker())) throw new IllegalArgumentException("orphan region");
            }
            for (var s : state.sites.values()) if (!s.siteId().equals(state.regions.get(s.regionMarker()))) throw new IllegalArgumentException("missing region");
            for (var e : state.tombstones.entrySet()) if (!e.getKey().equals(state.regions.get(e.getValue())) || state.sites.containsKey(e.getKey())) throw new IllegalArgumentException("invalid tombstone");
            Set<String> pending = new HashSet<>();
            Set<UUID> affected = new LinkedHashSet<>();
            for (var tx : state.transactions.values()) {
                FiniteSite s=state.sites.get(tx.siteId());
                if (s == null) { if (!state.tombstones.containsKey(tx.siteId()) || tx.phase()!=FiniteSite.TxPhase.COMMITTED) throw new IllegalArgumentException("orphan transaction"); }
                else if (tx.revision()>s.revision() || s.slots().stream().noneMatch(slot -> slot.index()==tx.slot())) throw new IllegalArgumentException("invalid transaction reference");
                if (tx.phase()!=FiniteSite.TxPhase.COMMITTED) {
                    if (!pending.add(tx.siteId()+":"+tx.slot())) throw new IllegalArgumentException("duplicate pending slot");
                    affected.add(tx.siteId());
                }
            }
            // An unresolved write freezes only the sites it touched: it is never replayed and never refilled,
            // but the rest of the ledger keeps working. Operators clear the row and release the site explicitly.
            for (UUID id : affected) {
                FiniteSite s = state.sites.get(id);
                // A revision-exhausted site cannot be re-stamped; its pending row alone already blocks rewrites.
                if (s != null && !s.terminal() && s.revision() < Long.MAX_VALUE)
                    state.sites.put(id, s.quarantine());
            }
        } catch (RuntimeException ex) {
            state.sites.clear(); state.regions.clear(); state.tombstones.clear(); state.transactions.clear();
            state.preserved=tag.copy(); state.failure=ex.toString();
        }
        return state;
    }
    private static <K,V> void unique(Map<K,V> map, K key, V value) { if (map.putIfAbsent(key,value)!=null) throw new IllegalArgumentException("duplicate id"); }
    private static void keys(CompoundTag tag, String... names) {
        if (!tag.getAllKeys().equals(Set.of(names))) throw new IllegalArgumentException("unknown/missing fields");
    }
    private static void type(CompoundTag tag,String key,int type) { if (!tag.contains(key,type)) throw new IllegalArgumentException("wrong type: "+key); }
    private static String text(CompoundTag tag,String key) { type(tag,key,Tag.TAG_STRING); String s=tag.getString(key); if(s.isBlank() || s.length()>4096) throw new IllegalArgumentException("invalid text"); return s; }
    private static UUID uuid(CompoundTag tag,String key) { String s=text(tag,key); UUID id=UUID.fromString(s); if(!id.toString().equals(s)) throw new IllegalArgumentException("noncanonical UUID"); return id; }
    private static int integer(CompoundTag tag,String key) { type(tag,key,Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag,String key) { type(tag,key,Tag.TAG_LONG); return tag.getLong(key); }
    private static boolean bool(CompoundTag tag,String key) { type(tag,key,Tag.TAG_BYTE); byte b=tag.getByte(key); if(b!=0 && b!=1) throw new IllegalArgumentException("invalid boolean"); return b==1; }
    private static ListTag list(CompoundTag tag,String key,int max) {
        type(tag,key,Tag.TAG_LIST); ListTag all=(ListTag)tag.get(key); ListTag rows=tag.getList(key,Tag.TAG_COMPOUND);
        if(all.size()!=rows.size() || rows.size()>max) throw new IllegalArgumentException("invalid/big list: "+key); return rows;
    }
    public static CompoundTag encodeSite(FiniteSite s) {
        CompoundTag r=new CompoundTag();
        r.putString("siteId",s.siteId().toString()); r.putString("dimension",s.dimension()); r.putInt("x",s.x()); r.putInt("y",s.y()); r.putInt("z",s.z());
        r.putString("template",s.template()); r.putInt("templateVersion",s.templateVersion()); r.putString("kind",s.kind().name()); r.putString("phase",s.phase().name()); r.putLong("revision",s.revision()); r.putString("regionMarker",s.regionMarker());
        var v=s.inventory(); CompoundTag inv=new CompoundTag(); inv.putInt("initial",v.initial()); inv.putInt("verifiedPlantGrowth",v.verifiedPlantGrowth()); inv.putInt("lifetimeGrowthBudget",v.lifetimeGrowthBudget()); inv.putInt("unmaterialized",v.unmaterialized()); inv.putInt("physicalRemaining",v.physicalRemaining()); inv.putInt("transferredOut",v.transferredOut()); inv.putInt("destroyed",v.destroyed()); r.put("inventory",inv);
        ListTag slots=new ListTag(); for(var s1:s.slots()) { CompoundTag t=new CompoundTag(); t.putInt("index",s1.index()); t.putInt("x",s1.x()); t.putInt("y",s1.y()); t.putInt("z",s1.z()); t.putString("originalFingerprint",s1.originalFingerprint()); t.putString("physicalFingerprint",s1.physicalFingerprint()); t.putBoolean("materialized",s1.materialized()); slots.add(t); } r.put("slots",slots);
        ListTag guards=new ListTag(); for(UUID id:s.guardians()) { CompoundTag t=new CompoundTag(); t.putString("id",id.toString()); guards.add(t); } r.put("guardians",guards);
        r.putLong("loadedGrowthTicks",s.loadedGrowthTicks()); r.putBoolean("rootIntact",s.rootIntact()); return r;
    }
    public static FiniteSite decodeSite(CompoundTag r) {
        keys(r,"siteId","dimension","x","y","z","template","templateVersion","kind","phase","revision","regionMarker","inventory","slots","guardians","loadedGrowthTicks","rootIntact");
        type(r,"inventory",Tag.TAG_COMPOUND); CompoundTag v=r.getCompound("inventory");
        keys(v,"initial","verifiedPlantGrowth","lifetimeGrowthBudget","unmaterialized","physicalRemaining","transferredOut","destroyed");
        SiteInventory inv=new SiteInventory(integer(v,"initial"),integer(v,"verifiedPlantGrowth"),integer(v,"lifetimeGrowthBudget"),integer(v,"unmaterialized"),integer(v,"physicalRemaining"),integer(v,"transferredOut"),integer(v,"destroyed"));
        List<FiniteSite.Slot> slots=new ArrayList<>(); for(Tag t:list(r,"slots",64)) { CompoundTag s=(CompoundTag)t; keys(s,"index","x","y","z","originalFingerprint","physicalFingerprint","materialized"); slots.add(new FiniteSite.Slot(integer(s,"index"),integer(s,"x"),integer(s,"y"),integer(s,"z"),text(s,"originalFingerprint"),text(s,"physicalFingerprint"),bool(s,"materialized"))); }
        List<UUID> guards=new ArrayList<>(); for(Tag t:list(r,"guardians",2)) { CompoundTag s=(CompoundTag)t; keys(s,"id"); guards.add(uuid(s,"id")); }
        return new FiniteSite(uuid(r,"siteId"),text(r,"dimension"),integer(r,"x"),integer(r,"y"),integer(r,"z"),text(r,"template"),integer(r,"templateVersion"),FiniteSite.Kind.valueOf(text(r,"kind")),FiniteSite.Phase.valueOf(text(r,"phase")),number(r,"revision"),text(r,"regionMarker"),inv,slots,guards,number(r,"loadedGrowthTicks"),bool(r,"rootIntact"));
    }
}
