package io.kunkun.tpsgenerator.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Collects HTTP status code metrics.
 * Thread-safe for concurrent recording.
 */
public class StatusCodeMetrics {

    /**
     * Status code counts using thread-safe LongAdder.
     */
    private final Map<Integer, LongAdder> statusCodeCounts = new ConcurrentHashMap<>();

    /**
     * Records a status code occurrence.
     *
     * @param statusCode the HTTP status code
     */
    public void recordStatusCode(int statusCode) {
        statusCodeCounts.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
    }

    /**
     * Gets the count for a specific status code.
     *
     * @param statusCode the HTTP status code
     * @return the count for that status code
     */
    public long getCount(int statusCode) {
        LongAdder adder = statusCodeCounts.get(statusCode);
        return adder != null ? adder.sum() : 0;
    }

    /**
     * Gets the counts for all status codes.
     *
     * @return a map of status code to count
     */
    public Map<Integer, Long> getAllCounts() {
        return statusCodeCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Gets the total count of all 2xx (success) responses.
     *
     * @return the count of successful responses
     */
    public long getSuccessfulCount() {
        return statusCodeCounts.entrySet().stream()
                .filter(e -> e.getKey() >= 200 && e.getKey() < 300)
                .mapToLong(e -> e.getValue().sum())
                .sum();
    }

    /**
     * Gets the total count of all 4xx (client error) responses.
     *
     * @return the count of client error responses
     */
    public long getClientErrorCount() {
        return statusCodeCounts.entrySet().stream()
                .filter(e -> e.getKey() >= 400 && e.getKey() < 500)
                .mapToLong(e -> e.getValue().sum())
                .sum();
    }

    /**
     * Gets the total count of all 5xx (server error) responses.
     *
     * @return the count of server error responses
     */
    public long getServerErrorCount() {
        return statusCodeCounts.entrySet().stream()
                .filter(e -> e.getKey() >= 500 && e.getKey() < 600)
                .mapToLong(e -> e.getValue().sum())
                .sum();
    }

    /**
     * Gets the total count of all recorded status codes.
     *
     * @return the total count
     */
    public long getTotalCount() {
        return statusCodeCounts.values().stream()
                .mapToLong(LongAdder::sum)
                .sum();
    }

    /**
     * Checks if any errors (4xx or 5xx) have been recorded.
     *
     * @return true if any errors have been recorded
     */
    public boolean hasErrors() {
        return statusCodeCounts.keySet().stream()
                .anyMatch(code -> code >= 400);
    }

    /**
     * Resets all counts.
     */
    public void reset() {
        statusCodeCounts.clear();
    }
}
