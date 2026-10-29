package net.guzhenren.tianyi.ascension;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 升仙的纯逻辑数据类型。此包不依赖 Minecraft；服务端必须先从本体和真实物品槽
 * 取得证据，再构造这里的输入。客户端数据不能直接成为有效规则档或物品凭证。
 */
public final class AscensionModel {
    private AscensionModel() {
    }

    public enum Phase {
        PREPARING,
        BREAKING,
        BROKEN,
        ABSORBING,
        FORMING,
        IMMORTAL,
        FAILED,
        CANCELLED
    }

    public enum Outcome {
        RUNNING,
        IMMORTAL,
        FAILED_ALIVE,
        DEAD,
        SUSPENDED,
        QUARANTINED,
        CANCELLED
    }

    public enum Action {
        START_BREAKING,
        CANCEL_PREPARATION,
        COMMIT_BROKEN,
        BEGIN_ABSORPTION,
        UPDATE_QI,
        PLACE_GU,
        COMPLETE_FORMATION,
        FAIL_ALIVE,
        DIE,
        SUSPEND,
        RESUME,
        QUARANTINE
    }

    public enum Capability {
        /** 本体境界可以安全表示并同步升仙后的修为状态。 */
        UPSTREAM_RANK_ADAPTER,
        /** 已有可用的真实仙窍空间、主人权限和出入口。 */
        IMMORTAL_APERTURE_SPACE,
        /** 有真实蛊虫唯一托管和恢复能力。 */
        UNIQUE_GU_ESCROW
    }

    public enum Blocker {
        UNSUPPORTED_PROFILE,
        MISSING_CAPABILITY,
        MISSING_UPSTREAM_RANK_ADAPTER,
        MISSING_APERTURE_SPACE,
        MISSING_GU_ESCROW,
        UNKNOWN_APERTURE,
        APERTURE_ALREADY_BROKEN,
        NOT_TURN_FIVE_PEAK,
        PHYSICAL_GU_NOT_VERIFIED
    }

    public enum Rejection {
        NONE,
        STALE_REVISION,
        INVALID_SERVER_TICK,
        INVALID_PHASE,
        INVALID_OUTCOME,
        SERVER_VALIDATION_REQUIRED,
        QI_NOT_READY,
        PHYSICAL_GU_NOT_VERIFIED,
        ESCROW_ID_MISSING,
        APERTURE_NOT_COMMITTED,
        APERTURE_RESULT_ID_MISSING
    }

    /**
     * 特定升仙路线的证据与能力声明。未知路线必须保持 disabled；没有自动退回普通路线。
     */
    public record RuleProfile(String id, String evidenceRef, boolean enabled,
                              Set<Capability> capabilities) {
        public RuleProfile {
            id = requireText(id, "profile id");
            evidenceRef = requireText(evidenceRef, "evidence reference");
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        }

        public boolean has(Capability capability) {
            return capabilities.contains(Objects.requireNonNull(capability, "capability"));
        }
    }

    /** 服务端已经审计的单个空窍事实快照。rank 与 stage 对应本体 double 字段。 */
    public record StartFacts(String apertureId, double rank, double stage,
                             boolean apertureVerified, boolean apertureBroken,
                             String physicalGuInstanceId, RuleProfile profile) {
        public StartFacts {
            apertureId = apertureId == null ? "" : apertureId.trim();
            physicalGuInstanceId = physicalGuInstanceId == null ? "" : physicalGuInstanceId.trim();
            Objects.requireNonNull(profile, "profile");
        }

        public boolean hasVerifiedPhysicalGu() {
            return !physicalGuInstanceId.isEmpty();
        }
    }

    public record StartDecision(boolean allowed, List<Blocker> blockers) {
        public StartDecision {
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            if (allowed != blockers.isEmpty()) {
                throw new IllegalArgumentException("allowed must match an empty blocker list");
            }
        }
    }

    /**
     * 会话内已纳入的三气快照。比例与容差不在数据类型中固化，由已审计 profile 解释。
     */
    public record QiState(double tian, double di, double ren) {
        public QiState {
            requireFiniteNonNegative(tian, "tian qi");
            requireFiniteNonNegative(di, "di qi");
            requireFiniteNonNegative(ren, "ren qi");
        }

        public static QiState empty() {
            return new QiState(0.0, 0.0, 0.0);
        }
    }

    /**
     * 本状态不是物品本身。guEscrowId 必须指向服务端持久托管记录，不能用 ItemStack 副本冒充。
     */
    public record Session(UUID sessionId, UUID ownerId, String apertureId, String profileId,
                          Phase phase, Outcome outcome, long phaseStartedAt,
                          boolean brokenCommitted, QiState qi, String guEscrowId,
                          String apertureResultId, long revision) {
        public Session {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(ownerId, "ownerId");
            apertureId = requireText(apertureId, "apertureId");
            profileId = requireText(profileId, "profileId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(qi, "qi");
            guEscrowId = guEscrowId == null ? "" : guEscrowId.trim();
            apertureResultId = apertureResultId == null ? "" : apertureResultId.trim();
            if (phaseStartedAt < 0 || revision < 0) {
                throw new IllegalArgumentException("negative phase tick or revision");
            }
            if (requiresBrokenAperture(phase) && !brokenCommitted) {
                throw new IllegalArgumentException("post-break phase requires committed broken aperture");
            }
            if ((phase == Phase.IMMORTAL) != (outcome == Outcome.IMMORTAL)) {
                throw new IllegalArgumentException("immortal phase and outcome must agree");
            }
            if (phase == Phase.IMMORTAL && apertureResultId.isEmpty()) {
                throw new IllegalArgumentException("immortal session requires an aperture result id");
            }
            if (phase == Phase.FAILED && outcome != Outcome.FAILED_ALIVE
                    && outcome != Outcome.DEAD && outcome != Outcome.QUARANTINED) {
                throw new IllegalArgumentException("failed phase requires a terminal failure outcome");
            }
            if ((phase == Phase.CANCELLED) != (outcome == Outcome.CANCELLED)
                    || (phase == Phase.CANCELLED && brokenCommitted)) {
                throw new IllegalArgumentException("cancelled session must be terminal and unbroken");
            }
            if (phase != Phase.FAILED && (outcome == Outcome.FAILED_ALIVE || outcome == Outcome.DEAD)) {
                throw new IllegalArgumentException("failure outcome requires failed phase");
            }
        }

        public boolean terminal() {
            return phase == Phase.IMMORTAL || phase == Phase.FAILED || phase == Phase.CANCELLED;
        }
    }

    /** 只由服务端构造的校验结果；网络 payload 不得包含可直接采信的该对象。 */
    public record Validation(boolean serverValidated, boolean qiReady,
                             boolean physicalGuVerified, boolean apertureCommitted) {
        public static Validation rejected() {
            return new Validation(false, false, false, false);
        }
    }

    public record Command(Action action, long expectedRevision, long serverTick,
                          QiState qi, Validation validation,
                          String guEscrowId, String apertureResultId) {
        public Command {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(validation, "validation");
            if (expectedRevision < 0 || serverTick < 0) {
                throw new IllegalArgumentException("negative revision or server tick");
            }
            guEscrowId = guEscrowId == null ? "" : guEscrowId.trim();
            apertureResultId = apertureResultId == null ? "" : apertureResultId.trim();
        }
    }

    public record Transition(boolean accepted, Session session, Rejection rejection) {
        public Transition {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(rejection, "rejection");
            if (accepted != (rejection == Rejection.NONE)) {
                throw new IllegalArgumentException("accepted transition must have no rejection");
            }
        }
    }

    static boolean requiresBrokenAperture(Phase phase) {
        return phase == Phase.BROKEN || phase == Phase.ABSORBING || phase == Phase.FORMING
                || phase == Phase.IMMORTAL;
    }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    private static void requireFiniteNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
