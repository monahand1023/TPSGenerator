package com.example.tpsgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * A single point-in-time record of metrics during a test.
 * This represents a snapshot of test metrics at a specific time during the test.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsRecord {

    /**
     * The timestamp of the record in milliseconds since epoch.
     */
    private long timestampMs;

    /**
     * The elapsed time since the test started in milliseconds.
     */
    private long elapsedTimeMs;

    /**
     * The current TPS rate.
     */
    private double currentTps;

    /**
     * The current success rate (0.0 - 1.0).
     */
    private double successRate;

    /**
     * The current number of active requests.
     */
    private int activeRequests;

    /**
     * The current response time percentiles in milliseconds.
     */
    private Map<Integer, Long> responseTimePercentiles = new HashMap<>();

    /**
     * The current status code distribution.
     */
    private Map<Integer, Long> statusCodes = new HashMap<>();

    /**
     * CPU usage percentage.
     */
    private double cpuUsage;

    /**
     * Memory usage in bytes.
     */
    private long memoryUsage;

    /**
     * Number of active threads.
     */
    private int activeThreads;

    /**
     * Checks if this record has resource metrics.
     *
     * @return true if this record has resource metrics
     */
    public boolean hasResourceMetrics() {
        return cpuUsage > 0 || memoryUsage > 0 || activeThreads > 0;
    }

    /**
     * Gets the response time at a specific percentile.
     *
     * @param percentile the percentile (0-100)
     * @return the response time or 0 if not available
     */
    public long getResponseTimeAtPercentile(int percentile) {
        return responseTimePercentiles.getOrDefault(percentile, 0L);
    }

    /**
     * Gets memory usage in megabytes.
     *
     * @return memory usage in MB
     */
    public double getMemoryUsageMB() {
        return memoryUsage / (1024.0 * 1024.0);
    }

    /**
     * Gets the count for a specific status code.
     *
     * @param statusCode the HTTP status code
     * @return the count or 0 if not available
     */
    public long getStatusCodeCount(int statusCode) {
        return statusCodes.getOrDefault(statusCode, 0L);
    }
}