package net.guzhenren.tianyi.world;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionRecovery;
import net.guzhenren.tianyi.ascension.GuEscrow.Evidence;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.AscensionModel.StartDecision;
import net.guzhenren.tianyi.ascension.ProgressionSnapshot;
import net.minecraft.server.level.ServerPlayer;

/** Server-only admission checks. This stage never creates a session or changes inventory. */
public final class AscensionService {
    public static final RuleProfile ORDINARY_PROFILE = new RuleProfile(
            "ordinary", "docs/升仙-原著规则清单.md", false, Set.of());

    private AscensionService() {
    }

    public record StartContext(String apertureId, boolean apertureVerified,
                               boolean apertureBroken, String sourceRef) {
        public StartContext {
            apertureId = apertureId == null ? "" : apertureId.trim();
            sourceRef = sourceRef == null ? "" : sourceRef.trim();
        }
    }

    public record StartResult(AscensionProgressionReader.Status readStatus,
                              Optional<StartDecision> decision,
                              Optional<Session> session) {
        public StartResult {
            Objects.requireNonNull(readStatus, "readStatus");
            decision = Objects.requireNonNull(decision, "decision");
            session = Objects.requireNonNull(session, "session");
            if (readStatus != AscensionProgressionReader.Status.OK
                    && (decision.isPresent() || session.isPresent())) {
                throw new IllegalArgumentException("failed progression reads cannot produce decisions or sessions");
            }
            if (readStatus == AscensionProgressionReader.Status.OK && decision.isEmpty()) {
                throw new IllegalArgumentException("valid progression reads require an admission decision");
            }
            if (session.isPresent() && (decision.isEmpty() || !decision.orElseThrow().allowed())) {
                throw new IllegalArgumentException("blocked admission cannot carry a session");
            }
        }
    }

    public static StartResult begin(ServerPlayer player, StartContext context,
                                    RuleProfile profile, long serverTick) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(profile, "profile");
        AscensionProgressionReader.ReadResult read =
                AscensionProgressionReader.read(player, serverTick);
        if (read.status() != AscensionProgressionReader.Status.OK) {
            return new StartResult(read.status(), Optional.empty(), Optional.empty());
        }
        ProgressionSnapshot snapshot = read.snapshot().orElseThrow();
        GuEscrowService.BeginResult begin = GuEscrowService.disabled()
                .begin(player, context, snapshot, profile, serverTick);
        return new StartResult(AscensionProgressionReader.Status.OK,
                Optional.of(begin.decision()), begin.session());
    }

    /**
     * Login and respawn recovery currently has no stable source adapter to inspect. Reconcile each
     * linked record with SOURCE_UNAVAILABLE so ambiguous custody remains untouched. The paired
     * compare-and-set is ready for a future audited source implementation to apply safe decisions.
     */
    public static void recover(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        AscensionStore store = AscensionStore.get(player.getServer());
        for (Session session : store.forOwner(player.getUUID())) {
            if (session.terminal()) {
                continue;
            }
            UUID escrowId;
            try {
                escrowId = UUID.fromString(session.guEscrowId());
            } catch (IllegalArgumentException invalidId) {
                continue;
            }
            Optional<Record> storedEscrow = store.findEscrow(escrowId);
            if (storedEscrow.isEmpty()) {
                continue;
            }
            Record escrow = storedEscrow.orElseThrow();
            AscensionRecovery.RecoveryDecision decision = AscensionRecovery.reconcile(
                    session, escrow, Evidence.SOURCE_UNAVAILABLE, null);
            if (!decision.session().equals(session) || !decision.escrow().equals(escrow)) {
                store.compareAndSetPair(session.sessionId(), session.revision(), decision.session(),
                        escrowId, escrow.revision(), decision.escrow());
            }
        }
    }
}
