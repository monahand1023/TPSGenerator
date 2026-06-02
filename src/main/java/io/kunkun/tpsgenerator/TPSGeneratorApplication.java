package io.kunkun.tpsgenerator;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.config.FlexibleDurationDeserializer;
import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.core.ExecutionController;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.metrics.RunComparator;
import io.kunkun.tpsgenerator.metrics.TestMetrics;
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
import java.util.ArrayList;
import java.util.List;

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

        // Subcommand: compare two prior JSON result files for regression gating.
        if ("compare".equalsIgnoreCase(args[0])) {
            runComparison(args);
            return;
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
                    // Virtual-thread executor for response callbacks scales to high concurrency.
                    .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
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

            // SLA check: exit 3 if any configured latency/throughput/success-rate budget is breached.
            List<String> slaBreaches = evaluateSlaBreaches(
                    config, latencyStats, metricsCollector.getTestMetrics());
            if (!slaBreaches.isEmpty()) {
                System.out.println("\n=== SLA BREACHES ===");
                for (String breach : slaBreaches) {
                    log.error("SLA breach: {}", breach);
                    System.out.println(" - " + breach);
                }
                System.exit(3);
            }

        } catch (Exception e) {
            log.error("Error executing test", e);
            System.exit(1);
        }
    }

    /**
     * Evaluates the configured SLA thresholds against the test results.
     * Returns a (possibly empty) list of human-readable breach descriptions.
     * Pure function (no side effects / no exit) so it is unit-testable.
     */
    static List<String> evaluateSlaBreaches(TestConfig config, LatencyStats latency, TestMetrics metrics) {
        List<String> breaches = new ArrayList<>();
        TestConfig.SlaConfig sla = config.getSla();
        if (sla == null) {
            return breaches;
        }
        if (sla.getMaxP50Ms() >= 0 && latency.getP50Ms() > sla.getMaxP50Ms()) {
            breaches.add(String.format("p50 latency %.1f ms exceeds %d ms", latency.getP50Ms(), sla.getMaxP50Ms()));
        }
        if (sla.getMaxP95Ms() >= 0 && latency.getP95Ms() > sla.getMaxP95Ms()) {
            breaches.add(String.format("p95 latency %.1f ms exceeds %d ms", latency.getP95Ms(), sla.getMaxP95Ms()));
        }
        if (sla.getMaxP99Ms() >= 0 && latency.getP99Ms() > sla.getMaxP99Ms()) {
            breaches.add(String.format("p99 latency %.1f ms exceeds %d ms", latency.getP99Ms(), sla.getMaxP99Ms()));
        }
        if (sla.getMinSuccessRate() >= 0 && metrics.getSuccessRate() < sla.getMinSuccessRate()) {
            breaches.add(String.format("success rate %.4f below %.4f", metrics.getSuccessRate(), sla.getMinSuccessRate()));
        }
        if (sla.getMinAverageTps() >= 0 && metrics.getAverageTps() < sla.getMinAverageTps()) {
            breaches.add(String.format("average TPS %.2f below %.2f", metrics.getAverageTps(), sla.getMinAverageTps()));
        }
        return breaches;
    }

    /**
     * Handles the {@code compare} subcommand: diffs two JSON result files and exits 4 on regression.
     * Usage: {@code compare <baseline.json> <candidate.json> [maxLatencyRegressionPct] [maxSuccessRateDrop]}
     */
    private static void runComparison(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar tps-generator.jar compare <baseline.json> <candidate.json> "
                    + "[maxLatencyRegressionPct=10] [maxSuccessRateDrop=0.01]");
            System.exit(1);
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> baseline = mapper.readValue(new File(args[1]), java.util.Map.class);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> candidate = mapper.readValue(new File(args[2]), java.util.Map.class);
            double maxLatPct = args.length > 3 ? Double.parseDouble(args[3]) : 10.0;
            double maxSrDrop = args.length > 4 ? Double.parseDouble(args[4]) : 0.01;

            RunComparator.Result result = RunComparator.compare(baseline, candidate, maxLatPct, maxSrDrop);

            System.out.println("\n=== Run Comparison (baseline -> candidate) ===");
            result.getLines().forEach(System.out::println);
            if (result.hasRegressions()) {
                System.out.println("\n=== REGRESSIONS ===");
                result.getRegressions().forEach(r -> System.out.println(" - " + r));
                System.exit(4);
            }
            System.out.println("\nNo regressions beyond thresholds.");
        } catch (Exception e) {
            log.error("Comparison failed", e);
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
        // Offered load (requests issued / duration) vs achieved throughput (responses
        // completed per second) — these diverge when the target can't keep up.
        System.out.println("Average TPS (offered): " +
                String.format("%.2f", metrics.getTestMetrics().getAverageTps()));
        System.out.println("Max TPS (achieved): " + metrics.getTestMetrics().getMaxTps());
        System.out.println("Max CPU Usage: " +
                String.format("%.2f%%", metrics.getTestMetrics().getMaxCpuUsage()));
        System.out.println("Max Memory Usage: " +
                String.format("%.2f MB", metrics.getTestMetrics().getMaxMemoryUsage() / (1024.0 * 1024.0)));
        System.out.println("--- Latency Percentiles (HDR, end-to-end, coordinated-omission corrected) ---");
        System.out.println("P50 Latency: " + String.format("%.3f ms", latencyStats.getP50Ms()));
        System.out.println("P95 Latency: " + String.format("%.3f ms", latencyStats.getP95Ms()));
        System.out.println("P99 Latency: " + String.format("%.3f ms", latencyStats.getP99Ms()));
        System.out.println("Max Latency: " + String.format("%.3f ms", latencyStats.getMaxMs()));
        System.out.println("Mean Latency: " + String.format("%.3f ms", latencyStats.getMeanMs()));
        System.out.println("==================");
    }
}