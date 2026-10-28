package net.guzhenren.tianyi.resource;

/** Integer accounting snapshot only; physical containers remain authoritative. */
public record SiteInventory(int initial, int verifiedPlantGrowth, int lifetimeGrowthBudget,
        int unmaterialized, int physicalRemaining, int transferredOut, int destroyed) {
    public SiteInventory {
        if (initial < 0 || verifiedPlantGrowth < 0 || lifetimeGrowthBudget < 0
                || unmaterialized < 0 || physicalRemaining < 0 || transferredOut < 0 || destroyed < 0)
            throw new IllegalArgumentException("negative inventory component");
        if (verifiedPlantGrowth > lifetimeGrowthBudget)
            throw new IllegalArgumentException("growth exceeds lifetime budget");
        if ((long) initial + verifiedPlantGrowth != (long) unmaterialized + physicalRemaining + transferredOut + destroyed)
            throw new IllegalArgumentException("inventory is not conserved");
    }
    public static SiteInventory reserved(int initial, int lifetimeGrowthBudget) {
        return new SiteInventory(initial, 0, lifetimeGrowthBudget, initial, 0, 0, 0);
    }
    /** Call after the world adapter verifies loaded time, root and environment.
     * Positive units consume remaining lifetime budget and enter unmaterialized stock.
     * Invalid/nonpositive units or exhausted budget must throw IllegalArgumentException.
     * A zero budget (all ore/guarded sites) can never grow. */
    public SiteInventory growVerified(int units) {
        if (units <= 0 || units > lifetimeGrowthBudget - verifiedPlantGrowth
                || (long) unmaterialized + units > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid finite growth");
        return new SiteInventory(initial, verifiedPlantGrowth + units, lifetimeGrowthBudget,
                unmaterialized + units, physicalRemaining, transferredOut, destroyed);
    }
    public SiteInventory materialize(int units) {
        require(units, unmaterialized);
        return new SiteInventory(initial, verifiedPlantGrowth, lifetimeGrowthBudget,
                unmaterialized - units, add(physicalRemaining, units), transferredOut, destroyed);
    }
    /** Call only with verified physical observations; never refill a missing container. */
    public SiteInventory removePhysical(int units, boolean lost) {
        require(units, physicalRemaining);
        return new SiteInventory(initial, verifiedPlantGrowth, lifetimeGrowthBudget, unmaterialized,
                physicalRemaining - units, lost ? transferredOut : add(transferredOut, units),
                lost ? add(destroyed, units) : destroyed);
    }
    public SiteInventory destroyRemaining() {
        return new SiteInventory(initial, verifiedPlantGrowth, lifetimeGrowthBudget, 0, 0,
                transferredOut, add(destroyed, add(unmaterialized, physicalRemaining)));
    }
    private static int add(int a, int b) {
        if ((long) a + b > Integer.MAX_VALUE) throw new IllegalArgumentException("inventory overflow");
        return a + b;
    }
    private static void require(int units, int available) {
        if (units <= 0 || units > available) throw new IllegalArgumentException("invalid quantity");
    }
    public long totalSupply() { return (long) initial + verifiedPlantGrowth; }
}
