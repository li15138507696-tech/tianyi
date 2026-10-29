package net.guzhenren.tianyi.ascension;

import net.guzhenren.tianyi.ascension.AscensionModel.QiState;

public final class QiRegulatorTest {
    private static final QiRegulator.Tuning TUNING = new QiRegulator.Tuning(
            10.0, 10.0, 1.0, 1.0, 1.0, 0.25, 3L);

    private QiRegulatorTest() {
    }

    public static void main(String[] args) {
        serverTickMovesFiniteGasAndConservesHumanReserve();
        repeatedOrOfflineTicksDoNotAutoregulate();
        sustainedImbalanceAccumulatesOnlyOnServerSteps();
        System.out.println("PASS ascension qi flow, human reserve, and tick idempotency");
    }

    private static void serverTickMovesFiniteGasAndConservesHumanReserve() {
        QiState current = new QiState(1.0, 1.0, 0.0, 3.0, 2.0, 2.0, 0L, 10L);
        var result = QiRegulator.advance(current, 11L,
                new QiRegulator.Control(1.0, 1.0, 1.0),
                new QiRegulator.Environment(2.0, 1.0), TUNING);
        check(result.accepted(), "fresh server tick advances gas flow");
        QiState next = result.state();
        check(close(next.tian(), 2.0) && close(next.di(), 2.0) && close(next.ren(), 1.0),
                "controls absorb capped weather, ground, and human qi");
        check(close(next.externalTian(), 4.0) && close(next.externalDi(), 2.0),
                "unabsorbed external qi remains available for later ticks");
        check(close(next.humanReserve() + next.ren(), current.humanReserve() + current.ren()),
                "human qi moves from reserve into gathered qi without being created");
        check(next.lastTick() == 11L, "server tick is durably recorded");
    }

    private static void repeatedOrOfflineTicksDoNotAutoregulate() {
        QiState current = new QiState(1.0, 1.0, 0.0, 3.0, 2.0, 2.0, 0L, 10L);
        var duplicate = QiRegulator.advance(current, 10L,
                new QiRegulator.Control(1.0, 1.0, 1.0),
                new QiRegulator.Environment(0.0, 0.0), TUNING);
        check(duplicate.rejection() == QiRegulator.Rejection.STALE_SERVER_TICK
                        && duplicate.state().equals(current),
                "duplicate server tick cannot duplicate absorption");

        var afterGap = QiRegulator.advance(current, 500L,
                QiRegulator.Control.none(), new QiRegulator.Environment(0.0, 0.0), TUNING);
        check(afterGap.accepted(), "a fresh online server tick can resume after a gap");
        QiState resumed = afterGap.state();
        check(close(resumed.tian(), current.tian()) && close(resumed.di(), current.di())
                        && close(resumed.ren(), current.ren())
                        && close(resumed.externalTian(), current.externalTian())
                        && close(resumed.externalDi(), current.externalDi())
                        && close(resumed.humanReserve(), current.humanReserve())
                        && resumed.lastTick() == 500L,
                "elapsed offline ticks do not apply automatic balancing or absorption");
    }

    private static void sustainedImbalanceAccumulatesOnlyOnServerSteps() {
        QiState current = new QiState(0.0, 0.0, 0.0, 5.0, 5.0, 0.0, 0L, 10L);
        for (long tick = 11L; tick <= 13L; tick++) {
            current = QiRegulator.advance(current, tick,
                    new QiRegulator.Control(1.0, 0.0, 0.0),
                    new QiRegulator.Environment(0.0, 0.0), TUNING).state();
        }
        check(close(current.tian(), 3.0) && close(current.di(), 0.0),
                "player inputs continue to control qi while the state evolves");
        check(current.imbalanceTicks() == 3L,
                "sustained imbalance increments by active server steps, not elapsed wall time");
        QiState stableWithOldWarning = new QiState(2.0, 2.0, 2.0, 0.0, 0.0,
                0.0, 3L, 13L);
        var idle = QiRegulator.advance(stableWithOldWarning, 14L, QiRegulator.Control.none(),
                new QiRegulator.Environment(0.0, 0.0), TUNING);
        check(idle.state().imbalanceTicks() == 0L,
                "a stable current qi ratio clears the continuous imbalance counter");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.0000001;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
