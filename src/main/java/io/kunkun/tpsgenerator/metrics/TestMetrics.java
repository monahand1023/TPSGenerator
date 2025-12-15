package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.model.ResourceSnapshot;
import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Contains metrics collected during a test.
 * Uses composition to delegate to specialized metrics classes.
 */
@Data
public class TestMetrics {

    /**
     * The start time of the test in milliseconds.
     */
    private long startTime;

    /**
     * The end time of the test in milliseconds.
     */
    private long endTime;

    /**
     * The duration of the test in milliseconds.
     */
    private long duration;

    /**
     * The total number of requests made.
     */
    private final LongAdder totalRequests = new LongAdder();

    /**
     * The number of successful requests.
     */
    private final LongAdder successCount = new LongAdder();

    /**
     * The number of failed requests.
     */
    private final LongAdder failureCount = new LongAdder();

    /**
     * The number of timed out requests.
     */
    private final LongAdder timeoutCount = new LongAdder();

    /**
     * The number of skipped requests.
     */
    private final LongAdder skippedCount = new LongAdder();

    /**
     * Response time metrics (histograms for response time and rate limiter wait).
     */
    @Getter
    private final ResponseTimeMetrics responseTimeMetrics = new ResponseTimeMetrics();

    /**
     * Status code metrics.
     */
    @Getter
    private final StatusCodeMetrics statusCodeMetrics = new StatusCodeMetrics();

    /**
     * TPS metrics.
     */
    @Getter
    private final TpsMetrics tpsMetrics = new TpsMetrics();

    /**
     * Maximum CPU usage (percentage).
     */
    private double maxCpuUsage;

    /**
     * Maximum memory usage (bytes).
     */
    private long maxMemoryUsage;

    /**
     * Average TPS.
     */
    private double averageTps;

    /**
     * Resource snapshots.
     */
    private List<ResourceSnapshot> resourceSnapshots;

    /**
     * Increments the total requests counter.
     */
    public void incrementTotalRequests() {
        totalRequests.increment();
    }

    /**
     * Increments the success count.
     */
    public void incrementSuccessCount() {
        successCount.increment();
    }

    /**
     * Increments the failure count.
     */
    public void incrementFailureCount() {
        failureCount.increment();
    }

    /**
     * Increments the timeout count.
     */
    public void incrementTimeoutCount() {
        timeoutCount.increment();
    }

    /**
     * Increments the skipped count.
     */
    public void incrementSkippedCount() {
        skippedCount.increment();
    }

    /**
     * Records a response time.
     * Delegates to ResponseTimeMetrics.
     *
     * @param responseTimeMs the response time in milliseconds
     */
    public void recordResponseTime(long responseTimeMs) {
        responseTimeMetrics.recordResponseTime(responseTimeMs);
    }

    /**
     * Records rate limiter wait time.
     * Delegates to ResponseTimeMetrics.
     *
     * @param waitTimeSeconds the wait time in seconds
     */
    public void recordRateLimiterWait(double waitTimeSeconds) {
        responseTimeMetrics.recordRateLimiterWait(waitTimeSeconds);
    }

    /**
     * Records a status code.
     * Delegates to StatusCodeMetrics.
     *
     * @param statusCode the HTTP status code
     */
    public void recordStatusCode(int statusCode) {
        statusCodeMetrics.recordStatusCode(statusCode);
    }

    /**
     * Records a TPS sample.
     * Delegates to TpsMetrics.
     *
     * @param tps the TPS value
     */
    public void recordTps(long tps) {
        tpsMetrics.recordTps(tps);
    }

    /**
     * Gets the total number of requests.
     *
     * @return the total number of requests
     */
    public long getTotalRequests() {
        return totalRequests.sum();
    }

    /**
     * Gets the number of successful requests.
     *
     * @return the number of successful requests
     */
    public long getSuccessCount() {
        return successCount.sum();
    }

    /**
     * Gets the number of failed requests.
     *
     * @return the number of failed requests
     */
    public long getFailureCount() {
        return failureCount.sum();
    }

    /**
     * Gets the number of timed out requests.
     *
     * @return the number of timed out requests
     */
    public long getTimeoutCount() {
        return timeoutCount.sum();
    }

    /**
     * Gets the number of skipped requests.
     *
     * @return the number of skipped requests
     */
    public long getSkippedCount() {
        return skippedCount.sum();
    }

    /**
     * Gets the success rate.
     *
     * @return the success rate (0.0 - 1.0)
     */
    public double getSuccessRate() {
        long total = getTotalRequests();
        return total > 0 ? (double) getSuccessCount() / total : 0.0;
    }

    /**
     * Gets a response time percentile.
     * Delegates to ResponseTimeMetrics.
     *
     * @param percentile the percentile (0-100)
     * @return the response time at the specified percentile
     */
    public long getResponseTimePercentile(double percentile) {
        return responseTimeMetrics.getResponseTimePercentile(percentile);
    }

    /**
     * Gets a rate limiter wait time percentile.
     * Delegates to ResponseTimeMetrics.
     *
     * @param percentile the percentile (0-100)
     * @return the wait time at the specified percentile
     */
    public long getRateLimiterWaitPercentile(double percentile) {
        return responseTimeMetrics.getRateLimiterWaitPercentile(percentile);
    }

    /**
     * Gets the counts for each status code.
     * Delegates to StatusCodeMetrics.
     *
     * @return a map of status code to count
     */
    public Map<Integer, Long> getStatusCodeCounts() {
        return statusCodeMetrics.getAllCounts();
    }

    /**
     * Gets the TPS samples.
     * Delegates to TpsMetrics.
     *
     * @return the TPS samples
     */
    public List<TpsMetrics.TpsSample> getTpsSamples() {
        return tpsMetrics.getSamples();
    }

    /**
     * Gets the maximum TPS recorded.
     * Delegates to TpsMetrics.
     *
     * @return the maximum TPS
     */
    public long getMaxTps() {
        return tpsMetrics.getMaxTps();
    }

    /**
     * Updates the response time histogram snapshots.
     * Should be called periodically (e.g., every second) to make recorded
     * values available for reading percentiles.
     */
    public void updateHistogramSnapshots() {
        responseTimeMetrics.updateSnapshots();
    }
}
