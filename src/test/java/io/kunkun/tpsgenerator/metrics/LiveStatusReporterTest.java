package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the live status line formatting + lifecycle.
 */
class LiveStatusReporterTest {

    @Test
    @DisplayName("status line includes the key metrics")
    void formatsKeyMetrics() {
        String line = LiveStatusReporter.formatStatusLine(42.5, 65_000, 123.4, 99.9, 10, 45, 90, 5000);
        assertTrue(line.contains("42.5%"), line);
        assertTrue(line.contains("01:05"), line);          // 65s elapsed
        assertTrue(line.contains("123.4"), line);          // TPS
        assertTrue(line.contains("99.9%"), line);          // success
        assertTrue(line.contains("10/45/90 ms"), line);    // percentiles
        assertTrue(line.contains("reqs 5000"), line);
    }

    @Test
    @DisplayName("start/stop is safe and does not throw")
    void startStopIsSafe() {
        MetricsCollector mc = new MetricsCollector(new io.kunkun.tpsgenerator.config.TestConfig());
        mc.start();
        LiveStatusReporter reporter = new LiveStatusReporter(mc, 1000);
        assertDoesNotThrow(() -> {
            reporter.start();
            reporter.stop();
        });
        mc.stop();
    }
}
