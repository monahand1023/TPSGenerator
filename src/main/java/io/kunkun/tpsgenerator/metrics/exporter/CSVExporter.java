package io.kunkun.tpsgenerator.metrics.exporter;

import com.example.tpsgenerator.model.ResourceSnapshot;
import com.example.tpsgenerator.metrics.TestMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Exports metrics to CSV files.
 */
@Slf4j
public class CSVExporter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    /**
     * Exports metrics to a CSV file.
     *
     * @param metrics the metrics to export
     * @param outputFile the output file
     * @throws IOException if writing to the file fails
     */
    public void exportMetrics(TestMetrics metrics, File outputFile) throws IOException {
        log.info("Exporting metrics to {}", outputFile.getAbsolutePath());

        try (FileWriter writer = new FileWriter(outputFile)) {
            CSVPrinter printer = CSVFormat.DEFAULT
                    .withHeader("Metric", "Value")
                    .print(writer);

            // Test info
            printer.printRecord("Start Time", formatTimestamp(metrics.getStartTime()));
            printer.printRecord("End Time", formatTimestamp(metrics.getEndTime()));
            printer.printRecord("Duration (ms)", metrics.getDuration());
            printer.printRecord("Duration (s)", metrics.getDuration() / 1000.0);

            // Request counts
            printer.printRecord("Total Requests", metrics.getTotalRequests());
            printer.printRecord("Successful Requests", metrics.getSuccessCount());
            printer.printRecord("Failed Requests", metrics.getFailureCount());
            printer.printRecord("Timeout Requests", metrics.getTimeoutCount());
            printer.printRecord("Skipped Requests", metrics.getSkippedCount());
            printer.printRecord("Success Rate", String.format("%.4f", metrics.getSuccessRate()));

            // TPS metrics
            printer.printRecord("Average TPS", String.format("%.2f", metrics.getAverageTps()));
            printer.printRecord("Max TPS", metrics.getMaxTps());

            // Response time percentiles
            printer.printRecord("Min Response Time (ms)", metrics.getResponseTimePercentile(0));
            printer.printRecord("Median Response Time (ms)", metrics.getResponseTimePercentile(50));
            printer.printRecord("P90 Response Time (ms)", metrics.getResponseTimePercentile(90));
            printer.printRecord("P95 Response Time (ms)", metrics.getResponseTimePercentile(95));
            printer.printRecord("P99 Response Time (ms)", metrics.getResponseTimePercentile(99));
            printer.printRecord("Max Response Time (ms)", metrics.getResponseTimePercentile(100));

            // Rate limiter metrics
            printer.printRecord("Min Rate Limiter Wait (ms)", metrics.getRateLimiterWaitPercentile(0));
            printer.printRecord("Median Rate Limiter Wait (ms)", metrics.getRateLimiterWaitPercentile(50));
            printer.printRecord("P90 Rate Limiter Wait (ms)", metrics.getRateLimiterWaitPercentile(90));
            printer.printRecord("P99 Rate Limiter Wait (ms)", metrics.getRateLimiterWaitPercentile(99));
            printer.printRecord("Max Rate Limiter Wait (ms)", metrics.getRateLimiterWaitPercentile(100));

            // Status code distribution
            Map<Integer, Long> statusCodes = metrics.getStatusCodeCounts();
            for (Map.Entry<Integer, Long> entry : statusCodes.entrySet()) {
                printer.printRecord("Status Code " + entry.getKey(), entry.getValue());
            }

            // Resource usage
            printer.printRecord("Max CPU Usage (%)", String.format("%.2f", metrics.getMaxCpuUsage()));
            printer.printRecord("Max Memory Usage (MB)",
                    String.format("%.2f", metrics.getMaxMemoryUsage() / (1024.0 * 1024.0)));

            printer.flush();
        }

        // Export TPS samples
        exportTpsSamples(metrics, new File(outputFile.getParentFile(), "tps_samples.csv"));

        // Export resource snapshots if available
        List<ResourceSnapshot> resourceSnapshots = metrics.getResourceSnapshots();
        if (resourceSnapshots != null && !resourceSnapshots.isEmpty()) {
            exportResourceSnapshots(resourceSnapshots,
                    new File(outputFile.getParentFile(), "resource_snapshots.csv"));
        }

        log.info("Metrics exported successfully");
    }

    /**
     * Exports TPS samples to a CSV file.
     *
     * @param metrics the metrics containing TPS samples
     * @param outputFile the output file
     * @throws IOException if writing to the file fails
     */
    private void exportTpsSamples(TestMetrics metrics, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            CSVPrinter printer = CSVFormat.DEFAULT
                    .withHeader("Timestamp", "Elapsed (ms)", "TPS")
                    .print(writer);

            List<TestMetrics.TpsSample> samples = metrics.getTpsSamples();
            for (TestMetrics.TpsSample sample : samples) {
                printer.printRecord(
                        formatTimestamp(sample.getTimestamp()),
                        sample.getTimestamp() - metrics.getStartTime(),
                        sample.getTps()
                );
            }

            printer.flush();
        }
    }

    /**
     * Exports resource snapshots to a CSV file.
     *
     * @param snapshots the resource snapshots
     * @param outputFile the output file
     * @throws IOException if writing to the file fails
     */
    private void exportResourceSnapshots(List<ResourceSnapshot> snapshots, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            CSVPrinter printer = CSVFormat.DEFAULT
                    .withHeader(
                            "Timestamp",
                            "Elapsed (ms)",
                            "CPU (%)",
                            "Heap Used (MB)",
                            "Heap Committed (MB)",
                            "Non-Heap Used (MB)",
                            "Total Memory (MB)",
                            "Free Memory (MB)",
                            "Active Threads",
                            "Total Threads",
                            "Daemon Threads"
                    )
                    .print(writer);

            long startTime = snapshots.get(0).getTimestampMs();

            for (ResourceSnapshot snapshot : snapshots) {
                printer.printRecord(
                        formatTimestamp(snapshot.getTimestampMs()),
                        snapshot.getTimestampMs() - startTime,
                        String.format("%.2f", snapshot.getCpuPercentage()),
                        String.format("%.2f", snapshot.getHeapUsedBytes() / (1024.0 * 1024.0)),
                        String.format("%.2f", snapshot.getHeapCommittedBytes() / (1024.0 * 1024.0)),
                        String.format("%.2f", snapshot.getNonHeapUsedBytes() / (1024.0 * 1024.0)),
                        String.format("%.2f", snapshot.getTotalMemoryBytes() / (1024.0 * 1024.0)),
                        String.format("%.2f", snapshot.getFreeMemoryBytes() / (1024.0 * 1024.0)),
                        snapshot.getActiveThreads(),
                        snapshot.getTotalThreads(),
                        snapshot.getDaemonThreads()
                );
            }

            printer.flush();
        }
    }

    /**
     * Formats a timestamp.
     *
     * @param timestampMs the timestamp in milliseconds
     * @return the formatted timestamp
     */
    private String formatTimestamp(long timestampMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
    }
}