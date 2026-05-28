package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.metrics.LatencyStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LatencyRecorderAdapter}.
 */
class LatencyRecorderAdapterTest {

    /** Acceptable relative tolerance for HDR histogram accuracy (±0.5%). */
    private static final double TOLERANCE = 0.005;

    // -------------------------------------------------------------------------
    // When recording is enabled
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("record() stores values that appear in getLatencyStats()")
    void recordedValueAppearsInStats() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);

        // Record exactly 1000 values: 1 µs … 1000 µs (as nanoseconds)
        for (int i = 1; i <= 1000; i++) {
            adapter.record(i * 1_000L); // i µs in ns
        }

        LatencyStats stats = adapter.getLatencyStats();

        // p50 ≈ 500 µs = 0.500 ms
        assertTrue(stats.getP50Ms() > 0, "p50 should be > 0");
        assertTrue(stats.getP99Ms() > stats.getP50Ms(), "p99 should exceed p50");
        assertTrue(stats.getMaxMs() >= stats.getP99Ms(), "max should be >= p99");
    }

    @Test
    @DisplayName("Empty adapter returns all-zero LatencyStats")
    void emptyAdapterReturnsZeroStats() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);
        LatencyStats stats = adapter.getLatencyStats();

        assertAll("empty adapter stats",
                () -> assertEquals(0.0, stats.getP50Ms()),
                () -> assertEquals(0.0, stats.getP95Ms()),
                () -> assertEquals(0.0, stats.getP99Ms()),
                () -> assertEquals(0.0, stats.getMaxMs()),
                () -> assertEquals(0.0, stats.getMeanMs())
        );
    }

    @Test
    @DisplayName("p50 is approximately 500 µs when recording 1–1000 µs")
    void p50IsApproximatelyCorrect() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);
        for (int i = 1; i <= 1000; i++) {
            adapter.record(i * 1_000L);
        }

        LatencyStats stats = adapter.getLatencyStats();
        double expected = 0.500; // ms
        assertTrue(
                stats.getP50Ms() >= expected * (1 - TOLERANCE)
                        && stats.getP50Ms() <= expected * (1 + TOLERANCE),
                () -> String.format("p50 should be %.3f ± 0.5%% ms but was %.3f ms",
                        expected, stats.getP50Ms())
        );
    }

    @Test
    @DisplayName("p99 is approximately 990 µs when recording 1–1000 µs")
    void p99IsApproximatelyCorrect() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);
        for (int i = 1; i <= 1000; i++) {
            adapter.record(i * 1_000L);
        }

        LatencyStats stats = adapter.getLatencyStats();
        double expected = 0.990; // ms
        // HDR histogram has 0.1% relative accuracy at 3 sig-figs; use 1% tolerance
        assertTrue(
                stats.getP99Ms() >= expected * 0.99
                        && stats.getP99Ms() <= expected * 1.01,
                () -> String.format("p99 should be ~%.3f ms but was %.3f ms",
                        expected, stats.getP99Ms())
        );
    }

    @Test
    @DisplayName("Nano-to-microsecond conversion is correct (1 ms = 1_000_000 ns → 1.0 ms)")
    void nanoToMicrosecondConversionIsCorrect() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);

        // Record exactly 1 ms = 1_000_000 ns, repeated 100 times
        long oneMillisNs = TimeUnit.MILLISECONDS.toNanos(1);
        for (int i = 0; i < 100; i++) {
            adapter.record(oneMillisNs);
        }

        LatencyStats stats = adapter.getLatencyStats();

        // All values are 1000 µs → p50 should be very close to 1.000 ms
        assertEquals(1.0, stats.getP50Ms(), 0.01,
                "All 1 ms recordings should yield p50 ≈ 1.000 ms");
    }

    // -------------------------------------------------------------------------
    // When recording is disabled
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Disabled adapter — record() is a no-op")
    void disabledAdapterRecordIsNoOp() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(false);

        // Should not throw
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                adapter.record(i * 1_000L);
            }
        });
    }

    @Test
    @DisplayName("Disabled adapter — getLatencyStats() returns all-zero")
    void disabledAdapterStatsAreZero() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(false);
        adapter.record(999_000L); // 999 µs — should be ignored

        LatencyStats stats = adapter.getLatencyStats();
        assertAll("disabled adapter stats",
                () -> assertEquals(0.0, stats.getP50Ms()),
                () -> assertEquals(0.0, stats.getP95Ms()),
                () -> assertEquals(0.0, stats.getP99Ms()),
                () -> assertEquals(0.0, stats.getMaxMs()),
                () -> assertEquals(0.0, stats.getMeanMs())
        );
    }

    @Test
    @DisplayName("Disabled adapter — getRecorder() returns null")
    void disabledAdapterRecorderIsNull() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(false);
        assertNull(adapter.getRecorder());
    }

    @Test
    @DisplayName("Enabled adapter — getRecorder() returns non-null Recorder")
    void enabledAdapterRecorderIsNotNull() {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);
        assertNotNull(adapter.getRecorder());
    }

    // -------------------------------------------------------------------------
    // Thread safety
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Concurrent record() calls do not throw")
    void concurrentRecordCallsDoNotThrow() throws InterruptedException {
        LatencyRecorderAdapter adapter = new LatencyRecorderAdapter(true);
        int threads = 8;
        int recordsPerThread = 1000;
        Thread[] workers = new Thread[threads];

        for (int t = 0; t < threads; t++) {
            final int base = t * recordsPerThread;
            workers[t] = new Thread(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    adapter.record((base + i + 1) * 1_000L);
                }
            });
        }

        for (Thread w : workers) w.start();
        for (Thread w : workers) w.join(5000);

        assertDoesNotThrow(adapter::getLatencyStats,
                "getLatencyStats() after concurrent records should not throw");
    }
}
