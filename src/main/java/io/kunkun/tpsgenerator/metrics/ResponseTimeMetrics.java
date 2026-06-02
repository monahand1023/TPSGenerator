package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.config.Constants;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

/**
 * Collects response time and rate limiter wait time metrics.
 * Uses HdrHistogram's Recorder pattern for lock-free concurrent recording
 * with periodic histogram snapshots for reading.
 */
public class ResponseTimeMetrics {

    /**
     * Default maximum trackable value (1 hour in milliseconds).
     */
    private static final long DEFAULT_MAX_VALUE = Constants.HISTOGRAM_MAX_VALUE_MS;

    /**
     * Response time recorder for lock-free recording.
     */
    private final Recorder responseTimeRecorder;

    /**
     * Rate limiter wait time recorder for lock-free recording.
     */
    private final Recorder rateLimiterWaitRecorder;

    /**
     * Snapshot histogram for response times (used for reads).
     */
    private volatile Histogram responseTimeSnapshot;

    /**
     * Snapshot histogram for rate limiter wait times (used for reads).
     */
    private volatile Histogram rateLimiterWaitSnapshot;

    /**
     * Accumulated histogram for response times (persists across snapshots).
     */
    private final Histogram responseTimeAccumulated;

    /**
     * Accumulated histogram for rate limiter wait times (persists across snapshots).
     */
    private final Histogram rateLimiterWaitAccumulated;

    /**
     * Creates a new ResponseTimeMetrics with default histogram configuration.
     */
    public ResponseTimeMetrics() {
        this(Constants.HISTOGRAM_PRECISION);
    }

    /**
     * Creates a new ResponseTimeMetrics with specified precision.
     *
     * @param numberOfSignificantValueDigits the number of significant digits for histogram precision
     */
    public ResponseTimeMetrics(int numberOfSignificantValueDigits) {
        this.responseTimeRecorder = new Recorder(DEFAULT_MAX_VALUE, numberOfSignificantValueDigits);
        this.rateLimiterWaitRecorder = new Recorder(DEFAULT_MAX_VALUE, numberOfSignificantValueDigits);
        this.responseTimeAccumulated = new Histogram(numberOfSignificantValueDigits);
        this.rateLimiterWaitAccumulated = new Histogram(numberOfSignificantValueDigits);
        // Initialize snapshots
        this.responseTimeSnapshot = new Histogram(numberOfSignificantValueDigits);
        this.rateLimiterWaitSnapshot = new Histogram(numberOfSignificantValueDigits);
    }

    /**
     * Records a response time (lock-free).
     *
     * @param responseTimeMs the response time in milliseconds
     */
    public void recordResponseTime(long responseTimeMs) {
        responseTimeRecorder.recordValue(Math.min(responseTimeMs, DEFAULT_MAX_VALUE));
    }

    /**
     * Records a response time with coordinated-omission correction. When the observed
     * latency exceeds {@code expectedIntervalMs}, HdrHistogram back-fills the samples that
     * an under-issuing generator would otherwise have omitted, so a slow target is reflected
     * honestly in the percentiles.
     *
     * @param responseTimeMs   the observed end-to-end latency in milliseconds
     * @param expectedIntervalMs the expected inter-request interval in milliseconds
     *                           (&lt;= 0 disables correction for this sample)
     */
    public void recordResponseTimeWithExpectedInterval(long responseTimeMs, long expectedIntervalMs) {
        long value = Math.min(responseTimeMs, DEFAULT_MAX_VALUE);
        if (expectedIntervalMs > 0) {
            responseTimeRecorder.recordValueWithExpectedInterval(value, expectedIntervalMs);
        } else {
            responseTimeRecorder.recordValue(value);
        }
    }

    /**
     * Records rate limiter wait time (lock-free).
     *
     * @param waitTimeSeconds the wait time in seconds
     */
    public void recordRateLimiterWait(double waitTimeSeconds) {
        long waitTimeMs = (long) (waitTimeSeconds * 1000);
        rateLimiterWaitRecorder.recordValue(Math.min(waitTimeMs, DEFAULT_MAX_VALUE));
    }

    /**
     * Updates the histogram snapshots with the latest recorded values.
     * Should be called periodically (e.g., every second) to make recorded
     * values available for reading. This method is thread-safe.
     *
     * <p>Uses the no-arg {@link Recorder#getIntervalHistogram()} form, which is
     * compatible with HdrHistogram 2.2.x (the recycle-histogram overload requires
     * the passed histogram to have been originally obtained from the same Recorder).
     */
    public void updateSnapshots() {
        // Get interval histogram (allocates a new one) and add to accumulated
        Histogram responseInterval = responseTimeRecorder.getIntervalHistogram();
        synchronized (responseTimeAccumulated) {
            responseTimeAccumulated.add(responseInterval);
            responseTimeSnapshot = responseTimeAccumulated.copy();
        }

        Histogram waitInterval = rateLimiterWaitRecorder.getIntervalHistogram();
        synchronized (rateLimiterWaitAccumulated) {
            rateLimiterWaitAccumulated.add(waitInterval);
            rateLimiterWaitSnapshot = rateLimiterWaitAccumulated.copy();
        }
    }

    /**
     * Gets a response time percentile from the latest snapshot.
     * Call updateSnapshots() periodically to get fresh data.
     *
     * @param percentile the percentile (0-100)
     * @return the response time at the specified percentile in milliseconds
     */
    public long getResponseTimePercentile(double percentile) {
        Histogram snapshot = responseTimeSnapshot;
        return snapshot.getTotalCount() > 0 ? snapshot.getValueAtPercentile(percentile) : 0;
    }

    /**
     * Gets a rate limiter wait time percentile from the latest snapshot.
     * Call updateSnapshots() periodically to get fresh data.
     *
     * @param percentile the percentile (0-100)
     * @return the wait time at the specified percentile in milliseconds
     */
    public long getRateLimiterWaitPercentile(double percentile) {
        Histogram snapshot = rateLimiterWaitSnapshot;
        return snapshot.getTotalCount() > 0 ? snapshot.getValueAtPercentile(percentile) : 0;
    }

    /**
     * Gets the total count of recorded response times from the latest snapshot.
     *
     * @return the total count
     */
    public long getResponseTimeCount() {
        return responseTimeSnapshot.getTotalCount();
    }

    /**
     * Gets the maximum recorded response time from the latest snapshot.
     *
     * @return the maximum response time in milliseconds
     */
    public long getMaxResponseTime() {
        Histogram snapshot = responseTimeSnapshot;
        return snapshot.getTotalCount() > 0 ? snapshot.getMaxValue() : 0;
    }

    /**
     * Gets the mean response time from the latest snapshot.
     *
     * @return the mean response time in milliseconds
     */
    public double getMeanResponseTime() {
        Histogram snapshot = responseTimeSnapshot;
        return snapshot.getTotalCount() > 0 ? snapshot.getMean() : 0.0;
    }

    /**
     * Gets the standard deviation of response times from the latest snapshot.
     *
     * @return the standard deviation in milliseconds
     */
    public double getResponseTimeStdDev() {
        Histogram snapshot = responseTimeSnapshot;
        return snapshot.getTotalCount() > 0 ? snapshot.getStdDeviation() : 0.0;
    }

    /**
     * Resets all metrics including recorders and accumulated histograms.
     */
    public void reset() {
        responseTimeRecorder.reset();
        rateLimiterWaitRecorder.reset();
        synchronized (responseTimeAccumulated) {
            responseTimeAccumulated.reset();
        }
        synchronized (rateLimiterWaitAccumulated) {
            rateLimiterWaitAccumulated.reset();
        }
        responseTimeSnapshot = responseTimeAccumulated.copy();
        rateLimiterWaitSnapshot = rateLimiterWaitAccumulated.copy();
    }
}
