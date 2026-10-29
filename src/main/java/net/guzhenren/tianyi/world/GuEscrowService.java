package net.guzhenren.tianyi.world;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.guzhenren.tianyi.ascension.AscensionRules;
import net.guzhenren.tianyi.ascension.AscensionModel.Capability;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.AscensionModel.StartDecision;
import net.guzhenren.tianyi.ascension.AscensionModel.StartFacts;
import net.guzhenren.tianyi.ascension.GuEscrow;
import net.guzhenren.tianyi.ascension.GuEscrow.Record;
import net.guzhenren.tianyi.ascension.ProgressionSnapshot;
import net.minecraft.server.level.ServerPlayer;

/** Custody admission stays closed until a stable, persistent server-owned source is proven. */
public final class GuEscrowService {
    private static final GuEscrowService DISABLED = new GuEscrowService();

    private GuEscrowService() {
    }

    public static GuEscrowService disabled() {
        return DISABLED;
    }

    public record BeginResult(StartDecision decision,
                              Optional<Session> session,
                              Optional<Record> escrow) {
        public BeginResult {
            Objects.requireNonNull(decision, "decision");
            session = Objects.requireNonNull(session, "session");
            escrow = Objects.requireNonNull(escrow, "escrow");
            if (session.isPresent() != escrow.isPresent()) {
                throw new IllegalArgumentException("session and escrow must be created together");
            }
            if (session.isPresent() && !decision.allowed()) {
                throw new IllegalArgumentException("blocked admission cannot carry records");
            }
        }
    }

    public BeginResult begin(ServerPlayer player, AscensionService.StartContext context,
                             ProgressionSnapshot snapshot, RuleProfile profile, long serverTick) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(profile, "profile");

        // Task 1 found that menu slot 0 can refer to a held item, entity, block container,
        // or a transient handler. No single stable source exists, so do not inspect or mutate it.
        EnumSet<Capability> verifiedCapabilities = EnumSet.noneOf(Capability.class);
        verifiedCapabilities.addAll(profile.capabilities());
        verifiedCapabilities.remove(Capability.UNIQUE_GU_ESCROW);
        RuleProfile gatedProfile = new RuleProfile(profile.id(), profile.evidenceRef(),
                profile.enabled(), Set.copyOf(verifiedCapabilities), profile.insightBudget(),
                profile.resonanceTolerance());
        StartFacts facts = new StartFacts(context.apertureId(), snapshot.rank(), snapshot.stage(),
                context.apertureVerified(), context.apertureBroken(), "", gatedProfile);
        StartDecision decision = AscensionRules.validateStart(facts);
        return new BeginResult(decision, Optional.empty(), Optional.empty());
    }
}
