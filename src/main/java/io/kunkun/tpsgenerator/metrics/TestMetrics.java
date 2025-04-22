package io.kunkun.tpsgenerator.metrics;

import com.example.tpsgenerator.model.ResourceSnapshot;
import lombok.Data;
import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Contains metrics collected during a test.
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
     * Response time histogram.
     */
    private final Histogram responseTimeHistogram = new Histogram(3);

    /**
     * Rate limiter wait time histogram.
     */
    private final Histogram rateLimiterWaitHistogram = new Histogram(3);

    /**
     * Status code counts.
     */
    private final Map<Integer, LongAdder> statusCodeCounts = new HashMap<>();

    /**
     * TPS samples over time.
     */
    private final List<TpsSample> tpsSamples = new CopyOnWriteArrayList<>();

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
     *
     * @param responseTimeMs the response time in milliseconds
     */
    public void recordResponseTime(long responseTimeMs) {
        synchronized (responseTimeHistogram) {
            responseTimeHistogram.recordValue(responseTimeMs);
        }
    }

    /**
     * Records rate limiter wait time.
     *
     * @param waitTimeSeconds the wait time in seconds
     */
    public void recordRateLimiterWait(double waitTimeSeconds) {
        synchronized (rateLimiterWaitHistogram) {
            // Convert to milliseconds
            long waitTimeMs = (long) (waitTimeSeconds * 1000);
            rateLimiterWaitHistogram.recordValue(waitTimeMs);
        }
    }

    /**
     * Records a status code.
     *
     * @param statusCode the HTTP status code
     */
    public void recordStatusCode(int statusCode) {
        statusCodeCounts.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
    }

    /**
     * Records a TPS sample.
     *
     * @param tps the TPS value
     */
    public void recordTps(long tps) {
        tpsSamples.add(new TpsSample(System.currentTimeMillis(), tps));
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
     *
     * @param percentile the percentile (0-100)
     * @return the response time at the specified percentile
     */
    public long getResponseTimePercentile(double percentile) {
        synchronized (responseTimeHistogram) {
            return responseTimeHistogram.getValueAtPercentile(percentile);
        }
    }

    /**
     * Gets a rate limiter wait time percentile.
     *
     * @param percentile the percentile (0-100)
     * @return the wait time at the specified percentile
     */
    public long getRateLimiterWaitPercentile(double percentile) {
        synchronized (rateLimiterWaitHistogram) {
            return rateLimiterWaitHistogram.getValueAtPercentile(percentile);
        }
    }

    /**
     * Gets the counts for each status code.
     *
     * @return a map of status code to count
     */
    public Map<Integer, Long> getStatusCodeCounts() {
        return statusCodeCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Gets the TPS samples.
     *
     * @return the TPS samples
     */
    public List<TpsSample> getTpsSamples() {
        return new ArrayList<>(tpsSamples);
    }

    /**
     * Gets the maximum TPS recorded.
     *
     * @return the maximum TPS
     */
    public long getMaxTps() {
        return tpsSamples.stream()
                .mapToLong(TpsSample::getTps)
                .max()
                .orElse(0);
    }

    /**
     * A TPS sample at a specific time.
     */
    @Data
    public static class TpsSample {
        /**
         * The timestamp of the sample in milliseconds.
         */
        private final long timestamp;

        /**
         * The TPS value.
         */
        private final long tps;
    }
}