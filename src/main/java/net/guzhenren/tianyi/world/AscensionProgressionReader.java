package net.guzhenren.tianyi.world;

import java.util.Objects;
import java.util.Optional;

import net.guzhenren.network.GuzhenrenModVariables;
import net.guzhenren.tianyi.ascension.ProgressionSnapshot;
import net.minecraft.server.level.ServerPlayer;

/** Reads upstream progression facts from the server-owned attachment without creating or mutating it. */
public final class AscensionProgressionReader {
    private AscensionProgressionReader() {
    }

    public enum Status {
        OK,
        NO_PLAYER,
        NO_ATTACHMENT,
        INVALID_VALUE,
        INVALID_TICK
    }

    public record ReadResult(Status status, Optional<ProgressionSnapshot> snapshot) {
        public ReadResult {
            Objects.requireNonNull(status, "status");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if ((status == Status.OK) != snapshot.isPresent()) {
                throw new IllegalArgumentException("only OK results may contain a progression snapshot");
            }
        }

        public static ReadResult unavailable(Status status) {
            if (status == Status.OK) {
                throw new IllegalArgumentException("OK requires a snapshot");
            }
            return new ReadResult(status, Optional.empty());
        }

        public static ReadResult ok(ProgressionSnapshot snapshot) {
            return new ReadResult(Status.OK, Optional.of(Objects.requireNonNull(snapshot, "snapshot")));
        }
    }

    public static ReadResult read(ServerPlayer player, long serverTick) {
        if (player == null) {
            return ReadResult.unavailable(Status.NO_PLAYER);
        }
        if (serverTick < 0L) {
            return ReadResult.unavailable(Status.INVALID_TICK);
        }

        if (!player.hasData(GuzhenrenModVariables.PLAYER_VARIABLES.get())) {
            return ReadResult.unavailable(Status.NO_ATTACHMENT);
        }
        GuzhenrenModVariables.PlayerVariables variables =
                player.getExistingDataOrNull(GuzhenrenModVariables.PLAYER_VARIABLES);
        if (variables == null) {
            return ReadResult.unavailable(Status.NO_ATTACHMENT);
        }

        try {
            return ReadResult.ok(new ProgressionSnapshot(player.getUUID(),
                    variables.zhuanshu, variables.jieduan, variables.renqi, variables.shouyuan,
                    variables.zhenyuan, variables.zuida_zhenyuan, serverTick));
        } catch (IllegalArgumentException invalidValue) {
            return ReadResult.unavailable(Status.INVALID_VALUE);
        }
    }
}
