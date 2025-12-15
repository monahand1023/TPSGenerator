package io.kunkun.tpsgenerator.traffic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A traffic pattern that linearly increases the TPS rate from a start value to a target value.
 * After reaching the target value, it maintains that rate for the remainder of the test.
 */
@RequiredArgsConstructor
public class RampUpPattern implements TrafficPattern {

    /**
     * The initial TPS rate at the start of the test.
     */
    private final double startTps;

    /**
     * The target TPS rate to ramp up to.
     */
    @Getter
    private final double targetTps;

    /**
     * The duration of the ramp-up period in milliseconds.
     */
    private final long rampDurationMs;

    @Override
    public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
        if (elapsedTimeMs >= rampDurationMs) {
            return targetTps;
        }

        double progressRatio = (double) elapsedTimeMs / rampDurationMs;
        return startTps + (targetTps - startTps) * progressRatio;
    }

    @Override
    public double getMaxTps() {
        return targetTps;
    }

    @Override
    public String toString() {
        return String.format("RampUpPattern(startTps=%.2f, targetTps=%.2f, rampDuration=%d ms)",
                startTps, targetTps, rampDurationMs);
    }
}