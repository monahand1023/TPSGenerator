package io.kunkun.tpsgenerator.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Calculates transactions per second (TPS) metrics.
 * Thread-safe implementation for concurrent access.
 */
public class TpsCalculator {

    private final LongAdder requestsLastSecond = new LongAdder();
    private final AtomicLong currentTps = new AtomicLong(0);

    /**
     * Increments the request counter.
     * Called when a request completes (success, failure, or timeout).
     */
    public void incrementRequestCount() {
        requestsLastSecond.increment();
    }

    /**
     * Updates the TPS value by reading and resetting the counter.
     * Should be called once per second by a scheduler.
     *
     * @return the TPS value for the last second
     */
    public long updateTps() {
        long tps = requestsLastSecond.sumThenReset();
        currentTps.set(tps);
        return tps;
    }

    /**
     * Gets the current TPS value.
     *
     * @return the current TPS
     */
    public long getCurrentTps() {
        return currentTps.get();
    }

    /**
     * Resets the calculator.
     */
    public void reset() {
        requestsLastSecond.reset();
        currentTps.set(0);
    }
}
