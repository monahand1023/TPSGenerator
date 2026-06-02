package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.config.Constants;
import lombok.Data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Collects TPS (transactions per second) metrics over time.
 * Thread-safe for concurrent access (all access is synchronized on the deque;
 * samples are recorded at ~1 Hz so contention is negligible).
 */
public class TpsMetrics {

    /**
     * Default maximum number of TPS samples to retain.
     */
    private static final int DEFAULT_MAX_SAMPLES = Constants.MAX_TPS_SAMPLES;

    /**
     * Maximum number of TPS samples to retain in memory.
     */
    private final int maxSamples;

    /**
     * TPS samples over time. An {@link ArrayDeque} ring buffer — evicting the oldest is
     * O(1) ({@code pollFirst}), unlike {@code List.remove(0)} which copies the whole backing array.
     */
    private final Deque<TpsSample> samples = new ArrayDeque<>();

    /**
     * Creates a new TpsMetrics with default max samples.
     */
    public TpsMetrics() {
        this(DEFAULT_MAX_SAMPLES);
    }

    /**
     * Creates a new TpsMetrics with specified max samples.
     *
     * @param maxSamples the maximum number of samples to retain
     */
    public TpsMetrics(int maxSamples) {
        this.maxSamples = maxSamples;
    }

    /**
     * Records a TPS sample at the current time.
     *
     * @param tps the TPS value
     */
    public void recordTps(long tps) {
        recordTps(System.currentTimeMillis(), tps);
    }

    /**
     * Records a TPS sample at a specific time.
     *
     * @param timestamp the timestamp in milliseconds
     * @param tps the TPS value
     */
    public void recordTps(long timestamp, long tps) {
        synchronized (samples) {
            if (samples.size() >= maxSamples) {
                samples.pollFirst(); // drop oldest — O(1)
            }
            samples.addLast(new TpsSample(timestamp, tps));
        }
    }

    /**
     * Gets all TPS samples.
     *
     * @return a copy of all TPS samples
     */
    public List<TpsSample> getSamples() {
        synchronized (samples) {
            return new ArrayList<>(samples);
        }
    }

    /**
     * Gets the number of recorded samples.
     *
     * @return the sample count
     */
    public int getSampleCount() {
        synchronized (samples) {
            return samples.size();
        }
    }

    /**
     * Gets the maximum TPS recorded.
     *
     * @return the maximum TPS, or 0 if no samples
     */
    public long getMaxTps() {
        synchronized (samples) {
            return samples.stream().mapToLong(TpsSample::getTps).max().orElse(0);
        }
    }

    /**
     * Gets the minimum TPS recorded.
     *
     * @return the minimum TPS, or 0 if no samples
     */
    public long getMinTps() {
        synchronized (samples) {
            return samples.stream().mapToLong(TpsSample::getTps).min().orElse(0);
        }
    }

    /**
     * Gets the average TPS across all samples.
     *
     * @return the average TPS, or 0 if no samples
     */
    public double getAverageTps() {
        synchronized (samples) {
            return samples.stream().mapToLong(TpsSample::getTps).average().orElse(0);
        }
    }

    /**
     * Gets the most recent TPS value.
     *
     * @return the most recent TPS, or 0 if no samples
     */
    public long getCurrentTps() {
        synchronized (samples) {
            TpsSample last = samples.peekLast();
            return last != null ? last.getTps() : 0;
        }
    }

    /**
     * Resets all samples.
     */
    public void reset() {
        synchronized (samples) {
            samples.clear();
        }
    }

    /**
     * A TPS sample at a specific time.
     */
    @Data
    public static class TpsSample {
        /**
         * The timestamp of the sample in milliseconds.
         */
        private final long timestamp;

        /**
         * The TPS value.
         */
        private final long tps;
    }
}
