package io.kunkun.tpsgenerator.metrics.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.TestMetrics;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exports test results to a JSON file combining {@link TestMetrics} and
 * {@link LatencyStats} into a single, machine-readable document.
 *
 * <p>Example output:
 * <pre>{@code
 * {
 *   "testName": "my-test",
 *   "startTime": "2024-01-01 12:00:00",
 *   "endTime":   "2024-01-01 12:05:00",
 *   "durationSeconds": 300.0,
 *   "totalRequests": 15000,
 *   "successCount": 14900,
 *   "failureCount": 100,
 *   "successRate": 0.9933,
 *   "averageTps": 50.0,
 *   "latency": {
 *     "p50Ms": 12.5,
 *     "p95Ms": 45.2,
 *     "p99Ms": 98.7,
 *     "maxMs": 250.1,
 *     "meanMs": 15.3
 *   }
 * }
 * }</pre>
 */
@Slf4j
public class JsonExporter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;

    /**
     * Creates a new {@code JsonExporter} using a default, pretty-printing
     * {@link ObjectMapper}.
     */
    public JsonExporter() {
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Exports the combined metrics and latency statistics to a JSON file.
     *
     * @param testName     the name of the test (from {@code TestConfig.getName()});
     *                     must not be {@code null}
     * @param metrics      the test metrics collected during the run; must not be {@code null}
     * @param latencyStats the HdrHistogram latency percentiles; must not be {@code null}
     * @param outputFile   the destination file (created or overwritten); must not be {@code null}
     * @throws IOException if the file cannot be written
     */
    public void exportResults(String testName, TestMetrics metrics, LatencyStats latencyStats,
            File outputFile) throws IOException {
        if (testName == null) {
            throw new IllegalArgumentException("testName must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        if (latencyStats == null) {
            throw new IllegalArgumentException("latencyStats must not be null");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }

        log.info("Exporting JSON results to {}", outputFile.getAbsolutePath());

        Map<String, Object> root = buildResultMap(testName, metrics, latencyStats);
        objectMapper.writeValue(outputFile, root);

        log.info("JSON results exported successfully");
    }

    /**
     * Builds the result map that will be serialised to JSON.
     */
    private Map<String, Object> buildResultMap(String testName, TestMetrics metrics,
            LatencyStats latencyStats) {
        // Use LinkedHashMap to preserve insertion order in the JSON output.
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("testName",        testName);
        root.put("startTime",       formatTimestamp(metrics.getStartTime()));
        root.put("endTime",         formatTimestamp(metrics.getEndTime()));
        root.put("durationSeconds", round3(metrics.getDuration() / 1000.0));
        root.put("totalRequests",   metrics.getTotalRequests());
        root.put("successCount",    metrics.getSuccessCount());
        root.put("failureCount",    metrics.getFailureCount());
        root.put("successRate",     round4(metrics.getSuccessRate()));
        root.put("averageTps",      round3(metrics.getAverageTps()));

        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("p50Ms",  round3(latencyStats.getP50Ms()));
        latency.put("p95Ms",  round3(latencyStats.getP95Ms()));
        latency.put("p99Ms",  round3(latencyStats.getP99Ms()));
        latency.put("maxMs",  round3(latencyStats.getMaxMs()));
        latency.put("meanMs", round3(latencyStats.getMeanMs()));
        // Encoded HDR histogram so independent node runs can be merged exactly (see `merge`).
        latency.put("histogram", metrics.getResponseTimeMetrics().getEncodedHistogram());
        root.put("latency", latency);

        return root;
    }

    private String formatTimestamp(long timestampMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
    }

    /** Rounds to 3 decimal places. */
    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** Rounds to 4 decimal places. */
    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
