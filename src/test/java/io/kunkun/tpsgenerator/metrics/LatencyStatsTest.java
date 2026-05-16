package io.kunkun.tpsgenerator.metrics;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LatencyStats and the HDR Recorder-based latency tracking pattern
 * used by ExecutionController.
 *
 * <p>Records 1000 values (0–999 microseconds) and verifies that percentiles
 * fall within ±5% of their expected values — matching HDR histogram accuracy.
 */
class LatencyStatsTest {

    /** Acceptable relative tolerance for HDR histogram percentile accuracy (±5%). */
    private static final double TOLERANCE = 0.05;

    /**
     * Helper: build a LatencyStats from a Recorder using the same logic as
     * ExecutionController.getLatencyPercentiles().
     */
    private LatencyStats buildLatencyStats(Recorder recorder) {
        Histogram h = recorder.getIntervalHistogram();
        if (h.getTotalCount() == 0) {
            return new LatencyStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double p50Ms  = h.getValueAtPercentile(50.0)  / 1000.0;
        double p95Ms  = h.getValueAtPercentile(95.0)  / 1000.0;
        double p99Ms  = h.getValueAtPercentile(99.0)  / 1000.0;
        double maxMs  = h.getMaxValue()               / 1000.0;
        double meanMs = h.getMean()                   / 1000.0;
        return new LatencyStats(p50Ms, p95Ms, p99Ms, maxMs, meanMs);
    }

    @Test
    @DisplayName("p50 should be approximately 500 µs (0.500 ms) within ±5%")
    void p50ShouldBeApproximately500Microseconds() {
        Recorder recorder = new Recorder(60_000_000L, 3);
        for (int i = 0; i < 1000; i++) {
            recorder.recordValue(i);
        }

        LatencyStats stats = buildLatencyStats(recorder);

        // Expected p50 ≈ 0.500 ms (500 µs)
        double expectedMs = 0.500;
        double lowerBound = expectedMs * (1 - TOLERANCE);
        double upperBound = expectedMs * (1 + TOLERANCE);

        assertTrue(
                stats.getP50Ms() >= lowerBound && stats.getP50Ms() <= upperBound,
                String.format("p50 should be %.3f ± 5%% ms, but was %.3f ms",
                        expectedMs, stats.getP50Ms())
        );
    }

    @Test
    @DisplayName("p95 should be approximately 950 µs (0.950 ms) within ±5%")
    void p95ShouldBeApproximately950Microseconds() {
        Recorder recorder = new Recorder(60_000_000L, 3);
        for (int i = 0; i < 1000; i++) {
            recorder.recordValue(i);
        }

        LatencyStats stats = buildLatencyStats(recorder);

        // Expected p95 ≈ 0.950 ms (950 µs)
        double expectedMs = 0.950;
        double lowerBound = expectedMs * (1 - TOLERANCE);
        double upperBound = expectedMs * (1 + TOLERANCE);

        assertTrue(
                stats.getP95Ms() >= lowerBound && stats.getP95Ms() <= upperBound,
                String.format("p95 should be %.3f ± 5%% ms, but was %.3f ms",
                        expectedMs, stats.getP95Ms())
        );
    }

    @Test
    @DisplayName("p99 should be approximately 990 µs (0.990 ms) within ±5%")
    void p99ShouldBeApproximately990Microseconds() {
        Recorder recorder = new Recorder(60_000_000L, 3);
        for (int i = 0; i < 1000; i++) {
            recorder.recordValue(i);
        }

        LatencyStats stats = buildLatencyStats(recorder);

        // Expected p99 ≈ 0.990 ms (990 µs)
        double expectedMs = 0.990;
        double lowerBound = expectedMs * (1 - TOLERANCE);
        double upperBound = expectedMs * (1 + TOLERANCE);

        assertTrue(
                stats.getP99Ms() >= lowerBound && stats.getP99Ms() <= upperBound,
                String.format("p99 should be %.3f ± 5%% ms, but was %.3f ms",
                        expectedMs, stats.getP99Ms())
        );
    }

    @Test
    @DisplayName("All three percentiles should be within ±5% in a single recorder pass")
    void allPercentilesWithinTolerance() {
        Recorder recorder = new Recorder(60_000_000L, 3);
        for (int i = 0; i < 1000; i++) {
            recorder.recordValue(i);
        }

        LatencyStats stats = buildLatencyStats(recorder);

        assertAll("latency percentiles",
                () -> {
                    double expected = 0.500;
                    assertTrue(
                            stats.getP50Ms() >= expected * (1 - TOLERANCE) &&
                            stats.getP50Ms() <= expected * (1 + TOLERANCE),
                            () -> String.format("p50 out of range: %.3f ms", stats.getP50Ms())
                    );
                },
                () -> {
                    double expected = 0.950;
                    assertTrue(
                            stats.getP95Ms() >= expected * (1 - TOLERANCE) &&
                            stats.getP95Ms() <= expected * (1 + TOLERANCE),
                            () -> String.format("p95 out of range: %.3f ms", stats.getP95Ms())
                    );
                },
                () -> {
                    double expected = 0.990;
                    assertTrue(
                            stats.getP99Ms() >= expected * (1 - TOLERANCE) &&
                            stats.getP99Ms() <= expected * (1 + TOLERANCE),
                            () -> String.format("p99 out of range: %.3f ms", stats.getP99Ms())
                    );
                }
        );
    }

    @Test
    @DisplayName("Empty recorder should return all-zero LatencyStats")
    void emptyRecorderShouldReturnZeroStats() {
        Recorder recorder = new Recorder(60_000_000L, 3);
        LatencyStats stats = buildLatencyStats(recorder);

        assertEquals(0.0, stats.getP50Ms(), "p50 should be 0 for empty recorder");
        assertEquals(0.0, stats.getP95Ms(), "p95 should be 0 for empty recorder");
        assertEquals(0.0, stats.getP99Ms(), "p99 should be 0 for empty recorder");
        assertEquals(0.0, stats.getMaxMs(), "max should be 0 for empty recorder");
        assertEquals(0.0, stats.getMeanMs(), "mean should be 0 for empty recorder");
    }

    @Test
    @DisplayName("LatencyStats fields and toString should reflect constructor values")
    void latencyStatsFieldsAndToStringShouldWork() {
        LatencyStats stats = new LatencyStats(1.1, 2.2, 3.3, 4.4, 5.5);

        assertEquals(1.1, stats.getP50Ms(), 1e-9);
        assertEquals(2.2, stats.getP95Ms(), 1e-9);
        assertEquals(3.3, stats.getP99Ms(), 1e-9);
        assertEquals(4.4, stats.getMaxMs(), 1e-9);
        assertEquals(5.5, stats.getMeanMs(), 1e-9);

        String str = stats.toString();
        assertTrue(str.contains("p50"), "toString should mention p50");
        assertTrue(str.contains("p95"), "toString should mention p95");
        assertTrue(str.contains("p99"), "toString should mention p99");
    }
}
