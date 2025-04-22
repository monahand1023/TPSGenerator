package io.kunkun.tpsgenerator.traffic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A traffic pattern that maintains a constant TPS rate throughout the test.
 */
@Slf4j
@RequiredArgsConstructor
public class StablePattern implements TrafficPattern {

    /**
     * The constant TPS rate.
     */
    @Getter
    private final double targetTps;

    @Override
    public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
        return targetTps;
    }

    @Override
    public double getMaxTps() {
        return targetTps;
    }

    @Override
    public String toString() {
        return String.format("StablePattern(tps=%.2f)", targetTps);
    }
}