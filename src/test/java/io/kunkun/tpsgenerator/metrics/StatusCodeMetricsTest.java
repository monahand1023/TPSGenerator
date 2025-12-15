package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatusCodeMetrics.
 */
class StatusCodeMetricsTest {

    private StatusCodeMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new StatusCodeMetrics();
    }

    @Test
    @DisplayName("Should record status codes")
    void shouldRecordStatusCodes() {
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(404);

        assertEquals(2, metrics.getCount(200));
        assertEquals(1, metrics.getCount(404));
        assertEquals(0, metrics.getCount(500));
    }

    @Test
    @DisplayName("Should return all counts")
    void shouldReturnAllCounts() {
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(201);
        metrics.recordStatusCode(404);

        Map<Integer, Long> counts = metrics.getAllCounts();

        assertEquals(3, counts.size());
        assertEquals(1L, counts.get(200));
        assertEquals(1L, counts.get(201));
        assertEquals(1L, counts.get(404));
    }

    @Test
    @DisplayName("Should count successful responses (2xx)")
    void shouldCountSuccessfulResponses() {
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(201);
        metrics.recordStatusCode(204);
        metrics.recordStatusCode(404);

        assertEquals(3, metrics.getSuccessfulCount());
    }

    @Test
    @DisplayName("Should count client errors (4xx)")
    void shouldCountClientErrors() {
        metrics.recordStatusCode(400);
        metrics.recordStatusCode(401);
        metrics.recordStatusCode(404);
        metrics.recordStatusCode(200);

        assertEquals(3, metrics.getClientErrorCount());
    }

    @Test
    @DisplayName("Should count server errors (5xx)")
    void shouldCountServerErrors() {
        metrics.recordStatusCode(500);
        metrics.recordStatusCode(502);
        metrics.recordStatusCode(503);
        metrics.recordStatusCode(200);

        assertEquals(3, metrics.getServerErrorCount());
    }

    @Test
    @DisplayName("Should calculate total count")
    void shouldCalculateTotalCount() {
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(404);
        metrics.recordStatusCode(500);

        assertEquals(4, metrics.getTotalCount());
    }

    @Test
    @DisplayName("Should detect errors")
    void shouldDetectErrors() {
        assertFalse(metrics.hasErrors());

        metrics.recordStatusCode(200);
        assertFalse(metrics.hasErrors());

        metrics.recordStatusCode(400);
        assertTrue(metrics.hasErrors());
    }

    @Test
    @DisplayName("Should reset all counts")
    void shouldResetCounts() {
        metrics.recordStatusCode(200);
        metrics.recordStatusCode(500);

        metrics.reset();

        assertEquals(0, metrics.getTotalCount());
        assertEquals(0, metrics.getCount(200));
        assertFalse(metrics.hasErrors());
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
                        metrics.recordStatusCode(200);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(numThreads * recordsPerThread, metrics.getCount(200));
    }
}
