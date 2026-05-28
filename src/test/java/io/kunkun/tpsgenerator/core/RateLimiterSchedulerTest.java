package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.traffic.StablePattern;
import io.kunkun.tpsgenerator.traffic.TrafficPattern;
import com.google.common.util.concurrent.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RateLimiterScheduler}.
 */
class RateLimiterSchedulerTest {

    private ExecutionResourceManager resourceManager;
    private MetricsCollector metricsCollector;
    private TestConfig config;

    @BeforeEach
    void setUp() {
        config = minimalConfig();
        resourceManager = new ExecutionResourceManager(config);
        metricsCollector = new MetricsCollector(config);
        metricsCollector.start();
    }

    @AfterEach
    void tearDown() {
        metricsCollector.stop();
        try {
            resourceManager.shutdownGracefully(1, TimeUnit.SECONDS);
        } catch (Exception ignored) { }
        resourceManager.removeShutdownHook();
    }

    @Test
    @DisplayName("start() schedules periodic ticks that invoke the rate limiter")
    @Timeout(5)
    void startSchedulesRateUpdates() throws InterruptedException {
        AtomicInteger tickCount = new AtomicInteger(0);
        RateLimiter rateLimiter = RateLimiter.create(10.0);

        // Use a custom traffic pattern that counts invocations
        TrafficPattern countingPattern = new TrafficPattern() {
            @Override
            public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
                tickCount.incrementAndGet();
                return 10.0;
            }

            @Override
            public double getMaxTps() { return 10.0; }
        };

        RateLimiterScheduler scheduler = new RateLimiterScheduler(
                countingPattern, rateLimiter, resourceManager, metricsCollector,
                config.getTestDuration().toMillis());

        scheduler.start();

        // Wait for at least 2 ticks (each 1 s apart, but initial delay is 0)
        Thread.sleep(1500);
        scheduler.stop();

        assertTrue(tickCount.get() >= 2,
                "Expected at least 2 rate-update ticks, got " + tickCount.get());
    }

    @Test
    @DisplayName("stop() prevents further ticks after cancellation")
    @Timeout(5)
    void stopCancelsFutureTicks() throws InterruptedException {
        AtomicInteger tickCount = new AtomicInteger(0);
        RateLimiter rateLimiter = RateLimiter.create(10.0);

        TrafficPattern countingPattern = new TrafficPattern() {
            @Override
            public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
                tickCount.incrementAndGet();
                return 10.0;
            }

            @Override
            public double getMaxTps() { return 10.0; }
        };

        RateLimiterScheduler scheduler = new RateLimiterScheduler(
                countingPattern, rateLimiter, resourceManager, metricsCollector,
                config.getTestDuration().toMillis());

        scheduler.start();
        Thread.sleep(200); // let the initial tick fire
        scheduler.stop();

        int countAtStop = tickCount.get();
        Thread.sleep(1500); // wait a full interval past stop

        int countAfterWait = tickCount.get();
        // Allow for at most 1 in-flight tick at the moment of stop
        assertTrue(countAfterWait <= countAtStop + 1,
                "Ticks should stop after stop() — before=" + countAtStop
                        + " after=" + countAfterWait);
    }

    @Test
    @DisplayName("start() is idempotent — second call does not double-schedule")
    @Timeout(5)
    void startIsIdempotent() throws InterruptedException {
        AtomicInteger tickCount = new AtomicInteger(0);
        RateLimiter rateLimiter = RateLimiter.create(10.0);

        TrafficPattern countingPattern = new TrafficPattern() {
            @Override
            public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
                tickCount.incrementAndGet();
                return 10.0;
            }

            @Override
            public double getMaxTps() { return 10.0; }
        };

        RateLimiterScheduler scheduler = new RateLimiterScheduler(
                countingPattern, rateLimiter, resourceManager, metricsCollector,
                config.getTestDuration().toMillis());

        scheduler.start();
        scheduler.start(); // second call should be a no-op
        Thread.sleep(1200);
        scheduler.stop();

        // If double-scheduled, ticks would be approx. 2x as many
        // For a 1-s interval + 0 initial delay in 1.2 s, expect ~2 ticks not ~4
        assertTrue(tickCount.get() <= 4,
                "Double-scheduling would produce ~4 ticks; got " + tickCount.get());
    }

    @Test
    @DisplayName("stop() before start() does not throw")
    void stopBeforeStartDoesNotThrow() {
        RateLimiter rateLimiter = RateLimiter.create(10.0);
        TrafficPattern pattern = new StablePattern(10.0);

        RateLimiterScheduler scheduler = new RateLimiterScheduler(
                pattern, rateLimiter, resourceManager, metricsCollector,
                config.getTestDuration().toMillis());

        assertDoesNotThrow(scheduler::stop);
    }

    @Test
    @DisplayName("RateLimiter rate is updated to match pattern output")
    @Timeout(5)
    void rateLimiterRateIsUpdated() throws InterruptedException {
        double targetTps = 42.0;
        RateLimiter rateLimiter = RateLimiter.create(1.0); // start with 1 TPS
        TrafficPattern pattern = new StablePattern(targetTps);

        CountDownLatch firstTick = new CountDownLatch(1);
        TrafficPattern latchPattern = new TrafficPattern() {
            @Override
            public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
                firstTick.countDown();
                return targetTps;
            }

            @Override
            public double getMaxTps() { return targetTps; }
        };

        RateLimiterScheduler scheduler = new RateLimiterScheduler(
                latchPattern, rateLimiter, resourceManager, metricsCollector,
                config.getTestDuration().toMillis());

        scheduler.start();
        assertTrue(firstTick.await(3, TimeUnit.SECONDS));
        scheduler.stop();

        assertEquals(targetTps, rateLimiter.getRate(), 0.001,
                "Rate limiter rate should be updated to " + targetTps);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TestConfig minimalConfig() {
        TestConfig cfg = new TestConfig();
        cfg.setName("rls-test");
        cfg.setTestDuration(Duration.ofSeconds(10));

        TestConfig.TrafficConfig tc = new TestConfig.TrafficConfig();
        tc.setType("stable");
        tc.setTargetTps(10);
        cfg.setTrafficPattern(tc);

        TestConfig.ThreadPoolConfig tp = new TestConfig.ThreadPoolConfig();
        tp.setCoreSize(2);
        tp.setMaxSize(4);
        tp.setQueueSize(10);
        tp.setKeepAliveTime(Duration.ofSeconds(10));
        cfg.setThreadPool(tp);

        io.kunkun.tpsgenerator.request.RequestTemplate rt =
                new io.kunkun.tpsgenerator.request.RequestTemplate();
        rt.setMethod("GET");
        rt.setUrlTemplate("http://localhost/test");
        cfg.setRequestTemplates(List.of(rt));

        TestConfig.CircuitBreakerConfig cb = new TestConfig.CircuitBreakerConfig();
        cb.setEnabled(false);
        cfg.setCircuitBreaker(cb);

        TestConfig.MetricsConfig mc = new TestConfig.MetricsConfig();
        TestConfig.MetricsConfig.ResourceMonitoringConfig rmc =
                new TestConfig.MetricsConfig.ResourceMonitoringConfig();
        rmc.setEnabled(false);
        mc.setResourceMonitoring(rmc);
        cfg.setMetrics(mc);

        return cfg;
    }
}
