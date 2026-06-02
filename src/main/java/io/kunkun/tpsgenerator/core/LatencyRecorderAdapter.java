package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.metrics.LatencyStats;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around an HDR {@link Recorder} that encapsulates the
 * "is latency recording enabled?" decision.
 *
 * <p>When recording is disabled every call to {@link #record(long)} is a
 * no-op, eliminating the scattered {@code if (recordLatency)} checks that
 * would otherwise litter calling code.
 *
 * <p>Values are recorded in <em>microseconds</em> and converted to
 * milliseconds when {@link #getLatencyStats()} is called, matching the
 * convention used by the rest of the metrics pipeline.
 */
public class LatencyRecorderAdapter {

    /** Max trackable value: 1 hour in µs. */
    private static final long MAX_TRACKABLE_US = TimeUnit.MINUTES.toMicros(60);

    /** HDR histogram significant digits (3 ≈ 0.1% relative error). */
    private static final int SIGNIFICANT_DIGITS = 3;

    private final boolean enabled;
    private final Recorder recorder;

    /**
     * Accumulated histogram that persists across {@link #getLatencyStats()} calls.
     * {@link Recorder#getIntervalHistogram()} resets the active recorder, so without
     * accumulating here a second call to getLatencyStats() would return all zeros
     * (every interval read drains the recorder).
     */
    private final Histogram accumulated;

    /**
     * Creates a new adapter.
     *
     * @param enabled {@code true} to record latency values; {@code false} to
     *                make all {@link #record(long)} calls no-ops
     */
    public LatencyRecorderAdapter(boolean enabled) {
        this.enabled = enabled;
        this.recorder = enabled
                ? new Recorder(MAX_TRACKABLE_US, SIGNIFICANT_DIGITS)
                : null;
        this.accumulated = enabled
                ? new Histogram(MAX_TRACKABLE_US, SIGNIFICANT_DIGITS)
                : null;
    }

    /**
     * Records a single latency observation.
     *
     * @param nanos elapsed time of the request in nanoseconds; automatically
     *              converted to microseconds before recording
     */
    public void record(long nanos) {
        if (enabled) {
            recorder.recordValue(nanos / 1_000);
        }
    }

    /**
     * Records a latency observation with coordinated-omission correction. When the
     * observed latency exceeds {@code expectedIntervalNanos}, HdrHistogram synthesises
     * the additional samples that "should" have been issued during the stall, so a
     * slow target can no longer hide behind an under-issuing load generator.
     *
     * @param nanos                 observed end-to-end latency in nanoseconds
     * @param expectedIntervalNanos expected inter-request interval in nanoseconds
     *                              (0 to disable correction for this sample)
     */
    public void recordWithExpectedInterval(long nanos, long expectedIntervalNanos) {
        if (!enabled) {
            return;
        }
        long valueUs = nanos / 1_000;
        long expectedUs = expectedIntervalNanos / 1_000;
        if (expectedUs > 0) {
            recorder.recordValueWithExpectedInterval(valueUs, expectedUs);
        } else {
            recorder.recordValue(valueUs);
        }
    }

    /**
     * Returns the underlying {@link Recorder}.
     * Callers that need fine-grained access (e.g. calling
     * {@link Recorder#getIntervalHistogram()} directly) may use this accessor.
     *
     * @return the recorder, or {@code null} when recording is disabled
     */
    public Recorder getRecorder() {
        return recorder;
    }

    /**
     * Returns a {@link LatencyStats} snapshot computed from all values recorded
     * so far.  Calls {@link Recorder#getIntervalHistogram()} to flip the active
     * histogram.
     *
     * <p>Returns an all-zero {@link LatencyStats} when recording is disabled or
     * no values have been recorded yet.
     *
     * @return latency percentile statistics; never {@code null}
     */
    public LatencyStats getLatencyStats() {
        if (!enabled || recorder == null) {
            return new LatencyStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        // Drain the recorder's interval into the persistent accumulator so repeated
        // calls remain correct (and additive) rather than destructive.
        accumulated.add(recorder.getIntervalHistogram());
        Histogram h = accumulated;
        if (h.getTotalCount() == 0) {
            return new LatencyStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double p50Ms  = h.getValueAtPercentile(50.0) / 1_000.0;
        double p95Ms  = h.getValueAtPercentile(95.0) / 1_000.0;
        double p99Ms  = h.getValueAtPercentile(99.0) / 1_000.0;
        double maxMs  = h.getMaxValue()              / 1_000.0;
        double meanMs = h.getMean()                  / 1_000.0;
        return new LatencyStats(p50Ms, p95Ms, p99Ms, maxMs, meanMs);
    }
}
