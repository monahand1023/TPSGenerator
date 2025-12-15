package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TpsMetrics.
 */
class TpsMetricsTest {

    private TpsMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new TpsMetrics();
    }

    @Test
    @DisplayName("Should record TPS samples")
    void shouldRecordTpsSamples() {
        metrics.recordTps(100);
        metrics.recordTps(200);
        metrics.recordTps(150);

        assertEquals(3, metrics.getSampleCount());
    }

    @Test
    @DisplayName("Should calculate max TPS")
    void shouldCalculateMaxTps() {
        metrics.recordTps(100);
        metrics.recordTps(300);
        metrics.recordTps(200);

        assertEquals(300, metrics.getMaxTps());
    }

    @Test
    @DisplayName("Should calculate min TPS")
    void shouldCalculateMinTps() {
        metrics.recordTps(100);
        metrics.recordTps(300);
        metrics.recordTps(200);

        assertEquals(100, metrics.getMinTps());
    }

    @Test
    @DisplayName("Should calculate average TPS")
    void shouldCalculateAverageTps() {
        metrics.recordTps(100);
        metrics.recordTps(200);
        metrics.recordTps(300);

        assertEquals(200.0, metrics.getAverageTps(), 0.01);
    }

    @Test
    @DisplayName("Should get current TPS")
    void shouldGetCurrentTps() {
        metrics.recordTps(100);
        metrics.recordTps(200);
        metrics.recordTps(150);

        assertEquals(150, metrics.getCurrentTps());
    }

    @Test
    @DisplayName("Should return zero when empty")
    void shouldReturnZeroWhenEmpty() {
        assertEquals(0, metrics.getMaxTps());
        assertEquals(0, metrics.getMinTps());
        assertEquals(0.0, metrics.getAverageTps());
        assertEquals(0, metrics.getCurrentTps());
    }

    @Test
    @DisplayName("Should return samples as list")
    void shouldReturnSamplesAsList() {
        metrics.recordTps(100);
        metrics.recordTps(200);

        List<TpsMetrics.TpsSample> samples = metrics.getSamples();

        assertEquals(2, samples.size());
        assertEquals(100, samples.get(0).getTps());
        assertEquals(200, samples.get(1).getTps());
    }

    @Test
    @DisplayName("Should enforce max samples limit")
    void shouldEnforceMaxSamplesLimit() {
        TpsMetrics limitedMetrics = new TpsMetrics(5);

        for (int i = 0; i < 10; i++) {
            limitedMetrics.recordTps(i * 100);
        }

        assertEquals(5, limitedMetrics.getSampleCount());
        // Should have the most recent 5 samples (500-900)
        assertEquals(500, limitedMetrics.getSamples().get(0).getTps());
    }

    @Test
    @DisplayName("Should reset all samples")
    void shouldResetSamples() {
        metrics.recordTps(100);
        metrics.recordTps(200);

        metrics.reset();

        assertEquals(0, metrics.getSampleCount());
        assertEquals(0, metrics.getMaxTps());
    }

    @Test
    @DisplayName("Should record TPS with specific timestamp")
    void shouldRecordTpsWithTimestamp() {
        long timestamp = System.currentTimeMillis();
        metrics.recordTps(timestamp, 100);

        TpsMetrics.TpsSample sample = metrics.getSamples().get(0);
        assertEquals(timestamp, sample.getTimestamp());
        assertEquals(100, sample.getTps());
    }
}
