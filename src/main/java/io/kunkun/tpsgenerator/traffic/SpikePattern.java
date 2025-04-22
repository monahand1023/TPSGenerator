package io.kunkun.tpsgenerator.traffic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A traffic pattern that maintains a base TPS rate with a spike of higher TPS at a specific time.
 */
@Slf4j
@RequiredArgsConstructor
public class SpikePattern implements TrafficPattern {

    /**
     * The base TPS rate.
     */
    private final double baseTps;

    /**
     * The TPS rate during the spike.
     */
    private final double spikeTps;

    /**
     * The start time of the spike in milliseconds from the test start.
     */
    private final long spikeStartMs;

    /**
     * The duration of the spike in milliseconds.
     */
    private final long spikeDurationMs;

    @Override
    public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
        if (elapsedTimeMs >= spikeStartMs && elapsedTimeMs < spikeStartMs + spikeDurationMs) {
            return spikeTps;
        }
        return baseTps;
    }

    @Override
    public double getMaxTps() {
        return Math.max(baseTps, spikeTps);
    }

    @Override
    public String toString() {
        return String.format("SpikePattern(baseTps=%.2f, spikeTps=%.2f, spikeStart=%d ms, spikeDuration=%d ms)",
                baseTps, spikeTps, spikeStartMs, spikeDurationMs);
    }
}