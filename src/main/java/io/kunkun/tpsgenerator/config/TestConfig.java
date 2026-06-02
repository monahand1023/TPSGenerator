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

        if (requestTemplates == null || requestTemplates.isEmpty()) {
            throw new IllegalArgumentException("At least one request template is required");
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
         * InfluxDB configuration for metrics export.
         */
        private InfluxDBConfig influxDb;

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
         * InfluxDB configuration.
         */
        @Data
        public static class InfluxDBConfig {
            /**
             * Whether InfluxDB export is enabled.
             */
            private boolean enabled = false;

            /**
             * URL of InfluxDB server.
             */
            private String url;

            /**
             * Token for authentication.
             */
            private String token;

            /**
             * Organization name.
             */
            private String org;

            /**
             * Bucket name.
             */
            private String bucket;
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
}