package io.kunkun.tpsgenerator.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircuitBreaker.
 */
class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // 50% error threshold, window size of 10
        circuitBreaker = new CircuitBreaker(0.5, 10);
    }

    @Test
    @DisplayName("Should allow requests when circuit is closed")
    void shouldAllowRequestsWhenClosed() {
        assertTrue(circuitBreaker.allowRequest());
        assertFalse(circuitBreaker.isOpen());
    }

    @Test
    @DisplayName("Should open circuit when error threshold exceeded")
    void shouldOpenCircuitWhenThresholdExceeded() {
        // Record 10 failures (100% error rate)
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordResult(false);
        }

        assertTrue(circuitBreaker.isOpen());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Should stay closed when error rate below threshold")
    void shouldStayClosedWhenBelowThreshold() {
        // Record 4 failures, 6 successes (40% error rate < 50% threshold)
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordResult(false);
        }
        for (int i = 0; i < 6; i++) {
            circuitBreaker.recordResult(true);
        }

        assertFalse(circuitBreaker.isOpen());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Should calculate error rate correctly")
    void shouldCalculateErrorRateCorrectly() {
        // Record 3 failures, 7 successes (30% error rate)
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordResult(false);
        }
        for (int i = 0; i < 7; i++) {
            circuitBreaker.recordResult(true);
        }

        assertEquals(0.3, circuitBreaker.getCurrentErrorRate(), 0.01);
    }

    @Test
    @DisplayName("Should return zero error rate when no results")
    void shouldReturnZeroErrorRateWhenEmpty() {
        assertEquals(0.0, circuitBreaker.getCurrentErrorRate());
    }

    @Test
    @DisplayName("Should reset circuit breaker")
    void shouldResetCircuitBreaker() {
        // Open the circuit
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordResult(false);
        }
        assertTrue(circuitBreaker.isOpen());

        // Reset
        circuitBreaker.reset();

        assertFalse(circuitBreaker.isOpen());
        assertEquals(0.0, circuitBreaker.getCurrentErrorRate());
    }

    @Test
    @DisplayName("Should throw exception for invalid threshold")
    void shouldThrowExceptionForInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(-0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(1.1, 10));
    }

    @Test
    @DisplayName("Should use sliding window semantics")
    void shouldUseSlidingWindow() {
        // Fill window with 10 successes
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordResult(true);
        }
        assertEquals(0.0, circuitBreaker.getCurrentErrorRate(), 0.01);

        // Add 5 more failures - window should slide
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordResult(false);
        }

        // Window now has 5 successes + 5 failures = 50% error rate
        assertEquals(0.5, circuitBreaker.getCurrentErrorRate(), 0.01);
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent access")
    void shouldBeThreadSafe() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(0.5, 100);
        int numThreads = 10;
        int recordsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        // Half succeed, half fail
                        cb.recordResult(threadNum % 2 == 0);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Should not throw any exceptions and error rate should be calculable
        double errorRate = cb.getCurrentErrorRate();
        assertTrue(errorRate >= 0.0 && errorRate <= 1.0);
    }

    @Test
    @DisplayName("Should record open time when circuit opens")
    void shouldRecordOpenTime() {
        assertNull(circuitBreaker.getOpenTime());

        // Open the circuit
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordResult(false);
        }

        assertNotNull(circuitBreaker.getOpenTime());
    }
}
