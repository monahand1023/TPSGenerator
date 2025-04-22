package io.kunkun.tpsgenerator.traffic;

/**
 * Interface for traffic pattern generators.
 * Implementations of this interface provide the target TPS rate at specific points in time.
 */
public interface TrafficPattern {

    /**
     * Get the target TPS rate at the specified time.
     *
     * @param elapsedTimeMs time elapsed since the start of the test (milliseconds)
     * @param totalDurationMs total duration of the test (milliseconds)
     * @return the target TPS rate
     */
    double getTpsAtTime(long elapsedTimeMs, long totalDurationMs);

    /**
     * Get the maximum TPS rate for this pattern.
     * This is used for resource allocation.
     *
     * @return the maximum TPS rate that will be requested by this pattern
     */
    double getMaxTps();
}