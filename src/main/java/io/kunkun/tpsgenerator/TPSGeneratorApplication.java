package io.kunkun.tpsgenerator;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.core.ExecutionController;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.metrics.exporter.CSVExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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

            // Initialize metrics collector
            MetricsCollector metricsCollector = new MetricsCollector(config);

            // Initialize execution controller
            ExecutionController controller = new ExecutionController(config, metricsCollector);

            // Run the test
            log.info("Starting test execution...");
            Instant startTime = Instant.now();
            controller.execute();
            Instant endTime = Instant.now();
            Duration testDuration = Duration.between(startTime, endTime);
            log.info("Test completed in {}", formatDuration(testDuration));

            // Export results
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .format(startTime);
            String resultsFile = String.format("%s/%s_%s.csv",
                    outputDir, config.getName(), timestamp);

            CSVExporter exporter = new CSVExporter();
            exporter.exportMetrics(metricsCollector.getTestMetrics(), new File(resultsFile));
            log.info("Results exported to {}", resultsFile);

            // Print summary
            printSummary(metricsCollector, testDuration);

        } catch (Exception e) {
            log.error("Error executing test", e);
            System.exit(1);
        }
    }

    private static TestConfig loadConfig(String configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
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

    private static void printSummary(MetricsCollector metrics, Duration testDuration) {
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
        System.out.println("==================");
    }
}