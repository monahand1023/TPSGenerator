package io.kunkun.tpsgenerator;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.TestMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pass/fail SLA evaluation (exit-code-3 gate).
 */
class TPSGeneratorApplicationSlaTest {

    /** 100 requests, 95 successful (95% success), 50 avg TPS. */
    private TestMetrics metrics() {
        TestMetrics m = new TestMetrics();
        for (int i = 0; i < 100; i++) m.incrementTotalRequests();
        for (int i = 0; i < 95; i++) m.incrementSuccessCount();
        m.setAverageTps(50.0);
        return m;
    }

    private LatencyStats latency(double p50, double p95, double p99) {
        return new LatencyStats(p50, p95, p99, p99, p50);
    }

    @Test
    @DisplayName("No SLA config => no breaches")
    void noSlaConfigNoBreaches() {
        TestConfig c = new TestConfig();
        assertTrue(TPSGeneratorApplication.evaluateSlaBreaches(c, latency(10, 20, 30), metrics()).isEmpty());
    }

    @Test
    @DisplayName("p95 over budget is reported")
    void p95BreachDetected() {
        TestConfig c = new TestConfig();
        TestConfig.SlaConfig sla = new TestConfig.SlaConfig();
        sla.setMaxP95Ms(15);
        c.setSla(sla);

        List<String> breaches = TPSGeneratorApplication.evaluateSlaBreaches(c, latency(10, 20, 30), metrics());
        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("p95"), breaches.get(0));
    }

    @Test
    @DisplayName("p50 over budget is reported")
    void p50BreachDetected() {
        TestConfig c = new TestConfig();
        TestConfig.SlaConfig sla = new TestConfig.SlaConfig();
        sla.setMaxP50Ms(5);
        c.setSla(sla);

        List<String> breaches = TPSGeneratorApplication.evaluateSlaBreaches(c, latency(10, 20, 30), metrics());
        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("p50"), breaches.get(0));
    }

    @Test
    @DisplayName("success rate under budget is reported")
    void successRateBreachDetected() {
        TestConfig c = new TestConfig();
        TestConfig.SlaConfig sla = new TestConfig.SlaConfig();
        sla.setMinSuccessRate(0.99);
        c.setSla(sla);

        List<String> breaches = TPSGeneratorApplication.evaluateSlaBreaches(c, latency(10, 20, 30), metrics());
        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("success rate"), breaches.get(0));
    }

    @Test
    @DisplayName("multiple breaches are all reported")
    void multipleBreaches() {
        TestConfig c = new TestConfig();
        TestConfig.SlaConfig sla = new TestConfig.SlaConfig();
        sla.setMaxP99Ms(5);          // 30 > 5
        sla.setMinAverageTps(100);   // 50 < 100
        c.setSla(sla);

        List<String> breaches = TPSGeneratorApplication.evaluateSlaBreaches(c, latency(10, 20, 30), metrics());
        assertEquals(2, breaches.size());
    }

    @Test
    @DisplayName("everything within budget => no breaches")
    void allWithinNoBreach() {
        TestConfig c = new TestConfig();
        TestConfig.SlaConfig sla = new TestConfig.SlaConfig();
        sla.setMaxP95Ms(100);
        sla.setMinSuccessRate(0.90);
        sla.setMinAverageTps(10);
        c.setSla(sla);

        assertTrue(TPSGeneratorApplication.evaluateSlaBreaches(c, latency(10, 20, 30), metrics()).isEmpty());
    }
}
