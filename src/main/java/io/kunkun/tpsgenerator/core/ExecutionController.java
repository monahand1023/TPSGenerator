package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.factory.TrafficPatternFactory;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.metrics.ResponseTimeMetrics;
import io.kunkun.tpsgenerator.metrics.exporter.DashboardClient;
import io.kunkun.tpsgenerator.model.TestResult;
import io.kunkun.tpsgenerator.request.RequestGenerator;
import io.kunkun.tpsgenerator.request.ResponseValidator;
import io.kunkun.tpsgenerator.traffic.TrafficPattern;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the execution of a load test.
 *
 * <p>Wires together {@link ExecutionResourceManager} (thread pool + shutdown),
 * {@link RateLimiterScheduler} (traffic-pattern-driven rate updates),
 * {@link LatencyRecorderAdapter} (HDR histogram wrapper), and
 * {@link RequestExecutor} (per-request HTTP logic).
 *
 * <p>The public API — constructor signatures, {@link #execute()},
 * {@link #getLatencyPercentiles()}, {@link #stop()}, and {@link #close()} —
 * is unchanged from the original implementation.
 */
@Slf4j
public class ExecutionController implements java.io.Closeable {

    private final TestConfig config;
    private final MetricsCollector metricsCollector;
    private final HttpClient httpClient;
    private final RequestGenerator requestGenerator;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;

    private final ExecutionResourceManager resourceManager;

    /** Optional live-dashboard reporter; non-null only when dashboard reporting is enabled. */
    private volatile DashboardClient dashboardClient;

    private final AtomicBoolean testRunning = new AtomicBoolean(false);
    private final AtomicBoolean warmupComplete = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a new ExecutionController using its own {@link HttpClient}.
     *
     * @param config           the test configuration
     * @param metricsCollector the metrics collector for recording test results
     */
    public ExecutionController(TestConfig config, MetricsCollector metricsCollector) {
        this(config, metricsCollector, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .version(HttpClient.Version.HTTP_2)
                // Virtual-thread executor for response callbacks scales to high concurrency
                // without ballooning a cached platform-thread pool.
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build());
    }

    /**
     * Creates a new ExecutionController with a shared {@link HttpClient}.
     * Prefer this constructor when sharing a client with other components
     * (e.g. {@code DashboardClient}).
     *
     * @param config           the test configuration
     * @param metricsCollector the metrics collector for recording test results
     * @param httpClient       the shared HttpClient to use
     */
    public ExecutionController(TestConfig config, MetricsCollector metricsCollector,
                               HttpClient httpClient) {
        this.config = config;
        this.metricsCollector = metricsCollector;
        this.httpClient = httpClient;

        TrafficPattern trafficPattern = TrafficPatternFactory.create(config.getTrafficPattern());
        double initialTps = trafficPattern.getTpsAtTime(0, config.getTestDuration().toMillis());
        // Floor to MIN_RATE_LIMITER_TPS so a ramp that starts at 0 TPS doesn't crash (rate 0) or
        // stall (a permit at a near-zero rate parks the next one far in the future). See the
        // constant's javadoc for why the floor must be ~1 TPS.
        this.rateLimiter = RateLimiter.create(Math.max(initialTps, Constants.MIN_RATE_LIMITER_TPS));

        this.resourceManager = new ExecutionResourceManager(config);
        this.requestGenerator = new RequestGenerator(config);

        if (config.getCircuitBreaker() != null && config.getCircuitBreaker().isEnabled()) {
            this.circuitBreaker = new CircuitBreaker(
                    config.getCircuitBreaker().getErrorThreshold(),
                    config.getCircuitBreaker().getWindowSize());
        } else {
            // circuitBreaker block is optional — null means "no breaker".
            this.circuitBreaker = null;
        }

        // RateLimiterScheduler is created just before execute() so it can
        // capture metricsCollector.getStartTime() accurately — but we keep a
        // reference to trafficPattern and totalDurationMs for that.
        // We store them in locals here and build the scheduler in execute().
        this._trafficPattern = trafficPattern;

        log.info("Initialized execution controller with traffic pattern: {}", trafficPattern);

        // Register shutdown hook last — after all initialization succeeds —
        // so the hook is not orphaned if the constructor throws.
        resourceManager.addShutdownHook();
    }

    // Stored only so execute() can build RateLimiterScheduler after metrics start.
    private final TrafficPattern _trafficPattern;

    // -------------------------------------------------------------------------
    // Main public API
    // -------------------------------------------------------------------------

    /**
     * Executes the load test.
     *
     * @return the test result
     * @throws InterruptedException if the test is interrupted
     * @throws IllegalStateException if the test is already running
     */
    public TestResult execute() throws InterruptedException {
        if (!testRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Test is already running");
        }

        long testStartTime = System.currentTimeMillis();

        Duration warmupDuration = config.getWarmupDuration();
        if (warmupDuration == null || warmupDuration.isZero()) {
            warmupComplete.set(true);
        } else {
            warmupComplete.set(false);
            log.info("Warm-up phase started: latency will not be recorded for {}", warmupDuration);
        }

        try {
            metricsCollector.start();

            RateLimiterScheduler rateLimiterScheduler = new RateLimiterScheduler(
                    _trafficPattern, rateLimiter, resourceManager, metricsCollector,
                    config.getTestDuration().toMillis());
            rateLimiterScheduler.start();

            startDashboardIfEnabled();

            RequestExecutor requestExecutor = RequestExecutor.builder()
                    .httpClient(httpClient)
                    .requestGenerator(requestGenerator)
                    .metricsCollector(metricsCollector)
                    .circuitBreaker(circuitBreaker)
                    .responseValidator(buildResponseValidator())
                    .build();

            executeMainLoop(requestExecutor, testStartTime);

            return completeTest(testStartTime);

        } finally {
            testRunning.set(false);
            resourceManager.getScheduler().shutdownNow();
        }
    }

    /**
     * Returns a snapshot of latency percentiles recorded during (or after) the
     * test.  All values are in milliseconds.
     *
     * @return a {@link LatencyStats} with p50, p95, p99, max, and mean
     */
    public LatencyStats getLatencyPercentiles() {
        ResponseTimeMetrics rtm = metricsCollector.getTestMetrics().getResponseTimeMetrics();
        // Drain any residual interval into the snapshot so a post-test read is complete.
        metricsCollector.getTestMetrics().updateHistogramSnapshots();
        return new LatencyStats(
                rtm.getResponseTimePercentile(50),
                rtm.getResponseTimePercentile(95),
                rtm.getResponseTimePercentile(99),
                rtm.getMaxResponseTime(),
                rtm.getMeanResponseTime());
    }

    /**
     * Waits for the test to complete.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout
     * @return {@code true} if the test completed, {@code false} on timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitForCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return completionLatch.await(timeout, unit);
    }

    /**
     * Stops the test execution.
     */
    public void stop() {
        if (testRunning.get()) {
            log.info("Stopping test execution");
            resourceManager.getExecutor().shutdownNow();
            resourceManager.getScheduler().shutdownNow();
            if (dashboardClient != null) {
                dashboardClient.stop();
            }
            testRunning.set(false);
            completionLatch.countDown();
        }
    }

    /**
     * Closes this controller and releases all resources.
     */
    @Override
    public void close() {
        stop();
        resourceManager.shutdownGracefully();
        resourceManager.removeShutdownHook();
    }

    // -------------------------------------------------------------------------
    // Internal execution logic
    // -------------------------------------------------------------------------

    /**
     * Starts live dashboard reporting if {@code metrics.dashboard.enabled} is set. Failures here
     * never abort the test — dashboard reporting is best-effort.
     */
    private void startDashboardIfEnabled() {
        TestConfig.MetricsConfig mc = config.getMetrics();
        if (mc == null || mc.getDashboard() == null || !mc.getDashboard().isEnabled()) {
            return;
        }
        try {
            String testId = java.util.UUID.randomUUID().toString();
            dashboardClient = new DashboardClient(config, testId, httpClient);
            dashboardClient.startRealtimeUpdates(metricsCollector::getTestMetrics, 2000L);
            log.info("Live dashboard reporting enabled (testId={})", testId);
        } catch (Exception e) {
            log.warn("Dashboard reporting could not be started: {}", e.getMessage());
            dashboardClient = null;
        }
    }

    /**
     * Builds a {@link ResponseValidator} from config, or {@code null} when validation is disabled.
     */
    private ResponseValidator buildResponseValidator() {
        TestConfig.ResponseValidationConfig rv = config.getResponseValidation();
        if (rv == null || !rv.isEnabled()) {
            return null;
        }
        ResponseValidator validator = new ResponseValidator()
                .withStatusCodeRange(rv.getExpectedStatusMin(), rv.getExpectedStatusMax());
        if (rv.getBodyContains() != null && !rv.getBodyContains().isBlank()) {
            validator.withBodyContaining(rv.getBodyContains());
        }
        if (rv.getMinSizeBytes() >= 0 || rv.getMaxSizeBytes() >= 0) {
            int min = Math.max(0, rv.getMinSizeBytes());
            int max = rv.getMaxSizeBytes() >= 0 ? rv.getMaxSizeBytes() : Integer.MAX_VALUE;
            validator.withSizeRange(min, max);
        }
        log.info("Response validation enabled (status [{}-{}])",
                rv.getExpectedStatusMin(), rv.getExpectedStatusMax());
        return validator;
    }

    private void executeMainLoop(RequestExecutor requestExecutor, long testStartTime)
            throws InterruptedException {
        long testEndTime = testStartTime + config.getTestDuration().toMillis();
        int submitters = Math.max(1, config.getSubmissionThreads());
        log.info("Test started, will run for {} seconds ({} submission thread(s))",
                config.getTestDuration().getSeconds(), submitters);

        if (submitters == 1) {
            runSubmissionLoop(requestExecutor, testStartTime, testEndTime);
            return;
        }

        // Multiple submission loops share the (thread-safe) rate limiter, so the total offered
        // rate is unchanged but the per-request submission overhead is spread across threads —
        // raising the ceiling for very high target TPS.
        Thread[] submitterThreads = new Thread[submitters];
        for (int i = 0; i < submitters; i++) {
            submitterThreads[i] = new Thread(() -> {
                try {
                    runSubmissionLoop(requestExecutor, testStartTime, testEndTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "tps-submitter-" + i);
            submitterThreads[i].start();
        }
        for (Thread t : submitterThreads) {
            t.join();
        }
    }

    private void runSubmissionLoop(RequestExecutor requestExecutor, long testStartTime, long testEndTime)
            throws InterruptedException {
        while (System.currentTimeMillis() < testEndTime
                && !Thread.currentThread().isInterrupted()) {
            if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                log.warn("Circuit breaker is open, stopping test");
                break;
            }
            try {
                submitRequest(requestExecutor, testStartTime);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Executor is shutting down (stop()/SIGTERM raced the loop) — stop submitting cleanly.
                log.debug("Submission rejected; executor is shutting down, ending submission loop");
                break;
            }
        }
    }

    private void submitRequest(RequestExecutor requestExecutor, long testStartTime)
            throws InterruptedException {

        // Transition out of warm-up exactly once.
        if (!warmupComplete.get()) {
            Duration warmupDuration = config.getWarmupDuration();
            long elapsedMs = System.currentTimeMillis() - testStartTime;
            if (warmupDuration != null && elapsedMs >= warmupDuration.toMillis()) {
                if (warmupComplete.compareAndSet(false, true)) {
                    log.info("Warm-up phase complete after {} ms — latency recording is now active",
                            elapsedMs);
                }
            }
        }

        // Pace submission to the target rate with a BOUNDED wait. A plain blocking acquire() would
        // park for 1/rate seconds and ignore rate updates and the test-end deadline while parked;
        // at a very low (e.g. ramp-start) rate that means multi-second stalls. tryAcquire with a
        // timeout returns control to the loop so it re-checks the deadline and the updated rate.
        long acquireStartNanos = System.nanoTime();
        if (!rateLimiter.tryAcquire(Constants.RATE_LIMITER_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return; // no permit within the bound; caller loop re-evaluates and tries again
        }
        metricsCollector.recordRateLimiterWait((System.nanoTime() - acquireStartNanos) / 1_000_000_000.0);

        // Think time + jitter between submissions, if configured (only after we have a permit).
        long thinkTimeMs = config.getThinkTimeMs();
        if (thinkTimeMs > 0) {
            long jitterMs = config.getThinkTimeJitterMs();
            long sleepMs = thinkTimeMs
                    + (jitterMs > 0 ? ThreadLocalRandom.current().nextLong(jitterMs + 1) : 0);
            Thread.sleep(sleepMs);
        }

        // Expected inter-request interval at the current target rate. Coordinated omission is
        // corrected by recordValueWithExpectedInterval (HDR back-fills the samples a stalled
        // target swallows) — NOT by starting the clock before the pace wait, which would inflate
        // every latency by the pacing interval even when the target is fast.
        double currentRate = rateLimiter.getRate();
        final long expectedIntervalNanos = currentRate > 0 ? (long) (1_000_000_000L / currentRate) : 0L;

        long requestId = requestCounter.incrementAndGet();
        final boolean recordLatency = warmupComplete.get();

        resourceManager.getExecutor().submit(() -> {
            // Clock starts at actual send time (after the pace wait): this is service latency.
            long serviceStartNanos = System.nanoTime();
            long requestStartTime = System.currentTimeMillis();
            long elapsedTime = requestStartTime - testStartTime;
            requestExecutor.executeRequest(requestId, elapsedTime);
            if (recordLatency) {
                metricsCollector.recordEndToEndLatency(
                        System.nanoTime() - serviceStartNanos, expectedIntervalNanos);
            }
        });
    }

    private TestResult completeTest(long testStartTime) throws InterruptedException {
        log.info("Test execution completed, waiting for pending requests to finish");

        ExecutorService executor = resourceManager.getExecutor();
        executor.shutdown();
        executor.awaitTermination(Constants.EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        completionLatch.countDown();
        metricsCollector.stop();

        // Push the final result BEFORE marking the run finished, so the dashboard records the
        // result against a still-open run (stop() posts the "finish" event).
        if (dashboardClient != null) {
            dashboardClient.sendTestResult(metricsCollector.getTestMetrics());
            dashboardClient.stop();
        }

        return new TestResult(
                config.getName(),
                testStartTime,
                System.currentTimeMillis(),
                metricsCollector.getTestMetrics());
    }
}
