package net.guzhenren.tianyi.ascension;

import java.util.Objects;
import java.util.UUID;

/** Server-read snapshot of the upstream progression fields; values retain upstream units. */
public record ProgressionSnapshot(UUID ownerId, double rank, double stage, double renqi,
                                  double shouyuan, double zhenyuan, double maxZhenyuan,
                                  long sampledAt) {
    public ProgressionSnapshot {
        Objects.requireNonNull(ownerId, "ownerId");
        requireFiniteNonNegative(rank, "rank");
        requireFiniteNonNegative(stage, "stage");
        requireFiniteNonNegative(renqi, "renqi");
        requireFiniteNonNegative(shouyuan, "shouyuan");
        requireFiniteNonNegative(zhenyuan, "zhenyuan");
        requireFiniteNonNegative(maxZhenyuan, "maxZhenyuan");
        if (sampledAt < 0L) {
            throw new IllegalArgumentException("sampledAt must be non-negative");
        }
    }

    private static void requireFiniteNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
