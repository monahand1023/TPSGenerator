package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.factory.TrafficPatternFactory;
import io.kunkun.tpsgenerator.metrics.LatencyStats;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.model.TestResult;
import io.kunkun.tpsgenerator.request.RequestGenerator;
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
    private final LatencyRecorderAdapter latencyRecorder;

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
        this.rateLimiter = RateLimiter.create(initialTps);

        this.resourceManager = new ExecutionResourceManager(config);
        this.latencyRecorder = new LatencyRecorderAdapter(true);
        this.requestGenerator = new RequestGenerator(config);

        if (config.getCircuitBreaker().isEnabled()) {
            this.circuitBreaker = new CircuitBreaker(
                    config.getCircuitBreaker().getErrorThreshold(),
                    config.getCircuitBreaker().getWindowSize());
        } else {
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

            RequestExecutor requestExecutor = RequestExecutor.builder()
                    .httpClient(httpClient)
                    .requestGenerator(requestGenerator)
                    .metricsCollector(metricsCollector)
                    .circuitBreaker(circuitBreaker)
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
        return latencyRecorder.getLatencyStats();
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

    private void executeMainLoop(RequestExecutor requestExecutor, long testStartTime)
            throws InterruptedException {
        long testEndTime = testStartTime + config.getTestDuration().toMillis();
        log.info("Test started, will run for {} seconds", config.getTestDuration().getSeconds());

        while (System.currentTimeMillis() < testEndTime
                && !Thread.currentThread().isInterrupted()) {
            if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                log.warn("Circuit breaker is open, stopping test");
                break;
            }
            submitRequest(requestExecutor, testStartTime);
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

        // Apply think time + jitter between submissions if configured.
        long thinkTimeMs = config.getThinkTimeMs();
        if (thinkTimeMs > 0) {
            long jitterMs = config.getThinkTimeJitterMs();
            long sleepMs = thinkTimeMs
                    + (jitterMs > 0 ? ThreadLocalRandom.current().nextLong(jitterMs + 1) : 0);
            Thread.sleep(sleepMs);
        }

        // Pace submission to the target rate. Blocking here — on the single
        // submission loop — rather than inside each worker means virtual threads
        // are spawned only as fast as the rate limiter allows, so the number of
        // live threads tracks real in-flight load (TPS x latency) instead of
        // piling up parked on a permit.
        double rateLimiterWaitSeconds = rateLimiter.acquire();
        metricsCollector.recordRateLimiterWait(rateLimiterWaitSeconds);

        long requestId = requestCounter.incrementAndGet();
        final boolean recordLatency = warmupComplete.get();

        resourceManager.getExecutor().submit(() -> {
            long requestStartTime = System.currentTimeMillis();
            long elapsedTime = requestStartTime - testStartTime;
            long nanoStart = System.nanoTime();
            requestExecutor.executeRequest(requestId, elapsedTime);
            if (recordLatency) {
                latencyRecorder.record(System.nanoTime() - nanoStart);
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

        return new TestResult(
                config.getName(),
                testStartTime,
                System.currentTimeMillis(),
                metricsCollector.getTestMetrics());
    }
}
