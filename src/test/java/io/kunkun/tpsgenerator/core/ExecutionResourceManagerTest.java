package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.request.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExecutionResourceManager}.
 */
class ExecutionResourceManagerTest {

    private TestConfig config;
    private ExecutionResourceManager manager;

    @BeforeEach
    void setUp() {
        config = minimalConfig();
        manager = new ExecutionResourceManager(config);
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup; ignore if already shut down
        try {
            manager.shutdownGracefully(1, TimeUnit.SECONDS);
        } catch (Exception ignored) { }
        try {
            manager.removeShutdownHook();
        } catch (Exception ignored) { }
    }

    @Test
    @DisplayName("getExecutor() returns a non-null ExecutorService")
    void getExecutorIsNotNull() {
        assertNotNull(manager.getExecutor());
    }

    @Test
    @DisplayName("getScheduler() returns a non-null ScheduledExecutorService")
    void getSchedulerIsNotNull() {
        assertNotNull(manager.getScheduler());
    }

    @Test
    @DisplayName("Executor accepts and runs submitted tasks")
    void executorRunsSubmittedTasks() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        Future<?> f = manager.getExecutor().submit(() -> ran.set(true));
        f.get(5, TimeUnit.SECONDS);
        assertTrue(ran.get());
    }

    @Test
    @DisplayName("Scheduler runs a task after an initial delay")
    void schedulerRunsTask() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        manager.getScheduler().schedule(latch::countDown, 50, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Scheduled task did not run in time");
    }

    @Test
    @DisplayName("shutdownGracefully() stops the executor and scheduler")
    void shutdownGracefullyStopsServices() throws Exception {
        manager.shutdownGracefully(5, TimeUnit.SECONDS);
        assertTrue(manager.getExecutor().isShutdown(), "Executor should be shut down");
        assertTrue(manager.getScheduler().isShutdown(), "Scheduler should be shut down");
    }

    @Test
    @DisplayName("shutdownGracefully() is idempotent")
    void shutdownGracefullyIsIdempotent() {
        assertDoesNotThrow(() -> {
            manager.shutdownGracefully(1, TimeUnit.SECONDS);
            manager.shutdownGracefully(1, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("addShutdownHook() / removeShutdownHook() do not throw")
    void shutdownHookRegistrationRoundTrip() {
        assertDoesNotThrow(() -> {
            manager.addShutdownHook();
            manager.removeShutdownHook();
        });
    }

    @Test
    @DisplayName("removeShutdownHook() before addShutdownHook() does not throw")
    void removeShutdownHookBeforeAddDoesNotThrow() {
        // New manager — hook not yet added via addShutdownHook(), but the
        // shutdown hook was created in the constructor. Calling remove on a
        // hook that was never added should throw IllegalArgumentException from
        // the JVM, which we swallow. Verify no unhandled exception escapes.
        assertDoesNotThrow(() -> manager.removeShutdownHook());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TestConfig minimalConfig() {
        TestConfig cfg = new TestConfig();
        cfg.setName("test");
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

        RequestTemplate rt = new RequestTemplate();
        rt.setMethod("GET");
        rt.setUrlTemplate("http://localhost/test");
        cfg.setRequestTemplates(List.of(rt));

        TestConfig.CircuitBreakerConfig cb = new TestConfig.CircuitBreakerConfig();
        cb.setEnabled(false);
        cfg.setCircuitBreaker(cb);

        return cfg;
    }
}
