package io.kunkun.tpsgenerator.metrics;

import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Analyzes errors encountered during test execution.
 */
public class ErrorAnalyzer {

    /**
     * Maximum number of error samples to store.
     */
    private static final int MAX_ERROR_SAMPLES = 100;

    /**
     * Error response samples grouped by status code.
     */
    private final Map<Integer, List<String>> errorResponseSamples = new ConcurrentHashMap<>();

    /**
     * Exception counts by type.
     */
    private final Map<String, LongAdder> exceptionCounts = new ConcurrentHashMap<>();

    /**
     * Exception samples by type.
     */
    private final Map<String, List<ExceptionSample>> exceptionSamples = new ConcurrentHashMap<>();

    /**
     * Records an error response.
     *
     * @param statusCode the HTTP status code
     * @param responseBody the response body
     */
    public void recordErrorResponse(int statusCode, String responseBody) {
        // Only record 4xx and 5xx status codes
        if (statusCode < 400) {
            return;
        }

        List<String> samples = errorResponseSamples.computeIfAbsent(
                statusCode, k -> new CopyOnWriteArrayList<>());

        // Add sample if we haven't reached the limit
        if (samples.size() < MAX_ERROR_SAMPLES) {
            samples.add(responseBody != null ? responseBody : "");
        }
    }

    /**
     * Records an exception.
     *
     * @param e the exception
     */
    public void recordException(Exception e) {
        String exceptionType = e.getClass().getName();

        // Increment counter
        exceptionCounts.computeIfAbsent(exceptionType, k -> new LongAdder()).increment();

        // Add sample
        List<ExceptionSample> samples = exceptionSamples.computeIfAbsent(
                exceptionType, k -> new CopyOnWriteArrayList<>());

        if (samples.size() < MAX_ERROR_SAMPLES) {
            samples.add(new ExceptionSample(
                    System.currentTimeMillis(),
                    e.getMessage(),
                    getStackTraceAsString(e)
            ));
        }
    }

    /**
     * Gets the error response samples.
     *
     * @return a map of status code to response samples
     */
    public Map<Integer, List<String>> getErrorResponseSamples() {
        Map<Integer, List<String>> result = new HashMap<>();

        for (Map.Entry<Integer, List<String>> entry : errorResponseSamples.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return result;
    }

    /**
     * Gets the exception counts.
     *
     * @return a map of exception type to count
     */
    public Map<String, Long> getExceptionCounts() {
        return exceptionCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Gets the exception samples.
     *
     * @return a map of exception type to samples
     */
    public Map<String, List<ExceptionSample>> getExceptionSamples() {
        Map<String, List<ExceptionSample>> result = new HashMap<>();

        for (Map.Entry<String, List<ExceptionSample>> entry : exceptionSamples.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return result;
    }

    /**
     * Gets the top error status codes by frequency.
     *
     * @param limit the maximum number of status codes to return
     * @return a map of status code to count, sorted by count in descending order
     */
    public Map<Integer, Integer> getTopErrorStatusCodes(int limit) {
        // Single-pass stream operation: map to size, sort, limit, and collect
        return errorResponseSamples.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().size()))
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Gets the top exception types by frequency.
     *
     * @param limit the maximum number of exception types to return
     * @return a map of exception type to count, sorted by count in descending order
     */
    public Map<String, Long> getTopExceptions(int limit) {
        return getExceptionCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Gets the total number of exceptions recorded.
     *
     * @return the total number of exceptions
     */
    public long getTotalExceptionCount() {
        return exceptionCounts.values().stream()
                .mapToLong(LongAdder::sum)
                .sum();
    }

    /**
     * Gets the total number of error responses recorded.
     *
     * @return the total number of error responses
     */
    public int getTotalErrorResponseCount() {
        return errorResponseSamples.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Generates an error report.
     *
     * @return the error report
     */
    public ErrorReport generateErrorReport() {
        ErrorReport report = new ErrorReport();

        // Set error counts
        report.setTotalExceptionCount(getTotalExceptionCount());
        report.setTotalErrorResponseCount(getTotalErrorResponseCount());

        // Set top exceptions
        report.setTopExceptions(getTopExceptions(10));

        // Set top error status codes
        report.setTopErrorStatusCodes(getTopErrorStatusCodes(10));

        // Set exception samples (most recent 3 per type)
        Map<String, List<ExceptionSample>> samples = new HashMap<>();
        for (Map.Entry<String, List<ExceptionSample>> entry : getExceptionSamples().entrySet()) {
            List<ExceptionSample> typeSamples = entry.getValue();

            // Sort by timestamp descending and take first 3
            samples.put(entry.getKey(), typeSamples.stream()
                    .sorted(Comparator.comparing(ExceptionSample::getTimestamp).reversed())
                    .limit(3)
                    .collect(Collectors.toList()));
        }
        report.setExceptionSamples(samples);

        // Set error response samples (most recent 3 per status code)
        Map<Integer, List<String>> responseSamples = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : getErrorResponseSamples().entrySet()) {
            List<String> truncatedSamples = entry.getValue().stream()
                    .limit(3)
                    .collect(Collectors.toList());
            responseSamples.put(entry.getKey(), truncatedSamples);
        }
        report.setErrorResponseSamples(responseSamples);

        return report;
    }

    /**
     * Converts a stack trace to a string.
     *
     * @param e the exception
     * @return the stack trace as a string
     */
    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");

        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        return sb.toString();
    }

    /**
     * A sample of an exception.
     */
    @Data
    public static class ExceptionSample {
        /**
         * The timestamp when the exception occurred.
         */
        private final long timestamp;

        /**
         * The exception message.
         */
        private final String message;

        /**
         * The stack trace.
         */
        private final String stackTrace;
    }

    /**
     * An error report.
     */
    @Data
    public static class ErrorReport {
        /**
         * The total number of exceptions recorded.
         */
        private long totalExceptionCount;

        /**
         * The total number of error responses recorded.
         */
        private int totalErrorResponseCount;

        /**
         * The top exception types by frequency.
         */
        private Map<String, Long> topExceptions;

        /**
         * The top error status codes by frequency.
         */
        private Map<Integer, Integer> topErrorStatusCodes;

        /**
         * Exception samples by type.
         */
        private Map<String, List<ExceptionSample>> exceptionSamples;

        /**
         * Error response samples by status code.
         */
        private Map<Integer, List<String>> errorResponseSamples;
    }
}