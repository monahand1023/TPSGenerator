package io.kunkun.tpsgenerator.config;

import io.kunkun.tpsgenerator.request.RequestTemplate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a TPS Generator test.
 * This class holds all the parameters needed to execute a load test.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestConfig {

    /**
     * Validates the configuration and throws an exception if invalid.
     *
     * @throws IllegalArgumentException if the configuration is invalid
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Test name cannot be null or empty");
        }

        if (testDuration == null || testDuration.isNegative() || testDuration.isZero()) {
            throw new IllegalArgumentException("Test duration must be a positive duration");
        }

        if (warmupDuration != null && warmupDuration.isNegative()) {
            throw new IllegalArgumentException("Warm-up duration must not be negative");
        }

        if (thinkTimeMs < 0) {
            throw new IllegalArgumentException("thinkTimeMs must not be negative");
        }

        if (thinkTimeJitterMs < 0) {
            throw new IllegalArgumentException("thinkTimeJitterMs must not be negative");
        }

        if (failThresholdErrorRate < 0.0 || failThresholdErrorRate > 1.0) {
            throw new IllegalArgumentException(
                    "failThresholdErrorRate must be in the range [0.0, 1.0]");
        }

        if (submissionThreads < 1) {
            throw new IllegalArgumentException("submissionThreads must be at least 1");
        }

        if (trafficPattern == null) {
            throw new IllegalArgumentException("Traffic pattern configuration is required");
        }

        if (trafficPattern.getType() == null || trafficPattern.getType().isBlank()) {
            throw new IllegalArgumentException("Traffic pattern type is required");
        }

        if (!"custom".equalsIgnoreCase(trafficPattern.getType())
                && trafficPattern.getTargetTps() <= 0) {
            throw new IllegalArgumentException(
                    "Target TPS must be positive for pattern type: " + trafficPattern.getType());
        }

        // Pattern-specific duration fields are otherwise null and would NPE (or divide by zero)
        // in the traffic-pattern factory/implementations, which run before any traffic is sent.
        String patternType = trafficPattern.getType().toLowerCase();
        if ("rampup".equals(patternType)) {
            Duration ramp = trafficPattern.getRampDuration();
            if (ramp == null || ramp.isZero() || ramp.isNegative()) {
                throw new IllegalArgumentException(
                        "rampDuration must be a positive duration for the rampUp pattern");
            }
        }
        if ("spike".equals(patternType)) {
            if (trafficPattern.getSpikeStartTime() == null || trafficPattern.getSpikeStartTime().isNegative()) {
                throw new IllegalArgumentException(
                        "spikeStartTime must be set (non-negative) for the spike pattern");
            }
            Duration spikeDur = trafficPattern.getSpikeDuration();
            if (spikeDur == null || spikeDur.isZero() || spikeDur.isNegative()) {
                throw new IllegalArgumentException(
                        "spikeDuration must be a positive duration for the spike pattern");
            }
        }

        // threadPool is optional: the virtual-thread engine no longer bounds concurrency by a
        // worker pool, so a missing block is fine. If provided (for backward-compatible configs)
        // its values are still sanity-checked so a clearly-wrong block surfaces an error.
        if (threadPool != null) {
            if (threadPool.getCoreSize() <= 0) {
                throw new IllegalArgumentException("Thread pool core size must be positive");
            }
            if (threadPool.getMaxSize() < threadPool.getCoreSize()) {
                throw new IllegalArgumentException("Thread pool max size must be >= core size");
            }
        }

        boolean websocket = "websocket".equalsIgnoreCase(protocol);
        if (protocol != null && !"http".equalsIgnoreCase(protocol) && !websocket) {
            throw new IllegalArgumentException("protocol must be 'http' or 'websocket'");
        }
        if (websocket && (targetServiceUrl == null || targetServiceUrl.isBlank())) {
            throw new IllegalArgumentException("targetServiceUrl is required for the websocket protocol");
        }

        boolean hasScenario = scenario != null && !scenario.isEmpty();
        // HTTP request mode needs templates (or a scenario); websocket mode uses webSocketMessage.
        if (!hasScenario && !websocket && (requestTemplates == null || requestTemplates.isEmpty())) {
            throw new IllegalArgumentException("At least one request template is required");
        }

        if (hasScenario) {
            for (ScenarioStep step : scenario) {
                if (step.getRequest() == null
                        || step.getRequest().getMethod() == null
                        || step.getRequest().getUrlTemplate() == null) {
                    throw new IllegalArgumentException(
                            "Each scenario step requires a request with method and urlTemplate");
                }
                if (step.getExtract() != null) {
                    for (ExtractRule rule : step.getExtract()) {
                        if (rule.getName() == null || rule.getName().isBlank() || rule.getExpr() == null) {
                            throw new IllegalArgumentException(
                                    "Each scenario extract rule requires a name and an expr");
                        }
                    }
                }
            }
        }
    }

    /**
     * Name of the test.
     */
    private String name;

    /**
     * Target service URL.
     */
    private String targetServiceUrl;

    /**
     * Duration of the test.
     */
    private Duration testDuration;

    /**
     * Traffic pattern configuration.
     */
    private TrafficConfig trafficPattern;

    /**
     * Thread pool configuration.
     */
    private ThreadPoolConfig threadPool;

    /**
     * Request templates to be used in the test.
     */
    private List<RequestTemplate> requestTemplates;

    /**
     * Parameter sources for request templates.
     */
    private Map<String, ParameterSourceConfig> parameterSources = new HashMap<>();

    /**
     * Metrics configuration.
     */
    private MetricsConfig metrics;

    /**
     * Circuit breaker configuration.
     */
    private CircuitBreakerConfig circuitBreaker;

    /**
     * Optional response validation. When enabled, each 2xx response is additionally checked
     * against the configured rules; a response that fails validation is counted as a failure.
     * JSON key: {@code "responseValidation"}.
     */
    private ResponseValidationConfig responseValidation;

    /**
     * Optional pass/fail SLA thresholds. Any breach makes the process exit with code 3,
     * so the tool can gate a CI pipeline on latency/throughput/success-rate budgets.
     * JSON key: {@code "sla"}.
     */
    private SlaConfig sla;

    /**
     * Protocol to drive: {@code http} (default) or {@code websocket}. In websocket mode each
     * rate-limited slot opens a WebSocket to {@link #targetServiceUrl} (http(s) → ws(s)), sends
     * {@link #webSocketMessage}, awaits one reply, and closes — measuring the round-trip latency.
     * JSON key: {@code "protocol"}.
     */
    private String protocol = "http";

    /** Message sent on each WebSocket exchange (websocket protocol only). JSON key: {@code "webSocketMessage"}. */
    private String webSocketMessage = "ping";

    /**
     * Optional multi-step scenario. When present, the engine runs sessions (one per rate-limited
     * slot): each session executes these steps in order on a single virtual thread, threading a
     * context map through them. Values extracted from a response (see {@link ExtractRule}) become
     * {@code ${vars}} usable by later steps. In scenario mode the target TPS is the session-start
     * rate. JSON key: {@code "scenario"}.
     */
    private List<ScenarioStep> scenario;

    /**
     * Duration of the warm-up phase at the start of the test.
     * During this period requests are sent at full rate but latency is NOT recorded,
     * allowing the JVM JIT compiler to warm up before measurements begin.
     * Defaults to {@link Duration#ZERO} (no warm-up).
     * JSON key: {@code "warmupDuration"}.
     */
    private Duration warmupDuration = Duration.ZERO;

    /**
     * Base think time in milliseconds added between request submissions.
     * Simulates realistic user think time between actions.
     * This idle time is added on top of any rate-limiter delay.
     * Defaults to {@code 0} (no think time).
     * JSON key: {@code "thinkTimeMs"}.
     */
    private long thinkTimeMs = 0;

    /**
     * Maximum random jitter in milliseconds added on top of {@link #thinkTimeMs}.
     * The actual extra delay per submission is drawn uniformly from
     * {@code [0, thinkTimeJitterMs]}. Ignored when {@code thinkTimeMs == 0}.
     * Defaults to {@code 0} (no jitter).
     * JSON key: {@code "thinkTimeJitterMs"}.
     */
    private long thinkTimeJitterMs = 0;

    /**
     * Maximum error rate (as a fraction 0.0–1.0) before the process exits with code 2.
     * A value of {@code 1.0} (the default) means the test never fails on error rate alone.
     * A value of {@code 0.0} means any error causes a failure exit.
     * Example: {@code 0.01} fails the run when more than 1% of requests are errors.
     * JSON key: {@code "failThresholdErrorRate"}.
     */
    private double failThresholdErrorRate = 1.0;

    /**
     * Number of concurrent submission loops that pace requests against the (shared) rate limiter.
     * The default of 1 preserves existing behaviour; raising it distributes the per-request
     * submission overhead across threads so the generator can sustain very high target TPS that a
     * single submission loop would bottleneck. The total offered rate is still governed by the
     * rate limiter / traffic pattern. JSON key: {@code "submissionThreads"}.
     */
    private int submissionThreads = 1;

    /**
     * Traffic pattern configuration.
     */
    @Data
    public static class TrafficConfig {
        /**
         * Type of traffic pattern (stable, rampUp, spike, custom).
         */
        private String type;

        /**
         * Starting TPS rate.
         */
        private double startTps;

        /**
         * Target TPS rate.
         */
        private double targetTps;

        /**
         * Duration of ramp-up period (for rampUp pattern).
         */
        private Duration rampDuration;

        /**
         * Start time of spike (for spike pattern).
         */
        private Duration spikeStartTime;

        /**
         * Duration of spike (for spike pattern).
         */
        private Duration spikeDuration;

        /**
         * Spike TPS rate (for spike pattern).
         */
        private double spikeTps;

        /**
         * Path to custom traffic pattern file (for custom pattern).
         */
        private String patternFile;
    }

    /**
     * Thread pool configuration.
     */
    @Data
    public static class ThreadPoolConfig {
        /**
         * Core thread pool size.
         */
        private int coreSize = 10;

        /**
         * Maximum thread pool size.
         */
        private int maxSize = 50;

        /**
         * Task queue size.
         */
        private int queueSize = 1000;

        /**
         * Keep-alive time for idle threads.
         */
        private Duration keepAliveTime = Duration.ofSeconds(60);
    }

    /**
     * Parameter source configuration.
     */
    @Data
    public static class ParameterSourceConfig {
        /**
         * Type of parameter source (random, file).
         */
        private String type;

        /**
         * Range for random parameters.
         */
        private int[] range;

        /**
         * Path to parameter file.
         */
        private String path;

        /**
         * Column name for file sources.
         */
        private String column;

        /**
         * Selection strategy (round-robin, random).
         */
        private String selection;

        /**
         * Distribution type for random sources.
         */
        private String distribution;

        /**
         * Mean value for normal distribution.
         */
        private double mean;

        /**
         * Standard deviation for normal distribution.
         */
        private double stddev;

        /**
         * Minimum value.
         */
        private double min;

        /**
         * Maximum value.
         */
        private double max;
    }

    /**
     * Metrics configuration.
     */
    @Data
    public static class MetricsConfig {
        /**
         * Response time percentiles to track.
         */
        private int[] responseTimePercentiles = {50, 90, 95, 99};

        /**
         * Output file for metrics.
         */
        private String outputFile;

        /**
         * Resource monitoring configuration.
         */
        private ResourceMonitoringConfig resourceMonitoring = new ResourceMonitoringConfig();

        /**
         * Dashboard configuration.
         */
        private DashboardConfig dashboard;

        /**
         * Resource monitoring configuration.
         */
        @Data
        public static class ResourceMonitoringConfig {
            /**
             * Whether resource monitoring is enabled.
             */
            private boolean enabled = true;

            /**
             * Interval between resource samples.
             */
            private Duration sampleInterval = Duration.ofSeconds(5);
        }

        /**
         * Dashboard configuration.
         */
        @Data
        public static class DashboardConfig {
            /**
             * Whether dashboard integration is enabled.
             */
            private boolean enabled = false;

            /**
             * URL of dashboard service.
             */
            private String url;

            /**
             * API key for authentication.
             */
            private String apiKey;
        }
    }

    /**
     * Circuit breaker configuration.
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * Whether circuit breaker is enabled.
         */
        private boolean enabled = true;

        /**
         * Error threshold rate to trigger circuit breaker.
         */
        private double errorThreshold = 0.5;

        /**
         * Window size for error rate calculation.
         */
        private int windowSize = 100;
    }

    /**
     * One step of a multi-step scenario: an HTTP request plus optional value extractions and a
     * think-time pause afterward. The request's templates may reference {@code ${vars}} from the
     * session context (default params, parameter sources, and values extracted by earlier steps).
     */
    @Data
    public static class ScenarioStep {
        /** Human-readable step name. */
        private String name;
        /** The request to send (method, urlTemplate, headers, bodyTemplate; weight is ignored). */
        private RequestTemplate request;
        /** Values to extract from the response into the session context for later steps. */
        private List<ExtractRule> extract;
        /** Pause (ms) after this step before the next, simulating user think time. */
        private long thinkTimeMs = 0;
    }

    /**
     * A rule for extracting a value from a response into the session context.
     */
    @Data
    public static class ExtractRule {
        /** Context variable name to populate (referenced as ${name} by later steps). */
        private String name;
        /** Source of the value: {@code body} (regex, capture group 1) or {@code header}. */
        private String from = "body";
        /** For {@code body}: a regex whose group 1 is captured. For {@code header}: the header name. */
        private String expr;
    }

    /**
     * Pass/fail SLA thresholds. Each bound is optional; a negative value means "not checked".
     * Latency bounds are in milliseconds and compared against the end-to-end latency percentiles.
     */
    @Data
    public static class SlaConfig {
        /** Maximum acceptable p50 latency in ms ({@code -1} = unchecked). */
        private long maxP50Ms = -1;
        /** Maximum acceptable p95 latency in ms ({@code -1} = unchecked). */
        private long maxP95Ms = -1;
        /** Maximum acceptable p99 latency in ms ({@code -1} = unchecked). */
        private long maxP99Ms = -1;
        /** Minimum acceptable success rate in [0,1] ({@code -1} = unchecked). */
        private double minSuccessRate = -1;
        /** Minimum acceptable average (offered) TPS ({@code -1} = unchecked). */
        private double minAverageTps = -1;
    }

    /**
     * Optional response validation configuration. A {@code -1} size bound means "unbounded".
     */
    @Data
    public static class ResponseValidationConfig {
        /** Whether response validation is active. */
        private boolean enabled = false;

        /** Lowest acceptable status code (inclusive). */
        private int expectedStatusMin = 200;

        /** Highest acceptable status code (inclusive). */
        private int expectedStatusMax = 299;

        /** If set, the response body must contain this substring. */
        private String bodyContains;

        /** Minimum acceptable response body size in bytes ({@code -1} = no minimum). */
        private int minSizeBytes = -1;

        /** Maximum acceptable response body size in bytes ({@code -1} = no maximum). */
        private int maxSizeBytes = -1;
    }
}