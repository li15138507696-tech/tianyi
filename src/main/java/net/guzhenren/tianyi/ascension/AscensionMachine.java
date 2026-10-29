package net.guzhenren.tianyi.ascension;

import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.Action;
import net.guzhenren.tianyi.ascension.AscensionModel.Command;
import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.Rejection;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;
import net.guzhenren.tianyi.ascension.AscensionModel.StartDecision;
import net.guzhenren.tianyi.ascension.AscensionModel.StartFacts;
import net.guzhenren.tianyi.ascension.AscensionModel.Transition;
import net.guzhenren.tianyi.ascension.AscensionModel.Validation;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;

/**
 * 纯逻辑升仙阶段机。它只决定状态迁移，不生成蛊虫、实体、仙窍或客户端效果；
 * 这些副作用须由服务端事务层提交后，再以 Validation 结果推进状态。
 */
public final class AscensionMachine {
    private AscensionMachine() {
    }

    public static Session start(UUID sessionId, UUID ownerId, StartFacts facts, long serverTick) {
        return start(sessionId, ownerId, facts, "", serverTick);
    }

    public static Session start(UUID sessionId, UUID ownerId, StartFacts facts,
                                String guEscrowId, long serverTick) {
        if (serverTick < 0) {
            throw new IllegalArgumentException("server tick must be non-negative");
        }
        StartDecision decision = AscensionRules.validateStart(facts);
        if (!decision.allowed()) {
            throw new IllegalStateException("ascension start blocked: " + decision.blockers());
        }
        return new Session(sessionId, ownerId, facts.apertureId(), facts.profile().id(),
                Phase.PREPARING, Outcome.RUNNING, serverTick, false, QiState.empty(),
                guEscrowId, "", 0L);
    }

    public static Transition advance(Session current, Command command) {
        if (command.expectedRevision() != current.revision()) {
            return reject(current, Rejection.STALE_REVISION);
        }
        if (command.serverTick() < current.phaseStartedAt()) {
            return reject(current, Rejection.INVALID_SERVER_TICK);
        }
        Validation validation = command.validation();
        if (!validation.serverValidated()) {
            return reject(current, Rejection.SERVER_VALIDATION_REQUIRED);
        }

        if (command.action() == Action.QUARANTINE) {
            if (current.terminal() || current.outcome() == Outcome.IMMORTAL || current.outcome() == Outcome.DEAD
                    || current.outcome() == Outcome.FAILED_ALIVE
                    || current.outcome() == Outcome.QUARANTINED) {
                return reject(current, Rejection.INVALID_OUTCOME);
            }
            return accept(copy(current, current.phase(), Outcome.QUARANTINED,
                    current.phaseStartedAt(), current.brokenCommitted(), current.qi(),
                    current.guEscrowId(), current.apertureResultId()));
        }

        if (current.outcome() == Outcome.QUARANTINED) {
            return reject(current, Rejection.INVALID_OUTCOME);
        }
        if (current.terminal()) {
            return reject(current, Rejection.INVALID_PHASE);
        }

        if (command.action() == Action.SUSPEND) {
            if (current.outcome() != Outcome.RUNNING) {
                return reject(current, Rejection.INVALID_OUTCOME);
            }
            return accept(copy(current, current.phase(), Outcome.SUSPENDED,
                    current.phaseStartedAt(), current.brokenCommitted(), current.qi(),
                    current.guEscrowId(), current.apertureResultId()));
        }
        if (command.action() == Action.RESUME) {
            if (current.outcome() != Outcome.SUSPENDED) {
                return reject(current, Rejection.INVALID_OUTCOME);
            }
            return accept(copy(current, current.phase(), Outcome.RUNNING,
                    current.phaseStartedAt(), current.brokenCommitted(), current.qi(),
                    current.guEscrowId(), current.apertureResultId()));
        }
        if (current.outcome() != Outcome.RUNNING) {
            return reject(current, Rejection.INVALID_OUTCOME);
        }

        return switch (command.action()) {
            case CANCEL_PREPARATION -> current.phase() == Phase.PREPARING && !current.brokenCommitted()
                    ? accept(copy(current, Phase.CANCELLED, Outcome.CANCELLED, command.serverTick(),
                    false, current.qi(), current.guEscrowId(), current.apertureResultId()))
                    : reject(current, Rejection.INVALID_PHASE);
            case START_BREAKING -> current.phase() == Phase.PREPARING
                    ? accept(copy(current, Phase.BREAKING, Outcome.RUNNING, command.serverTick(),
                    false, current.qi(), current.guEscrowId(), current.apertureResultId()))
                    : reject(current, Rejection.INVALID_PHASE);
            case COMMIT_BROKEN -> current.phase() == Phase.BREAKING
                    ? accept(copy(current, Phase.BROKEN, Outcome.RUNNING, command.serverTick(),
                    true, current.qi(), current.guEscrowId(), current.apertureResultId()))
                    : reject(current, Rejection.INVALID_PHASE);
            case BEGIN_ABSORPTION -> current.phase() == Phase.BROKEN && current.brokenCommitted()
                    ? accept(copy(current, Phase.ABSORBING, Outcome.RUNNING, command.serverTick(),
                    true, current.qi(), current.guEscrowId(), current.apertureResultId()))
                    : reject(current, Rejection.INVALID_PHASE);
            case UPDATE_QI -> updateQi(current, command);
            case PLACE_GU -> placeGu(current, command, validation);
            case COMPLETE_FORMATION -> completeFormation(current, command, validation);
            case FAIL_ALIVE -> accept(copy(current, Phase.FAILED, Outcome.FAILED_ALIVE,
                    command.serverTick(), current.brokenCommitted(), current.qi(),
                    current.guEscrowId(), ""));
            case DIE -> accept(copy(current, Phase.FAILED, Outcome.DEAD,
                    command.serverTick(), current.brokenCommitted(), current.qi(),
                    current.guEscrowId(), ""));
            case SUSPEND, RESUME, QUARANTINE -> reject(current, Rejection.INVALID_OUTCOME);
        };
    }

    private static Transition updateQi(Session current, Command command) {
        if (current.phase() != Phase.ABSORBING) {
            return reject(current, Rejection.INVALID_PHASE);
        }
        if (command.qi() == null) {
            return reject(current, Rejection.QI_NOT_READY);
        }
        return accept(copy(current, current.phase(), current.outcome(), current.phaseStartedAt(),
                current.brokenCommitted(), command.qi(), current.guEscrowId(),
                current.apertureResultId()));
    }

    private static Transition placeGu(Session current, Command command, Validation validation) {
        if (current.phase() != Phase.ABSORBING) {
            return reject(current, Rejection.INVALID_PHASE);
        }
        if (!validation.qiReady()) {
            return reject(current, Rejection.QI_NOT_READY);
        }
        if (!validation.physicalGuVerified()) {
            return reject(current, Rejection.PHYSICAL_GU_NOT_VERIFIED);
        }
        if (command.guEscrowId().isBlank()) {
            return reject(current, Rejection.ESCROW_ID_MISSING);
        }
        return accept(copy(current, Phase.FORMING, Outcome.RUNNING, command.serverTick(),
                current.brokenCommitted(), current.qi(), command.guEscrowId(), ""));
    }

    private static Transition completeFormation(Session current, Command command,
                                                Validation validation) {
        if (current.phase() != Phase.FORMING) {
            return reject(current, Rejection.INVALID_PHASE);
        }
        if (!validation.apertureCommitted()) {
            return reject(current, Rejection.APERTURE_NOT_COMMITTED);
        }
        if (command.apertureResultId().isBlank()) {
            return reject(current, Rejection.APERTURE_RESULT_ID_MISSING);
        }
        return accept(copy(current, Phase.IMMORTAL, Outcome.IMMORTAL, command.serverTick(),
                current.brokenCommitted(), current.qi(), current.guEscrowId(),
                command.apertureResultId()));
    }

    private static Transition accept(Session session) {
        return new Transition(true, session, Rejection.NONE);
    }

    private static Transition reject(Session session, Rejection reason) {
        return new Transition(false, session, reason);
    }

    private static Session copy(Session old, Phase phase, Outcome outcome, long phaseStartedAt,
                                boolean brokenCommitted, QiState qi, String guEscrowId,
                                String apertureResultId) {
        return new Session(old.sessionId(), old.ownerId(), old.apertureId(), old.profileId(),
                phase, outcome, phaseStartedAt, brokenCommitted, qi, guEscrowId,
                apertureResultId, old.revision() + 1);
    }
}
