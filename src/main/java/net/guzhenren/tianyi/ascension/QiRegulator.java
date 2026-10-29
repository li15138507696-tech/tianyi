package net.guzhenren.tianyi.ascension;

import java.util.Objects;

import net.guzhenren.tianyi.ascension.AscensionModel.QiState;

/**
 * One server-step of the three-qi process. All rates and tolerances are game-mapping inputs;
 * callers must obtain environment and player control from server-owned sources.
 */
public final class QiRegulator {
    private QiRegulator() {
    }

    public record Control(double tian, double di, double ren) {
        public Control {
            requireUnit(tian, "tian control");
            requireUnit(di, "di control");
            requireUnit(ren, "ren control");
        }

        public static Control none() {
            return new Control(0.0, 0.0, 0.0);
        }
    }

    /** Finite environmental inflows sampled on the server for this step only. */
    public record Environment(double tianSupply, double diSupply) {
        public Environment {
            requireFiniteNonNegative(tianSupply, "tian supply");
            requireFiniteNonNegative(diSupply, "di supply");
        }
    }

    /** Explicit balance/rate parameters; these are not claimed as numerical novel rules. */
    public record Tuning(double maxExternalTian, double maxExternalDi,
                         double maxTianAbsorptionPerTick, double maxDiAbsorptionPerTick,
                         double maxHumanReleasePerTick, double resonanceTolerance,
                         long maxImbalanceTicks) {
        public Tuning {
            requireFinitePositive(maxExternalTian, "maximum external tian qi");
            requireFinitePositive(maxExternalDi, "maximum external di qi");
            requireFinitePositive(maxTianAbsorptionPerTick, "tian absorption rate");
            requireFinitePositive(maxDiAbsorptionPerTick, "di absorption rate");
            requireFinitePositive(maxHumanReleasePerTick, "human qi release rate");
            if (!Double.isFinite(resonanceTolerance) || resonanceTolerance < 0.0
                    || resonanceTolerance > 1.0 || maxImbalanceTicks < 1L) {
                throw new IllegalArgumentException("invalid qi resonance tuning");
            }
        }
    }

    public enum Rejection {
        NONE,
        STALE_SERVER_TICK,
        INVALID_RESULT
    }

    public record Result(boolean accepted, QiState state, Rejection rejection) {
        public Result {
            Objects.requireNonNull(state, "qi state");
            Objects.requireNonNull(rejection, "rejection");
            if (accepted != (rejection == Rejection.NONE)) {
                throw new IllegalArgumentException("accepted qi step must have no rejection");
            }
        }
    }

    public static Result advance(QiState current, long serverTick, Control control,
                                 Environment environment, Tuning tuning) {
        Objects.requireNonNull(current, "current qi");
        Objects.requireNonNull(control, "player control");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(tuning, "qi tuning");
        if (serverTick <= current.lastTick()) {
            return reject(current, Rejection.STALE_SERVER_TICK);
        }

        double externalTian = addCapped(current.externalTian(), environment.tianSupply(),
                tuning.maxExternalTian());
        double externalDi = addCapped(current.externalDi(), environment.diSupply(), tuning.maxExternalDi());
        double tianAbsorbed = Math.min(externalTian, tuning.maxTianAbsorptionPerTick() * control.tian());
        double diAbsorbed = Math.min(externalDi, tuning.maxDiAbsorptionPerTick() * control.di());
        double renReleased = Math.min(current.humanReserve(), tuning.maxHumanReleasePerTick() * control.ren());

        double tian = current.tian() + tianAbsorbed;
        double di = current.di() + diAbsorbed;
        double ren = current.ren() + renReleased;
        if (!Double.isFinite(tian) || !Double.isFinite(di) || !Double.isFinite(ren)) {
            return reject(current, Rejection.INVALID_RESULT);
        }
        double humanReserve = current.humanReserve() - renReleased;
        long imbalanceTicks = imbalanceRatio(tian, di, ren) > tuning.resonanceTolerance()
                ? current.imbalanceTicks() >= tuning.maxImbalanceTicks()
                    ? tuning.maxImbalanceTicks() : current.imbalanceTicks() + 1L
                : 0L;
        QiState updated = new QiState(tian, di, ren,
                externalTian - tianAbsorbed, externalDi - diAbsorbed,
                humanReserve, imbalanceTicks, serverTick);
        return new Result(true, updated, Rejection.NONE);
    }

    private static double addCapped(double current, double supply, double cap) {
        if (current >= cap) return current;
        return current + Math.min(supply, cap - current);
    }

    private static double imbalanceRatio(double tian, double di, double ren) {
        double maximum = Math.max(tian, Math.max(di, ren));
        if (maximum == 0.0) return 0.0;
        double minimum = Math.min(tian, Math.min(di, ren));
        return (maximum - minimum) / maximum;
    }

    private static Result reject(QiState current, Rejection rejection) {
        return new Result(false, current, rejection);
    }

    private static void requireUnit(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be finite and between zero and one");
        }
    }

    private static void requireFiniteNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireFinitePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}
