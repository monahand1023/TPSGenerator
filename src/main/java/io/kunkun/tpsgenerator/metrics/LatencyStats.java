package io.kunkun.tpsgenerator.metrics;

/**
 * Immutable snapshot of latency percentile statistics.
 * All values are in milliseconds.
 */
public final class LatencyStats {

    /** 50th-percentile (median) latency in milliseconds. */
    private final double p50Ms;

    /** 95th-percentile latency in milliseconds. */
    private final double p95Ms;

    /** 99th-percentile latency in milliseconds. */
    private final double p99Ms;

    /** Maximum recorded latency in milliseconds. */
    private final double maxMs;

    /** Mean (average) latency in milliseconds. */
    private final double meanMs;

    /**
     * Creates a new LatencyStats instance.
     *
     * @param p50Ms  50th-percentile latency in milliseconds
     * @param p95Ms  95th-percentile latency in milliseconds
     * @param p99Ms  99th-percentile latency in milliseconds
     * @param maxMs  maximum latency in milliseconds
     * @param meanMs mean latency in milliseconds
     */
    public LatencyStats(double p50Ms, double p95Ms, double p99Ms, double maxMs, double meanMs) {
        this.p50Ms = p50Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.maxMs = maxMs;
        this.meanMs = meanMs;
    }

    /** Returns the 50th-percentile (median) latency in milliseconds. */
    public double getP50Ms() {
        return p50Ms;
    }

    /** Returns the 95th-percentile latency in milliseconds. */
    public double getP95Ms() {
        return p95Ms;
    }

    /** Returns the 99th-percentile latency in milliseconds. */
    public double getP99Ms() {
        return p99Ms;
    }

    /** Returns the maximum recorded latency in milliseconds. */
    public double getMaxMs() {
        return maxMs;
    }

    /** Returns the mean (average) latency in milliseconds. */
    public double getMeanMs() {
        return meanMs;
    }

    @Override
    public String toString() {
        return String.format(
                "LatencyStats{p50=%.3f ms, p95=%.3f ms, p99=%.3f ms, max=%.3f ms, mean=%.3f ms}",
                p50Ms, p95Ms, p99Ms, maxMs, meanMs);
    }
}
