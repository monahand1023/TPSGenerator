package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResponseTimeMetrics.
 */
class ResponseTimeMetricsTest {

    private ResponseTimeMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ResponseTimeMetrics();
    }

    @Test
    @DisplayName("Should record and retrieve response times")
    void shouldRecordResponseTimes() {
        metrics.recordResponseTime(100);
        metrics.recordResponseTime(200);
        metrics.recordResponseTime(300);
        metrics.updateSnapshots();

        assertEquals(3, metrics.getResponseTimeCount());
        assertEquals(100, metrics.getResponseTimePercentile(0));
        assertEquals(300, metrics.getResponseTimePercentile(100));
    }

    @Test
    @DisplayName("Should calculate percentiles correctly")
    void shouldCalculatePercentilesCorrectly() {
        // Record 100 values from 1 to 100
        for (int i = 1; i <= 100; i++) {
            metrics.recordResponseTime(i);
        }
        metrics.updateSnapshots();

        // P50 should be around 50
        long p50 = metrics.getResponseTimePercentile(50);
        assertTrue(p50 >= 49 && p50 <= 51, "P50 should be around 50, was: " + p50);

        // P99 should be around 99
        long p99 = metrics.getResponseTimePercentile(99);
        assertTrue(p99 >= 98 && p99 <= 100, "P99 should be around 99, was: " + p99);
    }

    @Test
    @DisplayName("Should record rate limiter wait times")
    void shouldRecordRateLimiterWaitTimes() {
        metrics.recordRateLimiterWait(0.5);  // 500ms
        metrics.recordRateLimiterWait(1.0);  // 1000ms
        metrics.recordRateLimiterWait(0.1);  // 100ms
        metrics.updateSnapshots();

        long min = metrics.getRateLimiterWaitPercentile(0);
        long max = metrics.getRateLimiterWaitPercentile(100);

        assertEquals(100, min);
        assertEquals(1000, max);
    }

    @Test
    @DisplayName("Should calculate mean response time")
    void shouldCalculateMeanResponseTime() {
        metrics.recordResponseTime(100);
        metrics.recordResponseTime(200);
        metrics.recordResponseTime(300);
        metrics.updateSnapshots();

        double mean = metrics.getMeanResponseTime();
        assertEquals(200.0, mean, 1.0);
    }

    @Test
    @DisplayName("Should reset all metrics")
    void shouldResetMetrics() {
        metrics.recordResponseTime(100);
        metrics.recordRateLimiterWait(0.5);
        metrics.updateSnapshots();

        assertEquals(1, metrics.getResponseTimeCount());

        metrics.reset();

        assertEquals(0, metrics.getResponseTimeCount());
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent access")
    void shouldBeThreadSafe() throws InterruptedException {
        int numThreads = 10;
        int recordsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        metrics.recordResponseTime(j + 1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        metrics.updateSnapshots();

        assertEquals(numThreads * recordsPerThread, metrics.getResponseTimeCount());
    }

    @Test
    @DisplayName("Should handle zero values")
    void shouldHandleZeroValues() {
        metrics.recordResponseTime(0);
        metrics.updateSnapshots();
        assertEquals(1, metrics.getResponseTimeCount());
        assertEquals(0, metrics.getResponseTimePercentile(100));
    }

    @Test
    @DisplayName("Should return zero for empty histograms before snapshot")
    void shouldReturnZeroBeforeSnapshot() {
        // Before updateSnapshots, values should be zero
        assertEquals(0, metrics.getResponseTimeCount());
        assertEquals(0, metrics.getResponseTimePercentile(50));
        assertEquals(0.0, metrics.getMeanResponseTime());
    }

    @Test
    @DisplayName("Should accumulate across multiple snapshot updates")
    void shouldAccumulateAcrossSnapshots() {
        // First batch
        metrics.recordResponseTime(100);
        metrics.recordResponseTime(200);
        metrics.updateSnapshots();
        assertEquals(2, metrics.getResponseTimeCount());

        // Second batch
        metrics.recordResponseTime(300);
        metrics.recordResponseTime(400);
        metrics.updateSnapshots();
        assertEquals(4, metrics.getResponseTimeCount());
    }
}
