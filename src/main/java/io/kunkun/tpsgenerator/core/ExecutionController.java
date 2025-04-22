package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.model.TestResult;
import io.kunkun.tpsgenerator.request.RequestGenerator;
import io.kunkun.tpsgenerator.traffic.RampUpPattern;
import io.kunkun.tpsgenerator.traffic.SpikePattern;
import io.kunkun.tpsgenerator.traffic.StablePattern;
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

        // Create traffic pattern
        TrafficPattern trafficPattern = createTrafficPattern(config);

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
        double initialTps = trafficPattern.getTpsAtTime(0, config.getTestDuration().toMillis());
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
     * Creates a traffic pattern based on the test configuration.
     *
     * @param config the test configuration
     * @return the traffic pattern
     */
    private TrafficPattern createTrafficPattern(TestConfig config) {
        TestConfig.TrafficConfig trafficConfig = config.getTrafficPattern();
        String patternType = trafficConfig.getType();

        switch (patternType.toLowerCase()) {
            case "stable":
                return new StablePattern(trafficConfig.getTargetTps());

            case "rampup":
                return new RampUpPattern(
                        trafficConfig.getStartTps(),
                        trafficConfig.getTargetTps(),
                        trafficConfig.getRampDuration().toMillis()
                );

            case "spike":
                return new SpikePattern(
                        trafficConfig.getTargetTps(),
                        trafficConfig.getSpikeTps(),
                        trafficConfig.getSpikeStartTime().toMillis(),
                        trafficConfig.getSpikeDuration().toMillis()
                );

            default:
                throw new IllegalArgumentException("Unsupported traffic pattern type: " + patternType);
        }
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

        try {
            // Start metrics collector
            metricsCollector.start();

            // Start traffic pattern scheduler
            startTrafficPatternScheduler();

            // Start request executor
            RequestExecutor requestExecutor = new RequestExecutor(
                    httpClient,
                    requestGenerator,
                    rateLimiter,
                    metricsCollector,
                    circuitBreaker
            );

            // Execute requests until duration expires
            long testStartTime = System.currentTimeMillis();
            long testEndTime = testStartTime + config.getTestDuration().toMillis();

            log.info("Test started, will run for {} seconds", config.getTestDuration().getSeconds());

            while (System.currentTimeMillis() < testEndTime && !Thread.currentThread().isInterrupted()) {
                // Check if circuit breaker is open
                if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                    log.warn("Circuit breaker is open, stopping test");
                    break;
                }

                long requestId = requestCounter.incrementAndGet();

                // Submit request task to executor
                executor.submit(() -> {
                    long requestStartTime = System.currentTimeMillis();
                    // Calculate elapsed time from the test start
                    long elapsedTime = requestStartTime - testStartTime;

                    // Execute the request
                    requestExecutor.executeRequest(requestId, elapsedTime);
                });

                // Small sleep to prevent CPU spinning
                Thread.sleep(1);
            }

            log.info("Test execution completed, waiting for pending requests to finish");

            // Shutdown executor and wait for pending tasks to complete
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // Signal completion
            completionLatch.countDown();

            // Stop metrics collector
            metricsCollector.stop();

            return new TestResult(
                    config.getName(),
                    testStartTime,
                    System.currentTimeMillis(),
                    metricsCollector.getTestMetrics()
            );

        } finally {
            testRunning.set(false);
            scheduler.shutdownNow();
        }
    }

    /**
     * Starts the scheduler that updates the rate limiter based on the traffic pattern.
     */
    private void startTrafficPatternScheduler() {
        TrafficPattern trafficPattern = createTrafficPattern(config);
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

                    log.info("Progress: {:.1f}% | Target TPS: {:.2f} | Actual TPS: {:.2f} | " +
                                    "Success Rate: {:.2f}%",
                            completionPercentage,
                            targetTps,
                            currentTps,
                            metricsCollector.getTestMetrics().getSuccessRate() * 100);
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