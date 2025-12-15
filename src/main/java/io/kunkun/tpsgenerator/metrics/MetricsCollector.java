package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.model.ResourceSnapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects and aggregates metrics for a load test.
 * Implements Closeable for proper resource cleanup.
 */
@Slf4j
public class MetricsCollector implements Closeable {

    private final TestConfig config;

    @Getter
    private final TestMetrics testMetrics = new TestMetrics();

    @Getter
    private final NetworkMetrics networkMetrics = new NetworkMetrics();

    @Getter
    private final ErrorAnalyzer errorAnalyzer = new ErrorAnalyzer();

    @Getter
    private final ResourceMonitor resourceMonitor;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, RequestInfo> activeRequests = new ConcurrentHashMap<>();

    @Getter
    private volatile long startTime;

    private final LongAdder requestsLastSecond = new LongAdder();
    private final AtomicLong currentTps = new AtomicLong(0);

    /**
     * Creates a new MetricsCollector.
     *
     * @param config the test configuration
     */
    public MetricsCollector(TestConfig config) {
        this.config = config;

        // Initialize resource monitor if enabled
        if (config.getMetrics() != null &&
                config.getMetrics().getResourceMonitoring() != null &&
                config.getMetrics().getResourceMonitoring().isEnabled()) {

            this.resourceMonitor = new ResourceMonitor();
        } else {
            this.resourceMonitor = null;
        }

        log.info("Initialized metrics collector");
    }

    /**
     * Starts collecting metrics.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTime = System.currentTimeMillis();
            testMetrics.setStartTime(startTime);

            // Start resource monitor if enabled
            if (resourceMonitor != null) {
                Duration sampleInterval = config.getMetrics().getResourceMonitoring().getSampleInterval();
                resourceMonitor.start(sampleInterval);
            }

            // Start TPS calculation scheduler
            scheduler.scheduleAtFixedRate(this::updateTps, 1, 1, TimeUnit.SECONDS);

            log.info("Started metrics collection at {}", startTime);
        }
    }

    /**
     * Stops collecting metrics.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            long endTime = System.currentTimeMillis();
            testMetrics.setEndTime(endTime);
            testMetrics.setDuration(endTime - startTime);

            // Stop scheduler
            scheduler.shutdownNow();

            // Stop resource monitor if running
            if (resourceMonitor != null) {
                resourceMonitor.stop();
            }

            // Calculate final metrics
            calculateFinalMetrics();

            log.info("Stopped metrics collection, test duration: {} ms", testMetrics.getDuration());
        }
    }

    /**
     * Closes this collector and releases resources.
     * This method delegates to stop().
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Records the start of a request.
     *
     * @param requestId the request ID
     * @param request the HTTP request
     */
    public void recordRequestStart(long requestId, HttpRequest request) {
        RequestInfo info = new RequestInfo();
        info.startTime = System.currentTimeMillis();
        info.request = request;

        activeRequests.put(requestId, info);
        testMetrics.incrementTotalRequests();
    }

    /**
     * Records a response to a request.
     *
     * @param requestId the request ID
     * @param response the HTTP response
     * @param responseTime the response time in milliseconds
     */
    public void recordResponse(long requestId, HttpResponse<String> response, long responseTime) {
        // Get request info
        RequestInfo info = activeRequests.remove(requestId);

        if (info != null) {
            // Record response time
            testMetrics.recordResponseTime(responseTime);

            // Record status code
            int statusCode = response.statusCode();
            testMetrics.recordStatusCode(statusCode);

            // Record success/failure
            boolean isSuccess = statusCode >= 200 && statusCode < 300;
            if (isSuccess) {
                testMetrics.incrementSuccessCount();
            } else {
                testMetrics.incrementFailureCount();
                errorAnalyzer.recordErrorResponse(statusCode, response.body());
            }

            // Record network metrics
            networkMetrics.recordResponse(response, NetworkMetrics.estimateResponseSize(response));
        }

        // Increment requests counter for TPS calculation
        requestsLastSecond.increment();
    }

    /**
     * Records an error.
     *
     * @param requestId the request ID
     * @param e the exception
     */
    public void recordError(long requestId, Exception e) {
        // Get request info
        RequestInfo info = activeRequests.remove(requestId);

        if (info != null) {
            // Calculate response time
            long responseTime = System.currentTimeMillis() - info.startTime;
            testMetrics.recordResponseTime(responseTime);
        }

        // Record error
        testMetrics.incrementFailureCount();
        errorAnalyzer.recordException(e);

        // Increment requests counter for TPS calculation
        requestsLastSecond.increment();
    }

    /**
     * Records a timeout.
     *
     * @param requestId the request ID
     * @param responseTime the response time in milliseconds
     */
    public void recordTimeout(long requestId, long responseTime) {
        // Remove request info
        activeRequests.remove(requestId);

        // Record response time
        testMetrics.recordResponseTime(responseTime);

        // Record timeout
        testMetrics.incrementTimeoutCount();
        testMetrics.incrementFailureCount();

        // Increment requests counter for TPS calculation
        requestsLastSecond.increment();
    }

    /**
     * Records a skipped request.
     *
     * @param requestId the request ID
     */
    public void recordSkippedRequest(long requestId) {
        testMetrics.incrementSkippedCount();
    }

    /**
     * Records rate limiter wait time.
     *
     * @param waitTime the wait time in seconds
     */
    public void recordRateLimiterWait(double waitTime) {
        testMetrics.recordRateLimiterWait(waitTime);
    }

    /**
     * Gets the current TPS (transactions per second).
     *
     * @return the current TPS
     */
    public double getCurrentTps() {
        return currentTps.get();
    }

    /**
     * Updates the current TPS value and histogram snapshots.
     * Called periodically by the scheduler.
     */
    private void updateTps() {
        try {
            long tps = requestsLastSecond.sumThenReset();
            currentTps.set(tps);
            testMetrics.recordTps(tps);

            // Update histogram snapshots for lock-free reading
            testMetrics.updateHistogramSnapshots();
        } catch (Exception e) {
            log.error("Error updating TPS", e);
        }
    }

    /**
     * Calculates final metrics after the test is complete.
     */
    private void calculateFinalMetrics() {
        // Final snapshot update to capture any remaining recorded values
        testMetrics.updateHistogramSnapshots();

        // Calculate average TPS
        if (testMetrics.getDuration() > 0) {
            double avgTps = 1000.0 * testMetrics.getTotalRequests() / testMetrics.getDuration();
            testMetrics.setAverageTps(avgTps);
        }

        // Set resource metrics if available
        if (resourceMonitor != null) {
            testMetrics.setMaxCpuUsage(resourceMonitor.getMaxCpuUsage());
            testMetrics.setMaxMemoryUsage(resourceMonitor.getMaxMemoryUsage());
            testMetrics.setResourceSnapshots(resourceMonitor.getSnapshots());
        }
    }

    /**
     * Information about an active request.
     */
    private static class RequestInfo {
        HttpRequest request;
        long startTime;
    }
}