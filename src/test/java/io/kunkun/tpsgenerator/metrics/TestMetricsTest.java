package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestMetrics.
 */
class TestMetricsTest {

    private TestMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new TestMetrics();
    }

    // -------- counters --------

    @Test
    @DisplayName("incrementTotalRequests: totalRequests increases")
    void incrementTotalRequestsIncreases() {
        metrics.incrementTotalRequests();
        metrics.incrementTotalRequests();
        assertEquals(2L, metrics.getTotalRequests());
    }

    @Test
    @DisplayName("incrementSuccessCount: successCount increases")
    void incrementSuccessCount() {
        metrics.incrementSuccessCount();
        assertEquals(1L, metrics.getSuccessCount());
    }

    @Test
    @DisplayName("incrementFailureCount: failureCount increases")
    void incrementFailureCount() {
        metrics.incrementFailureCount();
        assertEquals(1L, metrics.getFailureCount());
    }

    @Test
    @DisplayName("incrementTimeoutCount: timeoutCount increases")
    void incrementTimeoutCount() {
        metrics.incrementTimeoutCount();
        assertEquals(1L, metrics.getTimeoutCount());
    }

    @Test
    @DisplayName("incrementSkippedCount: skippedCount increases")
    void incrementSkippedCount() {
        metrics.incrementSkippedCount();
        assertEquals(1L, metrics.getSkippedCount());
    }

    // -------- successRate --------

    @Test
    @DisplayName("getSuccessRate: returns 0.0 when no requests")
    void successRateZeroWhenNoRequests() {
        assertEquals(0.0, metrics.getSuccessRate());
    }

    @Test
    @DisplayName("getSuccessRate: computes correct fraction")
    void successRateComputedCorrectly() {
        for (int i = 0; i < 4; i++) metrics.incrementTotalRequests();
        for (int i = 0; i < 3; i++) metrics.incrementSuccessCount();
        assertEquals(3.0 / 4.0, metrics.getSuccessRate(), 0.001);
    }

    // -------- response time recording --------

    @Test
    @DisplayName("recordResponseTime: values available after updateHistogramSnapshots")
    void recordResponseTimeAvailableAfterUpdate() {
        metrics.recordResponseTime(100L);
        metrics.recordResponseTime(200L);
        metrics.updateHistogramSnapshots();
        // After snapshot update, percentiles should reflect recorded values
        assertTrue(metrics.getResponseTimePercentile(50) > 0,
                "P50 should be positive after recording values");
    }

    // -------- TPS --------

    @Test
    @DisplayName("recordTps: getMaxTps returns max recorded value")
    void recordTpsMaxValue() {
        metrics.recordTps(100L);
        metrics.recordTps(50L);
        metrics.recordTps(200L);
        assertEquals(200L, metrics.getMaxTps());
    }

    @Test
    @DisplayName("getTpsSamples: returns all recorded samples")
    void tpsSamplesReturnsAllRecorded() {
        metrics.recordTps(10L);
        metrics.recordTps(20L);
        assertEquals(2, metrics.getTpsSamples().size());
    }

    // -------- status code counts --------

    @Test
    @DisplayName("recordStatusCode: increments count for that code")
    void recordStatusCodeIncrements() {
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(404);
        Map<Integer, Long> counts = metrics.getStatusCodeCounts();
        assertEquals(2L, counts.getOrDefault(200, 0L));
        assertEquals(1L, counts.getOrDefault(404, 0L));
    }

    // -------- time fields --------

    @Test
    @DisplayName("setStartTime and setEndTime are reflected by getters")
    void startAndEndTimeSetAndGet() {
        metrics.setStartTime(1_000_000L);
        metrics.setEndTime(2_000_000L);
        assertEquals(1_000_000L, metrics.getStartTime());
        assertEquals(2_000_000L, metrics.getEndTime());
    }

    @Test
    @DisplayName("setDuration is reflected by getDuration")
    void durationSetAndGet() {
        metrics.setDuration(5000L);
        assertEquals(5000L, metrics.getDuration());
    }

    // -------- averageTps --------

    @Test
    @DisplayName("setAverageTps and getAverageTps round-trip")
    void averageTpsRoundTrip() {
        metrics.setAverageTps(42.5);
        assertEquals(42.5, metrics.getAverageTps(), 0.001);
    }

    // -------- sub-collectors initialised --------

    @Test
    @DisplayName("getResponseTimeMetrics returns non-null")
    void responseTimeMetricsNonNull() {
        assertNotNull(metrics.getResponseTimeMetrics());
    }

    @Test
    @DisplayName("getStatusCodeMetrics returns non-null")
    void statusCodeMetricsNonNull() {
        assertNotNull(metrics.getStatusCodeMetrics());
    }

    @Test
    @DisplayName("getTpsMetrics returns non-null")
    void tpsMetricsNonNull() {
        assertNotNull(metrics.getTpsMetrics());
    }
}
