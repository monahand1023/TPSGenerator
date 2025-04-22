package com.example.tpsgenerator.model;

import com.example.tpsgenerator.metrics.TestMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents the result of a completed load test.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestResult {

    /**
     * The name of the test.
     */
    private String testName;

    /**
     * The start time of the test in milliseconds.
     */
    private long startTimeMs;

    /**
     * The end time of the test in milliseconds.
     */
    private long endTimeMs;

    /**
     * The metrics collected during the test.
     */
    private TestMetrics metrics;

    /**
     * Formatter for timestamps.
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Gets the duration of the test in milliseconds.
     *
     * @return the test duration
     */
    public long getDurationMs() {
        return endTimeMs - startTimeMs;
    }

    /**
     * Gets the duration of the test in seconds.
     *
     * @return the test duration in seconds
     */
    public double getDurationSeconds() {
        return getDurationMs() / 1000.0;
    }

    /**
     * Gets the formatted start time.
     *
     * @return the formatted start time
     */
    public String getFormattedStartTime() {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(startTimeMs));
    }

    /**
     * Gets the formatted end time.
     *
     * @return the formatted end time
     */
    public String getFormattedEndTime() {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(endTimeMs));
    }

    /**
     * Gets a summary of the test result.
     *
     * @return the test result summary
     */
    public String getSummary() {
        return String.format("Test: %s, Duration: %.2f seconds, Requests: %d, Success Rate: %.2f%%, Avg TPS: %.2f, P95 Response: %d ms",
                testName,
                getDurationSeconds(),
                metrics.getTotalRequests(),
                metrics.getSuccessRate() * 100,
                metrics.getAverageTps(),
                metrics.getResponseTimePercentile(95));
    }
}