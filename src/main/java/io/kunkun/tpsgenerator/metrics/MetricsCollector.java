package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.utils.HttpUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collects and aggregates metrics for a load test.
 * Uses composition to delegate to specialized metric collectors.
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

    @Getter
    private final RequestTracker requestTracker = new RequestTracker();

    private final TpsCalculator tpsCalculator = new TpsCalculator();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private volatile long startTime;

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
        requestTracker.startTracking(requestId, request);
        // Account for bytes sent (method + URL + headers + Content-Length). Previously this path
        // was never invoked, so totalBytesSent was always 0.
        networkMetrics.recordRequest(request, HttpUtils.estimateRequestSize(request));
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
        boolean statusSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
        recordResponse(requestId, response, responseTime, statusSuccess);
    }

    /**
     * Records a response with an explicit success verdict. Lets the caller fold response
     * validation into the success/failure decision so a 2xx that fails validation is counted
     * as a single failure (not double-counted as both a success and an error).
     *
     * @param requestId    the request ID
     * @param response     the HTTP response
     * @param responseTime the response time in milliseconds (kept for API symmetry; latency is
     *                     recorded via {@link #recordEndToEndLatency})
     * @param isSuccess    the final success verdict (status code AND any response validation)
     */
    public void recordResponse(long requestId, HttpResponse<String> response, long responseTime,
                               boolean isSuccess) {
        // Stop tracking the request
        RequestTracker.RequestInfo info = requestTracker.stopTracking(requestId);

        if (info != null) {
            // Note: latency is recorded via recordEndToEndLatency() (single coordinated-omission-
            // corrected pipeline driven from the submission loop), NOT from this per-response
            // service time — so the percentile histogram has one consistent, trustworthy source.

            // Record status code
            int statusCode = response.statusCode();
            testMetrics.recordStatusCode(statusCode);

            // Record success/failure using the caller's verdict
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
        tpsCalculator.incrementRequestCount();
    }

    /**
     * Records an error.
     *
     * @param requestId the request ID
     * @param e the exception
     */
    public void recordError(long requestId, Exception e) {
        // Stop tracking the request. We intentionally do NOT fold the failure latency into
        // the response-time percentile histogram — a transport error's "duration" is not a
        // meaningful service latency and would skew p99/max for successful traffic.
        requestTracker.stopTracking(requestId);

        // Record error
        testMetrics.incrementFailureCount();
        errorAnalyzer.recordException(e);

        // Increment requests counter for TPS calculation
        tpsCalculator.incrementRequestCount();
    }

    /**
     * Records a timeout.
     *
     * @param requestId the request ID
     * @param responseTime the response time in milliseconds
     */
    public void recordTimeout(long requestId, long responseTime) {
        // Stop tracking the request
        requestTracker.stopTracking(requestId);

        // Note: the timeout's elapsed time (responseTime) is deliberately NOT recorded into
        // the response-time percentile histogram — a 30s timeout sample would dominate p99/max
        // and misrepresent the latency of requests the server actually answered.

        // Record timeout
        testMetrics.incrementTimeoutCount();
        testMetrics.incrementFailureCount();

        // Increment requests counter for TPS calculation
        tpsCalculator.incrementRequestCount();
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
     * Records an end-to-end request latency with coordinated-omission correction. This is the
     * single source of truth for the latency percentile histogram: the value spans the intended
     * dispatch time (captured before the rate limiter) to response completion, so back-pressure
     * under a slow target is reflected honestly.
     *
     * @param nanos                 observed end-to-end latency in nanoseconds
     * @param expectedIntervalNanos expected inter-request interval in nanoseconds
     */
    public void recordEndToEndLatency(long nanos, long expectedIntervalNanos) {
        long valueMs = Math.max(0L, nanos / 1_000_000L);
        long expectedMs = expectedIntervalNanos / 1_000_000L;
        testMetrics.recordResponseTimeWithExpectedInterval(valueMs, expectedMs);
    }

    /**
     * Gets the current TPS (transactions per second).
     *
     * @return the current TPS
     */
    public double getCurrentTps() {
        return tpsCalculator.getCurrentTps();
    }

    /**
     * Updates the current TPS value and histogram snapshots.
     * Called periodically by the scheduler.
     */
    private void updateTps() {
        try {
            long tps = tpsCalculator.updateTps();
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
}