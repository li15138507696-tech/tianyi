package net.guzhenren.tianyi.ascension;

import java.util.UUID;

public final class ProgressionSnapshotTest {
    private ProgressionSnapshotTest() {
    }

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        ProgressionSnapshot snapshot = new ProgressionSnapshot(owner, 5.0, 4.0,
                1.0, 80.0, 120.0, 200.0, 0L);
        check(snapshot.ownerId().equals(owner), "owner is retained");
        check(snapshot.sampledAt() == 0L, "zero server tick is valid");

        expectNull(() -> new ProgressionSnapshot(null, 0, 0, 0, 0, 0, 0, 0));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1.0}) {
            expectIllegal(() -> new ProgressionSnapshot(owner, invalid, 0, 0, 0, 0, 0, 0));
            expectIllegal(() -> new ProgressionSnapshot(owner, 0, invalid, 0, 0, 0, 0, 0));
            expectIllegal(() -> new ProgressionSnapshot(owner, 0, 0, invalid, 0, 0, 0, 0));
            expectIllegal(() -> new ProgressionSnapshot(owner, 0, 0, 0, invalid, 0, 0, 0));
            expectIllegal(() -> new ProgressionSnapshot(owner, 0, 0, 0, 0, invalid, 0, 0));
            expectIllegal(() -> new ProgressionSnapshot(owner, 0, 0, 0, 0, 0, invalid, 0));
        }
        expectIllegal(() -> new ProgressionSnapshot(owner, 0, 0, 0, 0, 0, 0, -1));
        System.out.println("PASS progression snapshots reject invalid server facts");
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectNull(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
