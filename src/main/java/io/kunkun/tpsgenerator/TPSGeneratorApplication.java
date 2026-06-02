package io.kunkun.tpsgenerator;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.config.FlexibleDurationDeserializer;
import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.core.ExecutionController;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.metrics.exporter.CSVExporter;
import io.kunkun.tpsgenerator.metrics.exporter.JsonExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kunkun.tpsgenerator.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Main application class for the TPS Generator.
 * This tool generates HTTP traffic with configurable patterns for load testing.
 */
@Slf4j
public class TPSGeneratorApplication {

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        if (args.length > 2 && "--verbose".equals(args[2])) {
            HttpUtils.enableVerboseLogging();
            log.info("Verbose logging enabled");
        }

        String configFile = args[0];
        String outputDir = args.length > 1 ? args[1] : "results";

        try {
            // Ensure output directory exists
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            // Load configuration
            TestConfig config = loadConfig(configFile);
            log.info("Loaded test configuration: {}", config.getName());

            // Validate configuration
            try {
                config.validate();
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid configuration: " + e.getMessage());
                System.err.println("Please check your test configuration file and try again.");
                System.exit(1);
            }

            // Create a single shared HTTP client for all components
            HttpClient sharedHttpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS))
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            // Initialize metrics collector
            MetricsCollector metricsCollector = new MetricsCollector(config);

            // Initialize execution controller (passes shared client)
            ExecutionController controller = new ExecutionController(config, metricsCollector, sharedHttpClient);

            // Run the test
            log.info("Starting test execution...");
            Instant startTime = Instant.now();
            controller.execute();
            Instant endTime = Instant.now();
            Duration testDuration = Duration.between(startTime, endTime);
            log.info("Test completed in {}", formatDuration(testDuration));

            // Capture latency percentiles before closing the controller
            LatencyStats latencyStats = controller.getLatencyPercentiles();

            // Export results
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(startTime);
            String baseName = String.format("%s/%s_%s", outputDir, config.getName(), timestamp);

            // CSV export (with HdrHistogram latency columns)
            String csvFile = baseName + ".csv";
            CSVExporter csvExporter = new CSVExporter();
            csvExporter.exportMetrics(metricsCollector.getTestMetrics(), latencyStats,
                    new File(csvFile));
            log.info("CSV results exported to {}", csvFile);

            // JSON export
            String jsonFile = baseName + ".json";
            JsonExporter jsonExporter = new JsonExporter();
            jsonExporter.exportResults(config.getName(), metricsCollector.getTestMetrics(),
                    latencyStats, new File(jsonFile));
            log.info("JSON results exported to {}", jsonFile);

            // Print summary
            printSummary(metricsCollector, testDuration, latencyStats);

            // Fail-threshold check: exit 2 if error rate exceeds configured threshold
            double errorRate = 1.0 - metricsCollector.getTestMetrics().getSuccessRate();
            double threshold = config.getFailThresholdErrorRate();
            if (errorRate > threshold) {
                log.error("Test failed: error rate {}% exceeded threshold {}%",
                        String.format("%.2f", errorRate * 100),
                        String.format("%.2f", threshold * 100));
                System.exit(2);
            }

        } catch (Exception e) {
            log.error("Error executing test", e);
            System.exit(1);
        }
    }

    private static TestConfig loadConfig(String configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // Accept human-friendly durations ("10m", "60s", "1h30m") in addition to
        // ISO-8601 — registered after JavaTimeModule so it wins for Duration.
        SimpleModule durations = new SimpleModule();
        durations.addDeserializer(Duration.class, new FlexibleDurationDeserializer());
        mapper.registerModule(durations);

        return mapper.readValue(new File(configFile), TestConfig.class);
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar tps-generator.jar <config-file> [output-directory]");
        System.out.println("  config-file: JSON configuration file for the test");
        System.out.println("  output-directory: Directory to store results (default: 'results')");
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;

        return String.format("%02d:%02d:%02d",
                hours, minutes % 60, seconds % 60);
    }

    private static void printSummary(MetricsCollector metrics, Duration testDuration,
                                      LatencyStats latencyStats) {
        System.out.println("\n=== Test Summary ===");
        System.out.println("Duration: " + formatDuration(testDuration));
        System.out.println("Total Requests: " + metrics.getTestMetrics().getTotalRequests());
        System.out.println("Successful Requests: " + metrics.getTestMetrics().getSuccessCount());
        System.out.println("Failed Requests: " + metrics.getTestMetrics().getFailureCount());
        System.out.println("Success Rate: " +
                String.format("%.2f%%", metrics.getTestMetrics().getSuccessRate() * 100));
        System.out.println("Average TPS: " +
                String.format("%.2f", metrics.getTestMetrics().getAverageTps()));
        System.out.println("P95 Response Time: " +
                metrics.getTestMetrics().getResponseTimePercentile(95) + " ms");
        System.out.println("Max CPU Usage: " +
                String.format("%.2f%%", metrics.getTestMetrics().getMaxCpuUsage()));
        System.out.println("Max Memory Usage: " +
                String.format("%.2f MB", metrics.getTestMetrics().getMaxMemoryUsage() / (1024.0 * 1024.0)));
        System.out.println("--- Latency Percentiles (HDR, end-to-end) ---");
        System.out.println("P50 Latency: " + String.format("%.3f ms", latencyStats.getP50Ms()));
        System.out.println("P95 Latency: " + String.format("%.3f ms", latencyStats.getP95Ms()));
        System.out.println("P99 Latency: " + String.format("%.3f ms", latencyStats.getP99Ms()));
        System.out.println("Max Latency: " + String.format("%.3f ms", latencyStats.getMaxMs()));
        System.out.println("Mean Latency: " + String.format("%.3f ms", latencyStats.getMeanMs()));
        System.out.println("==================");
    }
}