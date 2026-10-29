package net.guzhenren.tianyi.world;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Stable-slot source adapter for the standard server-owned player inventory. It only proves the
 * selected bound stack is unique across the player's 41 inventory slots; it does not prove the
 * upstream cached copy or arbitrary external containers cannot be used as another source.
 */
public final class PlayerInventoryGuEscrowSource implements GuEscrowSource {
    public static final int PLAYER_INVENTORY_SIZE = 41;
    private static final String SOURCE_PREFIX = "player_inventory/v1/";

    interface InventoryAccess {
        int size();
        ItemStack get(int slot);
        void set(int slot, ItemStack stack);
        void setChanged();
    }

    private record SourceRef(UUID ownerId, int slot) {
    }

    @Override
    public SourceCheck inspect(ServerPlayer player, String sourceRef) {
        SourceRef ref = parse(sourceRef, player);
        HolderLookup.Provider registries = registries(player);
        GuzhenrenModVariables.PlayerVariables variables = variables(player);
        if (ref == null || registries == null || variables == null
                || !variables.BenMingGu_1 || variables.BenMingGu == null || variables.BenMingGu.isEmpty()) {
            return unavailable("player-bound Gu or stable inventory source is unavailable");
        }
        return inspectInventory(access(player.getInventory()), ref.slot(), variables.BenMingGu, registries);
    }

    @Override
    public SourceCheck remove(ServerPlayer player, String sourceRef, String expectedFingerprint) {
        SourceRef ref = parse(sourceRef, player);
        HolderLookup.Provider registries = registries(player);
        GuzhenrenModVariables.PlayerVariables variables = variables(player);
        if (ref == null || registries == null || variables == null
                || !variables.BenMingGu_1 || variables.BenMingGu == null || variables.BenMingGu.isEmpty()) {
            return unavailable("player-bound Gu or stable inventory source is unavailable");
        }
        return removeInventory(access(player.getInventory()), ref.slot(), variables.BenMingGu,
                expectedFingerprint, registries);
    }

    @Override
    public SourceCheck restore(ServerPlayer player, String sourceRef, String expectedFingerprint,
                               CompoundTag stackTag) {
        SourceRef ref = parse(sourceRef, player);
        HolderLookup.Provider registries = registries(player);
        if (ref == null || registries == null) {
            return unavailable("stable inventory source is unavailable");
        }
        return restoreInventory(access(player.getInventory()), ref.slot(), expectedFingerprint,
                stackTag, registries);
    }

    public static String sourceRef(UUID ownerId, int slot) {
        Objects.requireNonNull(ownerId, "owner id");
        if (slot < 0 || slot >= PLAYER_INVENTORY_SIZE) {
            throw new IllegalArgumentException("player inventory slot is outside the stable range");
        }
        return SOURCE_PREFIX + ownerId + "/" + slot;
    }

    static SourceCheck inspectInventory(InventoryAccess inventory, int sourceSlot,
                                        ItemStack boundCopy, HolderLookup.Provider registries) {
        if (!usable(inventory, sourceSlot, boundCopy, registries)) {
            return unavailable("invalid inventory, slot, bound stack, or registry provider");
        }
        String fingerprint = fingerprint(boundCopy, registries);
        int matchingCount = matchingCount(inventory, fingerprint, registries);
        if (matchingCount > 1) {
            return multiple("bound stack appears more than once in player inventory");
        }
        ItemStack source = inventory.get(sourceSlot);
        if (source.isEmpty()) {
            return matchingCount == 0 ? empty("stable source slot is empty")
                    : changed("bound stack moved to another inventory slot");
        }
        if (!fingerprint(source, registries).equals(fingerprint) || source.getCount() != 1) {
            return changed("stable source slot no longer contains the bound single stack");
        }
        return exact(source, fingerprint);
    }

    static SourceCheck removeInventory(InventoryAccess inventory, int sourceSlot,
                                      ItemStack boundCopy, String expectedFingerprint,
                                      HolderLookup.Provider registries) {
        SourceCheck before = inspectInventory(inventory, sourceSlot, boundCopy, registries);
        if (before.evidence() != Evidence.EXACT_SOURCE
                || !before.fingerprint().equals(expectedFingerprint)) {
            return before.evidence() == Evidence.EXACT_SOURCE
                    ? changed("expected fingerprint does not match the current bound stack") : before;
        }
        inventory.set(sourceSlot, ItemStack.EMPTY);
        inventory.setChanged();
        return inspectInventory(inventory, sourceSlot, boundCopy, registries);
    }

    static SourceCheck restoreInventory(InventoryAccess inventory, int sourceSlot,
                                       String expectedFingerprint,
                                       CompoundTag stackTag, HolderLookup.Provider registries) {
        if (inventory == null || inventory.size() != PLAYER_INVENTORY_SIZE
                || sourceSlot < 0 || sourceSlot >= inventory.size()
                || registries == null || expectedFingerprint == null || expectedFingerprint.isBlank()
                || stackTag == null) {
            return unavailable("invalid inventory restore request");
        }
        Optional<ItemStack> decoded;
        try {
            decoded = ItemStack.CODEC.parse(registries.createSerializationContext(
                    net.minecraft.nbt.NbtOps.INSTANCE), stackTag.copy()).result();
        } catch (RuntimeException malformed) {
            return changed("saved Gu stack cannot be decoded");
        }
        if (decoded.isEmpty() || decoded.orElseThrow().isEmpty()
                || decoded.orElseThrow().getCount() != 1
                || !fingerprint(decoded.orElseThrow(), registries).equals(expectedFingerprint)) {
            return changed("saved Gu stack does not match the bound item fingerprint");
        }

        int matchingCount = matchingCount(inventory, expectedFingerprint, registries);
        if (matchingCount > 1) {
            return multiple("saved stack already appears more than once in player inventory");
        }
        ItemStack source = inventory.get(sourceSlot);
        if (!source.isEmpty()) {
            return matchingCount == 1 && source.getCount() == 1
                    && fingerprint(source, registries).equals(expectedFingerprint)
                    ? exact(source, expectedFingerprint)
                    : changed("stable restore slot is occupied or the saved item moved");
        }
        if (matchingCount != 0) {
            return changed("saved item already appears in a different inventory slot");
        }
        inventory.set(sourceSlot, decoded.orElseThrow().copy());
        inventory.setChanged();
        ItemStack restored = inventory.get(sourceSlot);
        return restored.getCount() == 1 && fingerprint(restored, registries).equals(expectedFingerprint)
                ? exact(restored, expectedFingerprint) : changed("restored stack did not pass source verification");
    }

    static String fingerprint(ItemStack stack, HolderLookup.Provider registries) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        ItemStack singular = stack.copy();
        singular.setCount(1);
        CompoundTag serialized = (CompoundTag) singular.save(registries);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(serialized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }

    private static int matchingCount(InventoryAccess inventory, String fingerprint,
                                     HolderLookup.Provider registries) {
        int matches = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack candidate = inventory.get(slot);
            if (!candidate.isEmpty() && fingerprint(candidate, registries).equals(fingerprint)) {
                if (candidate.getCount() > 1 || matches > 0) return 2;
                matches += candidate.getCount();
            }
        }
        return matches;
    }

    private static boolean usable(InventoryAccess inventory, int sourceSlot, ItemStack boundCopy,
                                  HolderLookup.Provider registries) {
        return inventory != null && inventory.size() == PLAYER_INVENTORY_SIZE
                && sourceSlot >= 0 && sourceSlot < inventory.size()
                && boundCopy != null && !boundCopy.isEmpty() && boundCopy.getCount() == 1
                && registries != null;
    }

    private static SourceRef parse(String sourceRef, ServerPlayer player) {
        if (sourceRef == null || player == null || player.getServer() == null
                || !player.getServer().isSameThread() || !player.isAlive()) return null;
        String[] fields = sourceRef.split("/", -1);
        if (fields.length != 4 || !fields[0].equals("player_inventory") || !fields[1].equals("v1")) return null;
        try {
            UUID ownerId = UUID.fromString(fields[2]);
            int slot = Integer.parseInt(fields[3]);
            if (!ownerId.equals(player.getUUID()) || slot < 0 || slot >= PLAYER_INVENTORY_SIZE) return null;
            return new SourceRef(ownerId, slot);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static HolderLookup.Provider registries(ServerPlayer player) {
        return player == null || player.level() == null ? null : player.level().registryAccess();
    }

    private static GuzhenrenModVariables.PlayerVariables variables(ServerPlayer player) {
        if (player == null || !player.hasData(GuzhenrenModVariables.PLAYER_VARIABLES.get())) return null;
        return player.getExistingDataOrNull(GuzhenrenModVariables.PLAYER_VARIABLES);
    }

    private static InventoryAccess access(Inventory inventory) {
        return new InventoryAccess() {
            @Override public int size() { return inventory.getContainerSize(); }
            @Override public ItemStack get(int slot) { return inventory.getItem(slot); }
            @Override public void set(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
            @Override public void setChanged() { inventory.setChanged(); }
        };
    }

    private static SourceCheck exact(ItemStack stack, String fingerprint) {
        return new SourceCheck(Evidence.EXACT_SOURCE, Optional.of(stack.copy()), fingerprint, "");
    }

    private static SourceCheck empty(String reason) {
        return new SourceCheck(Evidence.EMPTY_SOURCE, Optional.empty(), "", reason);
    }

    private static SourceCheck changed(String reason) {
        return new SourceCheck(Evidence.CHANGED_SOURCE, Optional.empty(), "", reason);
    }

    private static SourceCheck multiple(String reason) {
        return new SourceCheck(Evidence.MULTIPLE_COPIES, Optional.empty(), "", reason);
    }

    private static SourceCheck unavailable(String reason) {
        return new SourceCheck(Evidence.SOURCE_UNAVAILABLE, Optional.empty(), "", reason);
    }
}
