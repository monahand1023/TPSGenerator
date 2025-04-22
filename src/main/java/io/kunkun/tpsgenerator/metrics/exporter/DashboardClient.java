package io.kunkun.tpsgenerator.metrics.exporter;

import com.example.tpsgenerator.config.TestConfig;
import com.example.tpsgenerator.metrics.TestMetrics;
import com.example.tpsgenerator.model.ResourceSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client for sending metrics to a dashboard service.
 */
@Slf4j
public class DashboardClient {

    private final TestConfig config;
    private final String testId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String dashboardUrl;
    private final String apiKey;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a new DashboardClient.
     *
     * @param config the test configuration
     * @param testId the test ID
     */
    public DashboardClient(TestConfig config, String testId) {
        this.config = config;
        this.testId = testId;

        // Initialize HTTP client
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.objectMapper = new ObjectMapper();

        // Get dashboard configuration
        TestConfig.MetricsConfig.DashboardConfig dashboardConfig = config.getMetrics().getDashboard();

        if (dashboardConfig == null || !dashboardConfig.isEnabled()) {
            throw new IllegalArgumentException("Dashboard client requires enabled dashboard configuration");
        }

        this.dashboardUrl = dashboardConfig.getUrl();
        this.apiKey = dashboardConfig.getApiKey();

        log.info("Initialized dashboard client with URL: {}", dashboardUrl);
    }

    /**
     * Starts sending metrics to the dashboard at fixed intervals.
     *
     * @param metricsSupplier a supplier of current metrics
     * @param interval the interval between updates
     */
    public void startRealtimeUpdates(MetricsSupplier metricsSupplier, long interval) {
        if (running.compareAndSet(false, true)) {
            // Register test with dashboard
            registerTest();

            // Schedule periodic updates
            scheduler.scheduleAtFixedRate(
                    () -> sendMetricsUpdate(metricsSupplier.getMetrics()),
                    0,
                    interval,
                    TimeUnit.MILLISECONDS
            );

            log.info("Started real-time dashboard updates with interval: {} ms", interval);
        }
    }

    /**
     * Stops sending metrics to the dashboard.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            finishTest();
            log.info("Stopped dashboard updates");
        }
    }

    /**
     * Registers the test with the dashboard.
     */
    private void registerTest() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("testId", testId);
            payload.put("testName", config.getName());
            payload.put("targetServiceUrl", config.getTargetServiceUrl());
            payload.put("startTime", System.currentTimeMillis());
            payload.put("testDuration", config.getTestDuration().toMillis());

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/tests/register"))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                log.error("Failed to register test with dashboard: {} - {}",
                        response.statusCode(), response.body());
            } else {
                log.info("Test registered with dashboard: {}", testId);
            }

        } catch (Exception e) {
            log.error("Error registering test with dashboard", e);
        }
    }

    /**
     * Sends a metrics update to the dashboard.
     *
     * @param metrics the metrics to send
     */
    private void sendMetricsUpdate(TestMetrics metrics) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("testId", testId);
            payload.put("timestamp", System.currentTimeMillis());

            // Summary metrics
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRequests", metrics.getTotalRequests());
            summary.put("successCount", metrics.getSuccessCount());
            summary.put("failureCount", metrics.getFailureCount());
            summary.put("successRate", metrics.getSuccessRate());
            summary.put("currentTps", metrics.getTpsSamples().isEmpty() ? 0 :
                    metrics.getTpsSamples().get(metrics.getTpsSamples().size() - 1).getTps());
            summary.put("p50ResponseTime", metrics.getResponseTimePercentile(50));
            summary.put("p95ResponseTime", metrics.getResponseTimePercentile(95));
            summary.put("p99ResponseTime", metrics.getResponseTimePercentile(99));

            payload.put("summary", summary);

            // Status code counts
            payload.put("statusCodes", metrics.getStatusCodeCounts());

            // Resource usage
            List<ResourceSnapshot> resourceSnapshots = metrics.getResourceSnapshots();
            if (resourceSnapshots != null && !resourceSnapshots.isEmpty()) {
                ResourceSnapshot latest = resourceSnapshots.get(resourceSnapshots.size() - 1);

                Map<String, Object> resources = new HashMap<>();
                resources.put("cpuPercentage", latest.getCpuPercentage());
                resources.put("heapUsedMB", latest.getHeapUsedMB());
                resources.put("totalMemoryUsedMB", latest.getTotalMemoryUsedMB());
                resources.put("activeThreads", latest.getActiveThreads());

                payload.put("resources", resources);
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/metrics/update"))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.warn("Failed to send metrics update: {} - {}",
                        response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.error("Error sending metrics update", e);
        }
    }

    /**
     * Marks the test as finished in the dashboard.
     */
    private void finishTest() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("testId", testId);
            payload.put("endTime", System.currentTimeMillis());

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/tests/finish"))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.error("Failed to finish test in dashboard: {} - {}",
                        response.statusCode(), response.body());
            } else {
                log.info("Test marked as finished in dashboard: {}", testId);
            }

        } catch (Exception e) {
            log.error("Error finishing test in dashboard", e);
        }
    }

    /**
     * Sends the final test results to the dashboard.
     *
     * @param metrics the final metrics
     */
    public void sendTestResult(TestMetrics metrics) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("testId", testId);
            payload.put("testName", config.getName());
            payload.put("startTime", metrics.getStartTime());
            payload.put("endTime", metrics.getEndTime());
            payload.put("duration", metrics.getDuration());
            payload.put("totalRequests", metrics.getTotalRequests());
            payload.put("successCount", metrics.getSuccessCount());
            payload.put("failureCount", metrics.getFailureCount());
            payload.put("timeoutCount", metrics.getTimeoutCount());
            payload.put("successRate", metrics.getSuccessRate());
            payload.put("avgTps", metrics.getAverageTps());
            payload.put("maxTps", metrics.getMaxTps());
            payload.put("p50ResponseTime", metrics.getResponseTimePercentile(50));
            payload.put("p90ResponseTime", metrics.getResponseTimePercentile(90));
            payload.put("p95ResponseTime", metrics.getResponseTimePercentile(95));
            payload.put("p99ResponseTime", metrics.getResponseTimePercentile(99));
            payload.put("maxResponseTime", metrics.getResponseTimePercentile(100));
            payload.put("maxCpuUsage", metrics.getMaxCpuUsage());
            payload.put("maxMemoryUsage", metrics.getMaxMemoryUsage());
            payload.put("statusCodes", metrics.getStatusCodeCounts());

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/tests/result"))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                log.error("Failed to send test result to dashboard: {} - {}",
                        response.statusCode(), response.body());
            } else {
                log.info("Test result sent to dashboard");
            }

        } catch (Exception e) {
            log.error("Error sending test result to dashboard", e);
        }
    }

    /**
     * Interface for supplying current metrics.
     */
    public interface MetricsSupplier {
        /**
         * Gets the current metrics.
         *
         * @return the metrics
         */
        TestMetrics getMetrics();
    }
}