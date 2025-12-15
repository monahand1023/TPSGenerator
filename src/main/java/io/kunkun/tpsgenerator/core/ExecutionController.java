package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.factory.TrafficPatternFactory;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.model.TestResult;
import io.kunkun.tpsgenerator.request.RequestGenerator;
import io.kunkun.tpsgenerator.traffic.TrafficPattern;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controls the execution of a load test.
 * This class orchestrates the test execution, manages threads, and controls the traffic rate.
 */
@Slf4j
public class ExecutionController {
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

    /**
     * Creates a new ExecutionController.
     *
     * @param config the test configuration
     * @param metricsCollector the metrics collector for recording test results
     */
    public ExecutionController(TestConfig config, MetricsCollector metricsCollector) {
        this.config = config;
        this.metricsCollector = metricsCollector;

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

        // Initialize HTTP client
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

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
            requestExecutor.executeRequest(requestId, elapsedTime);
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
        executor.awaitTermination(30, TimeUnit.SECONDS);
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
}