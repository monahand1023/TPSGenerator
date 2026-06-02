package io.kunkun.tpsgenerator.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Circuit breaker pattern implementation for preventing excessive failures in load tests.
 * The circuit breaker monitors the success/failure rate of requests and will "open"
 * (stop allowing requests) if the error rate exceeds a threshold.
 */
@Slf4j
public class CircuitBreaker {

    /**
     * The error threshold rate (0.0 - 1.0) at which the circuit breaker will open.
     */
    private final double errorThreshold;

    /**
     * The size of the sliding window for error rate calculation.
     */
    private final int windowSize;

    /**
     * Queue to store recent request results (true = success, false = failure).
     */
    private final CircularFifoQueue<Boolean> results;

    /**
     * Whether the circuit breaker is currently open (preventing requests).
     */
    private final AtomicBoolean isOpen = new AtomicBoolean(false);

    /**
     * Lock for safely accessing the results queue.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Cached error rate to avoid recalculating on every access.
     */
    private final AtomicReference<Double> cachedErrorRate = new AtomicReference<>(0.0);

    /**
     * Running count of failures currently in the window. Maintained incrementally so
     * {@link #recordResult(boolean)} is O(1) instead of re-summing the whole window
     * on every request (a serialization hot spot at high TPS). Guarded by the write lock.
     */
    private long failureCount = 0;

    /**
     * The time when the circuit breaker was opened.
     */
    private volatile Instant openTime;

    /**
     * Creates a new CircuitBreaker.
     *
     * @param errorThreshold the error threshold rate (0.0 - 1.0)
     * @param windowSize the size of the sliding window for error rate calculation
     */
    public CircuitBreaker(double errorThreshold, int windowSize) {
        if (errorThreshold < 0.0 || errorThreshold > 1.0) {
            throw new IllegalArgumentException("Error threshold must be between 0.0 and 1.0");
        }

        this.errorThreshold = errorThreshold;
        this.windowSize = windowSize;
        this.results = new CircularFifoQueue<>(windowSize);

        log.info("Initialized circuit breaker with error threshold {}, window size {}",
                errorThreshold, windowSize);
    }

    /**
     * Checks if the circuit breaker allows requests.
     *
     * @return true if requests are allowed, false if the circuit is open
     */
    public boolean allowRequest() {
        return !isOpen.get();
    }

    /**
     * Records the result of a request.
     *
     * @param success true if the request was successful, false if it failed
     */
    public void recordResult(boolean success) {
        lock.writeLock().lock();
        try {
            // Maintain failureCount incrementally: if the window is full the head is
            // about to be evicted, so subtract it before adding the new result.
            if (results.isAtFullCapacity()) {
                Boolean evicted = results.peek();
                if (evicted != null && !evicted) {
                    failureCount--;
                }
            }
            results.add(success);
            if (!success) {
                failureCount++;
            }

            double errorRate = results.isEmpty() ? 0.0 : (double) failureCount / results.size();
            cachedErrorRate.set(errorRate);

            // Only check the circuit state if we have enough data
            if (results.size() >= windowSize) {
                updateCircuitState(errorRate);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates the circuit state based on the current error rate.
     *
     * @param errorRate the calculated error rate
     */
    private void updateCircuitState(double errorRate) {
        // Check if we should open the circuit
        if (!isOpen.get() && errorRate > errorThreshold) {
            openCircuit(errorRate);
        }
        // We could add auto-reset logic here if desired
    }

    /**
     * Opens the circuit breaker.
     *
     * @param errorRate the current error rate
     */
    private void openCircuit(double errorRate) {
        if (isOpen.compareAndSet(false, true)) {
            openTime = Instant.now();
            log.warn("Circuit breaker opened due to error rate: {} (threshold: {})",
                    String.format("%.2f", errorRate), String.format("%.2f", errorThreshold));
        }
    }

    /**
     * Manually resets the circuit breaker to closed state.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            results.clear();
            failureCount = 0;
            cachedErrorRate.set(0.0);
            if (isOpen.compareAndSet(true, false)) {
                log.info("Circuit breaker manually reset to closed state");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the current error rate.
     * Uses a cached value for efficiency, updated on each recordResult call.
     *
     * @return the current error rate or 0.0 if no data is available
     */
    public double getCurrentErrorRate() {
        return cachedErrorRate.get();
    }

    /**
     * Gets whether the circuit breaker is open.
     *
     * @return true if the circuit breaker is open
     */
    public boolean isOpen() {
        return isOpen.get();
    }

    /**
     * Gets the time when the circuit breaker was opened.
     *
     * @return the open time or null if the circuit is closed
     */
    public Instant getOpenTime() {
        return openTime;
    }
}