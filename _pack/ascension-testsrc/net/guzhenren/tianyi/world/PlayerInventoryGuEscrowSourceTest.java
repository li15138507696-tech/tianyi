package net.guzhenren.tianyi.world;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.LoadingModList;
import net.guzhenren.tianyi.ascension.GuEscrow;

public final class PlayerInventoryGuEscrowSourceTest {
    private static net.minecraft.core.HolderLookup.Provider registries;

    private PlayerInventoryGuEscrowSourceTest() {
    }

    public static void main(String[] args) {
        LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        recognizesOneBoundInventoryStack();
        duplicateOrChangedStacksAreNeverSelected();
        removalAndRestoreAreFingerprintBound();
        System.out.println("PASS stable player inventory Gu source and custody checks");
    }

    private static void recognizesOneBoundInventoryStack() {
        MemoryInventory inventory = new MemoryInventory(41);
        ItemStack bound = new ItemStack(Items.STONE);
        inventory.set(8, bound.copy());
        GuEscrowSource.SourceCheck exact = PlayerInventoryGuEscrowSource.inspectInventory(
                inventory, 8, bound.copy(), registries);
        check(exact.evidence() == GuEscrow.Evidence.EXACT_SOURCE
                        && exact.stackCopy().orElseThrow().getCount() == 1
                        && !exact.fingerprint().isBlank(),
                "one exact bound stack in a stable player inventory slot is inspectable");
    }

    private static void duplicateOrChangedStacksAreNeverSelected() {
        ItemStack bound = new ItemStack(Items.STONE);
        MemoryInventory duplicate = new MemoryInventory(41);
        duplicate.set(8, bound.copy());
        duplicate.set(23, bound.copy());
        check(PlayerInventoryGuEscrowSource.inspectInventory(duplicate, 8, bound, registries).evidence()
                        == GuEscrow.Evidence.MULTIPLE_COPIES,
                "identical bound stacks across inventory slots are ambiguous");

        MemoryInventory oversized = new MemoryInventory(41);
        ItemStack pair = bound.copy();
        pair.setCount(2);
        oversized.set(8, pair);
        check(PlayerInventoryGuEscrowSource.inspectInventory(oversized, 8, bound, registries).evidence()
                        == GuEscrow.Evidence.MULTIPLE_COPIES,
                "a non-singular bound stack is not escrowable");

        MemoryInventory changed = new MemoryInventory(41);
        changed.set(8, new ItemStack(Items.DIAMOND));
        check(PlayerInventoryGuEscrowSource.inspectInventory(changed, 8, bound, registries).evidence()
                        == GuEscrow.Evidence.CHANGED_SOURCE,
                "changed slot contents are not mistaken for the bound item");
    }

    private static void removalAndRestoreAreFingerprintBound() {
        MemoryInventory inventory = new MemoryInventory(41);
        ItemStack bound = new ItemStack(Items.STONE);
        inventory.set(8, bound.copy());
        String fingerprint = PlayerInventoryGuEscrowSource.fingerprint(bound, registries);
        CompoundTag saved = (CompoundTag) bound.save(registries);

        GuEscrowSource.SourceCheck wrongFingerprint = PlayerInventoryGuEscrowSource.removeInventory(
                inventory, 8, bound.copy(), "wrong", registries);
        check(wrongFingerprint.evidence() == GuEscrow.Evidence.CHANGED_SOURCE
                        && !inventory.get(8).isEmpty(),
                "failed expected fingerprint cannot remove the item");

        GuEscrowSource.SourceCheck removed = PlayerInventoryGuEscrowSource.removeInventory(
                inventory, 8, bound.copy(), fingerprint, registries);
        check(removed.evidence() == GuEscrow.Evidence.EMPTY_SOURCE && inventory.get(8).isEmpty(),
                "successful removal leaves the original stable slot empty");

        GuEscrowSource.SourceCheck restored = PlayerInventoryGuEscrowSource.restoreInventory(
                inventory, 8, fingerprint, saved, registries);
        check(restored.evidence() == GuEscrow.Evidence.EXACT_SOURCE
                        && ItemStack.isSameItemSameComponents(inventory.get(8), bound),
                "restore returns the exact single stack to its original slot");

        CompoundTag altered = saved.copy();
        altered.putInt("count", 2);
        MemoryInventory empty = new MemoryInventory(41);
        GuEscrowSource.SourceCheck rejectedRestore = PlayerInventoryGuEscrowSource.restoreInventory(
                empty, 8, fingerprint, altered, registries);
        check(rejectedRestore.evidence() == GuEscrow.Evidence.CHANGED_SOURCE && empty.get(8).isEmpty(),
                "restore rejects altered item data without writing a stack");

        CompoundTag partialComponents = saved.copy();
        CompoundTag malformedComponents = new CompoundTag();
        malformedComponents.putString("minecraft:damage", "bad");
        partialComponents.put("components", malformedComponents);
        MemoryInventory partial = new MemoryInventory(41);
        GuEscrowSource.SourceCheck rejectedPartial = PlayerInventoryGuEscrowSource.restoreInventory(
                partial, 8, fingerprint, partialComponents, registries);
        check(rejectedPartial.evidence() == GuEscrow.Evidence.CHANGED_SOURCE && partial.get(8).isEmpty(),
                "restore rejects partial component decoding without writing a stack");
    }

    private static final class MemoryInventory implements PlayerInventoryGuEscrowSource.InventoryAccess {
        private final ItemStack[] stacks;

        private MemoryInventory(int size) {
            stacks = new ItemStack[size];
            java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        }

        @Override public int size() { return stacks.length; }
        @Override public ItemStack get(int slot) { return stacks[slot]; }
        @Override public void set(int slot, ItemStack stack) { stacks[slot] = stack; }
        @Override public void setChanged() { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
