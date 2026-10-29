package net.guzhenren.tianyi.ascension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.guzhenren.tianyi.ascension.AscensionModel.InsightState;
import net.guzhenren.tianyi.ascension.AscensionModel.Outcome;
import net.guzhenren.tianyi.ascension.AscensionModel.Phase;
import net.guzhenren.tianyi.ascension.AscensionModel.QiState;
import net.guzhenren.tianyi.ascension.AscensionModel.RuleProfile;
import net.guzhenren.tianyi.ascension.AscensionModel.Session;

public final class QiControlServiceTest {
    private static final UUID SESSION_ID = UUID.fromString("d9be948a-a601-4629-a995-9118fc2374d5");
    private static final UUID OWNER = UUID.fromString("1d6d4671-13e0-4a3d-bdfc-18a70035c3f0");
    private static final UUID OTHER = UUID.fromString("05bc7bf1-8208-4cf4-b201-bcd4be9e47e4");
    private static final RuleProfile PROFILE = new RuleProfile("flow", "test evidence", true, Set.of());
    private static final QiRegulator.Tuning TUNING = new QiRegulator.Tuning(
            10.0, 10.0, 1.0, 1.0, 1.0, 0.75, 10L);

    private QiControlServiceTest() {
    }

    public static void main(String[] args) {
        onlyOwnerCanControlStoredQi();
        gasContinuesWhenInsightCommitsOnTheSameTick();
        System.out.println("PASS ascension qi control ownership and concurrent insight");
    }

    private static void onlyOwnerCanControlStoredQi() {
        Session current = absorbing(0L);
        var denied = QiControlService.advance(current, OTHER,
                new QiControlService.Request(0L, 11L, new QiRegulator.Control(1.0, 1.0, 1.0)),
                new QiRegulator.Environment(1.0, 1.0), TUNING, PROFILE);
        check(denied.rejection() == QiControlService.Rejection.WRONG_OWNER,
                "another player cannot alter qi controls");
        check(denied.session().equals(current), "owner rejection leaves stored qi unchanged");

        var accepted = QiControlService.advance(current, OWNER,
                new QiControlService.Request(0L, 11L, new QiRegulator.Control(1.0, 0.0, 0.0)),
                new QiRegulator.Environment(1.0, 1.0), TUNING, PROFILE);
        check(accepted.accepted() && accepted.session().revision() == 1L,
                "owner control is stored as the next revision");
        check(accepted.session().qi().tian() > current.qi().tian()
                        && accepted.session().qi().di() == current.qi().di()
                        && accepted.session().qi().ren() == current.qi().ren(),
                "three controls act on distinct qi channels");
        RuleProfile disabled = new RuleProfile("flow", "test evidence", false, Set.of());
        var closed = QiControlService.advance(current, OWNER,
                new QiControlService.Request(0L, 11L, new QiRegulator.Control(1.0, 1.0, 1.0)),
                new QiRegulator.Environment(1.0, 1.0), TUNING, disabled);
        check(closed.rejection() == QiControlService.Rejection.PROFILE_DISABLED
                        && closed.session().equals(current),
                "disabled ascension profile cannot be regulated");
        RuleProfile mismatched = new RuleProfile("another-profile", "test evidence", true, Set.of());
        var wrongProfile = QiControlService.advance(current, OWNER,
                new QiControlService.Request(0L, 11L, new QiRegulator.Control(1.0, 1.0, 1.0)),
                new QiRegulator.Environment(1.0, 1.0), TUNING, mismatched);
        check(wrongProfile.rejection() == QiControlService.Rejection.PROFILE_MISMATCH
                        && wrongProfile.session().equals(current),
                "qi rules cannot fall back to a different ascension profile");
        var stale = QiControlService.advance(current, OWNER,
                new QiControlService.Request(1L, 11L, new QiRegulator.Control(1.0, 1.0, 1.0)),
                new QiRegulator.Environment(1.0, 1.0), TUNING, PROFILE);
        check(stale.rejection() == QiControlService.Rejection.STALE_REVISION
                        && stale.session().equals(current),
                "stale qi control revision has no side effect");
    }

    private static void gasContinuesWhenInsightCommitsOnTheSameTick() {
        Session current = absorbing(0L);
        RuleProfile profile = new RuleProfile("flow", "test evidence", true, Set.of(), 1.0, 0.75);
        InsightCatalog.Question question = new InsightCatalog.Question("topic", "knowledge.topic", "source",
                0.25, 1.0, 0.25, 0.75);
        var insight = InsightCatalog.resolve(current,
                new InsightCatalog.Request(0L, 0L, 11L, "topic"), List.of(question), profile, true);
        check(insight.accepted(), "insight query can commit on an active qi tick");

        var qi = QiControlService.advance(insight.session(), OWNER,
                new QiControlService.Request(insight.session().revision(), 11L,
                        new QiRegulator.Control(1.0, 0.0, 0.0)),
                new QiRegulator.Environment(1.0, 0.0), TUNING, PROFILE);
        check(qi.accepted(), "qi continues on the same server tick after insight");
        check(qi.session().insight().grants().size() == 1 && qi.session().revision() == 2L,
                "qi revision preserves the concurrently granted knowledge");
        check(qi.session().phase() == Phase.ABSORBING && qi.session().phaseStartedAt() == 10L,
                "parallel processes do not restart or pause the main phase");
    }

    private static Session absorbing(long revision) {
        return new Session(SESSION_ID, OWNER, "aperture", "flow", Phase.ABSORBING, Outcome.RUNNING,
                10L, true, new QiState(1.0, 1.0, 1.0, 5.0, 5.0, 2.0, 0L, 10L),
                "escrow", "", revision, InsightState.initial(1.0, 10L));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
