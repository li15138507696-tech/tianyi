package net.guzhenren.tianyi.resource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistent data contract. World transitions/materialization belong to FiniteSiteService. */
public record FiniteSite(UUID siteId, String dimension, int x, int y, int z,
        String template, int templateVersion, Kind kind, Phase phase, long revision,
        String regionMarker, SiteInventory inventory, List<Slot> slots,
        List<UUID> guardians, long loadedGrowthTicks, boolean rootIntact) {
    public enum Kind { PLANT, ORE, GUARDED }
    public enum Phase { RESERVED, MATERIALIZING, ACTIVE, DORMANT, DEPLETED, DESTROYED, QUARANTINED }
    public enum TxPhase { PREPARED, APPLIED, COMMITTED }
    public record Slot(int index, int x, int y, int z, String originalFingerprint,
            String physicalFingerprint, boolean materialized) {
        public Slot {
            if (index < 0) throw new IllegalArgumentException("negative slot");
            Objects.requireNonNull(originalFingerprint);
            Objects.requireNonNull(physicalFingerprint);
            if (originalFingerprint.isBlank() || physicalFingerprint.isBlank()
                    || originalFingerprint.length() > 4096 || physicalFingerprint.length() > 4096)
                throw new IllegalArgumentException("invalid fingerprint");
        }
    }
    public record Transaction(UUID txnId, UUID siteId, int slot, long revision, TxPhase phase) {
        public Transaction {
            Objects.requireNonNull(txnId); Objects.requireNonNull(siteId); Objects.requireNonNull(phase);
            if (slot < 0 || revision < 0) throw new IllegalArgumentException("invalid transaction");
        }
    }
    public FiniteSite {
        Objects.requireNonNull(siteId); Objects.requireNonNull(kind); Objects.requireNonNull(phase);
        Objects.requireNonNull(inventory);
        if (!resourceId(dimension) || !resourceId(template) || regionMarker == null || regionMarker.isBlank()
                || templateVersion < 1 || revision < 0 || loadedGrowthTicks < 0 || loadedGrowthTicks >= 72000
                || regionMarker.length() > 256)
            throw new IllegalArgumentException("invalid site identity/version");
        slots = List.copyOf(slots); guardians = List.copyOf(guardians);
        if (slots.size() > 64 || guardians.size() > 2
                || slots.stream().map(Slot::index).distinct().count() != slots.size()
                || guardians.stream().distinct().count() != guardians.size())
            throw new IllegalArgumentException("invalid physical bindings");
        if (slots.stream().map(s -> s.x()+","+s.y()+","+s.z()).distinct().count() != slots.size())
            throw new IllegalArgumentException("overlapping slots");
        if (phase == Phase.RESERVED && (inventory.unmaterialized() != inventory.initial()
                || inventory.verifiedPlantGrowth() != 0 || inventory.physicalRemaining() != 0
                || inventory.transferredOut() != 0 || inventory.destroyed() != 0
                || slots.stream().anyMatch(Slot::materialized) || !guardians.isEmpty()))
            throw new IllegalArgumentException("invalid reservation state");
        if ((phase == Phase.ACTIVE || phase == Phase.DORMANT) && (inventory.unmaterialized() != 0
                || slots.isEmpty() || slots.stream().anyMatch(s -> !s.materialized())))
            throw new IllegalArgumentException("incomplete active state");
        if (phase == Phase.DORMANT && (kind != Kind.PLANT || !rootIntact
                || inventory.physicalRemaining() != 0 || inventory.verifiedPlantGrowth() >= inventory.lifetimeGrowthBudget()))
            throw new IllegalArgumentException("invalid dormant state");
        if ((phase == Phase.DEPLETED || phase == Phase.DESTROYED)
                && (inventory.unmaterialized() != 0 || inventory.physicalRemaining() != 0))
            throw new IllegalArgumentException("terminal inventory remains");
        if (phase == Phase.DESTROYED && rootIntact) throw new IllegalArgumentException("destroyed root intact");
        if (kind != Kind.GUARDED && !guardians.isEmpty()) throw new IllegalArgumentException("non-guarded binding");
        if (kind != Kind.PLANT && (inventory.verifiedPlantGrowth() != 0 || inventory.lifetimeGrowthBudget() != 0 || loadedGrowthTicks != 0))
            throw new IllegalArgumentException("non-plant growth");
    }
    public static final long GROWTH_TICKS = 72000;
    public boolean terminal() { return phase == Phase.DESTROYED || phase == Phase.DEPLETED || phase == Phase.QUARANTINED; }
    private FiniteSite updated(Phase next, SiteInventory stock, List<Slot> bindings,
            List<UUID> guards, long ticks, boolean root) {
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("revision exhausted");
        return new FiniteSite(siteId, dimension, x, y, z, template, templateVersion, kind,
                next, revision + 1, regionMarker, stock, bindings, guards, ticks, root);
    }
    public FiniteSite beginMaterialization() {
        if (phase == Phase.MATERIALIZING) return this;
        if (phase != Phase.RESERVED) throw new IllegalStateException("not reserved");
        return updated(Phase.MATERIALIZING, inventory, slots, guardians, loadedGrowthTicks, rootIntact);
    }
    /** Retry of an already recorded slot is a no-op. Quantity is verified by the world adapter. */
    public FiniteSite materializeSlot(int index, int units) {
        Slot slot = slots.stream().filter(s -> s.index() == index).findFirst().orElseThrow();
        if (slot.materialized()) return this;
        if (phase != Phase.MATERIALIZING) throw new IllegalStateException("not materializing");
        List<Slot> next = slots.stream().map(s -> s.index() == index
                ? new Slot(s.index(), s.x(), s.y(), s.z(), s.originalFingerprint(), s.physicalFingerprint(), true) : s).toList();
        return updated(next.stream().allMatch(Slot::materialized) && inventory.unmaterialized() == units
                ? Phase.ACTIVE : Phase.MATERIALIZING, inventory.materialize(units), next, guardians, loadedGrowthTicks, rootIntact);
    }
    public FiniteSite bindGuardian(UUID id) {
        Objects.requireNonNull(id);
        if (guardians.contains(id)) return this;
        if (kind != Kind.GUARDED || phase != Phase.MATERIALIZING || guardians.size() >= 2)
            throw new IllegalStateException("guardian binding closed");
        var next = new java.util.ArrayList<>(guardians); next.add(id);
        return updated(phase, inventory, slots, next, loadedGrowthTicks, rootIntact);
    }
    /** Absence/unloaded entities never clears bindings or authorizes a replacement. */
    public boolean matchesGuardianDeath(UUID id) { return guardians.contains(id); }
    public FiniteSite observeRemaining(int remaining, boolean destroyedLoss) {
        if (remaining < 0 || remaining > inventory.physicalRemaining()) throw new IllegalArgumentException("cannot restock");
        if (remaining == inventory.physicalRemaining()) return this;
        SiteInventory next = inventory.removePhysical(inventory.physicalRemaining() - remaining, destroyedLoss);
        Phase p = remaining == 0 && next.unmaterialized() == 0
                ? (kind == Kind.PLANT && rootIntact && next.verifiedPlantGrowth() < next.lifetimeGrowthBudget() ? Phase.DORMANT : Phase.DEPLETED) : phase;
        return updated(p, next, slots, guardians, loadedGrowthTicks, rootIntact);
    }
    public FiniteSite destroyCarrier() {
        if (phase == Phase.DESTROYED) return this;
        return updated(Phase.DESTROYED, inventory.destroyRemaining(), slots, guardians, 0, false);
    }
    /** Caller passes only loaded elapsed ticks; unloaded time is deliberately ignored. */
    public FiniteSite growLoaded(long elapsed, boolean loaded, boolean environmentValid) {
        if (elapsed < 0) throw new IllegalArgumentException("negative elapsed");
        if (!loaded || !environmentValid || !rootIntact || kind != Kind.PLANT
                || (phase != Phase.ACTIVE && phase != Phase.DORMANT)
                || inventory.verifiedPlantGrowth() == inventory.lifetimeGrowthBudget() || elapsed == 0) return this;
        long ticks = loadedGrowthTicks + Math.min(elapsed, GROWTH_TICKS);
        if (ticks < GROWTH_TICKS) return updated(phase, inventory, slots, guardians, ticks, true);
        // Growth creates one pending unit, never a physical refill. Adapter must transact its placement.
        return updated(Phase.MATERIALIZING, inventory.growVerified(1), slots, guardians, 0, true);
    }
    public FiniteSite materializeGrowth() {
        if (kind != Kind.PLANT || phase != Phase.MATERIALIZING || inventory.unmaterialized() != 1
                || slots.stream().anyMatch(s -> !s.materialized())) throw new IllegalStateException("not pending growth");
        return updated(Phase.ACTIVE, inventory.materialize(1), slots, guardians, loadedGrowthTicks, rootIntact);
    }
    public FiniteSite quarantine() {
        return phase == Phase.QUARANTINED ? this : updated(Phase.QUARANTINED, inventory, slots, guardians, loadedGrowthTicks, rootIntact);
    }
    /** Operator escape hatch for a site whose slots can never be placed (player occupied the spot, soil
     * replaced). Remaining unmaterialized and physical stock is booked as destroyed; the site ends in the
     * explicit terminal phase DESTROYED so it can be retired and stops occupying a site slot.
     * It never refills and never replays a transaction. The caller must have verified that the remaining
     * slots hold nothing recoverable, because this method has no world access. */
    public FiniteSite abandon() {
        if (phase != Phase.MATERIALIZING && phase != Phase.RESERVED)
            throw new IllegalStateException("not abandonable");
        return updated(Phase.DESTROYED, inventory.destroyRemaining(), slots, guardians, 0, false);
    }
    /** Operator escape hatch for one quarantined site after manual reconciliation. Returns to the phase the
     * current accounting proves: MATERIALIZING while units are still pending, ACTIVE while physical stock
     * remains, otherwise DORMANT (plant, budget left) or DEPLETED. Never replays a transaction, never refills. */
    public FiniteSite releaseQuarantine() {
        if (phase != Phase.QUARANTINED) throw new IllegalStateException("not quarantined");
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("revision exhausted");
        return updated(resumePhase(), inventory, slots, guardians, loadedGrowthTicks, rootIntact);
    }
    private Phase resumePhase() {
        if (inventory.unmaterialized() != 0 || slots.isEmpty() || slots.stream().anyMatch(s -> !s.materialized()))
            return Phase.MATERIALIZING;
        if (inventory.physicalRemaining() != 0) return Phase.ACTIVE;
        return kind == Kind.PLANT && rootIntact && inventory.verifiedPlantGrowth() < inventory.lifetimeGrowthBudget()
                ? Phase.DORMANT : Phase.DEPLETED;
    }
    public static boolean resourceId(String id) {
        return id != null && id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }
}
