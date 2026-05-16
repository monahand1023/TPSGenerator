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
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controls the execution of a load test.
 * This class orchestrates the test execution, manages threads, and controls the traffic rate.
 * Implements Closeable for proper resource cleanup.
 */
@Slf4j
public class ExecutionController implements java.io.Closeable {
    private final TestConfig config;
    private final MetricsCollector metricsCollector;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final RateLimiter rateLimiter;
    private final HttpClient httpClient;
    private final RequestGenerator requestGenerator;
    private final CircuitBreaker circuitBreaker;
    private final TrafficPattern trafficPattern;
    private final AtomicBoolean testRunning = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private final Thread shutdownHook;

    /**
     * Recorder for per-request latency in microseconds (thread-safe, lock-free).
     * Values are recorded in microseconds and converted to milliseconds on read.
     */
    private final Recorder latencyRecorder = new Recorder(
            TimeUnit.MINUTES.toMicros(60), // max trackable value: 1 hour in µs
            3                              // 3 significant digits
    );

    /**
     * Creates a new ExecutionController using its own HttpClient.
     *
     * @param config the test configuration
     * @param metricsCollector the metrics collector for recording test results
     */
    public ExecutionController(TestConfig config, MetricsCollector metricsCollector) {
        this(config, metricsCollector, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .version(HttpClient.Version.HTTP_2)
                .build());
    }

    /**
     * Creates a new ExecutionController with a shared HttpClient.
     * Prefer this constructor when sharing a client with other components (e.g. DashboardClient).
     *
     * @param config the test configuration
     * @param metricsCollector the metrics collector for recording test results
     * @param httpClient the shared HttpClient to use
     */
    public ExecutionController(TestConfig config, MetricsCollector metricsCollector, HttpClient httpClient) {
        this.config = config;
        this.metricsCollector = metricsCollector;
        this.httpClient = httpClient;

        // Create traffic pattern using factory
        this.trafficPattern = TrafficPatternFactory.create(config.getTrafficPattern());

        // Initialize thread pool
        this.executor = new ThreadPoolExecutor(
                config.getThreadPool().getCoreSize(),
                config.getThreadPool().getMaxSize(),
                config.getThreadPool().getKeepAliveTime().toMillis(),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.getThreadPool().getQueueSize()),
                new ThreadFactory() {
                    private final AtomicLong counter = new AtomicLong(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("tps-worker-" + counter.incrementAndGet());
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Initialize rate limiter with initial rate
        double initialTps = this.trafficPattern.getTpsAtTime(0, config.getTestDuration().toMillis());
        this.rateLimiter = RateLimiter.create(initialTps);

        // Initialize scheduler for rate updates
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Initialize request generator
        this.requestGenerator = new RequestGenerator(config);

        // Initialize circuit breaker if enabled
        if (config.getCircuitBreaker().isEnabled()) {
            this.circuitBreaker = new CircuitBreaker(
                    config.getCircuitBreaker().getErrorThreshold(),
                    config.getCircuitBreaker().getWindowSize()
            );
        } else {
            this.circuitBreaker = null;
        }

        // Register shutdown hook for cleanup
        this.shutdownHook = new Thread(this::shutdownResources, "tps-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        log.info("Initialized execution controller with traffic pattern: {}", trafficPattern);
    }

    /**
     * Executes the load test.
     *
     * @return the test result
     * @throws InterruptedException if the test is interrupted
     */
    public TestResult execute() throws InterruptedException {
        if (!testRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Test is already running");
        }

        long testStartTime = System.currentTimeMillis();

        try {
            startMetricsCollection();
            startTrafficPatternScheduler();

            RequestExecutor requestExecutor = createRequestExecutor();
            executeMainLoop(requestExecutor, testStartTime);

            return completeTest(testStartTime);

        } finally {
            cleanup();
        }
    }

    /**
     * Starts the metrics collection.
     */
    private void startMetricsCollection() {
        metricsCollector.start();
    }

    /**
     * Creates and configures the request executor.
     *
     * @return the configured RequestExecutor
     */
    private RequestExecutor createRequestExecutor() {
        return RequestExecutor.builder()
                .httpClient(httpClient)
                .requestGenerator(requestGenerator)
                .rateLimiter(rateLimiter)
                .metricsCollector(metricsCollector)
                .circuitBreaker(circuitBreaker)
                .build();
    }

    /**
     * Executes the main test loop, submitting requests until the test duration expires
     * or the circuit breaker opens.
     *
     * @param requestExecutor the request executor to use
     * @param testStartTime the test start time in milliseconds
     * @throws InterruptedException if the thread is interrupted
     */
    private void executeMainLoop(RequestExecutor requestExecutor, long testStartTime) throws InterruptedException {
        long testEndTime = testStartTime + config.getTestDuration().toMillis();

        log.info("Test started, will run for {} seconds", config.getTestDuration().getSeconds());

        while (System.currentTimeMillis() < testEndTime && !Thread.currentThread().isInterrupted()) {
            if (isCircuitBreakerOpen()) {
                log.warn("Circuit breaker is open, stopping test");
                break;
            }

            submitRequest(requestExecutor, testStartTime);

            // Small sleep to prevent CPU spinning
            Thread.sleep(1);
        }
    }

    /**
     * Checks if the circuit breaker is open.
     *
     * @return true if the circuit breaker is open, false otherwise
     */
    private boolean isCircuitBreakerOpen() {
        return circuitBreaker != null && !circuitBreaker.allowRequest();
    }

    /**
     * Submits a single request to the executor.
     *
     * @param requestExecutor the request executor
     * @param testStartTime the test start time
     */
    private void submitRequest(RequestExecutor requestExecutor, long testStartTime) {
        long requestId = requestCounter.incrementAndGet();

        executor.submit(() -> {
            long requestStartTime = System.currentTimeMillis();
            long elapsedTime = requestStartTime - testStartTime;
            long nanoStart = System.nanoTime();
            requestExecutor.executeRequest(requestId, elapsedTime);
            latencyRecorder.recordValue((System.nanoTime() - nanoStart) / 1000);
        });
    }

    /**
     * Completes the test by shutting down executors and building the result.
     *
     * @param testStartTime the test start time
     * @return the test result
     * @throws InterruptedException if interrupted while waiting for shutdown
     */
    private TestResult completeTest(long testStartTime) throws InterruptedException {
        log.info("Test execution completed, waiting for pending requests to finish");

        shutdownExecutor();
        completionLatch.countDown();
        metricsCollector.stop();

        return buildTestResult(testStartTime);
    }

    /**
     * Shuts down the executor and waits for pending tasks.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    private void shutdownExecutor() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(Constants.EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Builds the test result.
     *
     * @param testStartTime the test start time
     * @return the test result
     */
    private TestResult buildTestResult(long testStartTime) {
        return new TestResult(
                config.getName(),
                testStartTime,
                System.currentTimeMillis(),
                metricsCollector.getTestMetrics()
        );
    }

    /**
     * Cleans up resources after test execution.
     */
    private void cleanup() {
        testRunning.set(false);
        scheduler.shutdownNow();
    }

    /**
     * Starts the scheduler that updates the rate limiter based on the traffic pattern.
     */
    private void startTrafficPatternScheduler() {
        long totalDurationMs = config.getTestDuration().toMillis();

        // Schedule rate updates every second
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long elapsedTimeMs = System.currentTimeMillis() - metricsCollector.getStartTime();
                // Get the target TPS for the current time
                double targetTps = trafficPattern.getTpsAtTime(elapsedTimeMs, totalDurationMs);
                // Update the rate limiter
                rateLimiter.setRate(targetTps);

                // Log progress every 10 seconds
                if (elapsedTimeMs % 10000 < 1000) {
                    double completionPercentage = 100.0 * elapsedTimeMs / totalDurationMs;
                    double currentTps = metricsCollector.getCurrentTps();

                    log.info("Progress: {}% | Target TPS: {} | Actual TPS: {} | Success Rate: {}%",
                            String.format("%.1f", completionPercentage),
                            String.format("%.2f", targetTps),
                            String.format("%.2f", currentTps),
                            String.format("%.2f", metricsCollector.getTestMetrics().getSuccessRate() * 100));
                }
            } catch (Exception e) {
                log.error("Error updating rate limiter", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Waits for the test to complete.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return true if the test completed, false if it timed out
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
            executor.shutdownNow();
            scheduler.shutdownNow();
            testRunning.set(false);
            completionLatch.countDown();
        }
    }

    /**
     * Returns a snapshot of latency percentiles recorded during (or after) the test.
     * Calls {@link Recorder#flip()} to capture all values recorded so far.
     * All returned values are in milliseconds.
     *
     * @return a {@link LatencyStats} containing p50, p95, p99, max, and mean latency in ms
     */
    public LatencyStats getLatencyPercentiles() {
        Histogram h = latencyRecorder.getIntervalHistogram();
        if (h.getTotalCount() == 0) {
            return new LatencyStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double p50Ms  = h.getValueAtPercentile(50.0)  / 1000.0;
        double p95Ms  = h.getValueAtPercentile(95.0)  / 1000.0;
        double p99Ms  = h.getValueAtPercentile(99.0)  / 1000.0;
        double maxMs  = h.getMaxValue()               / 1000.0;
        double meanMs = h.getMean()                   / 1000.0;
        return new LatencyStats(p50Ms, p95Ms, p99Ms, maxMs, meanMs);
    }

    /**
     * Closes this controller and releases all resources.
     * Removes the shutdown hook if not called from the hook itself.
     */
    @Override
    public void close() {
        stop();
        shutdownResources();

        // Remove shutdown hook if not currently executing it
        try {
            if (!Thread.currentThread().equals(shutdownHook)) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        } catch (IllegalStateException e) {
            // JVM is already shutting down, ignore
        }
    }

    /**
     * Shuts down executor resources gracefully.
     */
    private void shutdownResources() {
        log.debug("Shutting down executor resources");

        // Shutdown executor if not already shutdown
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(Constants.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown scheduler if not already shutdown
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(Constants.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}