package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RunComparator} regression detection.
 */
class RunComparatorTest {

    private Map<String, Object> doc(double successRate, double avgTps, double p95) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("successRate", successRate);
        m.put("averageTps", avgTps);
        Map<String, Object> lat = new LinkedHashMap<>();
        lat.put("p50Ms", 10.0);
        lat.put("p95Ms", p95);
        lat.put("p99Ms", p95 * 1.5);
        lat.put("maxMs", p95 * 2);
        lat.put("meanMs", 12.0);
        m.put("latency", lat);
        return m;
    }

    @Test
    @DisplayName("Identical runs produce no regressions")
    void identicalRunsNoRegression() {
        RunComparator.Result r = RunComparator.compare(doc(0.99, 50, 20), doc(0.99, 50, 20), 10.0, 0.01);
        assertFalse(r.hasRegressions());
        assertFalse(r.getLines().isEmpty());
    }

    @Test
    @DisplayName("Large p95 latency increase is flagged")
    void latencyRegressionFlagged() {
        RunComparator.Result r = RunComparator.compare(doc(0.99, 50, 20), doc(0.99, 50, 40), 10.0, 0.01);
        assertTrue(r.hasRegressions());
        assertTrue(r.getRegressions().stream().anyMatch(s -> s.contains("p95Ms")), r.getRegressions().toString());
    }

    @Test
    @DisplayName("Success-rate drop beyond threshold is flagged")
    void successRateDropFlagged() {
        RunComparator.Result r = RunComparator.compare(doc(0.99, 50, 20), doc(0.95, 50, 20), 10.0, 0.01);
        assertTrue(r.hasRegressions());
        assertTrue(r.getRegressions().stream().anyMatch(s -> s.contains("success rate")), r.getRegressions().toString());
    }

    @Test
    @DisplayName("Small latency increase within threshold is not flagged")
    void smallLatencyIncreaseTolerated() {
        RunComparator.Result r = RunComparator.compare(doc(0.99, 50, 20), doc(0.99, 50, 21), 10.0, 0.01);
        assertFalse(r.hasRegressions(), r.getRegressions().toString());
    }
}
