package net.guzhenren.tianyi.world;

import java.util.Objects;
import java.util.Optional;

import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-side port for one persistent source location; no implementation is enabled yet. */
public interface GuEscrowSource {
    record SourceCheck(Evidence evidence, Optional<ItemStack> stackCopy,
                       String fingerprint, String reason) {
        public SourceCheck {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(stackCopy, "stackCopy");
            stackCopy = stackCopy.map(ItemStack::copy);
            fingerprint = fingerprint == null ? "" : fingerprint.trim();
            reason = reason == null ? "" : reason.trim();
            boolean exact = evidence == Evidence.EXACT_SOURCE;
            if (exact != stackCopy.isPresent() || exact != !fingerprint.isEmpty()) {
                throw new IllegalArgumentException(
                        "only exact source evidence may carry a stack copy and fingerprint");
            }
            if (stackCopy.map(ItemStack::isEmpty).orElse(false)
                    || stackCopy.map(ItemStack::getCount).orElse(0) > 1) {
                throw new IllegalArgumentException("verified Gu source must contain exactly one item");
            }
        }

        @Override
        public Optional<ItemStack> stackCopy() {
            return stackCopy.map(ItemStack::copy);
        }
    }

    SourceCheck inspect(ServerPlayer player, String sourceRef);

    SourceCheck remove(ServerPlayer player, String sourceRef, String expectedFingerprint);

    SourceCheck restore(ServerPlayer player, String sourceRef, CompoundTag stackTag);
}
